package io.transwarp.holodesk.flink;

import io.transwarp.holodesk.core.common.Row;
import io.transwarp.holodesk.core.common.RowWriterType;
import io.transwarp.holodesk.core.common.SegmentMeta;
import io.transwarp.holodesk.core.common.WriterResult;
import io.transwarp.holodesk.core.options.WriteOptions;
import io.transwarp.holodesk.core.writer.RollingDiskRowSetWriter;
import io.transwarp.shiva.client.ShivaClient;
import io.transwarp.shiva.common.Options;
import io.transwarp.shiva.holo.ColumnSpec;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.util.Preconditions;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ArgoDBSink extends RichSinkFunction<ArgoDBRow> {

  private final ArgoDBConfig argoDBConfig;
  private transient ShivaClient shivaClient;
  private transient io.transwarp.shiva.holo.Table shivaTable;
  private transient RollingDiskRowSetWriter writer;
  private transient SegmentMeta meta;
  private transient Random random;
  private transient int bucketId = -1;
  private transient Timer timerTask;
  private transient LinkedBlockingQueue<WriterResult> queue;
  private transient Lock lock;
  private transient ArgoDBTable tableInfo;
  private transient MetaStoreUtils metaStoreUtils;
  private transient WriteOptions writeOptions;

  public ArgoDBSink(ArgoDBConfig argoDBConfig) {
    this.argoDBConfig = Preconditions.checkNotNull(argoDBConfig, "ArgoDB client config should not be null");
  }

  @Override
  public void open(Configuration parameters) throws Exception {
    super.open(parameters);
    lock = new ReentrantLock();
    random = new Random();
    queue = new LinkedBlockingQueue<>();
    bucketId = -1;
    newShivaClient(argoDBConfig.getMasterGroup());
    Map<String, String> properties = new HashMap<>();
    properties.put("url", argoDBConfig.getUrl());
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
      writeOptions = new WriteOptions()
          .setWriterType(RowWriterType.MainTableRowKeyWriter)
          .setRowKeyIndex(tableInfo.getRowKeyIndex());
    } else {
      writeOptions = new WriteOptions()
          .setWriterType(RowWriterType.MainTableCommonWriter);
    }
    ArgoFileGenerator fileGenerator = new ArgoFileGenerator(argoDBConfig.getTmpDirectory(),
        tableInfo.getShivaTableName(), 1);
    ArgoDBCloseCallBack callBack = new ArgoDBCloseCallBack(queue);
    writer = RollingDiskRowSetWriter.newWriter(meta, fileGenerator, callBack, writeOptions);
    timerTask = new Timer("flush-timer-" + System.nanoTime());
    timerTask.schedule(new TimerTask() {
      @Override
      public void run() {
        try {
          lock.lock();
          writer.streamFlush();
          lock.unlock();
          int tabletId = bucketId == -1 ? random.nextInt(shivaTable.getTabletNum()) :
              bucketId % shivaTable.getTabletNum();
          ArgoDBUtils.flushToShiva(shivaClient, queue, tableInfo.getShivaTableName(), shivaTable.getTabletNum(),
              tabletId, bucketId);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    }, 1000, argoDBConfig.getFlushDuration());
  }

  @Override
  public void invoke(ArgoDBRow dataPoint, Context context) throws Exception {
    String[] originRow = dataPoint.getRow();
    byte[][] row = new byte[originRow.length][];
    for (int i = 0; i < originRow.length; i++) {
      row[i] = ArgoDBUtils.getValue(originRow[i], tableInfo.getColumnTypes()[i]);
    }
    if (tableInfo.getBucketColumns() != null && tableInfo.getBucketColumns().length > 0) {
      int tmpBucketId = ArgoDBUtils.getBucketId(tableInfo, originRow);
      if (bucketId == -1) {
        bucketId = tmpBucketId;
      } else {
        if (bucketId != tmpBucketId) {
          throw new RuntimeException("Bucket id is not the same in the same task");
        } else {
          // do nothing
        }
      }
    }
    writeOptions.setHashValue(bucketId);
    lock.lock();
    writer.appendRow(Row.newBaseInsertRow(row), null);
    lock.unlock();
  }

  @Override
  public void close() throws Exception {
    timerTask.cancel();
    lock.lock();
    writer.streamFlush();
    lock.unlock();
    int tabletId = bucketId == -1 ? random.nextInt(shivaTable.getTabletNum()) :
        bucketId % shivaTable.getTabletNum();
    ArgoDBUtils.flushToShiva(shivaClient, queue, tableInfo.getShivaTableName(), shivaTable.getTabletNum(),
        tabletId, bucketId);
    writer.close();
  }

  private void newShivaClient(String masterGroup) {
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
}
