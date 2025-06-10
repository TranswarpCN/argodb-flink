package io.transwarp.connector.argodb.serde;

import org.apache.flink.annotation.Internal;
import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.api.common.serialization.SerializationSchema;

import java.io.Serializable;

@PublicEvolving
public interface ArgoDBRecordSerializationSchema<T> extends Serializable {

  default void open(SerializationSchema.InitializationContext context, ArgodbSinkContext tableInfo) {
  }

  @Internal
  interface ArgodbSinkContext {
  }

  static <T> ArgodbRecordSerializationSchemaBuilder<T> builder() {
    return new ArgodbRecordSerializationSchemaBuilder<>();
  }


  byte[][] serialize(T element, Long timestamp);

  String[] serialize(T element);

  default boolean isDelete(T element) {
    return false;
  }
}
