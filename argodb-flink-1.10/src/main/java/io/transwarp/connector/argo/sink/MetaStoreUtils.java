package io.transwarp.connector.argo.sink;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MetaStoreUtils implements AutoCloseable {
  private final Connection connection;

  public MetaStoreUtils(Map<String, String> properties) throws Exception {
    String driverName = "org.apache.hive.jdbc.HiveDriver";
    Class.forName(driverName);
    if (properties.containsKey("user")) {
      connection = DriverManager.getConnection(properties.get("url"), properties.get("user"), properties.get("password"));
    } else {
      connection = DriverManager.getConnection(properties.get("url"));
    }
  }

  public ArgoDBTable getArgoDBTable(String tableName) throws SQLException {
    ArgoDBTable table = new ArgoDBTable();
    String[] args = tableName.toLowerCase().split("\\.");
    if (args.length != 2) {
      throw new RuntimeException("You should use db.table as table name");
    }
    String descSql = "desc " + tableName;
    List<String> columnNames = new ArrayList<>();
    List<String> columnTypes = new ArrayList<>();
    ResultSet result = connection.prepareStatement(descSql).executeQuery();
    while (result.next()) {
      String columnName = result.getString("col_name");
      String columnType = result.getString("data_type");
      if (null != columnName && columnName.startsWith("#")) {
        break;
      } else {
        columnNames.add(columnName.toLowerCase());
        columnTypes.add(columnType.toLowerCase());
      }
    }

    String descFormattedSql = "desc formatted " + tableName;
    result = connection.prepareStatement(descFormattedSql).executeQuery();
    String realTableName = null;

    while (result.next()) {
      String key = result.getString("category").trim();
      String value = result.getString("attribute").trim();
      if (key.equalsIgnoreCase("holodesk.tablename")) {
        if (value.contains(".")) {
          realTableName = value;
        } else {
          realTableName = args[0] + "." + value;
        }
      }
      if (key.equalsIgnoreCase("holodesk.rowkey")) {
        table.setContainsRowKey(true);
      }
      if (key.equalsIgnoreCase("Num Buckets:")) {
        table.setBucketNum(Integer.parseInt(value));
      }
      if (key.equalsIgnoreCase("Bucket Columns:")) {
        if (value.length() > 2) {
          String[] columns = value.substring(1, value.length() - 1).split(",");
          for (int i = 0; i < columns.length; i++) {
            columns[i] = columns[i].trim();
          }
          table.setBucketColumns(columns);
        }
      }
    }
    table.setShivaTableName(realTableName);
    table.setColumnTypes(columnTypes.toArray(new String[0]));
    table.setColumnNames(columnNames.toArray(new String[0]));

    return table;
  }

  @Override
  public void close() throws Exception {
    connection.close();
  }
}
