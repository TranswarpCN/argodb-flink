package io.transwarp.connector.argodb.table;

import io.transwarp.shiva2.bulk.ShivaTransaction;
import io.transwarp.shiva2.shiva.client.ShivaClient;
import lombok.Getter;
import org.apache.flink.annotation.Internal;

@Getter
@Internal
public class HolodeskWriterState {

  private Long transactionalId;

  private ShivaClient shivaClient;

  private ShivaTransaction transaction;


  public HolodeskWriterState(Long transactionalId, ShivaClient shivaClient, ShivaTransaction transaction) {
    this.transactionalId = transactionalId;
    this.shivaClient = shivaClient;
    this.transaction = transaction;
  }

  public Long getShivaTransactionId() {
    return transactionalId;
  }

  @Override
  public String toString() {
    return "HolodeskLevelWriterState{"
      + ", transactionId='"
      + transactionalId
      + '\''
      + '}';
  }
}
