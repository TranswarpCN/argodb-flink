package io.transwarp.connector.argo.sink;

import io.transwarp.connector.argo.sink.serde.ArgoDBSerializationSchema;
import io.transwarp.connector.argo.sink.serde.ArgoDBSimpleStringSerializer;
import io.transwarp.holodesk.core.common.Row;
import io.transwarp.holodesk.core.common.RowWriterType;
import io.transwarp.holodesk.core.common.SegmentMeta;
import io.transwarp.holodesk.core.common.WriterResult;
import io.transwarp.holodesk.core.options.WriteOptions;
import io.transwarp.holodesk.core.writer.RollingDiskRowSetWriter;
import io.transwarp.shiva.BulkLoadMeta;
import io.transwarp.shiva.bulk.Transaction;
import io.transwarp.shiva.bulk.TransactionHandler;
import io.transwarp.shiva.client.ShivaClient;
import io.transwarp.shiva.common.Options;
import io.transwarp.shiva.common.Status;
import io.transwarp.shiva.holo.ColumnSpec;
import io.transwarp.shiva.holo.Writer;
import io.transwarp.shiva.utils.Bytes;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.state.FunctionInitializationContext;
import org.apache.flink.runtime.state.FunctionSnapshotContext;
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

public class ArgoDBSinkFunction<IN> extends RichSinkFunction<IN> implements CheckpointedFunction {
  private static final Logger LOG = LoggerFactory.getLogger(ArgoDBSinkFunction.class);

  final private ArgoDBConfig argoDBConfig;
  private transient ShivaClient shivaClient;
  private transient io.transwarp.shiva.holo.Table shivaTable;
  private transient RollingDiskRowSetWriter writer;
  private transient Writer shivaWriter;
  private transient Transaction transaction;
  private transient TransactionHandler handler;
  private transient SegmentMeta meta;
  private transient WriteOptions writeOptions;
  private transient Random random;
  private transient byte[] tabletId;
  private boolean isBucket;
  private int bucketId = -1;
  private transient ArgoDBTable tableInfo;
  private transient MetaStoreUtils metaStoreUtils;

  private transient LinkedBlockingQueue<WriterResult> queue;

  private ArgoDBSerializationSchema<IN> serializationSchema;


  public ArgoDBSinkFunction(ArgoDBConfig argoDBConfig) throws Exception {
    this.argoDBConfig = argoDBConfig;
    this.serializationSchema = (ArgoDBSerializationSchema<IN>) new ArgoDBSimpleStringSerializer();
  }

  public ArgoDBSinkFunction(ArgoDBConfig argoDBConfig, ArgoDBSerializationSchema serializationSchema) {
    this.argoDBConfig = argoDBConfig;
    this.serializationSchema = serializationSchema;
  }

  @Override
  public void open(Configuration parameters) throws Exception {
    super.open(parameters);
    newShivaClient(argoDBConfig.getMasterGroup());
    Map<String, String> properties = new HashMap<>();
    properties.put("url", argoDBConfig.getUrl());
    if (argoDBConfig.getJdbcUser() != null)
      properties.put("user", argoDBConfig.getJdbcUser());
    if (argoDBConfig.getJdbcPassword() != null)
      properties.put("password", argoDBConfig.getJdbcPassword());
    metaStoreUtils = new MetaStoreUtils(properties);
    tableInfo = metaStoreUtils.getArgoDBTable(argoDBConfig.getTableName());
    shivaTable = shivaClient.newHoloClient().openTable(tableInfo.getShivaTableName());
    int columnNum = shivaTable.getColumns().size();
    List<ColumnSpec> columns = shivaTable.getSchema().getColumns();
    int[] columnTypes = new int[columnNum];
    int[] columnDataTypes = new int[columnNum];
    int[] columnIds = new int[columnNum];
    for (int i = 0; i < columnNum; i++) {
      columnTypes[i] = columns.get(i).isIndexed() ? 0 : 1;
      columnDataTypes[i] = ArgoDBUtils.shivaTypeToArgoType(columns.get(i).getColumnType());
      columnIds[i] = i;
    }
    meta = new SegmentMeta(shivaTable.getColumns().size(), columnTypes, columnDataTypes, columnIds);
    if (tableInfo.isContainsRowKey()) {
      writeOptions = new WriteOptions().setWriterType(RowWriterType.MainTableRowKeyWriter).setRowKeyIndex(tableInfo.getRowKeyIndex());
    } else {
      writeOptions = new WriteOptions().setWriterType(RowWriterType.MainTableCommonWriter);
    }
    serializationSchema.open(tableInfo);
    isBucket = serializationSchema.isBucket();

    queue = new LinkedBlockingQueue<>();
    ArgoDBCloseCallBack callBack = new ArgoDBCloseCallBack(queue);
    random = new Random();
    bucketId = -1;
    beginTransaction();
  }

  @Override
  public void invoke(IN next, Context context) throws Exception {
    boolean[] ghostValue = serializationSchema.getGhostValue(next);
    byte[][] row = serializationSchema.serialize(next);
    if (isBucket && bucketId == -1) {
      bucketId = serializationSchema.getBucketId(next);
      writeOptions.setHashValue(bucketId);
    }
//        System.out.println("invoke: " + serializationSchema.getBucketId(next) + ", this: " + this);
    writer.appendRow(Row.newBaseInsertRow(row, ghostValue), null);
  }

  @Override
  public void snapshotState(FunctionSnapshotContext functionSnapshotContext) throws Exception {
//        writer.flush();
//        List<WriterResult> results = writer.finish();
//        Status status = shivaWriter.flush();
//        if (!status.ok()) {
//            throw new RuntimeException(status.toString());
//        }
//        ArgoDBUtils.commit(shivaClient, handler, results, tableInfo.getShivaTableName(), tabletId, bucketId);
//        TransactionUtils.commitTransaction(transaction);
//        writer.close();
//        beginTransaction();


//        System.out.println("flush bucket: " + bucketId + ", this: " + this);

    writer.streamFlush();
    int tabletId = bucketId == -1 ? random.nextInt(shivaTable.getTabletNum()) :
      bucketId % shivaTable.getTabletNum();
    ArgoDBUtils.flushToShiva(shivaClient, queue, tableInfo.getShivaTableName(), shivaTable.getTabletNum(),
      tabletId, bucketId);
  }

  @Override
  public void initializeState(FunctionInitializationContext functionInitializationContext) {
  }

  private void beginTransaction() throws Exception {
    transaction = TransactionUtils.beginTransaction(shivaClient, false);
    Set<String> sections = new HashSet<>();
    sections.add(tableInfo.getShivaTableName());
    handler = TransactionUtils.getMultiSectionAppendTransactionHandler(
      transaction, tableInfo.getShivaTableName(), sections);
    BulkLoadMeta bulkLoadMeta = handler.getBulkLoadMeta();
    shivaWriter = shivaClient.newBulkWriter(bulkLoadMeta, 6);
    ArgoFileGenerator fileGenerator = new ArgoFileGenerator(argoDBConfig.getTmpDirectory(), argoDBConfig.getTableName(),
      bulkLoadMeta.getBulkLoadId());
    tabletId = shivaWriter.primaryKeyToPartitionKey(Bytes.fromInt(random.nextInt(shivaTable.getTabletNum())));
    ArgoDBCloseCallBack callBack = new ArgoDBCloseCallBack(queue);
    writer = RollingDiskRowSetWriter.newWriter(meta, fileGenerator, callBack, writeOptions);
  }

  private void newShivaClient(String masterGroup) {
    System.out.println("master group ip: " + masterGroup);
    LOG.info("master group ip: " + masterGroup);
    Options options = new Options();
    options.masterGroup = masterGroup;
    options.rpcTimeoutMs = 120000;
    options.bulkTransactionWaitLockTimeS = 100;
    options.bulkTransactionRetryGetLockIntervalS = 5;
    options.maxErrorRetry = 15;
    shivaClient = ShivaClient.getInstance();
    try {
      shivaClient.start(options);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private long getBulkLoadId() throws Exception {
    Transaction transaction = shivaClient.newBulkTransaction();
    try {
      transaction.setWaitForCommitFinish(true);
      transaction.setWaitCommitFinishTimeoutS(600);
      transaction.setWaitLockTimeoutS(-1);
      Status status = transaction.begin();
      if (!status.ok()) {
        throw new RuntimeException("Begin insert transaction failed, error: " + status.toString());
      }
      Set<String> sections = new HashSet<>();
      sections.add(tableInfo.getShivaTableName());
      TransactionHandler handler = TransactionUtils.getMultiSectionAppendTransactionHandler(
        transaction, tableInfo.getShivaTableName(), sections);
      long bulkLoadId = handler.getBulkLoadMeta().getBulkLoadId();
      TransactionUtils.commitTransaction(transaction);
      return bulkLoadId;
    } catch (Exception e) {
      if (transaction != null) {
        TransactionUtils.abortTransaction(transaction);
      }
      return 0;
    }
  }

}
