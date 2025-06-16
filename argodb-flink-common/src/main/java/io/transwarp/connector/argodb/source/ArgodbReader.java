package io.transwarp.connector.argodb.source;

import io.transwarp.connector.argodb.ArgoSourceConfig;
import io.transwarp.connector.argodb.ShivaEnv;
import io.transwarp.holodesk.connector.attachment.task.ScanTaskAttachment;
import io.transwarp.holodesk.connector.utils.shiva2.RowSetUtilsShiva2;
import io.transwarp.holodesk.core.common.RowSetElement;
import io.transwarp.holodesk.core.iterator.ConvertRowReferenceIteratorToRowResultIterator;
import io.transwarp.holodesk.core.iterator.RowReferenceIterator;
import io.transwarp.holodesk.core.iterator.RowResultIterator;
import io.transwarp.holodesk.core.options.ReadOptions;
import io.transwarp.holodesk.core.scanner.PurePlainScanner;
import io.transwarp.nucleon.rdd.shiva2.CorePartition;
import io.transwarp.shiva2.shiva.client.ShivaClient;
import io.transwarp.shiva2.shiva.holo.HoloClient;
import io.transwarp.shiva2.shiva.holo.RowSet;
import io.transwarp.shiva2.shiva.holo.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;

public class ArgodbReader {

  private final Logger LOGGER = LoggerFactory.getLogger(ArgodbReader.class);

  public RowResultIterator rowResultIterator = null;
  public RowReferenceIterator rowReferenceIterator = null;
  public ArgoSourceConfig argoSourceConfig;

  private final int rowCount = 0;

  private final ArgoScanInfo argoScanInfo;

  public ArgodbReader(ArgoSourceConfig argoSourceConfig, ArgoScanInfo argoScanInfo) {
    this.argoSourceConfig = argoSourceConfig;
    this.argoScanInfo = argoScanInfo;
  }


  public boolean openForRead(ArgoTablePartition partition) throws Exception {
    ShivaClient tddmsClient = ShivaEnv.getShiva2Client(partition.tddmsMasterGroup);
    HoloClient holoClient = ShivaEnv.getHoloClient(partition.tddmsMasterGroup);

    CorePartition split = partition.getCorePartition();
    RowSet[] rowSets = RowSetUtilsShiva2
      .getRowSetsFromSplitContexts(tddmsClient, split.splitContexts(), new HashSet<>());
    if (rowSets.length != split.rowSetNum()) {
      throw new RuntimeException("[ARGODB] RowSet number has changed before scan, current RowSets: [" +
        rowSets.length + "], expected RowSets: [" + split.rowSetNum() + "].");
    }
    String tableId = split.splitContexts()[0].getTableId();
    if (split.attachment() instanceof ScanTaskAttachment) {
      this.rowReferenceIterator = scanPerfTable(
        tddmsClient, holoClient, tableId, partition.getReadOptions(), rowSets,
        ((ScanTaskAttachment) split.attachment()).sectionId()
      );
      this.rowResultIterator = ConvertRowReferenceIteratorToRowResultIterator.newRowResultIterator(this.rowReferenceIterator);
    } else {
      throw new RuntimeException("[ARGODB] Only support ScanTaskAttachment for performance table !");
    }
    return true;
  }

  private RowReferenceIterator scanPerfTable(ShivaClient shivaClient, HoloClient holoClient, String tableId, ReadOptions readOptions, RowSet[] rowSets, int sectionId) throws Exception {
    Table table = holoClient.openTableById(tableId);

    RowSetElement[] rowSetElements = new RowSetElement[rowSets.length];

    PurePlainScanner scanner = new PurePlainScanner(readOptions, rowSetElements);
    return scanner.newScanRowReferenceIterator();
  }

  public ArgoInputSplit[] createInputSplits() throws Exception {

    ArgoSplitUtils argoSplitUtils = new ArgoSplitUtils();

    argoSplitUtils.init(argoSourceConfig, argoScanInfo);

    List<ArgoTablePartition> splits = argoSplitUtils.getPartitions();

    ArgoInputSplit[] argoInputSplits = new ArgoInputSplit[splits.size()];

    for (int i = 0; i < splits.size(); i++) {
      ArgoInputSplit argoInputSplit = new ArgoInputSplit(i, null);
      argoInputSplit.setArgoTablePartitionSplits(new ArgoTablePartitionSplits(splits.get(i)));
      argoInputSplits[i] = argoInputSplit;
    }
    return argoInputSplits;
  }
}
