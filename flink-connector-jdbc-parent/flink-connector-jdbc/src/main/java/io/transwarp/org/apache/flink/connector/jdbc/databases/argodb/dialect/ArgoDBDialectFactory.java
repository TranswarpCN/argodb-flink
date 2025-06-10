package io.transwarp.org.apache.flink.connector.jdbc.databases.argodb.dialect;

import io.transwarp.org.apache.flink.connector.jdbc.dialect.JdbcDialect;
import io.transwarp.org.apache.flink.connector.jdbc.dialect.JdbcDialectFactory;
import org.apache.flink.annotation.Internal;

/**
 * Factory for {@link ArgoDBDialect}.
 */
@Internal
public class ArgoDBDialectFactory implements JdbcDialectFactory {
  @Override
  public boolean acceptsURL(String url) {
    return url.startsWith("jdbc:transwarp2:") || url.startsWith("jdbc:hive2:");
  }

  @Override
  public JdbcDialect create() {
    return new ArgoDBDialect();
  }
}
