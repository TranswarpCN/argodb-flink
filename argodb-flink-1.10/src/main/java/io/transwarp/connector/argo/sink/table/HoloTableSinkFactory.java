package io.transwarp.connector.argo.sink.table;

import io.transwarp.connector.argo.sink.ArgoDBConfig;
import org.apache.flink.table.api.TableSchema;
import org.apache.flink.table.descriptors.DescriptorProperties;
import org.apache.flink.table.factories.StreamTableSinkFactory;
import org.apache.flink.table.sinks.StreamTableSink;
import org.apache.flink.table.utils.TableSchemaUtils;
import org.apache.flink.types.Row;
import org.apache.flink.util.Preconditions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.transwarp.connector.argo.sink.table.ArgoConnectorValidator.*;
import static org.apache.flink.table.descriptors.Schema.SCHEMA;

public class HoloTableSinkFactory implements StreamTableSinkFactory<Row> {
  @Override
  public StreamTableSink createStreamTableSink(Map<String, String> properties) {
    DescriptorProperties validatedProperties = getValidatedProperties(properties);
    ArgoDBConfig.Builder builder = ArgoDBConfig.builder()
      .masterGroup(properties.get(CONNECTOR_SHIVA_MASTER_GROUP))
      .tableName(properties.get(CONNECTOR_TABLE_NAME))
      .tmpDirectory(properties.getOrDefault(CONNECTOR_DATA_DIR, "/mnt/tmp"))
      .url(properties.get(CONNECTOR_METASTORE_URL));

    //todo kerberos
    String validateType = properties.get(CONNECTOR_VALIDATE);
    if ("ldap".equalsIgnoreCase(validateType)) {
      builder.jdbcUser(Preconditions.checkNotNull(properties.get(properties.get(CONNECTOR_JDBC_USER_LDAP)), "ldap username"));
      builder.jdbcPassword(Preconditions.checkNotNull(properties.get(properties.get(CONNECTOR_JDBC_PASSWORD_LDAP)), "ldap password"));
    }

    TableSchema tableSchema = TableSchemaUtils.getPhysicalSchema(validatedProperties.getTableSchema(SCHEMA));


    return new HoloTableSink(builder.build(), tableSchema);
  }


  @Override
  public Map<String, String> requiredContext() {
    Map<String, String> context = new HashMap<>();
    context.put(CONNECTOR_TYPE, CONNECTOR_TYPE_ARGO); // hbase
    return context;
  }

  @Override
  public List<String> supportedProperties() {
    List<String> properties = new ArrayList<>();
    properties.add(CONNECTOR_TABLE_NAME);
    properties.add(CONNECTOR_DATA_DIR);
    properties.add(CONNECTOR_VALIDATE);
    properties.add(CONNECTOR_JDBC_USER_LDAP);
    properties.add(CONNECTOR_JDBC_PASSWORD_LDAP);
    properties.add(CONNECTOR_SHIVA_MASTER_GROUP);
    properties.add(CONNECTOR_METASTORE_URL);
    properties.add(CONNECTOR_WRITE_BUFFER_FLUSH_INTERVAL);

    properties.add(SCHEMA + ".#." + DescriptorProperties.TABLE_SCHEMA_TYPE);
    properties.add(SCHEMA + ".#." + DescriptorProperties.TABLE_SCHEMA_DATA_TYPE);
    properties.add(SCHEMA + ".#." + DescriptorProperties.TABLE_SCHEMA_NAME);

    properties.add(SCHEMA + "." + DescriptorProperties.WATERMARK + ".*");


    return properties;
  }

  private DescriptorProperties getValidatedProperties(Map<String, String> properties) {
    final DescriptorProperties descriptorProperties = new DescriptorProperties(true);
    descriptorProperties.putProperties(properties);
    new ArgoConnectorValidator().validate(descriptorProperties);
    return descriptorProperties;
  }

}
