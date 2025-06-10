package io.transwarp.org.apache.flink.connector.jdbc.databases.argodb.dialect;

import io.transwarp.org.apache.flink.connector.jdbc.converter.JdbcRowConverter;
import io.transwarp.org.apache.flink.connector.jdbc.dialect.AbstractDialect;
import org.apache.flink.annotation.Internal;
import org.apache.flink.table.types.logical.LogicalTypeRoot;
import org.apache.flink.table.types.logical.RowType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * JDBC dialect for ArgoDB.
 */
@Internal
public class ArgoDBDialect extends AbstractDialect {
  private static final Logger LOG = LoggerFactory.getLogger(ArgoDBDialect.class);
  private static final long serialVersionUID = 1L;
  private static final int MAX_DECIMAL_PRECISION = 38;
  private static final int MIN_DECIMAL_PRECISION = 1;
  private static final int MAX_TIMESTAMP_PRECISION = 6;
  private static final int MIN_TIMESTAMP_PRECISION = 0;

  @Override
  public JdbcRowConverter getRowConverter(RowType rowType) {
    return new ArgoDBRowConverter(rowType);
  }

  @Override
  public String getLimitClause(long limit) {
    return "LIMIT " + limit;
  }

  @Override
  public Optional<String> defaultDriverName() {
    return Optional.of("io.transwarp.jdbc.InceptorDriver");
  }

  @Override
  public String dialectName() {
    return "ArgoDB";
  }

  @Override
  public String quoteIdentifier(String identifier) {
    return "`" + identifier + "`";
  }

  @Override
  public String getSelectFromStatement(
    String tableName, String[] selectFields, String[] conditionFields) {
    String selectExpressions =
      Arrays.stream(selectFields)
        .map(this::quoteIdentifier)
        .collect(Collectors.joining(", "));
    LOG.info("Select expressions: {}", selectExpressions);
    LOG.info("tableName: {}", tableName);
    LOG.info("selectFields: {}", selectFields);
    String fieldExpressions =
      Arrays.stream(conditionFields)
        .map(f -> format("%s = :%s", quoteIdentifier(f), f))
        .collect(Collectors.joining(" AND "));
    LOG.info("Field expressions: {}", fieldExpressions);
    LOG.info("conditionFields: {}", conditionFields);
    String sqlStatement =
      "SELECT "
        + selectExpressions
        + " FROM "
        + quoteIdentifier(tableName)
        + (conditionFields.length > 0 ? " WHERE " + fieldExpressions : "");
    LOG.info("Generated SQL statement: {}", sqlStatement);

    return sqlStatement;
  }

  @Override
  public Optional<String> getUpsertStatement(
    String tableName, String[] fieldNames, String[] uniqueKeyFields) {
    throw new UnsupportedOperationException("Upsert operation is not supported.");
  }

  @Override
  public String getInsertIntoStatement(String tableName, String[] fieldNames) {
    throw new UnsupportedOperationException("Insert operation is not supported.");
  }

  @Override
  public String getDeleteStatement(String tableName, String[] conditionFields) {
    throw new UnsupportedOperationException("Insert operation is not supported.");
  }

  @Override
  public Optional<Range> decimalPrecisionRange() {
    return Optional.of(Range.of(MIN_DECIMAL_PRECISION, MAX_DECIMAL_PRECISION));
  }

  @Override
  public Optional<Range> timestampPrecisionRange() {
    return Optional.of(Range.of(MIN_TIMESTAMP_PRECISION, MAX_TIMESTAMP_PRECISION));
  }

  @Override
  public Set<LogicalTypeRoot> supportedTypes() {

    return EnumSet.of(
      LogicalTypeRoot.CHAR,
      LogicalTypeRoot.VARCHAR,
      LogicalTypeRoot.BOOLEAN,
      //                LogicalTypeRoot.VARBINARY,
      LogicalTypeRoot.DECIMAL,
      LogicalTypeRoot.TINYINT,
      LogicalTypeRoot.SMALLINT,
      LogicalTypeRoot.INTEGER,
      LogicalTypeRoot.BIGINT,
      LogicalTypeRoot.FLOAT,
      LogicalTypeRoot.DOUBLE,
      LogicalTypeRoot.DATE,
      LogicalTypeRoot.TIME_WITHOUT_TIME_ZONE,
      LogicalTypeRoot.TIMESTAMP_WITHOUT_TIME_ZONE);
  }
}
