package io.transwarp.connector.argodb.table;

import io.transwarp.connector.argodb.streaming.HolodeskSinkLevelWriterFunction;
import io.transwarp.holodesk.sink.ArgoDBConfig;
import io.transwarp.holodesk.sink.ArgoDBSinkConfig;
import io.transwarp.holodesk.sink.ArgoDBSinkTable;
import io.transwarp.holodesk.sink.meta.ArgoDBMetaGenerator;
import io.transwarp.holodesk.sink.meta.SingleTable;
import io.transwarp.holodesk.sink.meta.impl.ArgoDBJdbcMetaGenerator;
import org.apache.hadoop.hive.ql.dblink.DbLinkException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.text.MessageFormat;

public class ArgoTable {

  private final ArgoDBSinkTable table;

  private final ArgoDBSinkConfig sinkConfig;

  public ArgoTable(ArgoDBSinkConfig sinkConfig) throws SQLException, DbLinkException, Exception {
    LOG.info("[ArgoDB] sink config {}", sinkConfig.toString());
    ArgoDBMetaGenerator generator = new ArgoDBJdbcMetaGenerator(sinkConfig);
    SingleTable singleTable = new SingleTable(null);

    table = generator.getArgoDBSinkTable(sinkConfig.getTableName(), singleTable);

    this.sinkConfig = sinkConfig;
  }

  public ArgoTable(String tableName, String metastoreUrl, String masterGroups) throws SQLException, DbLinkException, Exception {

    ArgoDBConfig argoDBConfig = new ArgoDBConfig.Builder()
      .url(metastoreUrl)
      .shivaMasterGroup(masterGroups)
      .build();

    ArgoDBSinkConfig sinkConfig = ArgoDBSinkConfig.builder()
      .argoConfig(argoDBConfig)
      .tableName(tableName)
      .build();

    LOG.info("[ArgoDB] sink config {}", sinkConfig.toString());
    ArgoDBMetaGenerator generator = new ArgoDBJdbcMetaGenerator(sinkConfig);
    SingleTable singleTable = new SingleTable(null);

    table = generator.getArgoDBSinkTable(sinkConfig.getTableName(), singleTable);

    this.sinkConfig = sinkConfig;
  }


  private static final Logger LOG = LoggerFactory.getLogger(ArgoTable.class);

  public String[] getTableNames() throws DbLinkException, SQLException {

    String[] columns = new String[table.columnToId().size()];

    table.columnToId().forEach((key, value) -> columns[(int) value] = key);

    String sinkInfo = MessageFormat.format("ArgoDBSink(table={0}, fields={1})", sinkConfig.getTableName(), String.join(",", columns));

    LOG.debug(sinkInfo);

    return columns;
  }

  public String[] getTypes() {
    return table.columnTypes();
  }
}
