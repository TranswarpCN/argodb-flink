package io.transwarp.connector.argo.sink.serde;

import io.transwarp.connector.argo.sink.ArgoDBTable;
import io.transwarp.connector.argo.sink.ArgoDBUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArgoDBSimpleStringSerializer implements ArgoDBSerializationSchema<String[]> {
  private static final Logger LOG = LoggerFactory.getLogger(ArgoDBSimpleStringSerializer.class);

  private transient ArgoDBTable tableInfo;
  private int bucketId = -1;
  private boolean isBucket = false;

  public boolean isBucket() {
    return isBucket;
  }

  public void setBucket(boolean bucket) {
    isBucket = bucket;
  }

  public ArgoDBSimpleStringSerializer() {

  }

  @Override
  public void open(ArgoDBTable tableInfo) throws Exception {
    this.tableInfo = tableInfo;
    if (tableInfo.getBucketColumns() != null && tableInfo.getBucketColumns().length > 0) {
      isBucket = true;
    }

  }

  @Override
  public byte[][] serialize(String[] originRow) {
    byte[][] row = new byte[originRow.length][];

    try {
      for (int i = 0; i < originRow.length; i++) {
        row[i] = ArgoDBUtils.getValue(originRow[i], tableInfo.getColumnTypes()[i]);
      }
    } catch (Exception e) {
      LOG.error("serialize error: ", originRow);
      e.printStackTrace();
    }

    return row;
  }

  @Override
  public int getBucketId(String[] originRow) {
    if (isBucket && this.bucketId == -1) {
      bucketId = ArgoDBUtils.getBucketId(tableInfo, originRow);
    }
    return bucketId;
  }

  @Override
  public boolean isEndOfStream(String[] nextElement) {
    return false;
  }

  /**
   * 有值的列为false
   *
   * @param next
   * @return
   */
  @Override
  public boolean[] getGhostValue(String[] next) {
    int size = next.length;
    boolean[] res = new boolean[size];
    for (int i = 0; i < size; i++) {
      res[i] = next[i] == null;
    }

    return res;
  }
}
