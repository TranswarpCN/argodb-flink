package io.transwarp.connector.argodb.table;

import org.apache.flink.configuration.ConfigOption;
import org.apache.flink.configuration.ConfigOptions;
import org.apache.flink.configuration.ReadableConfig;
import io.transwarp.org.apache.flink.connector.jdbc.dialect.JdbcDialect;
import io.transwarp.org.apache.flink.connector.jdbc.dialect.JdbcDialectLoader;
import io.transwarp.org.apache.flink.connector.jdbc.internal.options.InternalJdbcConnectionOptions;
import io.transwarp.org.apache.flink.connector.jdbc.internal.options.JdbcReadOptions;
import io.transwarp.org.apache.flink.connector.jdbc.table.JdbcDynamicTableSource;
import org.apache.flink.table.connector.source.DynamicTableSource;
import org.apache.flink.table.connector.source.lookup.LookupOptions;
import org.apache.flink.table.connector.source.lookup.cache.DefaultLookupCache;
import org.apache.flink.table.connector.source.lookup.cache.LookupCache;
import org.apache.flink.table.factories.FactoryUtil;
import org.apache.flink.table.types.DataType;
import org.apache.flink.table.types.logical.RowType;
import org.apache.flink.util.Preconditions;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.transwarp.org.apache.flink.connector.jdbc.table.JdbcConnectorOptions.*;


public class ArgoDBDynamicTableFactoryImpl extends ArgoDBDynamicTableFactory {

  @Override
  public String factoryIdentifier() {
    return "argodb";
  }

  @Override
  public DynamicTableSource createDynamicTableSource(Context context) {
    final FactoryUtil.TableFactoryHelper helper =
      FactoryUtil.createTableFactoryHelper(this, context);
    final ReadableConfig config = helper.getOptions();

    helper.validate();
    validateConfigOptions(config, context.getClassLoader());
    validateDataTypeWithJdbcDialect(
      context.getPhysicalRowDataType(), config.get(URL), context.getClassLoader());
    return new JdbcDynamicTableSource(
      getJdbcOptions(helper.getOptions(), context.getClassLoader()),
      getJdbcReadOptions(helper.getOptions()),
      helper.getOptions().get(LookupOptions.MAX_RETRIES),
      getLookupCache(config),
      context.getPhysicalRowDataType());
  }

  private void checkAllOrNone(ReadableConfig config, ConfigOption<?>[] configOptions) {
    int presentCount = 0;
    for (ConfigOption configOption : configOptions) {
      if (config.getOptional(configOption).isPresent()) {
        presentCount++;
      }
    }
    String[] propertyNames =
      Arrays.stream(configOptions).map(ConfigOption::key).toArray(String[]::new);
    Preconditions.checkArgument(
      configOptions.length == presentCount || presentCount == 0,
      "Either all or none of the following options should be provided:\n"
        + String.join("\n", propertyNames));
  }

  private void validateConfigOptions(ReadableConfig config, ClassLoader classLoader) {
    String jdbcUrl = config.get(URL);
    JdbcDialectLoader.load(jdbcUrl, classLoader);

    checkAllOrNone(config, new ConfigOption[]{USERNAME, PASSWORD});

    checkAllOrNone(
      config,
      new ConfigOption[]{
        SCAN_PARTITION_COLUMN,
        SCAN_PARTITION_NUM,
        SCAN_PARTITION_LOWER_BOUND,
        SCAN_PARTITION_UPPER_BOUND
      });

    if (config.getOptional(SCAN_PARTITION_LOWER_BOUND).isPresent()
      && config.getOptional(SCAN_PARTITION_UPPER_BOUND).isPresent()) {
      long lowerBound = config.get(SCAN_PARTITION_LOWER_BOUND);
      long upperBound = config.get(SCAN_PARTITION_UPPER_BOUND);
      if (lowerBound > upperBound) {
        throw new IllegalArgumentException(
          String.format(
            "'%s'='%s' must not be larger than '%s'='%s'.",
            SCAN_PARTITION_LOWER_BOUND.key(),
            lowerBound,
            SCAN_PARTITION_UPPER_BOUND.key(),
            upperBound));
      }
    }

    checkAllOrNone(config, new ConfigOption[]{LOOKUP_CACHE_MAX_ROWS, LOOKUP_CACHE_TTL});

    if (config.get(LOOKUP_MAX_RETRIES) < 0) {
      throw new IllegalArgumentException(
        String.format(
          "The value of '%s' option shouldn't be negative, but is %s.",
          LOOKUP_MAX_RETRIES.key(), config.get(LOOKUP_MAX_RETRIES)));
    }

    if (config.get(SINK_MAX_RETRIES) < 0) {
      throw new IllegalArgumentException(
        String.format(
          "The value of '%s' option shouldn't be negative, but is %s.",
          SINK_MAX_RETRIES.key(), config.get(SINK_MAX_RETRIES)));
    }

    if (config.get(MAX_RETRY_TIMEOUT).getSeconds() <= 0) {
      throw new IllegalArgumentException(
        String.format(
          "The value of '%s' option must be in second granularity and shouldn't be smaller than 1 second, but is %s.",
          MAX_RETRY_TIMEOUT.key(),
          config.get(
            ConfigOptions.key(MAX_RETRY_TIMEOUT.key())
              .stringType()
              .noDefaultValue())));
    }
  }

  private static void validateDataTypeWithJdbcDialect(
    DataType dataType, String url, ClassLoader classLoader) {
    final JdbcDialect dialect = JdbcDialectLoader.load(url, classLoader);
    dialect.validate((RowType) dataType.getLogicalType());
  }

  private InternalJdbcConnectionOptions getJdbcOptions(
    ReadableConfig readableConfig, ClassLoader classLoader) {
    final String url = readableConfig.get(URL);
    final InternalJdbcConnectionOptions.Builder builder =
      InternalJdbcConnectionOptions.builder()
        .setClassLoader(classLoader)
        .setDBUrl(url)
        .setTableName(readableConfig.get(TABLE_NAME))
        .setDialect(JdbcDialectLoader.load(url, classLoader))
        .setParallelism(readableConfig.getOptional(SINK_PARALLELISM).orElse(null))
        .setConnectionCheckTimeoutSeconds(
          (int) readableConfig.get(MAX_RETRY_TIMEOUT).getSeconds());

    readableConfig.getOptional(DRIVER).ifPresent(builder::setDriverName);
    readableConfig.getOptional(USERNAME).ifPresent(builder::setUsername);
    readableConfig.getOptional(PASSWORD).ifPresent(builder::setPassword);
    return builder.build();
  }

  private JdbcReadOptions getJdbcReadOptions(ReadableConfig readableConfig) {
    final Optional<String> partitionColumnName =
      readableConfig.getOptional(SCAN_PARTITION_COLUMN);
    final JdbcReadOptions.Builder builder = JdbcReadOptions.builder();
    if (partitionColumnName.isPresent()) {
      builder.setPartitionColumnName(partitionColumnName.get());
      builder.setPartitionLowerBound(readableConfig.get(SCAN_PARTITION_LOWER_BOUND));
      builder.setPartitionUpperBound(readableConfig.get(SCAN_PARTITION_UPPER_BOUND));
      builder.setNumPartitions(readableConfig.get(SCAN_PARTITION_NUM));
    }
    readableConfig.getOptional(SCAN_FETCH_SIZE).ifPresent(builder::setFetchSize);
    builder.setAutoCommit(readableConfig.get(SCAN_AUTO_COMMIT));
    return builder.build();
  }

  @Nullable
  private LookupCache getLookupCache(ReadableConfig tableOptions) {
    LookupCache cache = null;
    // Legacy cache options
    if (tableOptions.get(LOOKUP_CACHE_MAX_ROWS) > 0
      && tableOptions.get(LOOKUP_CACHE_TTL).compareTo(Duration.ZERO) > 0) {
      cache =
        DefaultLookupCache.newBuilder()
          .maximumSize(tableOptions.get(LOOKUP_CACHE_MAX_ROWS))
          .expireAfterWrite(tableOptions.get(LOOKUP_CACHE_TTL))
          .cacheMissingKey(tableOptions.get(LOOKUP_CACHE_MISSING_KEY))
          .build();
    }
    if (tableOptions
      .get(LookupOptions.CACHE_TYPE)
      .equals(LookupOptions.LookupCacheType.PARTIAL)) {
      cache = DefaultLookupCache.fromConfig(tableOptions);
    }
    return cache;
  }

  @Override
  public Set<ConfigOption<?>> forwardOptions() {
    return Stream.of(
        URL,
        TABLE_NAME,
        USERNAME,
        PASSWORD,
        DRIVER,
        SINK_BUFFER_FLUSH_MAX_ROWS,
        SINK_BUFFER_FLUSH_INTERVAL,
        SINK_MAX_RETRIES,
        MAX_RETRY_TIMEOUT,
        SCAN_FETCH_SIZE,
        SCAN_AUTO_COMMIT)
      .collect(Collectors.toSet());
  }

  @Override
  public Set<ConfigOption<?>> optionalOptions() {
    final Set<ConfigOption<?>> optionalOptions = super.optionalOptions();
    optionalOptions.add(DRIVER);
    optionalOptions.add(USERNAME);
    optionalOptions.add(PASSWORD);
    optionalOptions.add(SCAN_PARTITION_COLUMN);
    optionalOptions.add(SCAN_PARTITION_LOWER_BOUND);
    optionalOptions.add(SCAN_PARTITION_UPPER_BOUND);
    optionalOptions.add(SCAN_PARTITION_NUM);
    optionalOptions.add(SCAN_FETCH_SIZE);
    optionalOptions.add(SCAN_AUTO_COMMIT);
    optionalOptions.add(LOOKUP_CACHE_MAX_ROWS);
    optionalOptions.add(LOOKUP_CACHE_TTL);
    optionalOptions.add(LOOKUP_MAX_RETRIES);
    optionalOptions.add(LOOKUP_CACHE_MISSING_KEY);
    optionalOptions.add(SINK_BUFFER_FLUSH_MAX_ROWS);
    optionalOptions.add(SINK_BUFFER_FLUSH_INTERVAL);
    optionalOptions.add(SINK_MAX_RETRIES);
    optionalOptions.add(SINK_PARALLELISM);
    optionalOptions.add(MAX_RETRY_TIMEOUT);
    optionalOptions.add(LookupOptions.CACHE_TYPE);
    optionalOptions.add(LookupOptions.PARTIAL_CACHE_EXPIRE_AFTER_ACCESS);
    optionalOptions.add(LookupOptions.PARTIAL_CACHE_EXPIRE_AFTER_WRITE);
    optionalOptions.add(LookupOptions.PARTIAL_CACHE_MAX_ROWS);
    optionalOptions.add(LookupOptions.PARTIAL_CACHE_CACHE_MISSING_KEY);
    optionalOptions.add(LookupOptions.MAX_RETRIES);
    return optionalOptions;
  }

}
