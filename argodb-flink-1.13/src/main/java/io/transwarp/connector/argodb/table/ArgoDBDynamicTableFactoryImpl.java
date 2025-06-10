package io.transwarp.connector.argodb.table;

import org.apache.flink.table.connector.source.DynamicTableSource;

public class ArgoDBDynamicTableFactoryImpl extends ArgoDBDynamicTableFactory {
  @Override
  public String factoryIdentifier() {
    return "argodb";
  }

  @Override
  public DynamicTableSource createDynamicTableSource(Context context) {
    throw new RuntimeException("connector='argodb' does not support reading, please use jdbc to read/lookup argodb");
  }
}
