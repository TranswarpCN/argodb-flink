package io.transwarp.connector.argodb.serde;

import org.apache.flink.api.common.serialization.SerializationSchema;


class ArgodbSerializerWrapper<IN> implements SerializationSchema<IN> {

  // Whether the serializer is for key or value.
  private ArgodbSerializer serializer;

  ArgodbSerializerWrapper() {
  }

  @SuppressWarnings("unchecked")
  @Override
  public void open(InitializationContext context) throws Exception {

  }

  @Override
  public byte[] serialize(IN element) {
    return serializer.serialize(element);
  }
}
