package io.transwarp.connector.argodb.streaming;

import io.transwarp.connector.argodb.serde.ArgoDBRecordSerializationSchema;
import io.transwarp.holodesk.sink.ArgoDBConfig;
import io.transwarp.holodesk.sink.ArgoDBRow;
import io.transwarp.holodesk.sink.ArgoDBSinkClient;
import io.transwarp.holodesk.sink.ArgoDBSinkConfig;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.state.FunctionInitializationContext;
import org.apache.flink.runtime.state.FunctionSnapshotContext;
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Deprecated
public class HolodeskSinkLevelWriterFunction<IN> extends RichSinkFunction<IN> implements CheckpointedFunction {

  private static final Logger LOG = LoggerFactory.getLogger(HolodeskSinkLevelWriterFunction.class);

  private transient ArgoDBSinkClient argoDBSinkClient;


  private final ArgoDBRecordSerializationSchema<IN> recordSerialization;
  private final io.transwarp.connector.argodb.ArgoDBSinkConfig flinkArgoDBSinkConfig;


  public HolodeskSinkLevelWriterFunction(io.transwarp.connector.argodb.ArgoDBSinkConfig argoDBSinkConfig, ArgoDBRecordSerializationSchema<IN> recordSerialization) {
    this.flinkArgoDBSinkConfig = argoDBSinkConfig;
    this.recordSerialization = recordSerialization;
  }

  @Override
  public void open(Configuration parameters) throws Exception {
    super.open(parameters);

  }

  @Override
  public void snapshotState(FunctionSnapshotContext context) throws Exception {
    argoDBSinkClient.flush();
  }

  @Override
  public void close() throws Exception {
    System.out.println("close sink function");
    argoDBSinkClient.flush();
    argoDBSinkClient.closeTable(flinkArgoDBSinkConfig.getTableName());
  }

  @Override
  public void finish() throws Exception {
    argoDBSinkClient.flush();
    System.out.println("process batch complete");
  }

  @Override
  public void initializeState(FunctionInitializationContext context) throws Exception {
    ArgoDBConfig argoDBConfig = new ArgoDBConfig.Builder()
      .url(flinkArgoDBSinkConfig.getUrl())
      .user(flinkArgoDBSinkConfig.getLdapUser())
      .passwd(flinkArgoDBSinkConfig.getLdapPassword())
      .kerberosKeytab(flinkArgoDBSinkConfig.getKerberosKeytab())
      .kerberosUser(flinkArgoDBSinkConfig.getKerberosUser())
      .shivaMasterGroup(flinkArgoDBSinkConfig.getMasterGroup())
      .build();
    ArgoDBSinkConfig sinkConfig = ArgoDBSinkConfig.builder()
      .argoConfig(argoDBConfig)
      .tableName(flinkArgoDBSinkConfig.getTableName())
      .tmpDirectory(flinkArgoDBSinkConfig.getTmpDirectory())
      .autoFlushDurationSeconds(-1)
      .useAutoFlush(false)
      .build();
    System.out.println(sinkConfig.toString());
    System.out.println(argoDBConfig.toString());
    LOG.info("initialize state succeed, {}, {}", sinkConfig, argoDBConfig);

    try {
      argoDBSinkClient = new ArgoDBSinkClient(sinkConfig);
      argoDBSinkClient.init();
      argoDBSinkClient.openTable(flinkArgoDBSinkConfig.getTableName());
    } catch (Exception e) {
      e.printStackTrace();
      LOG.error(e.getMessage());
      throw e;
    }
  }

  @Override
  public void invoke(IN value, Context context) throws Exception {
    String[] serialize = recordSerialization.serialize(value);
    LOG.debug("[HoloSink invoke]: {}", String.join(",", serialize));
    System.out.println("[HoloSink invoke]: " + String.join(",", serialize));
    boolean isDelete = recordSerialization.isDelete(value);
    if (isDelete)
      argoDBSinkClient.delete(flinkArgoDBSinkConfig.getTableName(), new ArgoDBRow(serialize));
    else
      argoDBSinkClient.insert(flinkArgoDBSinkConfig.getTableName(), new ArgoDBRow(serialize));
  }
}
