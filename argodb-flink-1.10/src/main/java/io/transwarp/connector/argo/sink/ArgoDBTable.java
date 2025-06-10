package io.transwarp.connector.argo.sink;

import java.util.HashMap;
import java.util.Map;

public class ArgoDBTable {
  private String shivaTableName;
  private String[] columnTypes;
  private String[] columnNames;

  private String[] bucketColumns;
  private int bucketNum;

  private boolean containsRowKey;

  private Map<String, Integer> columnToId;

  public ArgoDBTable() {

  }

  public String getShivaTableName() {
    return shivaTableName;
  }

  public void setShivaTableName(String shivaTableName) {
    this.shivaTableName = shivaTableName;
  }

  public String[] getColumnTypes() {
    return columnTypes;
  }

  public void setColumnTypes(String[] columnTypes) {
    this.columnTypes = columnTypes;
  }

  public String[] getBucketColumns() {
    return bucketColumns;
  }

  public void setBucketColumns(String[] bucketColumns) {
    this.bucketColumns = bucketColumns;
  }

  public int getBucketNum() {
    return bucketNum;
  }

  public void setBucketNum(int bucketNum) {
    this.bucketNum = bucketNum;
  }

  public boolean isContainsRowKey() {
    return containsRowKey;
  }

  public void setContainsRowKey(boolean containsRowKey) {
    this.containsRowKey = containsRowKey;
  }

  public String[] getColumnNames() {
    return columnNames;
  }

  public void setColumnNames(String[] columnNames) {
    this.columnNames = columnNames;
  }

  public int columnToId(String column) {
    if (columnToId == null) {
      columnToId = new HashMap<>();
      for (int i = 0; i < columnNames.length; i++) {
        columnToId.put(columnNames[i].toLowerCase(), i);
      }
    }
    return columnToId.get(column.toLowerCase());
  }

  public int[] getRowKeyIndex() {
    int[] rowKeyIndex = new int[bucketColumns.length];
    for (int i = 0; i < rowKeyIndex.length; i++) {
      rowKeyIndex[i] = columnToId(bucketColumns[i]);
    }
    return rowKeyIndex;
  }
}
