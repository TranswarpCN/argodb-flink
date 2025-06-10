package io.transwarp.connector.argodb;

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.junit.Test;

public class ArgoDBSinkExampleSQL {

  @Test
  public static void main(String[] args) {
    StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
    EnvironmentSettings settings = EnvironmentSettings.newInstance().inBatchMode().build();
    StreamTableEnvironment tEnv = StreamTableEnvironment.create(env, settings);

    String sinkTable = "create table flink_holo_s_partition1\n" +
      "(\n" +
      "id int,\n" +
      "name string,\n" +
      "p1 int\n" +
      ") PARTITIONED BY (p1) WITH (\n" +
      "'connector' = 'argodb',\n" +
      "'table.name' = 'db_flink_to_argo.table_holo_s_partition_1',\n" +
      "'metastore.url' = 'jdbc:hive2://vqa05:10000/default;guardianToken=A3oSujWLOv3yqjyhFQDf-TDH',\n" +
      "'master.group' = '172.22.7.25:9630,172.22.7.26:9630,172.22.7.27:9630',\n" +
      "'shiva2.enable' = 'true',\n" +
      "'compression' = 'snappy',\n" +
      "'use.external.address' = 'true'\n" +
      ");\n";

    tEnv.executeSql(sinkTable);

    String sourceTable = "create table flink_select_holo_1\n" +
      "(\n" +
      "id int,\n" +
      "name string\n" +
      ") WITH (\n" +
      "'connector' = 'argodb',\n" +
      "'table.name' = 'db_flink_to_argo.table_holo_1_read',\n" +
      "'metastore.url' = 'jdbc:hive2://172.22.7.25:10000/default;guardianToken=A3oSujWLOv3yqjyhFQDf-TDH',\n" +
      "'master.group' = '172.22.7.25:9630,172.22.7.26:9630,172.22.7.27:9630',\n" +
      "'shiva2.enable' = 'true',\n" +
      "'compression' = 'snappy',\n" +
      "'use.external.address' = 'true'\n" +
      ");\n";

    tEnv.executeSql(sourceTable);

    tEnv.executeSql("INSERT INTO flink_holo_s_partition1 partition(p1=1) select id, name from flink_select_holo_1");
  }
}
