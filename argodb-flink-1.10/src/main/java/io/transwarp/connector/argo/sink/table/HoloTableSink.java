package io.transwarp.connector.argo.sink.table;

import io.transwarp.connector.argo.sink.*;
import io.transwarp.connector.argo.sink.serde.DynamicArgoRecordSerializationSchema;
import org.apache.flink.api.common.functions.Partitioner;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSink;
import org.apache.flink.table.api.TableSchema;
import org.apache.flink.table.catalog.exceptions.CatalogException;
import org.apache.flink.table.sinks.*;
import org.apache.flink.table.types.DataType;
import org.apache.flink.types.Row;

import java.util.*;

public class HoloTableSink implements AppendStreamTableSink<Row> {

  private ArgoDBConfig argoConfig;

  private TableSchema tableSchema;

  public HoloTableSink(ArgoDBConfig argoConfig, TableSchema tableSchema) {
    this.tableSchema = tableSchema;

    this.argoConfig = argoConfig;
  }

  @Override
  public void emitDataStream(DataStream dataStream) {
    consumeDataStream(dataStream);
  }

  @Override
  public DataStreamSink<?> consumeDataStream(DataStream inputStream) {
    Map<String, String> metaProperties = new HashMap<>();
    metaProperties.put("url", argoConfig.getUrl());

    try (MetaStoreUtils metaStoreUtils = new MetaStoreUtils(metaProperties)) {

      ArgoDBTable argoDBTable = metaStoreUtils.getArgoDBTable(argoConfig.getTableName());
      int parallelism = 0;
      if (argoDBTable.getBucketColumns().length > 0 && argoDBTable.getBucketNum() > 0) {
        parallelism = argoDBTable.getBucketNum();
      }
      String[] bucketColumns = argoDBTable.getBucketColumns();


      HashMap<String, Integer> colNamesMap = new HashMap<>();
      String[] columnNames = argoDBTable.getColumnNames();
      for (int i = 0; i < columnNames.length; i++) {
        colNamesMap.put(columnNames[i], i);
      }

      String[] columnTypes = argoDBTable.getColumnTypes();

      int[] bucketIndex = new int[bucketColumns.length];

      DataStream<Row> rowDataDataStream = inputStream.partitionCustom((Partitioner<Integer>)
          (key, numPartitions) -> key % numPartitions,
        (KeySelector<Row, Integer>) argoDBRow -> {
          int bucketId = 0;
          for (int i = 0; i < bucketIndex.length; i++) {
            bucketId = bucketId * 31 + ArgoDBUtils.hashCode(argoDBRow.getField(bucketIndex[i]).toString(), columnTypes[bucketIndex[i]]);
          }
          return (bucketId & Integer.MAX_VALUE);
        });

      DynamicArgoRecordSerializationSchema dynamicArgoRecordSerializationSchema = new DynamicArgoRecordSerializationSchema(null);

      ArgoDBSinkFunction<Row> argoSink = new ArgoDBSinkFunction<>(argoConfig, dynamicArgoRecordSerializationSchema);

      DataStreamSink<Row> rowDataStreamSink = rowDataDataStream.addSink(argoSink);
      if (parallelism != 0) {
        rowDataStreamSink = rowDataStreamSink.setParallelism(parallelism);
      }
      return rowDataStreamSink;
    } catch (Exception e) {
      throw new CatalogException("Failed to query ArgoDB metaStore", e);
    }
  }


  @Override
  public TableSchema getTableSchema() {
    return tableSchema;
  }

  @Override
  public TableSink configure(String[] fieldNames, TypeInformation<?>[] fieldTypes) {
    return null;
  }

  @Override
  public DataType getConsumedDataType() {
    return tableSchema.toRowDataType();
  }
}
