package io.transwarp.connector.argodb.table;

import io.transwarp.connector.argodb.ArgoDBSinkConfig;
import io.transwarp.connector.argodb.serde.ArgoDBRecordSerializationSchema;
import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.java.ClosureCleaner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

import static org.apache.flink.util.Preconditions.checkNotNull;
import static org.apache.flink.util.Preconditions.checkState;

@PublicEvolving
public class ArgodbSinkBuilder<IN> {

  private static final Logger LOG = LoggerFactory.getLogger(ArgodbSinkBuilder.class);

  private String transactionalIdPrefix = "argodb-sink";

  private static final int MAXIMUM_PREFIX_BYTES = 64000;

  private ArgoDBSinkConfig argodbSinkConfig;
  private ArgoDBRecordSerializationSchema<IN> recordSerializer;

  ArgodbSinkBuilder() {
  }


  public ArgodbSinkBuilder<IN> setArgodbSinkConfig(ArgoDBSinkConfig argodbSinkConfig) {
    checkNotNull(argodbSinkConfig);
    this.argodbSinkConfig = argodbSinkConfig;
    return this;
  }

  public ArgodbSinkBuilder<IN> setRecordSerializer(
    ArgoDBRecordSerializationSchema<IN> recordSerializer) {
    this.recordSerializer = checkNotNull(recordSerializer, "recordSerializer");
    ClosureCleaner.clean(
      this.recordSerializer, ExecutionConfig.ClosureCleanerLevel.RECURSIVE, true);
    return this;
  }

  public ArgodbSinkBuilder<IN> setTransactionalIdPrefix(String transactionalIdPrefix) {
    this.transactionalIdPrefix = checkNotNull(transactionalIdPrefix, "transactionalIdPrefix");
    checkState(
      transactionalIdPrefix.getBytes(StandardCharsets.UTF_8).length
        <= MAXIMUM_PREFIX_BYTES,
      "The configured prefix is too long and the resulting transactionalId might exceed Kafka's transactionalIds size.");
    return this;
  }


  public ArgoDBSink<IN> build() {
    return new ArgoDBSink<>(argodbSinkConfig, transactionalIdPrefix, recordSerializer);
  }
}
