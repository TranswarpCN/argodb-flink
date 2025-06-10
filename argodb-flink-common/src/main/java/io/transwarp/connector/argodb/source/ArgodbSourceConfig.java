package io.transwarp.connector.argodb.source;

import io.transwarp.holodesk.core.options.ReadOptions;
import io.transwarp.nucleon.rdd.shiva2.CorePartition;
import lombok.Getter;

@Getter
public class ArgodbSourceConfig {

  protected String tddmsMasterGroup;

  @Getter
  protected String tableKey;


  @Getter
  protected CorePartition corePartition;

  @Getter
  protected ReadOptions readOptions;

  @Getter
  protected int[] holoColumnsDataTypes;

  @Getter
  protected int columnNum;


  public ArgodbSourceConfig(String tddmsMasterGroup, String tableKey,
                            int columnNum, int[] holoColumnsDataTypes,
                            CorePartition corePartition, ReadOptions readOptions) {
    this.tddmsMasterGroup = tddmsMasterGroup;
    this.tableKey = tableKey;
    this.columnNum = columnNum;
    this.holoColumnsDataTypes = holoColumnsDataTypes;
    this.corePartition = corePartition;
    this.readOptions = readOptions;
  }
}
