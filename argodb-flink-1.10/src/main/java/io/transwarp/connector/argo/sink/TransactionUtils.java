package io.transwarp.connector.argo.sink;

import io.transwarp.shiva.bulk.Transaction;
import io.transwarp.shiva.bulk.TransactionHandler;
import io.transwarp.shiva.client.ShivaClient;
import io.transwarp.shiva.common.Status;

import java.util.Set;


public class TransactionUtils {

  public static final String DEFAULT_DATABASE = "default";

  public static Transaction beginTransaction(ShivaClient shivaClient,
                                             boolean readOnlyTx) throws Exception {
    Transaction transaction = readOnlyTx ? shivaClient.newBulkROTransaction() : shivaClient.newBulkTransaction();
    transaction.setWaitForCommitFinish(true);
    transaction.setWaitCommitFinishTimeoutS(600);
    transaction.setWaitLockTimeoutS(-1);
    Status status = transaction.begin();
    if (!status.ok()) {
      throw new RuntimeException("Begin insert transaction failed, error:" + status);
    }
    return transaction;
  }

  public static void commitTransaction(Transaction transaction) throws Exception {
    try {
      Status status = transaction.commit();
      if (!status.ok()) {
        throw new RuntimeException("Commit transaction " + transaction.getTransactionId()
          + " failed, error: " + status.getMsg());
      }
    } catch (Exception e) {
      TransactionUtils.abortTransaction(transaction);
      throw e;
    }

  }

  public static void abortTransaction(Transaction transaction) throws Exception {
    Status status = transaction.abort();
  }

  public static TransactionHandler getMultiSectionAppendTransactionHandler(
    Transaction transaction, String tableName, Set<String> sectionNames) throws Exception {
    TransactionHandler handler = transaction.append(tableName, sectionNames);

    Status status = handler.getStatus();
    if (!status.ok()) {
      throw new RuntimeException("Get append transaction handler failed, error: " + status.getMsg());
    } else {
      return handler;
    }
  }


}

