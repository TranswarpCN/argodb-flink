package io.transwarp.connector.argo.sink;

import io.transwarp.holodesk.core.common.WriterResult;
import io.transwarp.holodesk.core.storage.RowWriter;
import io.transwarp.holodesk.core.writer.RowWriterCloseCallBack;
import io.transwarp.shiva.holo.Writer;

import java.util.concurrent.LinkedBlockingQueue;

public class ArgoDBCloseCallBack implements RowWriterCloseCallBack {
  private Writer shivaWriter;
  private byte[] tabletId;

  public ArgoDBCloseCallBack(Writer shivaWriter, byte[] tabletId) {
    this.shivaWriter = shivaWriter;
    this.tabletId = tabletId;
  }


  @Override
  public void execute(WriterResult writerResult) {
    queue.offer(writerResult);
//    List<Writer.FileItem> fileItems = new ArrayList<Writer.FileItem>();
//    fileItems.add(new Writer.FileItem(writerResult.getFilePath(), tabletId));
//    Status status = shivaWriter.submit(fileItems);
//    if (!status.ok()) {
//      throw new RuntimeException("Shiva submit file fails, due to " + status);
//    }
  }


  private LinkedBlockingQueue<WriterResult> queue;

  public ArgoDBCloseCallBack(LinkedBlockingQueue<WriterResult> queue) {
    this.queue = queue;
  }

  @Override
  public void execute(RowWriter rowWriter) {

  }
}
