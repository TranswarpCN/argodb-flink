/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.transwarp.org.apache.flink.connector.jdbc.dialect;

import org.apache.flink.table.types.logical.LogicalTypeRoot;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JDBC dialect for PostgreSQL compatible databases.
 */
public abstract class AbstractPostgresCompatibleDialect extends AbstractDialect {

  private static final long serialVersionUID = 1L;

  // Define MAX/MIN precision of TIMESTAMP type according to PostgreSQL docs:
  // https://www.postgresql.org/docs/12/datatype-datetime.html
  private static final int MAX_TIMESTAMP_PRECISION = 6;
  private static final int MIN_TIMESTAMP_PRECISION = 1;

  // Define MAX/MIN precision of DECIMAL type according to PostgreSQL docs:
  // https://www.postgresql.org/docs/12/datatype-numeric.html#DATATYPE-NUMERIC-DECIMAL
  private static final int MAX_DECIMAL_PRECISION = 1000;
  private static final int MIN_DECIMAL_PRECISION = 1;

  @Override
  public String getLimitClause(long limit) {
    return "LIMIT " + limit;
  }

  /**
   * Postgres upsert query. It use ON CONFLICT ... DO UPDATE SET.. to replace into Postgres.
   */
  @Override
  public Optional<String> getUpsertStatement(
    String tableName, String[] fieldNames, String[] uniqueKeyFields) {
    String uniqueColumns =
      Arrays.stream(uniqueKeyFields)
        .map(this::quoteIdentifier)
        .collect(Collectors.joining(", "));
    String updateClause =
      Arrays.stream(fieldNames)
        .map(f -> quoteIdentifier(f) + "=EXCLUDED." + quoteIdentifier(f))
        .collect(Collectors.joining(", "));
    return Optional.of(
      getInsertIntoStatement(tableName, fieldNames)
        + " ON CONFLICT ("
        + uniqueColumns
        + ")"
        + " DO UPDATE SET "
        + updateClause);
  }

  @Override
  public String quoteIdentifier(String identifier) {
    return identifier;
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
    // The data types used in PostgreSQL are list at:
    // https://www.postgresql.org/docs/12/datatype.html

    // TODO: We can't convert BINARY data type to
    //  PrimitiveArrayTypeInfo.BYTE_PRIMITIVE_ARRAY_TYPE_INFO in
    // LegacyTypeInfoDataTypeConverter.

    return EnumSet.of(
      LogicalTypeRoot.CHAR,
      LogicalTypeRoot.VARCHAR,
      LogicalTypeRoot.BOOLEAN,
      LogicalTypeRoot.VARBINARY,
      LogicalTypeRoot.DECIMAL,
      LogicalTypeRoot.TINYINT,
      LogicalTypeRoot.SMALLINT,
      LogicalTypeRoot.INTEGER,
      LogicalTypeRoot.BIGINT,
      LogicalTypeRoot.FLOAT,
      LogicalTypeRoot.DOUBLE,
      LogicalTypeRoot.DATE,
      LogicalTypeRoot.TIME_WITHOUT_TIME_ZONE,
      LogicalTypeRoot.TIMESTAMP_WITHOUT_TIME_ZONE,
      LogicalTypeRoot.TIMESTAMP_WITH_LOCAL_TIME_ZONE,
      LogicalTypeRoot.ARRAY);
  }
}
