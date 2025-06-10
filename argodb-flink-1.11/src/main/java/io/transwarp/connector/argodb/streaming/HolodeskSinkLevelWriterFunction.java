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


  private ArgoDBRecordSerializationSchema<IN> recordSerialization;
  private final io.transwarp.connector.argodb.ArgoDBConfig flinkArgoDBConfig;


  public HolodeskSinkLevelWriterFunction(io.transwarp.connector.argodb.ArgoDBConfig argoDBConfig, ArgoDBRecordSerializationSchema<IN> recordSerialization) {
    this.flinkArgoDBConfig = argoDBConfig;
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
    argoDBSinkClient.closeTable(flinkArgoDBConfig.getTableName());
//        argoDBSinkClient.close();
  }

  @Override
  public void initializeState(FunctionInitializationContext context) throws Exception {
    ArgoDBConfig argoDBConfig = new ArgoDBConfig.Builder()
      .url(flinkArgoDBConfig.getUrl())
      .user(flinkArgoDBConfig.getLdapUser())
      .passwd(flinkArgoDBConfig.getLdapPassword())
      .kerberosKeytab(flinkArgoDBConfig.getKerberosKeytab())
      .kerberosUser(flinkArgoDBConfig.getKerberosUser())
      .shivaMasterGroup(flinkArgoDBConfig.getMasterGroup())
      .useExternalAddress(flinkArgoDBConfig.useExternalAddress())
      .build();
    ArgoDBSinkConfig sinkConfig = ArgoDBSinkConfig.builder()
      .argoConfig(argoDBConfig)
      .tableName(flinkArgoDBConfig.getTableName())
      .tmpDirectory(flinkArgoDBConfig.getTmpDirectory())
      .useExternalAddress(flinkArgoDBConfig.useExternalAddress())
      .compression(flinkArgoDBConfig.getCompression())
      .autoFlushDurationSeconds(flinkArgoDBConfig.getFlushDuration() > 0 ? flinkArgoDBConfig.getFlushDuration() : 10)
      .useAutoFlush(flinkArgoDBConfig.isAutoFlush())
      .build();
    System.out.println(sinkConfig.toString());
    System.out.println(argoDBConfig.toString());
    LOG.info("initialize state succeed, {}, {}", sinkConfig, argoDBConfig);

    try {
      argoDBSinkClient = new ArgoDBSinkClient(sinkConfig);
      argoDBSinkClient.init();
      argoDBSinkClient.openTable(flinkArgoDBConfig.getTableName());
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
    argoDBSinkClient.insert(flinkArgoDBConfig.getTableName(), new ArgoDBRow(serialize));
  }
}
