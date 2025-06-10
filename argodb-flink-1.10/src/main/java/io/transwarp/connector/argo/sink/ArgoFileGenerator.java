package io.transwarp.connector.argo.sink;

import io.transwarp.holodesk.core.writer.FileGenerator;

import java.io.File;
import java.io.Serializable;
import java.net.InetAddress;

public class ArgoFileGenerator implements FileGenerator, Serializable {
  private final String rootPath;
  private final String tableName;
  private final int machineId;
  private final long bulkLoadId;

  public ArgoFileGenerator(String rootPath, String tableName, long bulkLoadId) throws Exception {
    this.rootPath = rootPath;
    this.tableName = tableName;
    this.machineId = Math.abs(InetAddress.getLocalHost().getHostName().hashCode());
    this.bulkLoadId = bulkLoadId;
  }

  @Override
  public File newFile() {
    String fileName = rootPath + "/holodesk_" + tableName + "_" +
      System.currentTimeMillis() + "_" + machineId + "_" + bulkLoadId;
    return new File(fileName);
  }
}
