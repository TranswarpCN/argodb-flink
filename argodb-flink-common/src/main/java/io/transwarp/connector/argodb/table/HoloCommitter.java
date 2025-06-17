package io.transwarp.connector.argodb.table;

import org.apache.flink.api.connector.sink2.Committer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;

public class HoloCommitter implements Committer<HolodeskWriterCommittable>, Cloneable {
  private final Long test = 0L;

  private static final Logger LOG = LoggerFactory.getLogger(HoloCommitter.class);

  @Override
  public void commit(Collection<CommitRequest<HolodeskWriterCommittable>> committables) throws IOException, InterruptedException {
    // do nothing
  }

  @Override
  public void close() throws Exception {

  }
}
