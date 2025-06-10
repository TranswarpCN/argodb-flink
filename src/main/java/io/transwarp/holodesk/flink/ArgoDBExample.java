package io.transwarp.holodesk.flink;

import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ArgoDBExample {
  private static final int N = 5;

  public static void main(String[] args) throws Exception {
    final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
    env.setParallelism(1);

    List<String> dataList = new ArrayList<>();
    for (int i = 0; i < N; ++i) {
      dataList.add(String.valueOf(i));
    }
    DataStream<String> source = env.fromElements(dataList.toArray(new String[0]));

    DataStream<ArgoDBRow> dataStream = source.map(
        new RichMapFunction<String, ArgoDBRow>() {
          @Override
          public ArgoDBRow map(String s) throws Exception {
            String[] input = new String[]{"1"};
            Thread.sleep(1);
            return new ArgoDBRow(input);
          }
        }
    );

    ArgoDBConfig argoDBConfig = ArgoDBConfig.builder()
        .masterGroup("172.26.0.70:9630,172.26.0.71:9630,172.26.0.72:9630")
        .url("jdbc:transwarp2://172.26.0.72:10000/default")
        .tableName("default.test_b")
        .flushDuration(10000)
        .tmpDirectory("/tmp")
        .build();

    dataStream.addSink(new ArgoDBSink(argoDBConfig));

    env.execute("ArgoDB Sink Example");
  }

}

