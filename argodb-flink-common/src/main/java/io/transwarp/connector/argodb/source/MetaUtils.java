package io.transwarp.connector.argodb.source;

import io.transwarp.connector.argodb.consts.PartitionType;
import io.transwarp.connector.argodb.consts.Props;
import io.transwarp.holodesk.table.HolodeskTableManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.*;

public class MetaUtils {

  private static final Logger logger = LoggerFactory.getLogger(MetaUtils.class);

  private static final String PART_TYPE_QUERY = "SELECT is_range_partitioned " +
    "FROM system.partition_keys_v " +
    "WHERE concat(database_name, '.', table_name) = '%s' " +
    "LIMIT 1;";
  private static final String SINGLE_VALUE_PART_QUERY =
    "SELECT partition_key, partition_value, location " +
      "FROM system.partitions_v " +
      "WHERE concat(database_name, '.', table_name) = '%s';";
  private static final String RANGE_VALUE_PART_QUERY =
    "SELECT partition_key, partition_range, location " +
      "FROM system.range_partitions_v " +
      "WHERE concat(database_name, '.', table_name) = '%s';";
  private static final String PART_LOCATION_PREFIX = "star:/holodesk/";

  public static Map<Props, String> getTableInfo(String url, String user, String passwd, String argodbTableName,
                                                List<PartitionValue> partValueList) {
    // TODO: argument check
    logger.info("[ARGODB] Connection url: [{}]", url);
    Map<Props, String> props = new HashMap<>();
    try (Connection conn = DriverManager.getConnection(url, user, passwd)) {
      // TODO: do some check - table type etc.
      getTddmsMasterGroup(conn, props);
      getTableInfoFromDescFormatted(conn, argodbTableName, props);
      // get partition info
      getPartitionType(conn, argodbTableName, props);
      partValueList.addAll(getPartitionInfo(conn, argodbTableName, PartitionType.valueOf(props.get(Props.PART_TYPE))));
    } catch (Exception e) {
      logger.error("Get argodb table info failed: " + e.getMessage(), e);
      throw new RuntimeException(e);
    }
    return props;
  }

  private static List<PartitionValue> getPartitionInfo(Connection conn, String argodbTableName, PartitionType partType) {
    List<PartitionValue> parts = new ArrayList<>();
    if (PartitionType.NONE.equals(partType)) {
      return parts;
    }
    if (PartitionType.SINGLE.equals(partType)) {
      return getSingleValuePartitionInfo(conn, argodbTableName);
    } else {
      return getRangeValuePartitionInfo(conn, argodbTableName);
    }
  }

  private static List<PartitionValue> getSingleValuePartitionInfo(Connection conn, String argodbTableName) {
    String query = String.format(SINGLE_VALUE_PART_QUERY, argodbTableName);
    List<PartitionValue> partValueList = new ArrayList<>();
    try (ResultSet result = conn.prepareStatement(query).executeQuery()) {
      while (result.next()) {

        // get data ResultSet and check
        String[] partKeys = result.getString("partition_key").split(",");
        String[] partVals = result.getString("partition_value").split(",");
        if (partKeys.length != partVals.length) {
          throw new RuntimeException("Partition key and value not match, probably some fields contain comma.");
        }
        String location = result.getString("location");
        if (!location.startsWith(PART_LOCATION_PREFIX)) {
          throw new RuntimeException("Illegal partition location: " + location);
        }

        // generate result
        LinkedHashMap<String, String> partKeyVal = new LinkedHashMap<>();
        for (int i = 0; i < partKeys.length; ++i) {
          partKeyVal.put(partKeys[i], partVals[i]);
        }
        String sectionName = location.replace(PART_LOCATION_PREFIX, "");
        partValueList.add(new PartitionValue(PartitionType.SINGLE, partKeyVal, sectionName));

      }
    } catch (Exception e) {
      throw new RuntimeException("Get single value partition info failed.", e);
    }
    return partValueList;
  }

  private static List<PartitionValue> getRangeValuePartitionInfo(Connection conn, String argodbTableName) {
    String query = String.format(RANGE_VALUE_PART_QUERY, argodbTableName);
    List<PartitionValue> partValueList = new ArrayList<>();
    try (ResultSet result = conn.prepareStatement(query).executeQuery()) {
      while (result.next()) {

        // get data ResultSet and check
        String location = result.getString("location");
        if (!location.startsWith(PART_LOCATION_PREFIX)) {
          throw new RuntimeException("Illegal partition location: " + location);
        }

        // generate result
        // range partition table needing only section info now
        LinkedHashMap<String, String> partKeyVal = new LinkedHashMap<>();
        String sectionName = location.replace(PART_LOCATION_PREFIX, "");
        partValueList.add(new PartitionValue(PartitionType.RANGE, partKeyVal, sectionName));

      }
    } catch (Exception e) {
      throw new RuntimeException("Get single value partition info failed.", e);
    }
    return partValueList;
  }

  private static void getPartitionType(Connection conn, String argodbTableName, Map<Props, String> props) {
    PartitionType partType = PartitionType.NONE;
    String query = String.format(PART_TYPE_QUERY, argodbTableName);
    try (ResultSet result = conn.prepareStatement(query).executeQuery()) {
      while (result.next()) {
        boolean isRangePart = result.getBoolean(1);
        if (isRangePart) {
          partType = PartitionType.RANGE;
        } else {
          partType = PartitionType.SINGLE;
        }
      }
      props.put(Props.PART_TYPE, partType.name());
    } catch (Exception e) {
      throw new RuntimeException("Get table partition type failed.", e);
    }
  }

  private static void getTddmsMasterGroup(Connection conn, Map<Props, String> props) {
    String getMasterGroupSql = "set ngmr.tddms2.mastergroup";
    try (ResultSet result = conn.prepareStatement(getMasterGroupSql).executeQuery()) {
      String masterGroup = "";
      while (result.next()) {
        String masterGroupResult = result.getString(1).trim();
        masterGroup = masterGroupResult.split("=")[1];
      }
      props.put(Props.TDDMS_MASTER_GROUP, masterGroup);
    } catch (Exception e) {
      throw new RuntimeException("Get tddms master group failed.", e);
    }
  }

  private static void getTableInfoFromDescFormatted(Connection conn, String argodbTableName, Map<Props, String> props) {
    String getTableMetaSql = "desc formatted " + argodbTableName;
    try (ResultSet result = conn.prepareStatement(getTableMetaSql).executeQuery()) {
      String holodeskDatabaseName = "";
      String holodeskTableName = "";
      while (result.next()) {
        String key = result.getString("category").trim();
        String value = result.getString("attribute").trim();

        // table key
        if ("holodesk.databasename".equals(key)) {
          holodeskDatabaseName = value;
        }
        if ("holodesk.tablename".equals(key)) {
          holodeskTableName = value;
        }
      }
      String tableKey = HolodeskTableManager.makeTableKey(holodeskDatabaseName, holodeskTableName);
      logger.info("[ARGODB] holodesk.databasename: [{}], holodesk.tablename: [{}], tableKey: [{}]",
        holodeskDatabaseName, holodeskTableName, tableKey);
      props.put(Props.TABLE_KEY, tableKey);
    } catch (Exception e) {
      throw new RuntimeException("Get tddms table key failed.", e);
    }
  }
}
