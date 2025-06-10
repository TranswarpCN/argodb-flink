package io.transwarp.connector.argodb.table;

import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSink;
import org.apache.flink.table.connector.sink.DataStreamSinkProvider;
import org.apache.flink.table.data.RowData;

import java.util.function.Function;

public class ArgoDataStreamSinkProvider implements DataStreamSinkProvider {

  private final Function<DataStream<RowData>, DataStreamSink<?>> producer;

  public ArgoDataStreamSinkProvider(Function<DataStream<RowData>, DataStreamSink<?>> producer) {
    this.producer = producer;
  }

  @Override
  public DataStreamSink<?> consumeDataStream(
    DataStream<RowData> dataStream) {
    return producer.apply(dataStream);
  }
}
