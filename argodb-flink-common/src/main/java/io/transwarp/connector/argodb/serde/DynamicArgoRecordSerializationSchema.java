package io.transwarp.connector.argodb.serde;

import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.data.TimestampData;
import org.apache.flink.table.types.DataType;
import org.apache.flink.table.types.logical.LogicalType;
import org.apache.flink.table.types.logical.LogicalTypeRoot;
import org.apache.flink.table.types.logical.RowType;
import org.apache.flink.types.RowKind;

import java.time.format.DateTimeFormatter;
import java.util.List;


public class DynamicArgoRecordSerializationSchema implements ArgoDBRecordSerializationSchema<RowData> {

  private final RowData.FieldGetter[] valueFieldGetters;

  private final DataType physicalDataType;
  private final boolean upsertMode;
  private final SerializationSchema<RowData> valueSerialization;

  private LogicalType type;

  private final RowType rowTypes;


  public DynamicArgoRecordSerializationSchema(
      DataType physicalDataType,
      SerializationSchema<RowData> valueSerialization,
      RowData.FieldGetter[] valueFieldGetters,
      boolean upsertMode) {
    this.physicalDataType = physicalDataType;
    this.valueSerialization = valueSerialization;
    this.valueFieldGetters = valueFieldGetters;
    this.upsertMode = upsertMode;
    RowType logicalType = (RowType) this.physicalDataType.getLogicalType();
    List<RowType.RowField> fields = logicalType.getFields();
    rowTypes = (RowType) physicalDataType.getLogicalType();
  }

  @Override
  public byte[][] serialize(RowData element, Long timestamp) {
    return new byte[0][];
  }

  @Override
  public String[] serialize(RowData record) {
    int length = record.getArity();
    String[] res = new String[length];
    for (int i = 0; i < length; i++) {
      Object fieldOrNull = valueFieldGetters[i].getFieldOrNull(record);
//            if (fieldOrNull.getClass())
      if (rowTypes.getTypeAt(i).getTypeRoot().equals(LogicalTypeRoot.TIMESTAMP_WITHOUT_TIME_ZONE)) {
        res[i] = castTimestampData((TimestampData) fieldOrNull);
      } else {
        res[i] = String.valueOf(fieldOrNull);
      }
    }
    return res;
  }

  @Override
  public boolean isDelete(RowData element) {
    return element.getRowKind().equals(RowKind.DELETE);
  }

  private String castTimestampData(TimestampData timestampData) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    return formatter.format(timestampData.toLocalDateTime()); // 输出：2021-05-10 12:40:00
  }
}
