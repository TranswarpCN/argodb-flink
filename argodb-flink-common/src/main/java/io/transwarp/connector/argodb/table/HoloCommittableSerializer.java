/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.transwarp.connector.argodb.table;

import io.transwarp.connector.argodb.ShivaNewEnv;
import io.transwarp.shiva2.ShivaTransactionTokenPB;
import io.transwarp.shiva2.bulk.ShivaBulkTransactionToken;
import io.transwarp.shiva2.bulk.ShivaTransaction;
import io.transwarp.shiva2.exception.ShivaException;
import org.apache.flink.core.io.SimpleVersionedSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.text.MessageFormat;

class HoloCommittableSerializer implements SimpleVersionedSerializer<HolodeskWriterCommittable> {
  private static final Logger LOG = LoggerFactory.getLogger(HoloCommittableSerializer.class);

  @Override
  public int getVersion() {
    return 1;
  }

  @Override
  public byte[] serialize(HolodeskWriterCommittable state) throws IOException {
    try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
         final DataOutputStream out = new DataOutputStream(baos)) {

//            byte[] transactionPB = state.getTransaction().serializeToTokenPB().toByteArray();

      out.writeLong(state.getTransactionalId());
//            out.writeUTF(state.getShivaClient().getShiva2Client().options().masterGroup);
//            out.writeInt(transactionPB.length);
//            out.write(transactionPB);

      out.flush();
      String message = MessageFormat.format("[ARGODB] store Committable when checkpoint {0}", state.getTransactionalId());
      System.out.println(message);
      LOG.info(message);
      return baos.toByteArray();
    }
  }

  @Override
  public HolodeskWriterCommittable deserialize(int version, byte[] serialized) throws IOException {
    try (final ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
         final DataInputStream in = new DataInputStream(bais)) {
      final Long transactionalId = in.readLong();
      final String masterGroup = in.readUTF();
      int pbSize = in.readInt();
      final byte[] tokenBytes = new byte[pbSize];
      in.readFully(tokenBytes);
      String message = MessageFormat.format("[ARGODB] restore Committable when checkpoint {0}", transactionalId);
      System.out.println(message);
      LOG.info(message);

      ShivaTransactionTokenPB shivaTransactionTokenPB = ShivaTransactionTokenPB.parseFrom(tokenBytes);
      ShivaBulkTransactionToken shivaBulkTransactionToken = ShivaBulkTransactionToken.fromPB(shivaTransactionTokenPB);
      ShivaTransaction shivaBulkTransaction = new ShivaTransaction(ShivaNewEnv.getShiva2Client(masterGroup), shivaBulkTransactionToken);

      return new HolodeskWriterCommittable(transactionalId, null, shivaBulkTransaction);
    } catch (ShivaException e) {
      throw new RuntimeException(e);
    }
  }
}
