package io.transwarp.connector.argodb;

import io.transwarp.shiva2.client.ShivaClient;
import io.transwarp.shiva2.common.Options;

import java.util.concurrent.ConcurrentHashMap;

public class ShivaNewEnv {
  private static final ConcurrentHashMap<String, ShivaClient> shiva2ClientMap = new ConcurrentHashMap<>();

  public static ShivaClient getShiva2Client(String mastergroup) {
    if (shiva2ClientMap.get(mastergroup) == null) {
      newShivaClient(mastergroup);
    }

    return shiva2ClientMap.get(mastergroup);
  }

  private static void newShivaClient(String masterGroup) {
    Options options = new Options();
    options.masterGroup = masterGroup;
    options.bulkTransactionWaitLockTimeS = 100;
    options.bulkTransactionRetryGetLockIntervalS = 5;
    options.maxErrorRetry = 15;
    options.useExternalAddress = true;
    ShivaClient shiva2Client = ShivaClient.getInstance();
    try {
      shiva2Client.start(options);
      shiva2ClientMap.put(masterGroup, shiva2Client);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

}
