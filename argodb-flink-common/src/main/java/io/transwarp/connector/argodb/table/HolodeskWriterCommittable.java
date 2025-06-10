package io.transwarp.connector.argodb.table;


import io.transwarp.shiva2.bulk.ShivaTransaction;
import io.transwarp.shiva2.client.ShivaClient;
import lombok.Getter;
import org.apache.flink.annotation.Internal;


@Getter
@Internal
class HolodeskWriterCommittable {
  HolodeskWriterCommittable() {
  }

  private Long transactionalId;

  private ShivaClient shivaClient;

  private ShivaTransaction transaction;

  HolodeskWriterCommittable(
    ShivaTransaction transaction,
    ShivaClient shivaClient) {
    this.transactionalId = transaction.getTransactionId();
    this.transaction = transaction;
    this.shivaClient = shivaClient;
  }

  HolodeskWriterCommittable(
    Long transactionalId,
    ShivaClient shivaClient, ShivaTransaction transaction) {
    this.transactionalId = transactionalId;
    this.shivaClient = shivaClient;
  }

  public static <K, V> HolodeskWriterCommittable of(ShivaClient shivaClient, ShivaTransaction transaction) {
    return new HolodeskWriterCommittable(transaction, shivaClient);
  }

  public void setTransactionalId(Long transactionalId) {
    this.transactionalId = transactionalId;
  }

  public void setShivaClient(ShivaClient shivaClient) {
    this.shivaClient = shivaClient;
  }

  public void setTransaction(ShivaTransaction transaction) {
    this.transaction = transaction;
  }
}
