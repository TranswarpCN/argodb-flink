package io.transwarp.connector.argodb.table;


import com.esotericsoftware.minlog.Log;
import io.transwarp.connector.argodb.ArgoDBSinkConfig;
import io.transwarp.connector.argodb.ArgoSourceConfig;
import io.transwarp.holodesk.sink.ArgoDBSinkTable;
import io.transwarp.holodesk.sink.meta.ArgoDBMetaGenerator;
import io.transwarp.holodesk.sink.meta.SingleTable;
import io.transwarp.holodesk.sink.meta.impl.ArgoDBJdbcMetaGenerator;
import lombok.SneakyThrows;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.configuration.ConfigOption;
import org.apache.flink.configuration.ConfigOptions;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.ReadableConfig;
import org.apache.flink.table.api.TableSchema;
import org.apache.flink.table.connector.format.EncodingFormat;
import org.apache.flink.table.connector.sink.DynamicTableSink;
import org.apache.flink.table.connector.source.DynamicTableSource;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.factories.DynamicTableSinkFactory;
import org.apache.flink.table.factories.DynamicTableSourceFactory;
import org.apache.flink.table.factories.FactoryUtil;
import org.apache.flink.table.factories.SerializationFormatFactory;
import org.apache.flink.table.types.DataType;
import org.apache.flink.table.types.logical.LogicalType;
import org.apache.flink.table.types.logical.LogicalTypeRoot;
import org.apache.flink.table.types.logical.TimestampType;
import org.apache.flink.table.types.logical.utils.LogicalTypeChecks;
import org.apache.flink.table.utils.TableSchemaUtils;
import org.apache.flink.util.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;


public class ArgoDBDynamicTableFactory implements DynamicTableSinkFactory, DynamicTableSourceFactory {

  public static final Logger LOG = LoggerFactory.getLogger(ArgoDBDynamicTableFactory.class);

  private static final ConfigOption<String> SINK_SEMANTIC =
    ConfigOptions.key("sink.semantic")
      .stringType()
      .noDefaultValue()
      .withDescription("Optional semantic when committing.");


  @SneakyThrows
  @Override
  public DynamicTableSink createDynamicTableSink(Context context) {
    final FactoryUtil.TableFactoryHelper helper =
      FactoryUtil.createTableFactoryHelper(
        this, autoCompleteSchemaRegistrySubject(context));

    final ReadableConfig tableOptions = helper.getOptions();

    String tableName = context.getObjectIdentifier().getObjectName();

    ArgoDBSinkConfig flinkArgoDBSinkConfig = ArgoDBSinkConfig.builder()
      .masterGroup(tableOptions.get(ArgoDBConnectorOptions.MASTER_GROUP))
      .tableName(tableOptions.get(ArgoDBConnectorOptions.TABLE_NAME))
      .tmpDirectory(tableOptions.get(ArgoDBConnectorOptions.TMP_DIR))
      .url(tableOptions.get(ArgoDBConnectorOptions.METASTORE))
      .enableShiva2(tableOptions.get(ArgoDBConnectorOptions.SHIVA2_ENABLE))
      .useExternalAddress(tableOptions.get(ArgoDBConnectorOptions.USE_EXTERNAL_ADDRESS))
      .compression(tableOptions.get(ArgoDBConnectorOptions.COMPRESSION_TYPE))
      .ldapUser(tableOptions.get(ArgoDBConnectorOptions.LDAP_USER))
      .ldapPassword(tableOptions.get(ArgoDBConnectorOptions.LDAP_PASSWORD))
      .flushDuration(tableOptions.get(ArgoDBConnectorOptions.SINK_BUFFER_FLUSH_INTERVAL))
      .build();

    io.transwarp.holodesk.sink.ArgoDBConfig argoDBConfig = new io.transwarp.holodesk.sink.ArgoDBConfig.Builder()
      .url(flinkArgoDBSinkConfig.getUrl())
      .user(flinkArgoDBSinkConfig.getLdapUser())
      .passwd(flinkArgoDBSinkConfig.getLdapPassword())
      .kerberosUser(flinkArgoDBSinkConfig.getKerberosUser())
      .kerberosKeytab(flinkArgoDBSinkConfig.getKerberosKeytab())
      .shivaMasterGroup(flinkArgoDBSinkConfig.getMasterGroup())
      .build();

    io.transwarp.holodesk.sink.ArgoDBSinkConfig sinkConfig = io.transwarp.holodesk.sink.ArgoDBSinkConfig.builder()
      .argoConfig(argoDBConfig)
      .tableName(flinkArgoDBSinkConfig.getTableName())
      .tmpDirectory(flinkArgoDBSinkConfig.getTmpDirectory())
      .build();

    Log.info("[ArgoDB] sink config {}", sinkConfig.toString());
    ArgoDBMetaGenerator generator = new ArgoDBJdbcMetaGenerator(sinkConfig);
    SingleTable singleTable = new SingleTable(null);

    ArgoDBSinkTable argoDBTable = generator.getArgoDBSinkTable(sinkConfig.getTableName(), singleTable);

    LOG.info("Create Dynamic Sink table succeed, " + argoDBTable.toString());

    final DataType physicalDataType =
      context.getCatalogTable().getSchema().toPhysicalRowDataType();

    final EncodingFormat<SerializationSchema<RowData>> valueEncodingFormat =
      null;

    final int[] valueProjection = createValueFormatProjection(physicalDataType);
    Integer parallelism = tableOptions.getOptional(FactoryUtil.SINK_PARALLELISM).orElse(1);


    return new ArgoDynamicSink(physicalDataType, valueEncodingFormat, valueProjection, flinkArgoDBSinkConfig,
      parallelism, argoDBTable, context.getCatalogTable(),
      tableOptions.get(ArgoDBConnectorOptions.USE_NEW_SINK));
  }

  private static EncodingFormat<SerializationSchema<RowData>> getValueEncodingFormat(
    FactoryUtil.TableFactoryHelper helper) {
    return helper.discoverOptionalEncodingFormat(
        SerializationFormatFactory.class, FactoryUtil.FORMAT)
      .get();
  }

  private Context autoCompleteSchemaRegistrySubject(Context context) {
    Map<String, String> tableOptions = context.getCatalogTable().getOptions();
    Map<String, String> newOptions = autoCompleteSchemaRegistrySubject(tableOptions);
    if (newOptions.size() > tableOptions.size()) {
      // build a new context
      return new FactoryUtil.DefaultDynamicTableContext(
        context.getObjectIdentifier(),
        context.getCatalogTable().copy(newOptions),
        context.getEnrichmentOptions(),
        context.getConfiguration(),
        context.getClassLoader(),
        context.isTemporary());
    } else {
      return context;
    }
  }

  private static Map<String, String> autoCompleteSchemaRegistrySubject(
    Map<String, String> options) {
    Configuration configuration = Configuration.fromMap(options);
    return configuration.toMap();
  }

  @Override
  public String factoryIdentifier() {
    return "argodb-base";
  }

  @Override
  public Set<ConfigOption<?>> requiredOptions() {
    final Set<ConfigOption<?>> options = new HashSet<>();
    return options;
  }

  @Override
  public Set<ConfigOption<?>> optionalOptions() {
    final Set<ConfigOption<?>> optionalOptions = new HashSet<>();
    optionalOptions.add(ArgoDBConnectorOptions.TABLE_NAME);
    optionalOptions.add(ArgoDBConnectorOptions.MASTER_GROUP);
    optionalOptions.add(ArgoDBConnectorOptions.METASTORE);
    optionalOptions.add(ArgoDBConnectorOptions.TMP_DIR);
    optionalOptions.add(ArgoDBConnectorOptions.SHIVA2_ENABLE);
    optionalOptions.add(ArgoDBConnectorOptions.COMPRESSION_TYPE);
    optionalOptions.add(ArgoDBConnectorOptions.USE_EXTERNAL_ADDRESS);
    return optionalOptions;
  }

  public static int[] createValueFormatProjection(DataType physicalDataType) {
    final LogicalType physicalType = physicalDataType.getLogicalType();
    Preconditions.checkArgument(
      physicalType.getTypeRoot().equals(LogicalTypeRoot.ROW), "Row data type expected.");
    final int physicalFieldCount = LogicalTypeChecks.getFieldCount(physicalType);
    final IntStream physicalFields = IntStream.range(0, physicalFieldCount);

    return physicalFields.toArray();
  }

  @Override
  public DynamicTableSource createDynamicTableSource(Context context) {
    final FactoryUtil.TableFactoryHelper helper =
      FactoryUtil.createTableFactoryHelper(
        this, autoCompleteSchemaRegistrySubject(context));
    ReadableConfig tableOptions = helper.getOptions();

    ArgoSourceConfig argoSourceConfig = ArgoSourceConfig.builder()
      .masterGroup(tableOptions.get(ArgoDBConnectorOptions.MASTER_GROUP))
      .tableName(tableOptions.get(ArgoDBConnectorOptions.TABLE_NAME))
      .url(tableOptions.get(ArgoDBConnectorOptions.METASTORE))
      .enableShiva2(tableOptions.get(ArgoDBConnectorOptions.SHIVA2_ENABLE))
      .useExternalAddress(tableOptions.get(ArgoDBConnectorOptions.USE_EXTERNAL_ADDRESS))
      .ldapUser(tableOptions.get(ArgoDBConnectorOptions.LDAP_USER))
      .ldapPassword(tableOptions.get(ArgoDBConnectorOptions.LDAP_PASSWORD))
      .build();
    TableSchema schema = context.getCatalogTable().getSchema();
    TableSchema physicalSchema = getSchemaWithSqlTimestamp(schema);

    final DataType physicalDataType = context.getPhysicalRowDataType();
    final int[] valueProjection = createValueFormatProjection(physicalDataType);


    return new ArgoDynamicSource(argoSourceConfig, physicalSchema, physicalDataType, valueProjection);
  }

  public static TableSchema getSchemaWithSqlTimestamp(TableSchema schema) {
    TableSchema.Builder builder = new TableSchema.Builder();
    TableSchemaUtils.getPhysicalSchema(schema).getTableColumns().forEach(
      tableColumn -> {
        if (tableColumn.getType().getLogicalType() instanceof TimestampType) {
          builder.field(tableColumn.getName(), tableColumn.getType().bridgedTo(Timestamp.class));
        } else {
          builder.field(tableColumn.getName(), tableColumn.getType());
        }
      });
    return builder.build();
  }

}
