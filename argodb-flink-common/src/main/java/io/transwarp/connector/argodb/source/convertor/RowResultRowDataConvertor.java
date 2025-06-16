package io.transwarp.connector.argodb.source.convertor;

import io.transwarp.holodesk.core.result.ByteArrayColumnResult;
import io.transwarp.holodesk.core.result.ColumnResult;
import io.transwarp.holodesk.core.result.RowResult;
import io.transwarp.holodesk.core.serde.HolodeskType;
import io.transwarp.holodesk.core.serde.SerDeHelper;
import io.transwarp.holodesk.core.utils.DecimalUtil;
import io.transwarp.org.apache.hadoop.hive.serde2.io.TimestampWritable;
import org.apache.flink.table.data.*;
import org.apache.flink.table.types.DataType;
import org.apache.flink.table.types.logical.LogicalType;
import org.apache.flink.table.types.logical.LogicalTypeRoot;
import org.apache.flink.table.types.logical.RowType;

import java.math.BigDecimal;
import java.sql.Timestamp;

public class RowResultRowDataConvertor implements RowResultConvertor<RowData> {

  private final int[] cols;
  private final RowType rowTypes;

  public RowResultRowDataConvertor(
      int[] cols,
      DataType physicalDataType) {
    this.cols = cols;
    rowTypes = (RowType) physicalDataType.getLogicalType();
  }

  @Override
  public RowData convertor(RowResult rowResult) {
    ColumnResult[] columnResults = rowResult.getColumns();

    GenericRowData values = new GenericRowData(cols.length);

    for (int i = 0; i < cols.length; i++) {
      int pos = cols[i];
      ColumnResult col = columnResults[pos];
      int dataType = col.getDataType();
      ByteArrayColumnResult bytes = (ByteArrayColumnResult) col;
      Object deseValue;
      LogicalType type = rowTypes.getTypeAt(pos);
      if (type.is(LogicalTypeRoot.TIMESTAMP_WITHOUT_TIME_ZONE)) {
        deseValue = SerDeHelper.deserialize(bytes.getData(), bytes.getOffset(), bytes.getLength(), dataType);
        TimestampWritable writable = new TimestampWritable();
        writable.setBinarySortable((byte[]) deseValue, 0);
        Timestamp timestamp = writable.getTimestamp();
        values.setField(i, TimestampData.fromTimestamp(timestamp));
      } else if (type.is(LogicalTypeRoot.DECIMAL)) {
        deseValue = SerDeHelper.deserialize(bytes.getData(), bytes.getOffset(), bytes.getLength(), dataType);
        BigDecimal bigDecimal = DecimalUtil.getBigDecimal((byte[]) deseValue, 0);
        values.setField(i, DecimalData.fromBigDecimal(bigDecimal, bigDecimal.precision(), bigDecimal.scale()));
      } else {
        switch (dataType) {
          case HolodeskType.STRING:
            deseValue = SerDeHelper.deserialize(bytes.getData(), bytes.getOffset(), bytes.getLength(), dataType);
            values.setField(i, StringData.fromString((String) deseValue));
            break;
          case HolodeskType.VARCHAR:
            values.setField(i, StringData.fromBytes(bytes.getData()));
            break;
          default:
            deseValue = SerDeHelper.deserialize(bytes.getData(), bytes.getOffset(), bytes.getLength(), dataType);
            values.setField(i, deseValue);
        }
      }
    }
    return values;
  }
}
