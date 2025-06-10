package io.transwarp.connector.argodb.table;

import io.transwarp.connector.argodb.ArgoSourceConfig;
import io.transwarp.connector.argodb.source.ArgoRowDataInputFormat;
import io.transwarp.connector.argodb.source.ArgoScanInfo;
import io.transwarp.connector.argodb.source.convertor.RowResultRowDataConvertor;
import org.apache.commons.collections.CollectionUtils;
import org.apache.flink.table.api.TableSchema;
import org.apache.flink.table.connector.ChangelogMode;
import org.apache.flink.table.connector.source.DynamicTableSource;
import org.apache.flink.table.connector.source.InputFormatProvider;
import org.apache.flink.table.connector.source.LookupTableSource;
import org.apache.flink.table.connector.source.ScanTableSource;
import org.apache.flink.table.connector.source.abilities.SupportsFilterPushDown;
import org.apache.flink.table.connector.source.abilities.SupportsLimitPushDown;
import org.apache.flink.table.connector.source.abilities.SupportsProjectionPushDown;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.expressions.ResolvedExpression;
import org.apache.flink.table.types.DataType;
import org.apache.flink.table.types.logical.LogicalType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ArgoDynamicSource implements ScanTableSource, SupportsProjectionPushDown,
  SupportsLimitPushDown, LookupTableSource, SupportsFilterPushDown {

  private static final Logger LOG = LoggerFactory.getLogger(ArgoDynamicSource.class);
  private final ArgoSourceConfig sourceConfig;
  private int[] projectedFields; // 要读的列
  private transient List<ResolvedExpression> filters;
  private final TableSchema physicalSchema;

  private final DataType physicalDataType;
  private final int[] valueProjection;

  private RowResultRowDataConvertor rowResultRowDataConvertor;

  private final ArgoScanInfo.Builder scanInfoBuilder;

  public ArgoDynamicSource(ArgoSourceConfig sourceConfig,
                           TableSchema physicalSchema,
                           DataType physicalDataType,
                           int[] valueProjection) {


    this.sourceConfig = sourceConfig;
    this.physicalSchema = physicalSchema;
    this.scanInfoBuilder = ArgoScanInfo.builder();
    this.valueProjection = valueProjection;
    this.physicalDataType = physicalDataType;
  }

  public ArgoDynamicSource(ArgoSourceConfig sourceConfig,
                           RowResultRowDataConvertor rowResultRowDataConvertor,
                           ArgoScanInfo.Builder scanInfoBuilder,
                           TableSchema physicalSchema,
                           DataType physicalDataType,
                           int[] valueProjection) {
    this.sourceConfig = sourceConfig;
    this.rowResultRowDataConvertor = rowResultRowDataConvertor;
    this.physicalSchema = physicalSchema;
    this.scanInfoBuilder = scanInfoBuilder;
    this.valueProjection = valueProjection;
    this.physicalDataType = physicalDataType;
  }

  @Override
  public ChangelogMode getChangelogMode() {
    return ChangelogMode.insertOnly();
  }

  public void buildFilters() {

  }

  @Override
  public ScanRuntimeProvider getScanRuntimeProvider(ScanContext runtimeProviderContext) {
    if (CollectionUtils.isNotEmpty(this.filters)) {
      buildFilters();
    }
    final List<LogicalType> physicalChildren = physicalDataType.getLogicalType().getChildren();

    ArgoRowDataInputFormat inputFormat = new ArgoRowDataInputFormat(
      sourceConfig,
      new RowResultRowDataConvertor(setProjectedFields(), physicalDataType),
      this.filters,
      projectedFields,
      scanInfoBuilder.build());
    return InputFormatProvider.of(inputFormat);
  }

//    private SGFilterContainer buildArgoScanInfoFilters() {
//        if (CollectionUtils.isNotEmpty(this.filters)) {
//            for (ResolvedExpression filter : this.filters) {
//
//                Optional<KuduFilterInfo> kuduFilterInfo = KuduTableUtils.toKuduFilterInfo(filter);
//                if (kuduFilterInfo != null && kuduFilterInfo.isPresent()) {
//                    this.predicates.add(kuduFilterInfo.get());
//                }
//
//            }
//        }
//        return null;
//    }

  private RowData.FieldGetter[] getFieldGetters(
    List<LogicalType> physicalChildren, int[] keyProjection) {
    return Arrays.stream(keyProjection)
      .mapToObj(
        targetField ->
          RowData.createFieldGetter(
            physicalChildren.get(targetField), targetField))
      .toArray(RowData.FieldGetter[]::new);
  }


  public int[] setProjectedFields() {
    if (scanInfoBuilder.getScanColumnsIndex() != null) {
      return scanInfoBuilder.getScanColumnsIndex();
    } else {
      int fieldCount = this.physicalSchema.getFieldCount();
      int[] fields = new int[fieldCount];
      for (int i = 0; i < fieldCount; i++)
        fields[i] = i;
      scanInfoBuilder.setScanColumnsIndex(fields);
      return fields;
    }
  }

  @Override
  public DynamicTableSource copy() {
    return new ArgoDynamicSource(this.sourceConfig, this.rowResultRowDataConvertor, this.scanInfoBuilder, this.physicalSchema, this.physicalDataType, this.valueProjection);
  }

  @Override
  public String asSummaryString() {
    return "argo";
  }

  @Override
  public LookupRuntimeProvider getLookupRuntimeProvider(LookupContext context) {
    return null;
  }

  @Override
  public boolean supportsNestedProjection() {
    return false;
  }

  @Override
  public Result applyFilters(List<ResolvedExpression> filters) {
    this.filters = filters;
    return Result.of(Collections.emptyList(), filters);
  }

  @Override
  public void applyProjection(int[][] projectedFields, DataType producedDataType) {
    this.projectedFields = Arrays.stream(projectedFields).mapToInt(value -> value[0]).toArray();
    scanInfoBuilder.setScanColumnsIndex(this.projectedFields);
  }


  @Override
  public void applyLimit(long limit) {
    scanInfoBuilder.setLimit((int) limit);
  }

}
