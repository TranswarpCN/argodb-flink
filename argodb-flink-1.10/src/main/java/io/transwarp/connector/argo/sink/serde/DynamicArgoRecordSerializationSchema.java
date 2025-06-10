package io.transwarp.connector.argo.sink.serde;

import io.transwarp.connector.argo.sink.ArgoDBTable;
import io.transwarp.connector.argo.sink.ArgoDBUtils;
import org.apache.flink.types.Row;


public class DynamicArgoRecordSerializationSchema implements ArgoDBSerializationSchema<Row> {

  private transient ArgoDBTable tableInfo;
  private String[] originRow;
  private int bucketId = -1;
  private boolean isBucket;

  public DynamicArgoRecordSerializationSchema(String[] originRow) {
    this.originRow = originRow;
  }

  @Override
  public void open(ArgoDBTable tableInfo) {
    this.tableInfo = tableInfo;
    if (tableInfo.getBucketColumns() != null && tableInfo.getBucketColumns().length > 0) {
      isBucket = true;
    }
  }

  @Override
  public byte[][] serialize(Row element) {
    int length = element.getArity();
    byte[][] row = new byte[length][];

    for (int i = 0; i < length; i++) {
      String value = element.getField(i).toString();
      row[i] = ArgoDBUtils.getValue(value, tableInfo.getColumnTypes()[i]);
    }
    return row;
  }

  @Override
  public int getBucketId(Row element) {
    if (isBucket && this.bucketId == -1) {
      bucketId = ArgoDBUtils.getBucketId(tableInfo, element);
    }
    return bucketId;
  }


  @Override
  public boolean isEndOfStream(Row nextElement) {
    return false;
  }

  @Override
  public boolean[] getGhostValue(Row next) {
    return new boolean[0];
  }

  @Override
  public boolean isBucket() {
    return isBucket;
  }
}
