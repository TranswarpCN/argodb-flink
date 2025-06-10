# Argo Flink须知

### 1. flink-conf.yml

flink配置文件, 修改后尽量重启flink

```yaml

# 修改slot数: 并行度不够任务是起不来的
taskmanager.numberOfTaskSlots: 20


# checkpoint配置默认关闭, 需要配置以驱动数据入Argo
execution.checkpointing.interval: 5000
state.checkpoints.num-retained: 20
execution.checkpointing.mode: EXACTLY_ONCE
execution.checkpointing.externalized-checkpoint-retention: RETAIN_ON_CANCELLATION
state.backend: filesystem
state.checkpoints.dir: file:///tmp/chk
state.savepoints.dir: file:///tmp/chk

# 远程Debug 排查问题 
env.java.opts.jobmanager: "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5006"
env.java.opts.taskmanager: "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"


# webui火焰图
rest.flamegraph.enabled: true

# 远程访问webui
rest.bind-address: 0.0.0.0

```

### log4j.properties

使用星环shade后的log配置

```properties
inceptor.root.logger=DEBUG,RFA
log4j.rootLogger=DEBUG,RFA
log4j.appender.console=io.transwarp.org.apache.log4j.ConsoleAppender
log4j.appender.console.target=System.err
log4j.appender.console.layout=io.transwarp.org.apache.log4j.PatternLayout
log4j.appender.console.layout.ConversionPattern=%d{yy/MM/dd HH:mm:ss} %p %c: %m%n
log4j.appender.RFA=io.transwarp.org.apache.log4j.RollingFileAppender
log4j.appender.RFA.File=${log.file}
log4j.appender.RFA.MaxFileSize=10MB
log4j.appender.RFA.MaxBackupIndex=1024
log4j.appender.RFA.layout=io.transwarp.org.apache.log4j.PatternLayout
log4j.appender.RFA.layout.ConversionPattern=%d{yy/MM/dd HH:mm:ss} %p %c: %m%n

```

### 简单standlone模式运行任务

测试用standlone模式运行. 生产环境根据使用场景而定

```shell
cp flink-connector-argo-1.15.4.jar  flink-1.15.4/lib
./bin/stop-cluster.sh
./bin/start-cluster.sh
./bin/sql-client.sh -f sqlfile.sql
```

### sql

#### flink写argodb

```sql
-- 构造源表
CREATE TABLE KafkaTable4 (
   `user` string,
    message string,
    num decimal(22,2)
) WITH (
  'connector' = 'kafka',
  'topic' = 'flink_input_rowkey555',
  'properties.bootstrap.servers' = 'localhost:9092',
  'properties.group.id' = 'newgroupid510',
  'scan.startup.mode' = 'earliest-offset',
  'format' = 'csv'
);

-- argo表的catelog
create table flink_input_rowkey4 (
    `user` string,
    message string,
    num decimal(22,2)
) WITH (
    'connector' = 'argodb',
    'master.group' = '172.18.120.32:29630,172.18.120.33:29630,172.18.120.34:29630',
    'table.name' = 'default.flink_input_rowkey_col3',
    'shiva2.enable' = 'true',
    'compression' = 'snappy',
    'use.external.address' = 'true',
    'metastore.url' = 'jdbc:hive2://172.18.120.33:10010/default'
);

-- 入库任务
insert into flink_input_rowkey4 select * from KafkaTable4;

```

#### flink读argodb

flink读argodb不支持流读

```sql
SET sql-client.execution.result-mode=TABLEAU;
    
create table flink_input_rowkey4 (
    `id` string,
    `num` string
) WITH (
    'connector' = 'argodb',
    'master.group' = '172.18.120.33:39630,172.18.120.32:39630,172.18.120.31:39630',
    'table.name' = 'default.flink_input_rowkey4',
    'shiva2.enable' = 'true',
    'metastore.url' = 'jdbc:hive2://172.18.120.32:10000/default'
);

select id, num from flink_input_rowkey4;
```

### 通用datastream api入argodb

``` java

import io.transwarp.connector.argodb.ArgoDBSinkConfig;
import io.transwarp.connector.argodb.serde.ArgoDBRecordSerializationSchema;
import io.transwarp.connector.argodb.serde.ArgoDBSimpleStringSerializer;
import io.transwarp.connector.argodb.streaming.HolodeskSinkLevelWriterFunction;
import io.transwarp.connector.argodb.table.ArgoDBSink;
import io.transwarp.connector.argodb.table.ArgodbSinkBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;


public class ArgoDBSinkExample {

    public static void main(String[] args) {

        String metastoreUrl = System.getProperty("metastore.url");
        String tableName = System.getProperty("table.name");
        String shivaMasterGroup = System.getProperty("shiva.mastergroup");
        String kafkaTopic = System.getProperty("kafka.topic");
        String kafkaServer = System.getProperty("kafka.bootstrap.server");
        String kafkaGroupName = System.getProperty("kafka.group.name");
        String para = System.getProperty("sink.parallelism");


        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

// 每 1000ms 开始一次 checkpoint
        env.enableCheckpointing(10000);

// 高级选项：

// 设置模式为精确一次 (这是默认值)
        env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);

// 确认 checkpoints 之间的时间会进行 500 ms
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(500);

// Checkpoint 必须在一分钟内完成，否则就会被抛弃
        env.getCheckpointConfig().setCheckpointTimeout(60000);

// 允许两个连续的 checkpoint 错误
        env.getCheckpointConfig().setTolerableCheckpointFailureNumber(2);

// 同一时间只允许一个 checkpoint 进行
        env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);

// 使用 externalized checkpoints，这样 checkpoint 在作业取消后仍就会被保留
        env.getCheckpointConfig().setExternalizedCheckpointCleanup(
                CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);

// 开启实验性的 unaligned checkpoints
        env.getCheckpointConfig().enableUnalignedCheckpoints();


        env.getCheckpointConfig().setCheckpointStorage("file:///tmp/flink115/checkpoints");  // 设置检查点存储路径

        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(kafkaServer)
                .setTopics(kafkaTopic)
                .setGroupId(kafkaGroupName)
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();
        env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source").setParallelism(30);

        DataStream<String[]> dataStream = env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source").setParallelism(30)
                .map(
                        new RichMapFunction<String, String[]>() {
                            @Override
                            public String[] map(String s) throws Exception {
                                return StringUtils.split(s, ",", -1);
                            }
                        }
                ).setParallelism(30);


        ArgoDBConfig flinkArgoDBSinkConfig = ArgoDBConfig.builder()
                .masterGroup(shivaMasterGroup)
                .tableName(tableName)
                .tmpDirectory("/tmp")
                .url(metastoreUrl)
                .enableShiva2(true)
                .useExternalAddress(false)
                .build();
        ArgoDBSimpleStringSerializer stringRecordSerializationSchema = new ArgoDBSimpleStringSerializer();

        HolodeskSinkLevelWriterFunction<String[]> argoSink = new HolodeskSinkLevelWriterFunction<>(flinkArgoDBSinkConfig, stringRecordSerializationSchema);

        dataStream.addSink(argoSink).setParallelism(Integer.parseInt(para));

        try {
            env.execute("argodb-sink");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}


```

### 参数含义

# flink connector argo 参数说明

-DataStreamAPI构造任务时, 通过 io.transwarp.connector.argodb.ArgoDBSinkConfig 构造入库

| datastreamAPI 参数   | flink sql 参数         | 详情                                       | 推荐值                                  |
|--------------------|----------------------|------------------------------------------|--------------------------------------|
| url                | metastore.url        | url 是连接 metastore的连接, 用来获取quark中的table信息 | jdbc:hive2://localhost:10000/default |
| masterGroup        | master.group         | shiva连接地址, 推荐写hostname                   | tdh01:9630,tdh02:9630,tdh03:9630     |
| tableName          | table.name           | 在quark中创建表时定义的表名, 需要带上数据库名, 库名和表名用点分割    | default.flink_holo                   |
| tmpDirectory       | dir                  | flink中写argodb的临时文件地址, 会在flush后删除         | /tmp                                 |
| useExternalAddress | use.external.address | 如果flink客户端节点不支持配置host, 则需要讲次参数设置成true    | true                                 |
| ldapUser           | ldap.user            | ldap认证时的username                         | user                                 |
| ldapPassword       | ldap.password        | ldap认证时的passwrod                         | passwd                               |
| None               | sink.parallelism     | sink任务并行度                                | 1                                    |

