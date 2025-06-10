package io.transwarp.connector.argo.sink.table;

import org.apache.flink.table.descriptors.ConnectorDescriptorValidator;
import org.apache.flink.table.descriptors.DescriptorProperties;


public class ArgoConnectorValidator extends ConnectorDescriptorValidator {

  public static final String CONNECTOR_TYPE_ARGO = "argodb";
  public static final String CONNECTOR_TABLE_NAME = "table.name";
  public static final String CONNECTOR_SHIVA_MASTER_GROUP = "master.group";
  public static final String CONNECTOR_METASTORE_URL = "metastore.url";
  public static final String CONNECTOR_WRITE_BUFFER_FLUSH_INTERVAL = "connector.write.buffer-flush.interval";
  public static final String CONNECTOR_VALIDATE = "security.validate.type";
  public static final String CONNECTOR_JDBC_USER_LDAP = "ldap.username";
  public static final String CONNECTOR_JDBC_PASSWORD_LDAP = "ldap.password";
  public static final String CONNECTOR_DATA_DIR = "tmp.directory";


  @Override
  public void validate(DescriptorProperties properties) {
    super.validate(properties);
    properties.validateValue(CONNECTOR_TYPE, CONNECTOR_TYPE_ARGO, false);
    properties.validateString(CONNECTOR_TABLE_NAME, false);
    properties.validateString(CONNECTOR_SHIVA_MASTER_GROUP, false);
    properties.validateString(CONNECTOR_METASTORE_URL, false);
    properties.validateString(CONNECTOR_WRITE_BUFFER_FLUSH_INTERVAL, true);
    properties.validateString(CONNECTOR_DATA_DIR, true);
    properties.validateString(CONNECTOR_VALIDATE, false);
    properties.validateString(CONNECTOR_JDBC_USER_LDAP, true);
    properties.validateString(CONNECTOR_JDBC_PASSWORD_LDAP, true);
  }


}
