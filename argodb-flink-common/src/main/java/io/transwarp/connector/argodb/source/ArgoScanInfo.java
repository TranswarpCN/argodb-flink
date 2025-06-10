package io.transwarp.connector.argodb.source;

import io.transwarp.stargate.filter.SGFilterContainer;
import lombok.Getter;

import java.io.Serializable;

@Getter
public class ArgoScanInfo implements Serializable {

  private final int[] scanColumnsIndex;

  private final SGFilterContainer filterContainer;

  private final int limit;

  private final int[] orderByColumnsIndex;


  public ArgoScanInfo(Builder builder) {
    this.scanColumnsIndex = builder.getScanColumnsIndex();
    this.filterContainer = builder.getFilterContainer();
    this.limit = builder.getLimit();
    this.orderByColumnsIndex = builder.getOrderByColumnsIndex();
  }

  public static Builder builder() {
    return new Builder();
  }


  @Getter
  public static class Builder {

    private int[] scanColumnsIndex;

    private SGFilterContainer filterContainer;

    private int limit = -1;

    private int[] orderByColumnsIndex;

    public Builder() {

    }

    public Builder setScanColumnsIndex(int[] scanColumnsIndex) {
      this.scanColumnsIndex = scanColumnsIndex;
      return this;
    }

    public Builder setFilterContainer(SGFilterContainer filterContainer) {
      this.filterContainer = filterContainer;
      return this;
    }

    public Builder setOrderByColumnsIndex(int[] orderByColumnsIndex) {
      this.orderByColumnsIndex = orderByColumnsIndex;
      return this;
    }

    public Builder setLimit(int limit) {
      this.limit = limit;
      return this;
    }


    public ArgoScanInfo build() {
      return new ArgoScanInfo(this);
    }

  }
}
