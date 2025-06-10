package io.transwarp.connector.argodb;

import io.transwarp.shiva2.shiva.client.ShivaClient;
import io.transwarp.shiva2.shiva.common.Options;
import io.transwarp.shiva2.shiva.exception.ShivaException;

import java.util.concurrent.ConcurrentHashMap;

public class ShivaEnv {
  private static ConcurrentHashMap<String, ShivaClient> shiva2ClientMap = new ConcurrentHashMap<>();


  public static ShivaClient getShiva2Client(String mastergroup) throws ShivaException {
    if (shiva2ClientMap.get(mastergroup) == null) {
      newShivaClient(mastergroup);
    }

    return shiva2ClientMap.get(mastergroup);
  }

  private static void newShivaClient(String masterGroup) throws ShivaException {
    Options options = new Options();
    options.masterGroup = masterGroup;
    options.rpcTimeoutMs = 120000;
    options.bulkTransactionWaitLockTimeS = 100;
    options.bulkTransactionRetryGetLockIntervalS = 5;
    options.maxErrorRetry = 15;
    options.useExternalAddress = true;
    ShivaClient shiva2Client = ShivaClient.getInstance();
    shiva2Client.start(options);
    shiva2ClientMap.put(masterGroup, shiva2Client);
  }

}
