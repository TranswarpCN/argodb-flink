package io.transwarp.connector.argo.sink;

import java.util.Objects;

import static org.apache.flink.util.Preconditions.checkNotNull;

class HoloWriterState {
  private final String transactionalIdPrefix;

  HoloWriterState(String transactionalIdPrefix) {
    this.transactionalIdPrefix = checkNotNull(transactionalIdPrefix, "transactionalIdPrefix");
  }

  public String getTransactionalIdPrefix() {
    return transactionalIdPrefix;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    HoloWriterState that = (HoloWriterState) o;
    return transactionalIdPrefix.equals(that.transactionalIdPrefix);
  }

  @Override
  public int hashCode() {
    return Objects.hash(transactionalIdPrefix);
  }

  @Override
  public String toString() {
    return "HoloWriterState{"
      + ", transactionalIdPrefix='"
      + transactionalIdPrefix
      + '\''
      + '}';
  }
}
