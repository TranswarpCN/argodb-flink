package io.transwarp.connector.argo.sink;

public class ArgoDBRow {
  private final String[] row;

  public ArgoDBRow(String[] row) {
    this.row = row;
  }

  public String[] getRow() {
    return row;
  }
}
