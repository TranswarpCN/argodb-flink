package io.transwarp.connector.argodb.source;

import io.transwarp.connector.argodb.consts.PartitionType;
import io.transwarp.holodesk.core.options.ReadOptions;
import io.transwarp.nucleon.rdd.shiva2.CorePartition;
import lombok.Getter;

import java.io.Serializable;


/**
 * represents one section in ArgoDB
 */
@Getter
public class ArgoTablePartition implements Serializable {

  protected String tddmsMasterGroup;

  @Getter
  protected String tableKey;

  @Getter
  protected PartitionType partitionType;

  @Getter
  protected CorePartition corePartition;

  @Getter
  protected ReadOptions readOptions;

  @Getter
  protected int[] holoColumnsDataTypes;

  @Getter
  protected int columnNum;

  @Getter
  protected PartitionValue partValue;


  public ArgoTablePartition(String tddmsMasterGroup, String tableKey, PartitionType partitionType,
                            int columnNum, int[] holoColumnsDataTypes,
                            CorePartition corePartition, ReadOptions readOptions,
                            PartitionValue partValue) {
    this.tddmsMasterGroup = tddmsMasterGroup;
    this.tableKey = tableKey;
    this.partitionType = partitionType;
    this.columnNum = columnNum;
    this.holoColumnsDataTypes = holoColumnsDataTypes;
    this.corePartition = corePartition;
    this.readOptions = readOptions;
    this.partValue = partValue;
  }
}
