package io.transwarp.connector.argodb.serde;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.api.common.serialization.SerializationSchema;

import javax.annotation.Nullable;
import java.util.Map;

import static org.apache.flink.util.Preconditions.checkNotNull;
import static org.apache.flink.util.Preconditions.checkState;

@PublicEvolving
public class ArgodbRecordSerializationSchemaBuilder<IN> {

  @Nullable
  private SerializationSchema<? super IN> valueSerializationSchema;


  public <T extends IN> ArgodbRecordSerializationSchemaBuilder<T> setValueSerializationSchema(
      SerializationSchema<T> valueSerializationSchema) {
    checkValueSerializerNotSet();
    ArgodbRecordSerializationSchemaBuilder<T> self = self();
    self.valueSerializationSchema = checkNotNull(valueSerializationSchema);
    return self;
  }

  @SuppressWarnings("unchecked")
  private <T extends IN> ArgodbRecordSerializationSchemaBuilder<T> self() {
    return (ArgodbRecordSerializationSchemaBuilder<T>) this;
  }

  public <T extends IN> ArgodbRecordSerializationSchemaBuilder<T> setArgodbValueSerializer(
      Class<? extends ArgodbSerializer<? super T>> valueSerializer) {
    checkValueSerializerNotSet();
    ArgodbRecordSerializationSchemaBuilder<T> self = self();
    self.valueSerializationSchema = new ArgodbSerializerWrapper<>();
    return self;
  }

  public <T extends IN, S extends ArgodbSerializer<? super T>>
  ArgodbRecordSerializationSchemaBuilder<T> setKafkaValueSerializer(
      Class<S> valueSerializer, Map<String, String> configuration) {
    checkValueSerializerNotSet();
    ArgodbRecordSerializationSchemaBuilder<T> self = self();
    self.valueSerializationSchema =
        new ArgodbSerializerWrapper<>();
    return self;
  }

  public ArgoDBRecordSerializationSchema<IN> build() {
    checkState(valueSerializationSchema != null, "No value serializer is configured.");
    return new ArgodbRecordSerializationSchemaWrapper<>(valueSerializationSchema);
  }

  private void checkValueSerializerNotSet() {
    checkState(valueSerializationSchema == null, "Value serializer already set.");
  }

  private static class ArgodbRecordSerializationSchemaWrapper<IN>
      implements ArgoDBRecordSerializationSchema<IN> {
    private final SerializationSchema<? super IN> valueSerializationSchema;

    ArgodbRecordSerializationSchemaWrapper(
        SerializationSchema<? super IN> valueSerializationSchema) {
      this.valueSerializationSchema = valueSerializationSchema;
    }


    @Override
    public byte[][] serialize(
        IN element, Long timestamp) {
      final byte[] value = valueSerializationSchema.serialize(element);
      return null;
    }

    @Override
    public String[] serialize(IN element) {
      return new String[0];
    }
  }
}
