package io.transwarp.connector.argodb.source;

import io.transwarp.connector.argodb.consts.PartitionType;
import lombok.Getter;

import java.io.Serializable;
import java.util.LinkedHashMap;

@Getter
public class PartitionValue implements Serializable {

  private final PartitionType partType;

  private final LinkedHashMap<String, String> partValues;

  private final String sectionName;


  public PartitionValue(PartitionType partType, LinkedHashMap<String, String> partValues, String sectionName) {
    this.partType = partType;
    this.partValues = partValues;
    this.sectionName = sectionName;
  }

}
