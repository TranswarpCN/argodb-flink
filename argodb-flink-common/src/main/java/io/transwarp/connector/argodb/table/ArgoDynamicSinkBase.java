package io.transwarp.connector.argodb.table;

import io.transwarp.connector.argodb.ArgoDBSinkConfig;
import io.transwarp.connector.argodb.serde.DynamicArgoRecordSerializationSchema;
import io.transwarp.connector.argodb.streaming.HolodeskSinkLevelWriterFunction;
import io.transwarp.holodesk.sink.ArgoDBSinkTable;
import org.apache.flink.annotation.Internal;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSink;
import org.apache.flink.table.api.TableSchema;
import org.apache.flink.table.catalog.CatalogTable;
import org.apache.flink.table.connector.ChangelogMode;
import org.apache.flink.table.connector.Projection;
import org.apache.flink.table.connector.ProviderContext;
import org.apache.flink.table.connector.format.EncodingFormat;
import org.apache.flink.table.connector.sink.DataStreamSinkProvider;
import org.apache.flink.table.connector.sink.DynamicTableSink;
import org.apache.flink.table.connector.sink.abilities.SupportsOverwrite;
import org.apache.flink.table.connector.sink.abilities.SupportsPartitioning;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.types.DataType;
import org.apache.flink.table.types.logical.LogicalType;
import org.apache.flink.table.utils.TableSchemaUtils;
import org.apache.flink.types.RowKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.text.MessageFormat;
import java.util.*;

@Internal
public class ArgoDynamicSinkBase implements DynamicTableSink, SupportsPartitioning, SupportsOverwrite {

  private static final Logger LOG = LoggerFactory.getLogger(ArgoDynamicSinkBase.class);

  final private DataType physicalDataType;
  final private int[] valueProjection;
  final private ArgoDBSinkConfig argoConfig;

  protected final EncodingFormat<SerializationSchema<RowData>> valueEncodingFormat;

  protected final @Nullable Integer parallelism;

  final private ArgoDBSinkTable table;
  private final TableSchema tableSchema;

  /**
   * 区分流批， 目前只支持流
   */
  private final boolean isStream;


  /**
   * 优先级：
   * 分桶表定义的并行度 > sql中定义的并行度 > 默认并行度
   */
  protected final @Nullable
  Integer configuredParallelism;

  boolean useNewSink = false;


  public ArgoDynamicSinkBase(DataType physicalDataType,
                             EncodingFormat<SerializationSchema<RowData>> valueEncodingFormat,
                             int[] valueProjection,
                             ArgoDBSinkConfig argoConfig,
                             @Nullable Integer parallelism,
                             ArgoDBSinkTable table,
                             CatalogTable tableSchema,
                             boolean useNewSink
  ) {
    this.physicalDataType = physicalDataType;
    this.valueEncodingFormat = valueEncodingFormat;
    this.valueProjection = valueProjection;
    this.configuredParallelism = parallelism;
    this.argoConfig = argoConfig;
    this.table = table;
    this.tableSchema = TableSchemaUtils.getPhysicalSchema(tableSchema.getSchema());
    this.isStream = true;
    this.parallelism = parallelism;
    this.useNewSink = useNewSink;
  }

  @Override
  public ChangelogMode getChangelogMode(ChangelogMode requestedMode) {
    return ChangelogMode
      .newBuilder()
      .addContainedKind(RowKind.UPDATE_AFTER)
      .addContainedKind(RowKind.INSERT)
      .addContainedKind(RowKind.DELETE)
      .build();
  }

  @Override
  public SinkRuntimeProvider getSinkRuntimeProvider(Context context) {
    if (useNewSink)
      return createBucketStreamSink(context);
    else return createBucketStreamSinkFunction(context);
  }

  private SinkRuntimeProvider createBucketStreamSinkFunction(Context context) {
    return new ArgoDataStreamSinkProvider(this::consume);
  }

  private DataStreamSink<?> consume(DataStream<RowData> inputStream) {

    LOG.info("ArgoDynamicSink sink table: {}", argoConfig.getTableName());

    int parallelism;

    parallelism = Optional.ofNullable(configuredParallelism).orElse(inputStream.getParallelism());

    final List<LogicalType> physicalChildren = physicalDataType.getLogicalType().getChildren();
    RowData.FieldGetter[] fieldGetters = Arrays.stream(valueProjection)
      .mapToObj(
        targetField ->
          RowData.createFieldGetter(
            physicalChildren.get(targetField), targetField))
      .toArray(RowData.FieldGetter[]::new);

    String[] columns = new String[table.columnToId().size()];

    table.columnToId().forEach((key, value) -> columns[(int) value] = key);

    String sinkName = MessageFormat.format("ArgoDBSink(table={0}, fields={1})", argoConfig.getTableName(), String.join(",", columns));

    DynamicArgoRecordSerializationSchema recordSerialization = new DynamicArgoRecordSerializationSchema(physicalDataType, null, fieldGetters, false);

    HolodeskSinkLevelWriterFunction<RowData> argoSink = new HolodeskSinkLevelWriterFunction<>(argoConfig, recordSerialization);

    LOG.info("ArgoDynamicSink init stream succeed: {}", argoConfig);
    return inputStream.addSink(argoSink).name(sinkName).setParallelism(parallelism);
  }


  /**
   * 返回分区表runtime provider
   *
   * @param context
   * @return
   */
  private SinkRuntimeProvider createBucketStreamSink(Context context) {
    SerializationSchema<RowData> valueSerialization = createSerialization(context, valueEncodingFormat, valueProjection);

    final List<LogicalType> physicalChildren = physicalDataType.getLogicalType().getChildren();

    ArgodbSinkBuilder<RowData> sinkBuilder = ArgoDBSink.builder();

    DynamicArgoRecordSerializationSchema dynamicArgoRecordSerializationSchema =
      new DynamicArgoRecordSerializationSchema(
        physicalDataType,
        valueSerialization,
        getFieldGetters(physicalChildren, valueProjection),
        false);
    final ArgoDBSink<RowData> argoDBSink =
      sinkBuilder
        .setArgodbSinkConfig(argoConfig)
        .setRecordSerializer(dynamicArgoRecordSerializationSchema)
        .build();

    return new DataStreamSinkProvider() {
      @Override
      public DataStreamSink<?> consumeDataStream(
        ProviderContext providerContext, DataStream<RowData> dataStream) {

        Integer para = Optional.ofNullable(configuredParallelism).orElse(dataStream.getParallelism());

        String sinkName = MessageFormat.format("ArgoDBSink(table={0}, fields={1})", argoConfig.getTableName(), String.join(",", table.columnToId().keySet()));

        LOG.info("ArgoDynamicSink init stream succeed: {}", argoConfig);
        DataStreamSink<RowData> end = dataStream.sinkTo(argoDBSink).name(sinkName).setParallelism(1);
        if (para != null) {
          end.setParallelism(1);
        }
        return end;
      }
    };
  }

  private RowData.FieldGetter[] getFieldGetters(
    List<LogicalType> physicalChildren, int[] keyProjection) {
    return Arrays.stream(keyProjection)
      .mapToObj(
        targetField ->
          RowData.createFieldGetter(
            physicalChildren.get(targetField), targetField))
      .toArray(RowData.FieldGetter[]::new);
  }


  @Override
  public DynamicTableSink copy() {
    return null;
  }


  @Override
  public String asSummaryString() {
    return "ArgoDB Table";
  }


  @Override
  public void applyStaticPartition(Map<String, String> partition) {

  }

  private @Nullable SerializationSchema<RowData> createSerialization(
    DynamicTableSink.Context context,
    @Nullable EncodingFormat<SerializationSchema<RowData>> format,
    int[] projection) {
    if (format == null) {
      return null;
    }
    DataType physicalFormatDataType = Projection.of(projection).project(this.physicalDataType);
    return format.createRuntimeEncoder(context, physicalFormatDataType);
  }


  @Override
  public boolean requiresPartitionGrouping(boolean supportsGrouping) {
    return SupportsPartitioning.super.requiresPartitionGrouping(supportsGrouping);
  }

  @Override
  public void applyOverwrite(boolean overwrite) {

  }
}
