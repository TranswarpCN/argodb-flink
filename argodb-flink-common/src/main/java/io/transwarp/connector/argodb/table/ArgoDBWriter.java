package io.transwarp.connector.argodb.table;


import io.transwarp.connector.argodb.ShivaEnv;
import io.transwarp.connector.argodb.ShivaNewEnv;
import io.transwarp.connector.argodb.serde.ArgoDBRecordSerializationSchema;
import io.transwarp.connector.argodb.streaming.HolodeskSinkLevelWriterFunction;
import io.transwarp.holodesk.connector.index.fulltext.utils.ArgoLocalResult;
import io.transwarp.holodesk.sink.*;
import io.transwarp.holodesk.sink.meta.SingleTable;
import io.transwarp.holodesk.sink.meta.impl.ArgoDBJdbcMetaGenerator;
import io.transwarp.holodesk.sink.utils.ArgoDB2Utils;
import io.transwarp.holodesk.transaction.shiva2.TransactionUtilsShiva2;
import io.transwarp.holodesk.utils.shiva2.MetaCommitUtilsShiva2;
import io.transwarp.shiva2.BulkTransactionStatus;
import io.transwarp.shiva2.bulk.RWTransaction;
import io.transwarp.shiva2.bulk.ShivaAcquireLocksOptions;
import io.transwarp.shiva2.common.*;
import io.transwarp.shiva2.exception.ShivaException;
import io.transwarp.shiva2.shiva.BulkLoadMeta;
import io.transwarp.shiva2.shiva.BulkLoadType;
import io.transwarp.shiva2.shiva.bulk.Transaction;
import io.transwarp.shiva2.shiva.bulk.TransactionHandler;
import io.transwarp.shiva2.shiva.client.ShivaClient;
import io.transwarp.shiva2.shiva.common.Status;
import io.transwarp.shiva2.shiva.holo.HoloClient;
import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.api.common.operators.ProcessingTimeService;
import org.apache.flink.api.connector.sink2.Sink;
import org.apache.flink.api.connector.sink2.StatefulSink;
import org.apache.flink.api.connector.sink2.TwoPhaseCommittingSink;
import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.groups.SinkWriterMetricGroup;
import org.apache.flink.runtime.checkpoint.CheckpointIDCounter;
import org.apache.flink.shaded.guava30.com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Stream;

import static org.apache.flink.util.Preconditions.checkNotNull;

@PublicEvolving
public class ArgoDBWriter<IN> implements StatefulSink.StatefulSinkWriter<IN, HolodeskWriterState>, TwoPhaseCommittingSink.PrecommittingSinkWriter<IN, HolodeskWriterCommittable> {
  private final SinkWriterMetricGroup metricGroup;
  private final Counter numRecordsOutCounter;
  private final Counter numBytesOutCounter;
  private final Counter numRecordsOutErrorsCounter;
  private final ProcessingTimeService timeService;

  private static final Logger LOG = LoggerFactory.getLogger(HolodeskSinkLevelWriterFunction.class);

  private final ArgoDBSinkConfig sinkConfig;
  private final ArgoDBJdbcMetaGenerator metaGenerator;
  private static Timer fileCleanTask;
  private static Timer autoFlushTask;
  private ShivaClient tddms2Client;

  private io.transwarp.shiva2.client.ShivaClient tddms2NewClient;
  private final HoloClient holoClient;
  private transient Transaction transaction;
  private transient RWTransaction rwTransaction;

  private final HolodeskWriterState holodeskWriterState;

  private final ArgoDBRecordSerializationSchema<IN> recordSerializer;

  private transient TransactionHandler transactionHandler;
  private ArgoDBLocalWriteClient writeClient;
  private final SingleTable singleTable;
  private Set<String> holoSectionSet = new HashSet<>();

  private final io.transwarp.connector.argodb.ArgoDBSinkConfig flinkArgoDBSinkConfig;

  private ShivaContext<io.transwarp.shiva2.bulk.TransactionHandler> handlerCtx;

  private io.transwarp.shiva2.bulk.TransactionHandler transactionHandlerForNewShiva;

  private final long lastCheckpointId;

  private final boolean isRK;

  private final ShivaAcquireLocksOptions locksOptions;

  private final ArgoDBSinkTable argoDBTable;


  /**
   * 初始化writer时, 如果
   * 1. 从上次任务中恢复
   * 2. 从头开始
   *
   * @param flinkConfig
   * @param sinkInitContext
   */
  public ArgoDBWriter(io.transwarp.connector.argodb.ArgoDBSinkConfig flinkConfig, ArgoDBRecordSerializationSchema<IN> recordSerializer, Sink.InitContext sinkInitContext, Collection<HolodeskWriterState> recoveredStates) {
    try {
      // setup metric
      checkNotNull(sinkInitContext, "sinkInitContext");
      this.timeService = sinkInitContext.getProcessingTimeService();
      this.metricGroup = sinkInitContext.metricGroup();
      this.numBytesOutCounter = metricGroup.getIOMetricGroup().getNumBytesOutCounter();
      this.numRecordsOutCounter = metricGroup.getIOMetricGroup().getNumRecordsOutCounter();
      this.numRecordsOutErrorsCounter = metricGroup.getNumRecordsOutErrorsCounter();
      this.recordSerializer = recordSerializer;

      // set up flink config
      this.flinkArgoDBSinkConfig = flinkConfig;
      ArgoDBConfig argoDBConfig = new ArgoDBConfig
        .Builder()
        .url(flinkArgoDBSinkConfig.getUrl())
        .user(flinkArgoDBSinkConfig.getLdapUser())
        .passwd(flinkArgoDBSinkConfig.getLdapPassword())
        .kerberosUser(flinkArgoDBSinkConfig.getKerberosUser())
        .kerberosKeytab(flinkArgoDBSinkConfig.getKerberosKeytab())
        .shivaMasterGroup(flinkArgoDBSinkConfig.getMasterGroup())
//                .useExternalAddress(flinkArgoDBConfig.useExternalAddress())
        .build();
      sinkConfig = ArgoDBSinkConfig.builder().argoConfig(argoDBConfig).tableName(flinkArgoDBSinkConfig.getTableName()).tmpDirectory(flinkArgoDBSinkConfig.getTmpDirectory()).build();
      LOG.info("initialize state succeed, {}, {}", sinkConfig.toString(), argoDBConfig.toString());
      this.metaGenerator = new ArgoDBJdbcMetaGenerator(sinkConfig);

      String tddmsMasterGroup = metaGenerator.getTddmsMasterGroup(true);

      this.tddms2Client = ShivaEnv.getShiva2Client(tddmsMasterGroup);
      this.tddms2NewClient = ShivaNewEnv.getShiva2Client(tddmsMasterGroup);
      this.holoClient = tddms2Client.newHoloClient();

      // generate argodb table and open table
      singleTable = new SingleTable(this.tddms2Client);

      argoDBTable = metaGenerator.getArgoDBSinkTable(sinkConfig.getTableName(), singleTable);

      initFileCleanTask();
//        initAutoFlushTask();
      openTable(this.flinkArgoDBSinkConfig.getTableName());

      isRK = argoDBTable.containsRK();

      //global transaction related
      ShivaTableIdentifier tableIdentifier = ShivaTableIdentifier.newTableIdentifier(ShivaTableName.newName(argoDBTable.holodeskTableName()));
      locksOptions = new ShivaAcquireLocksOptions().setTableIdentifier(tableIdentifier);
      if (isRK) {
        locksOptions.setLockType(ShivaTransactionLockType.APPEND);
      } else {
        locksOptions.setLockType(ShivaTransactionLockType.MUTATE);
      }

      // generate serializier todo


      // generate last checkpoint id
      // 测试方法: 完成一次checkpoint后中止任务, 重新启动任务后观察是否拿到正确的checkpointId和transactionId
      this.lastCheckpointId = sinkInitContext.getRestoredCheckpointId().orElse(CheckpointIDCounter.INITIAL_CHECKPOINT_ID - 1);

      // if holo(not rk)
      if (!isRK)
        abortLingeringTransactions(checkNotNull(recoveredStates, "recoveredStates"));

      // init transaction
      openNewTxn(); //todo 是否需要手动从状态中恢复
      holodeskWriterState = new HolodeskWriterState(rwTransaction.getTransactionId(), this.tddms2Client, null);
    } catch (Exception e) {
      if (this.tddms2NewClient != null) this.tddms2NewClient.close();
      if (this.tddms2Client != null) this.tddms2Client.close();
      throw new RuntimeException(e);
    }

    initAutoFlushTask();
  }

  void abortLingeringTransactions(Collection<HolodeskWriterState> recoveredStates) throws ShivaException {
    Optional<HolodeskWriterState> lastStateOpt = recoveredStates.stream().findFirst();
    if (lastStateOpt.isPresent()) {
      HolodeskWriterState lastState = lastStateOpt.get();
      Long transactionId = lastState.getTransactionalId();
      LOG.info("Abort transaction {} as job stopped or failed", transactionId);
      try {
        BulkTransactionStatus transactionStatus = this.tddms2NewClient.admin().getTransactionStatus(transactionId);
        switch (transactionStatus) {
          case kCommittedBulkTransaction:
            LOG.info("kCommittedBulkTransaction, Nothing do with transaction [{}] as it is committed", transactionId);
            break;
          case kActiveBulkTransaction:
            LOG.info("kActiveBulkTransaction, Abort transaction[{}] as it is active: begin but not commit", transactionId);
            tddms2NewClient.admin().removeTransaction(transactionId, false);
            break;
          case kAbortedBulkTransaction:
            LOG.info("kActiveBulkTransaction, Nothing do with transaction [{}] as it is aborted", transactionId);
            break;
          case kCommitFailedBulkTransaction:
            LOG.info("kActiveBulkTransaction, [{}] is committed failed last time", transactionId);
            tddms2NewClient.admin().removeTransaction(transactionId, false);
            break;
          default:
            LOG.info("Nothing do with transaction[${transactionId}] status is ${status}");
        }
        LOG.info("Remove the transaction[${transactionId}] after abort");
      } catch (ShivaException e) {
        processShivaException(e, transactionId);
      } catch (Exception e) {
        throw e;
      }
    }
  }

  private void processShivaException(ShivaException e, Long transactionId) throws ShivaException {
    if (e.getErrorCode() == ErrorCode.TRANSACTION_NOT_EXIST.getCode()) {
      LOG.warn("Transaction[{}] may be committed or may not be opened", transactionId);
    } else {
      LOG.error("Get transaction [{}] status failed as {}", transactionId, e.getMessage());
      throw e;
    }
  }

  public void openTable(String fullTableName) throws Exception {
    ArgoDB2Utils.isIllegalFullTableName(fullTableName);
    // todo depre SingleTable
//    singleTable.open(fullTableName, this.metaGenerator, this.holoClient, this.sinkConfig);
    writeClient = singleTable.getWriteClient();

    this.holoSectionSet = singleTable.getHoloSectionSet();
  }


  private void initFileCleanTask() {
    if (sinkConfig.isUseAutoFileClean() && sinkConfig.getFileCleanTtlSeconds() > 0 && sinkConfig.getFileCleanIntervalSeconds() > 0) {
      fileCleanTask = new Timer("file-clean-timer-" + System.nanoTime());
      fileCleanTask.schedule(new TimerTask() {
        @Override
        public void run() {
          String fileNamePattern = "^holodesk_.*_.{36}_[0-9]+_1_[0-9]+$";
          String directory = sinkConfig.getTmpDirectory();
          long expiredTime = (new Date()).getTime() - sinkConfig.getFileCleanTtlSeconds() * 1000;
          try (Stream<Path> paths = Files.list(Paths.get(directory))) {
            paths.forEach(path -> {
              try {
                BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
                String fileName = path.getFileName().toString();
                long fileCreateTime = attr.creationTime().toMillis();

                if (fileName.matches(fileNamePattern) && fileCreateTime != 0 && fileCreateTime < expiredTime) {
                  Files.deleteIfExists(path);
                }
              } catch (Exception e) {
                // ignore
              }
            });
          } catch (Exception e) {
            // ignore
          }
        }
      }, 1000L, sinkConfig.getFileCleanIntervalSeconds() * 1000L);
    }
  }

  /**
   * 定时发送文件给shiva, 防止数据过大
   */
  private void initAutoFlushTask() {
    if (sinkConfig.isUseAutoFlush() && sinkConfig.getAutoFlushDurationSeconds() > 0) {
      autoFlushTask = new Timer("auto-flush-timer-" + System.nanoTime());
      autoFlushTask.schedule(new TimerTask() {
        @Override
        public void run() {
          try {
            tableFlush();
          } catch (Exception e) {
            LOG.info("[SINK] Flush table [{}] failed.", flinkArgoDBSinkConfig.getTableName(), e);
          }
        }
      }, 1000L, sinkConfig.getAutoFlushDurationSeconds() * 1000L);
    }
  }


  @Override
  public void write(IN input, Context context) throws IOException, InterruptedException {
    // if holo
    // if rk

    try {
      writeClient.insert(ArgoDB2Utils.convertToSinkRow(Collections.singletonList(new ArgoDBRow(recordSerializer.serialize(input)))));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

//        try {
//            getTable(flinkArgoDBConfig.getTableName()).getWriteClient().upsert(ArgoDB2Utils.convertToSinkRow(Collections.singletonList(new ArgoDBRow(parseRowData((RowData) input)))));
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }

    //if delete
  }

  @Override
  public void flush(boolean endOfInput) throws IOException, InterruptedException {
    try {
      ArgoLocalResult localResult = writeClient.flush();
      if (localResult == null) {
        return;
      }
      MetaCommitUtilsShiva2.commit(this.tddms2Client, this.transactionHandler, localResult.getHolodeskLocalResult().getResult(), this.holoSectionSet.toArray(new String[0]), BulkLoadType.kInsertBulkLoad);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void tableFlush() throws Exception {
    ArgoLocalResult localResult = writeClient.flush();
    if (localResult == null) {
      return;
    }
    MetaCommitUtilsShiva2.commit(this.tddms2Client, this.transactionHandler, localResult.getHolodeskLocalResult().getResult(), this.holoSectionSet.toArray(new String[0]), BulkLoadType.kInsertBulkLoad);

    updateWriterClientMeta();
  }

  private void updateWriterClientMeta() throws ShivaException, io.transwarp.shiva2.shiva.exception.ShivaException {
    handlerCtx = rwTransaction.acquireLocks(locksOptions);
    checkShivaResp(handlerCtx.getStatus());

    transactionHandlerForNewShiva = handlerCtx.getResource();
    transactionHandler = TransactionHandler.newWriteSuccess("default", argoDBTable.holodeskTableName(), holoSectionSet, new Status(), transactionHandlerForNewShiva);

    BulkLoadMeta bulkLoadMeta = this.transactionHandler.getBulkLoadMeta();
    writeClient.setBulkLoadMeta(bulkLoadMeta);
  }

  private void test() {
    long transactionId = transaction.getTransactionId();


  }


  /**
   * @param checkpointId
   * @return
   * @throws IOException
   */
  @Override
  public List<HolodeskWriterState> snapshotState(long checkpointId) throws IOException {
    //begin transaction
    if (!isRK) {
      try {
//                openNewTxn();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    return ImmutableList.of(holodeskWriterState);
  }

  private void checkShivaResp(io.transwarp.shiva2.common.Status status) throws ShivaException {
    if (!status.ok()) {
      LOG.error("[ARGODB] acquire transaction locks failed, {}", handlerCtx.getStatus());
      throw new ShivaException(handlerCtx.getStatus().appendMsg(", acquire transaction locks failed"));
    }
  }


  private void openNewTxn() throws Exception {
    try {
      rwTransaction = tddms2NewClient.newBulkRWTransaction();
      io.transwarp.shiva2.common.Status status = rwTransaction.begin();
      checkShivaResp(status);
      updateWriterClientMeta();
    } catch (Exception e) {
      if (this.transaction != null) {
        TransactionUtilsShiva2.abortTransaction(this.transaction);
      }
      cleanTableTxn();
      throw e;
    }
  }

  private void cleanTableTxn() {
    this.transaction = null;
    this.transactionHandler = null;
    writeClient.clearBulkLoadMeta();
  }

  @Override
  public Collection<HolodeskWriterCommittable> prepareCommit() throws IOException, InterruptedException {
    if (true) {
//            List<HolodeskWriterCommittable> committables = Collections.singletonList(HolodeskWriterCommittable.of(tddms2Client, rwTransaction));
//            LOG.debug("Committing {} committables.", committables);
//            return committables;
      return null;
    }
    return Collections.emptyList();
  }

  @Override
  public void close() throws Exception {
    this.tddms2Client.close();
    this.tddms2NewClient.close();

  }
}
