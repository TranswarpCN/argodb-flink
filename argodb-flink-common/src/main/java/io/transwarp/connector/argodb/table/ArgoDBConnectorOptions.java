package io.transwarp.connector.argodb.table;

import org.apache.flink.configuration.ConfigOption;
import org.apache.flink.configuration.ConfigOptions;

import java.time.Duration;

import static org.apache.flink.configuration.ConfigOptions.key;

public class ArgoDBConnectorOptions {

  public static final ConfigOption<String> MASTER_GROUP =
    ConfigOptions.key("master.group")
      .stringType()
      .noDefaultValue()
      .withDescription("The shiva master group URL.");

  public static final ConfigOption<String> METASTORE =
    ConfigOptions.key("metastore.url")
      .stringType()
      .noDefaultValue()
      .withDescription("The jdbc URL.");

  public static final ConfigOption<String> TMP_DIR =
    ConfigOptions.key("dir")
      .stringType()
      .defaultValue("/tmp")
      .withDescription("The jdbc URL.");

  public static final ConfigOption<String> TABLE_NAME =
    ConfigOptions.key("table.name")
      .stringType()
      .noDefaultValue()
      .withDescription("The argo table name.");


  public static final ConfigOption<Boolean> SHIVA2_ENABLE =
    ConfigOptions.key("shiva2.enable")
      .booleanType()
      .defaultValue(true)
      .withDescription("Is enable shiva2. Default shiva1");

  public static final ConfigOption<Boolean> USE_EXTERNAL_ADDRESS =
    ConfigOptions.key("use.external.address")
      .booleanType()
      .defaultValue(false)
      .withDescription("is enable external address");


  public static final ConfigOption<Boolean> USE_NEW_SINK = ConfigOptions.key("use.new.sink")
    .booleanType()
    .defaultValue(false)
    .withDescription("is enable external address");


  public static final ConfigOption<String> LDAP_USER =
    ConfigOptions.key("ldap.user")
      .stringType()
      .noDefaultValue()
      .withDescription("The ldap user.");


  public static final ConfigOption<String> LDAP_PASSWORD =
    ConfigOptions.key("ldap.password")
      .stringType()
      .noDefaultValue()
      .withDescription("The ldap password.");

  public static final ConfigOption<String> KERBEROS_USER =
    ConfigOptions.key("kerberos.user")
      .stringType()
      .noDefaultValue()
      .withDescription("The metastore kerberos user.");


  public static final ConfigOption<String> KERBEROS_PASSWORD =
    ConfigOptions.key("kerberos.password")
      .stringType()
      .noDefaultValue()
      .withDescription("The metastore kerberos password.");


  public static final ConfigOption<String> COMPRESSION_TYPE =
    ConfigOptions.key("compression")
      .stringType()
      .noDefaultValue()
      .withDescription("The compression type, support snappy, zlib, lzf.");
  public static final ConfigOption<String> SCAN_PARTITION_COLUMN =
    ConfigOptions.key("scan.partition.column")
      .stringType()
      .noDefaultValue()
      .withDescription("The column name used for partitioning the input.");

  public static final ConfigOption<Integer> SCAN_PARTITION_NUM =
    ConfigOptions.key("scan.partition.num")
      .intType()
      .noDefaultValue()
      .withDescription("The number of partitions.");

  public static final ConfigOption<Long> SCAN_PARTITION_LOWER_BOUND =
    ConfigOptions.key("scan.partition.lower-bound")
      .longType()
      .noDefaultValue()
      .withDescription("The smallest value of the first partition.");

  public static final ConfigOption<Long> SCAN_PARTITION_UPPER_BOUND =
    ConfigOptions.key("scan.partition.upper-bound")
      .longType()
      .noDefaultValue()
      .withDescription("The largest value of the last partition.");

  public static final ConfigOption<Integer> SCAN_FETCH_SIZE =
    ConfigOptions.key("scan.fetch-size")
      .intType()
      .defaultValue(0)
      .withDescription(
        "Gives the reader a hint as to the number of rows that should be fetched "
          + "from the database per round-trip when reading. "
          + "If the value is zero, this hint is ignored.");

  public static final ConfigOption<Boolean> SCAN_AUTO_COMMIT =
    ConfigOptions.key("scan.auto-commit")
      .booleanType()
      .defaultValue(true)
      .withDescription("Sets whether the driver is in auto-commit mode.");

  // -----------------------------------------------------------------------------------------
  // Lookup options
  // -----------------------------------------------------------------------------------------

  public static final ConfigOption<Long> LOOKUP_CACHE_MAX_ROWS =
    ConfigOptions.key("lookup.cache.max-rows")
      .longType()
      .defaultValue(-1L)
      .withDescription(
        "The max number of rows of lookup cache, over this value, the oldest rows will "
          + "be eliminated. \"cache.max-rows\" and \"cache.ttl\" options must all be specified if any of them is "
          + "specified.");

  public static final ConfigOption<Duration> LOOKUP_CACHE_TTL =
    ConfigOptions.key("lookup.cache.ttl")
      .durationType()
      .defaultValue(Duration.ofSeconds(10))
      .withDescription("The cache time to live.");

  public static final ConfigOption<Integer> LOOKUP_MAX_RETRIES =
    ConfigOptions.key("lookup.max-retries")
      .intType()
      .defaultValue(3)
      .withDescription("The max retry times if lookup database failed.");

  public static final ConfigOption<Boolean> LOOKUP_CACHE_MISSING_KEY =
    ConfigOptions.key("lookup.cache.caching-missing-key")
      .booleanType()
      .defaultValue(true)
      .withDescription("Flag to cache missing key. true by default");

  // write config options
  public static final ConfigOption<Integer> SINK_BUFFER_FLUSH_MAX_ROWS =
    ConfigOptions.key("sink.buffer-flush.max-rows")
      .intType()
      .defaultValue(1)
      .withDescription(
        "The flush max size (includes all append, upsert and delete records), over this number"
          + " of records, will flush data.");

  public static final ConfigOption<Integer> SINK_BUFFER_FLUSH_INTERVAL =
    ConfigOptions.key("sink.buffer-flush.interval")
      .intType()
      .defaultValue(-1)
      .withDescription(
        "The flush interval mills, over this time, asynchronous threads will flush data.");

  public static final ConfigOption<Integer> SINK_MAX_RETRIES =
    ConfigOptions.key("sink.max-retries")
      .intType()
      .defaultValue(3)
      .withDescription("The max retry times if writing records to database failed.");


  public static final ConfigOption<Boolean> TABLE_EXEC_ARGO_INFER_SOURCE_PARALLELISM =
    key("table.exec.argo.infer-source-parallelism")
      .booleanType()
      .defaultValue(true)
      .withDescription(
        "If is false, parallelism of source are set by config.\n"
          + "If is true, source parallelism is inferred according to splits number.\n");

  public static final ConfigOption<Integer> TABLE_EXEC_ARGO_INFER_SOURCE_PARALLELISM_MAX =
    key("table.exec.argo.infer-source-parallelism.max")
      .intType()
      .defaultValue(1000)
      .withDescription("Sets max infer parallelism for source operator.");

  private ArgoDBConnectorOptions() {
  }
}
