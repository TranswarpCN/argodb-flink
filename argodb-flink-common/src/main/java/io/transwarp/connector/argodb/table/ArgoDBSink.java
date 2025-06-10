package io.transwarp.connector.argodb.table;

import io.transwarp.connector.argodb.ArgoDBSinkConfig;
import io.transwarp.connector.argodb.serde.ArgoDBRecordSerializationSchema;
import org.apache.flink.annotation.Internal;
import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.api.connector.sink2.Committer;
import org.apache.flink.api.connector.sink2.StatefulSink;
import org.apache.flink.api.connector.sink2.TwoPhaseCommittingSink;
import org.apache.flink.core.io.SimpleVersionedSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

@PublicEvolving
public class ArgoDBSink<IN>
  implements StatefulSink<IN, HolodeskWriterState>,
  TwoPhaseCommittingSink<IN, HolodeskWriterCommittable> {

  private static final Logger LOG = LoggerFactory.getLogger(ArgoDBSink.class);

  private final ArgoDBRecordSerializationSchema<IN> recordSerializer;

  private final ArgoDBSinkConfig flinkArgoDBSinkConfig;

  private final String transactionalIdPrefix;


  ArgoDBSink(ArgoDBSinkConfig argoDBSinkConfig, String transactionalIdPrefix, ArgoDBRecordSerializationSchema<IN> recordSerializer) {
    this.flinkArgoDBSinkConfig = argoDBSinkConfig;
    this.transactionalIdPrefix = transactionalIdPrefix;
    this.recordSerializer = recordSerializer;
  }


  @Internal
  @Override
  public ArgoDBWriter<IN> createWriter(InitContext context) throws IOException {
    try {
      return new ArgoDBWriter<>(flinkArgoDBSinkConfig, recordSerializer, context, Collections.emptyList());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Internal
  @Override
  public ArgoDBWriter<IN> restoreWriter(InitContext context, Collection<HolodeskWriterState> recoveredState) throws IOException {
    try {
      return new ArgoDBWriter<>(flinkArgoDBSinkConfig, recordSerializer, context, recoveredState);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public SimpleVersionedSerializer<HolodeskWriterState> getWriterStateSerializer() {
    return new HolodeskWriterStateSerializer();
  }

  @Override
  public Committer<HolodeskWriterCommittable> createCommitter() throws IOException {
    return new HoloCommitter();
  }

  @Override
  public SimpleVersionedSerializer<HolodeskWriterCommittable> getCommittableSerializer() {
    return new HoloCommittableSerializer();
  }

  public static <IN> ArgodbSinkBuilder<IN> builder() {
    return new ArgodbSinkBuilder<>();
  }
}
