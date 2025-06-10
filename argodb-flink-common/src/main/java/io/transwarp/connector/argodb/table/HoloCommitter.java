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
    for (CommitRequest<HolodeskWriterCommittable> committable : committables) {
      HolodeskWriterCommittable holodeskWriterCommittable = committable.getCommittable();
//            try {
//                RWTransaction transaction = holodeskWriterCommittable.getTransaction();
//                if (transaction != null) {
//                    transaction.commit();
//                }
//            } catch (Exception e) {
//                throw new RuntimeException(e);
//            }
    }
  }

//    private void commit(RWTransaction transaction) {
//        ShivaTransactionResponsePB.Builder builder = ShivaTransactionResponsePB.newBuilder();
//        bulkPBs.get.subtaskStates.values.foreach(subTaskState => {
//                val inputStream = deser.deserialize[StreamTaskStateHandle](ByteBuffer.wrap(subTaskState.getState())).getState().getOperatorState().asInstanceOf[ByteStreamStateHandle].getState()
//                val length = Bytes.getInt(inputStream)
//                val array = new Array[Byte](length)
//                inputStream.read(array)
//                val responsePB = ShivaTransactionResponsePB.parseFrom(array)
//                responsePB.getTablesList.foreach(table => builder.addTables(table))
//              (0 until size).foreach(i => {
//                val length = Bytes.getInt(inputStream)
//                val array = new Array[Byte](length)
//                inputStream.read(array)
//                val responsePB = ShivaTransactionResponsePB.parseFrom(array)
//                responsePB.getTablesList.foreach(table => builder.addTables(table))
//              })
//    })
//        Status commit = transaction.commit(responsePB);
//
//        LOG.info("Commit global transaction[${lastTxn.getTransactionId}] successful");
//    }

  @Override
  public void close() throws Exception {

  }
}
