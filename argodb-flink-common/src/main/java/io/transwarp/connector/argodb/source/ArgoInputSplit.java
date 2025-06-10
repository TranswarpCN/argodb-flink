package io.transwarp.connector.argodb.source;

import lombok.Getter;
import org.apache.flink.annotation.Internal;
import org.apache.flink.core.io.LocatableInputSplit;

@Getter
@Internal
public class ArgoInputSplit extends LocatableInputSplit {


  private ArgoTablePartitionSplits argoTablePartitionSplits;


  public ArgoInputSplit(int splitNumber, String[] hostnames) {
    super(splitNumber, hostnames);
  }

  public void setArgoTablePartitionSplits(ArgoTablePartitionSplits argoTablePartitionSplits) {
    this.argoTablePartitionSplits = argoTablePartitionSplits;
  }
}
