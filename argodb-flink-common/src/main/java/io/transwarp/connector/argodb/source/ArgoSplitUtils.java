package io.transwarp.connector.argodb.source;


import io.transwarp.connector.argodb.ArgoSourceConfig;
import io.transwarp.connector.argodb.ShivaEnv;
import io.transwarp.connector.argodb.consts.Configs;
import io.transwarp.connector.argodb.consts.PartitionType;
import io.transwarp.connector.argodb.consts.Props;
import io.transwarp.holodesk.common.shiva2.TableUtilitiesShiva2;
import io.transwarp.holodesk.connector.attachment.task.ScanTaskAttachment;
import io.transwarp.holodesk.connector.attachment.task.TaskType$;
import io.transwarp.holodesk.connector.stargate.PerformanceTablePredicateGenerator;
import io.transwarp.holodesk.connector.utils.shiva2.RowSetSplitHelperShiva2;
import io.transwarp.holodesk.connector.utils.shiva2.RowSetsGroup;
import io.transwarp.holodesk.core.common.*;
import io.transwarp.holodesk.core.options.ReadOptions;
import io.transwarp.holodesk.core.predicate.primitive.PrimitivePredicate;
import io.transwarp.holodesk.transaction.shiva2.TransactionUtilsShiva2;
import io.transwarp.nucleon.rdd.shiva2.CorePartition;
import scala.collection.JavaConverters;
import io.transwarp.shiva2.shiva.bulk.BulkReadOptions;
import io.transwarp.shiva2.shiva.bulk.Transaction;
import io.transwarp.shiva2.shiva.bulk.TransactionHandler;
import io.transwarp.shiva2.shiva.client.ShivaClient;
import io.transwarp.shiva2.shiva.holo.Distribution;
import io.transwarp.shiva2.shiva.holo.HoloClient;
import io.transwarp.shiva2.shiva.holo.RowSet;
import io.transwarp.shiva2.shiva.holo.Table;
import io.transwarp.stargate.filter.SGFilterContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


public class ArgoSplitUtils implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(ArgoSplitUtils.class);
  private String tddmsMasterGroup;
  private String tableName;
  private PartitionType partType;
  private final List<PartitionValue> partValueList = new ArrayList<>();
  protected List<ArgoTablePartition> readerMetadata = new LinkedList<>();
  private ShivaClient tddmsClient;
  private HoloClient holoClient;

  private Table tddmsTable;

  private io.transwarp.holodesk.common.Table holoTable;

  private ArgoScanInfo argoScanInfo;


  public void init(ArgoSourceConfig argoSourceConfig, ArgoScanInfo argoScanInfo) throws Exception {
    try {
      Class.forName(Configs.ARGODB_JDBC_DRIVER_NAME);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
    Map<Props, String> tableInfo = MetaUtils.getTableInfo(
      argoSourceConfig.getUrl(),
      argoSourceConfig.getLdapUser(),
      argoSourceConfig.getLdapPassword(),
      argoSourceConfig.getTableName(),
      this.partValueList
    );
    this.tddmsMasterGroup = tableInfo.get(Props.TDDMS_MASTER_GROUP);
    this.tableName = tableInfo.get(Props.TABLE_KEY);
    this.partType = PartitionType.valueOf(tableInfo.get(Props.PART_TYPE));

    // todo
//        this.tddmsMasterGroup = "172.18.120.33:39630,172.18.120.32:39630,172.18.120.31:39630";

    tddmsClient = ShivaEnv.getShiva2Client(this.tddmsMasterGroup);
    holoClient = ShivaEnv.getHoloClient(this.tddmsMasterGroup);
    tddmsTable = holoClient.openTable(tableName);
//    holoTable = TableUtilitiesShiva2.getHolodeskTable(tddmsTable);

    this.argoScanInfo = argoScanInfo;
  }

  public void setTddmsMasterGroup(String tddmsMasterGroup) {
    this.tddmsMasterGroup = tddmsMasterGroup;
  }

  private Set<String> getSections() {
    Set<String> sectionNames = new HashSet<>();
    if (PartitionType.NONE.equals(this.partType)) {
      tddmsTable.getSchema().getDynamicSchema().getSectionMap().values().forEach(item -> {
        String sectionName = item.getName();
        sectionNames.add(sectionName);
        partValueList.add(new PartitionValue(PartitionType.NONE, new LinkedHashMap<>(0), sectionName));
      });
    } else {
      this.partValueList.forEach(p -> sectionNames.add(p.getSectionName()));
    }
    LOGGER.info("[ARGODB] Sections: {}.", sectionNames);

    return sectionNames;
  }

  private ReadOptions buildCoreReadOptions() {
    RuntimeContext runtimeContext = new RuntimeContext();
    runtimeContext.setForceRemoteRead(true);
    ReadOptions coreReadOptions = new ReadOptions()
      .setIsPerformanceTable(true)
      .setRuntimeContext(runtimeContext)
      .setGlobalColumnIds(holoTable.getGlobalColumnIds())
      .setNeedColumnIds(argoScanInfo.getScanColumnsIndex() == null ? holoTable.getGlobalColumnIds() : argoScanInfo.getScanColumnsIndex())
      .setCanDoRowBlockScan(true)
      .setCanPipeLineFetch(false)
      .setCompressionType(0) // SNAPPY:2
      .setRpcPacketSize(1024 * 1024)
      .setNeedLoadInternalRowKey(false)
      .setPreferReadMode(ReadMode.BatchMode)
      .setScannerIteratorType(ScannerIteratorType.scan)
      .setLimit(argoScanInfo.getLimit())
      .setOrderByColumnIndex(argoScanInfo.getOrderByColumnsIndex())
      .setScannerType(ScannerType.MainTable);

    SGFilterContainer filterContainer = argoScanInfo.getFilterContainer();
    if (filterContainer != null) {
      PrimitivePredicate primitivePredicate = new PerformanceTablePredicateGenerator(runtimeContext, Dialect.ORACLE)
        .generatorArgoPerformanceTablePredicate(filterContainer);
      coreReadOptions.setPrimitivePredicate(primitivePredicate);
    }
    return coreReadOptions;
  }


  private List<ArgoTablePartition> initPartition(PartitionValue part, Distribution distribution, Map<String, Integer> sectionToSectionId, int holoColumnNum, int[] holoColumnsDataTypes) throws Exception {
    List<ArgoTablePartition> res = new ArrayList<>();

    String section = part.getSectionName();

    Iterator<RowSet> sectionRowSetIter = distribution.getRowSetIterator(section);

    RowSetsGroup[] rowSetsGroupArray = RowSetSplitHelperShiva2
      .splitRowSetsToFiles(distribution, JavaConverters.asScalaIteratorConverter(sectionRowSetIter).asScala(), sectionToSectionId);

    LOGGER.debug("[ARGODB] Get [{}] RowSetsGroup from section [{}].", rowSetsGroupArray.length, section);

    int bucketId;
    String rowSetsGroupSection;
    for (int index = 0; index < rowSetsGroupArray.length; ++index) {
      RowSetsGroup rowSetsGroup = rowSetsGroupArray[index];
      if (TaskType$.MODULE$.Scan().equals(rowSetsGroup.attachment().taskType())) {
        ScanTaskAttachment attachment = (ScanTaskAttachment) rowSetsGroup.attachment();
        bucketId = attachment.bucketId();
        rowSetsGroupSection = attachment.section();
      } else {
        bucketId = -1;
        rowSetsGroupSection = null;
      }

      ArgoTablePartition readMeta = new ArgoTablePartition(
        this.tddmsMasterGroup, this.tableName, this.partType, holoColumnNum, holoColumnsDataTypes,
        new CorePartition(rowSetsGroup.splitContexts(), rowSetsGroup.attachment(),
          JavaConverters.collectionAsScalaIterableConverter(Arrays.asList(rowSetsGroup.hosts())).asScala().toSeq(),
          rowSetsGroup.rowSetNum(), index, bucketId, rowSetsGroupSection, rowSetsGroup.rowCount(),
          rowSetsGroup.memoryUsage(), false),
        buildCoreReadOptions(), part
      );
      res.add(readMeta);
    }
    return res;
  }


  public List<ArgoTablePartition> getPartitions() throws Exception {
    Set<String> sections = getSections();

    int holoColumnNum = holoTable.columns().length;
    int[] holoColumnsDataTypes = holoTable.getColumnDataTypes();

    // TODO: Add filter
    BulkReadOptions bulkReadOption = TransactionUtilsShiva2.getDefaultBulkReadOptions(tableName, sections, null);

    Transaction transaction = null;
    try {
      // 获取shiva中元数据
      transaction = TransactionUtilsShiva2.beginTransaction(tddmsClient, true);
      TransactionHandler transactionHandler = TransactionUtilsShiva2.getMultiSectionReadTransactionHandler(transaction, bulkReadOption);
      Distribution distribution = transactionHandler.getDistribution();
      Map<String, Integer> sectionToSectionId = transactionHandler.getSectionIds();
      TransactionUtilsShiva2.commitTransaction(transaction);

      for (PartitionValue part : partValueList) {
        readerMetadata.addAll(initPartition(part, distribution, sectionToSectionId, holoColumnNum, holoColumnsDataTypes));
      }
      LOGGER.debug("[ARGODB] Split fragment count: [{}].", readerMetadata.size());
      return readerMetadata;
    } catch (
      Exception e) {
      if (transaction != null) {
        try {
          TransactionUtilsShiva2.abortTransaction(transaction);
        } catch (Exception ex) {
          LOGGER.error("[ARGODB] abort transaction failed.");
        }
      }
      throw e;
    }

  }

//    private SGFilterContainer getSGFilters(Column[] holoColumns, PartitionType partType, List<String> partKeys) throws Exception {
//        TreeVisitor argodbOperatorPruner = new ArgodbOperatorPruner(SUPPORTED_OPERATORS);
//        ArgodbSGFilterBuilder argodbSGFilterBuilder = new ArgodbSGFilterBuilder(holoColumns, partType, partKeys);
//
//        Node root = new FilterParser().parse(context.getFilterString());
//        TRAVERSER.traverse(root, argodbOperatorPruner, argodbSGFilterBuilder);
//
//        if (argodbSGFilterBuilder.containUnsupportedPattern()) {
//            LOGGER.warn("[ARGODB] SG filters have unsupported pattern, degenerate to full table scan !");
//            return null;
//        }
//
//        // debug info
//        if (LOGGER.isDebugEnabled() && !argodbSGFilterBuilder.containUnsupportedPattern()) {
//            List<SGAndFilterList> andFilterList = argodbSGFilterBuilder.getSgAndFilterList();
//            for (int i = 0; i < andFilterList.size(); ++i) {
//                LOGGER.debug("[ARGODB] And SG filter group: no.[{}]", i);
//                SGAndFilterList sgAndFilterList = andFilterList.get(i);
//                Map<SGNode, SGFilter> sgFilterMap = scala.collection.JavaConverters.asJavaMapConverter(sgAndFilterList.getFilters()).asJava();
//                for (Map.Entry<SGNode, SGFilter> sgFilterEntry : sgFilterMap.entrySet()) {
//                    if (sgFilterEntry.getKey() instanceof SGColumn) {
//                        LOGGER.debug("[ARGODB] SG filter column: [{}], conditions: [{}]",
//                                ((SGColumn) sgFilterEntry.getKey()).getSGColumnName(), sgFilterEntry.getValue().toString());
//                    }
//                }
//            }
//        }
//
//        // generate result
//        SGFilterContainer filterContainer = new SGFilterContainer();
//        for (SGAndFilterList andFilterList : argodbSGFilterBuilder.getSgAndFilterList()) {
//            filterContainer.or(andFilterList);
//        }
//        return filterContainer;
//    }

  @Override
  public void close() throws Exception {

  }
}
