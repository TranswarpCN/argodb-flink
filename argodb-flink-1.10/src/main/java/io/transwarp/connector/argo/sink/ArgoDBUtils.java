package io.transwarp.connector.argo.sink;

import io.transwarp.connector.argo.sink.writable.ArgoDBDateUtils;
import io.transwarp.connector.argo.sink.writable.ArgoDBTimestampWritable;
import io.transwarp.holodesk.core.common.WriterResult;
import io.transwarp.holodesk.core.serde.HolodeskType;
import io.transwarp.holodesk.core.serde.SerDeHelper;
import io.transwarp.shiva.bulk.Transaction;
import io.transwarp.shiva.bulk.TransactionHandler;
import io.transwarp.shiva.client.ShivaClient;
import io.transwarp.shiva.common.Status;
import io.transwarp.shiva.engine.holo.VersionEditPB;
import io.transwarp.shiva.engine.holo.VersionEditsPB;
import io.transwarp.shiva.holo.ColumnSpec;
import io.transwarp.shiva.holo.MetaWriter;
import io.transwarp.shiva.holo.Writer;
import io.transwarp.shiva.utils.Bytes;
import org.apache.flink.types.Row;

import java.io.File;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

public class ArgoDBUtils {

  public static int getBucketId(ArgoDBTable table,
                                String[] values) {
    int bucketId = 0;
    for (int i = 0; i < table.getBucketColumns().length; i++) {
      int id = table.columnToId(table.getBucketColumns()[i]);
      bucketId = bucketId * 31 + hashCode(values[id], table.getColumnTypes()[id]);
    }
    return (bucketId & Integer.MAX_VALUE) % table.getBucketNum();
  }


  public static int getBucketId(ArgoDBTable table,
                                Row values) {
    int bucketId = 0;
    for (int i = 0; i < table.getBucketColumns().length; i++) {
      int id = table.columnToId(table.getBucketColumns()[i]);
      bucketId = bucketId * 31 + hashCode(values.getField(i).toString(), table.getColumnTypes()[id]);
    }
    return (bucketId & Integer.MAX_VALUE) % table.getBucketNum();
  }

  public static int hashCode(String obj, String type) {
    if (obj == null) {
      return 0;
    } else {
      if (type.equalsIgnoreCase("int")) {
        return Integer.parseInt(obj);
      } else if (type.equalsIgnoreCase("float")) {
        return Float.floatToIntBits(Float.parseFloat(obj));
      } else if (type.equalsIgnoreCase("double")) {
        long v = Double.doubleToLongBits(Double.parseDouble(obj));
        return (int) (v ^ (v >>> 32));
      } else if (type.equalsIgnoreCase("bigint")) {
        return (int) Long.parseLong(obj);
      } else if (type.equalsIgnoreCase("integer")) {
        return Integer.parseInt(obj);
      } else if (type.equalsIgnoreCase("smallint")) {
        return Short.parseShort(obj);
      } else if (type.equalsIgnoreCase("tinyint")) {
        return Byte.parseByte(obj);
      } else if (type.equalsIgnoreCase("long")) {
        return (int) Long.parseLong(obj);
      } else if (type.equalsIgnoreCase("boolean")) {
        return Boolean.parseBoolean(obj) ? 0 : 1;
      } else if (type.equalsIgnoreCase("string")) {
        byte[] bytes = SerDeHelper.serString(obj);
        int hash = 1;
        for (int i = 0; i < bytes.length; ++i) {
          hash = 31 * hash + bytes[i];
        }
        return hash;
      } else if (type.equalsIgnoreCase("date")) {
        return (int) getDateFromString(obj).getTime();
      } else if (type.equalsIgnoreCase("timestamp")) {
        Timestamp timestamp = getTimestampFromString(obj);
        ArgoDBTimestampWritable writable = new ArgoDBTimestampWritable();
        writable.set(timestamp);
        return writable.hashCode();
      } else if (type.contains("decimal")) {
        String[] precisionAndScale = type.substring(8, type.length() - 1).split(",");
        int precision = Integer.parseInt(precisionAndScale[0].trim());
        int scale = Integer.parseInt(precisionAndScale[1].trim());
        MathContext mathContext = new MathContext(precision, RoundingMode.HALF_UP);
        BigDecimal bigDecimal = new BigDecimal(obj, mathContext);
        BigDecimal newDecimal = bigDecimal.setScale(scale, RoundingMode.HALF_UP);
        return newDecimal.hashCode();
      } else {
        byte[] bytes = SerDeHelper.serString(obj);
        int hash = 1;
        for (int i = 0; i < bytes.length; ++i) {
          hash = 31 * hash + bytes[i];
        }
        return hash;
      }
    }
  }

  public static byte[] getValue(String obj, String type) {
    if (obj == null) {
      return null;
    } else {
      if (type.equalsIgnoreCase("int")) {
        return SerDeHelper.serInt(Integer.parseInt(obj));
      } else if (type.equalsIgnoreCase("float")) {
        return SerDeHelper.serFloat(Float.parseFloat(obj));
      } else if (type.equalsIgnoreCase("double")) {
        return SerDeHelper.serDouble(Double.parseDouble(obj));
      } else if (type.equalsIgnoreCase("bigint")) {
        return SerDeHelper.serLong(Long.parseLong(obj));
      } else if (type.equalsIgnoreCase("integer")) {
        return SerDeHelper.serInt(Integer.parseInt(obj));
      } else if (type.equalsIgnoreCase("smallint")) {
        return SerDeHelper.serShort(Short.parseShort(obj));
      } else if (type.equalsIgnoreCase("tinyint")) {
        return SerDeHelper.serByte(Byte.parseByte(obj));
      } else if (type.equalsIgnoreCase("long")) {
        return SerDeHelper.serLong(Long.parseLong(obj));
      } else if (type.equalsIgnoreCase("boolean")) {
        return SerDeHelper.serBoolean(Boolean.parseBoolean(obj));
      } else if (type.equalsIgnoreCase("string")) {
        return SerDeHelper.serString(obj);
      } else if (type.equalsIgnoreCase("date")) {
        return SerDeHelper.serLong(getDateFromString(obj).getTime());
      } else if (type.equalsIgnoreCase("timestamp")) {
        Timestamp timestamp = getTimestampFromString(obj);
        ArgoDBTimestampWritable writable = new ArgoDBTimestampWritable();
        writable.set(timestamp);
        return writable.getBinarySortable();
      } else if (type.toLowerCase().startsWith("decimal")) {
        ;
        String[] precisionAndScale = type.substring(8, type.length() - 1).split(",");
        int precision = Integer.parseInt(precisionAndScale[0].trim());
        int scale = Integer.parseInt(precisionAndScale[1].trim());
        MathContext mathContext = new MathContext(precision, RoundingMode.HALF_UP);
        BigDecimal bigDecimal = new BigDecimal(obj, mathContext);
        BigDecimal newDecimal = bigDecimal.setScale(scale, RoundingMode.HALF_UP);

        byte[] internalStorage = newDecimal.unscaledValue().toByteArray();
        ByteBuffer buffer = ByteBuffer.allocate(9 + 9 + internalStorage.length);
        writeVInt(buffer, scale);
        writeVInt(buffer, internalStorage.length);
        buffer.put(internalStorage, 0, internalStorage.length);
        return buffer.array();
      } else {
        return SerDeHelper.serString(obj);
      }
    }
  }

  public static int shivaTypeToArgoType(ColumnSpec.ColumnType ht) {
    if (ht.equals(ColumnSpec.ColumnType.INT)) {
      return HolodeskType.INT;
    } else if (ht.equals(ColumnSpec.ColumnType.BYTE)) {
      return HolodeskType.BYTE;
    } else if (ht.equals(ColumnSpec.ColumnType.BOOLEAN)) {
      return HolodeskType.BOOLEAN;
    } else if (ht.equals(ColumnSpec.ColumnType.SHORT)) {
      return HolodeskType.SHORT;
    } else if (ht.equals(ColumnSpec.ColumnType.LONG)) {
      return HolodeskType.LONG;
    } else if (ht.equals(ColumnSpec.ColumnType.CHAR)) {
      return HolodeskType.CHAR;
    } else if (ht.equals(ColumnSpec.ColumnType.VARCHAR)) {
      return HolodeskType.VARCHAR;
    } else if (ht.equals(ColumnSpec.ColumnType.VARCHAR2)) {
      return HolodeskType.VARCHAR2;
    } else if (ht.equals(ColumnSpec.ColumnType.FLOAT)) {
      return HolodeskType.FLOAT;
    } else if (ht.equals(ColumnSpec.ColumnType.DOUBLE)) {
      return HolodeskType.DOUBLE;
    } else if (ht.equals(ColumnSpec.ColumnType.STRING)) {
      return HolodeskType.STRING;
    } else if (ht.equals(ColumnSpec.ColumnType.WRDECIMAL)) {
      return HolodeskType.WRDECIMAL;
    } else {
      return HolodeskType.OTHER;
    }
  }

  public static void commit(ShivaClient shivaClient, TransactionHandler handler,
                            List<WriterResult> results, String sectionName,
                            byte[] tabletId, int bucketId) throws Exception {
    MetaWriter metaWriter = shivaClient.newBulkMetaWriter(handler, 1);
    List<Writer.FileItem> metaItems = new ArrayList<>();
    VersionEditsPB.Builder versionEditsBuilder = VersionEditsPB.newBuilder();
    VersionEditPB.Builder versionEditBuilder = versionEditsBuilder.addEditsBuilder();
    versionEditBuilder.setSection(sectionName);
    for (WriterResult result : results) {
      VersionEditPB.RowSetEdit.Builder rowSetEditBuilder = versionEditBuilder.addEditsBuilder();
      rowSetEditBuilder.setBucket(bucketId);
      rowSetEditBuilder.getAddBaseBuilder().setName(result.getFileName());
    }
    metaItems.add(new Writer.FileItem(tabletId, versionEditBuilder.build()));

    Status status = metaWriter.send(metaItems);
    if (!status.ok()) {
      throw new RuntimeException(status.toString());
    }
  }

  public static void flushToShiva(ShivaClient shivaClient, LinkedBlockingQueue<WriterResult> queue,
                                  String tableName, int tabletNum, int tabletId, int bucketId) throws Exception {
    ArrayList<WriterResult> fileBuffers = new ArrayList<>();
    int i = 0;
    while (!queue.isEmpty() && i < tabletNum) {
      fileBuffers.add(queue.poll());
      i++;
    }
    if (fileBuffers.isEmpty()) {
      return;
    }
    Transaction transaction = shivaClient.newBulkTransaction();
    try {
      transaction.setWaitForCommitFinish(true);
      transaction.setWaitCommitFinishTimeoutS(600);
      transaction.setWaitLockTimeoutS(-1);
      Status status = transaction.begin();
      if (!status.ok()) {
        throw new RuntimeException("Begin insert transaction failed, error: " + status.toString());
      }
      Set<String> sections = new HashSet<>();
      sections.add(tableName);
      TransactionHandler handler = TransactionUtils.getMultiSectionAppendTransactionHandler(
        transaction, tableName, sections);
      Writer shivaWriter = shivaClient.newBulkWriter(handler.getBulkLoadMeta(), 6);
      byte[] tablet = shivaWriter.primaryKeyToPartitionKey(Bytes.fromInt(tabletId));
      ArrayList<Writer.FileItem> fileItems = new ArrayList<>();
      for (int index = 0; index < fileBuffers.size(); index++) {
        fileItems.add(new Writer.FileItem(fileBuffers.get(index).getFilePath(), tablet));
      }
      status = shivaWriter.submit(fileItems);
      if (!status.ok()) {
        throw new RuntimeException("Shiva submit file fails, due to " + status);
      }
      status = shivaWriter.flush();
      if (!status.ok()) {
        throw new RuntimeException("Shiva flush file fails, due to " + status);
      }
      commit(shivaClient, handler, fileBuffers, tableName, tablet, bucketId);
      TransactionUtils.commitTransaction(transaction);
      for (int index = 0; index < fileBuffers.size(); index++) {
        new File(fileBuffers.get(index).getFilePath()).delete();
      }
    } catch (Exception e) {
      if (transaction != null) {
        TransactionUtils.abortTransaction(transaction);
      }
    }
  }

  static Timestamp getTimestampFromString(String s) {
    Timestamp result;
    s = s.trim();

    // Throw away extra if more than 9 decimal places
    int periodIdx = s.indexOf(".");
    if (periodIdx != -1) {
      if (s.length() - periodIdx > 9) {
        s = s.substring(0, periodIdx + 10);
      }
    }
    try {
      result = Timestamp.valueOf(s);
    } catch (IllegalArgumentException ia) {
      // if Timestamp.valueof(s) could not get result, we
      // convert it to date first
      Date d = getDateFromString(s);
      result = new Timestamp(d.getTime());
    }
    return result;
  }

  public static Date getDateFromString(String s) {
    int firstDash;
    int secondDash;
    int firstSlash;
    int secondSlash;
    int dateStrLen = s.length();
    Date d = null;
    Calendar calendar = Calendar.getInstance();
    String hourStr = null;

    if (s.indexOf(' ') != -1) {
      String[] dateStr = s.split("\\s+");
      if (dateStr.length < 1 || dateStr.length > 2) {
        throw new java.lang.IllegalArgumentException("String " + s + " can not be converted to Date");
      }
      if (dateStr.length == 2) {
        s = dateStr[0];
        hourStr = dateStr[1];
      }
      dateStrLen = dateStr[0].length();
    }

    firstDash = s.indexOf('-');
    secondDash = s.indexOf('-', firstDash + 1);
    firstSlash = s.indexOf('/');
    secondSlash = s.indexOf('/', firstSlash + 1);
    if (firstDash == 2 && secondDash == 6 && dateStrLen == 9) {
      // dd-MMM-yy
      d = new Date();
      ArgoDBDateParser dateParser = new ArgoDBDateParser(new SimpleDateFormat(ArgoDBDateUtils.ENGLISH_DATE_FORMAT));
      if (!dateParser.parseDate(s, d, false)) {
        throw new java.lang.IllegalArgumentException("String " + s + " can not be converted to Date");
      }
      // Solve the time part
      if (d != null && hourStr != null) {
        calendar.setTime(d);
        String year = String.valueOf(calendar.get(Calendar.YEAR));
        String month = String.valueOf(calendar.get(Calendar.MONTH) + 1);
        String day = String.valueOf(calendar.get(Calendar.DAY_OF_MONTH));
        d = getHiveDate(year, month, day, hourStr);
      }
    } else if (firstDash == 2 && secondDash == 6 && dateStrLen == 11) {
      // dd-MMM-yyyy
      d = new Date();
      ArgoDBDateParser dateParser = new ArgoDBDateParser(new SimpleDateFormat(ArgoDBDateUtils.ENGLISH_DATE_FORMAT2));
      if (!dateParser.parseDate(s, d, false)) {
        throw new java.lang.IllegalArgumentException("String " + s + " can not be converted to Date");
      }
      // Solve the time part
      if (d != null && hourStr != null) {
        calendar.setTime(d);
        String year = String.valueOf(calendar.get(Calendar.YEAR));
        String month = String.valueOf(calendar.get(Calendar.MONTH) + 1);
        String day = String.valueOf(calendar.get(Calendar.DAY_OF_MONTH));
        d = getHiveDate(year, month, day, hourStr);
      }
    } else if ((firstDash > 0) && (secondDash > 0) && (secondDash < s.length() - 1)) {
      // yyyy-MM-dd
      String yyyy = s.substring(0, firstDash);
      String mm = s.substring(firstDash + 1, secondDash);
      String dd = s.substring(secondDash + 1);
      d = getHiveDate(yyyy, mm, dd, hourStr);
    } else if ((firstSlash > 0) && (secondSlash > 0) && (secondSlash < s.length() - 1)) {
      // yyyy/MM/dd
      String yyyy = s.substring(0, firstSlash);
      String mm = s.substring(firstSlash + 1, secondSlash);
      String dd = s.substring(secondSlash + 1);
      d = getHiveDate(yyyy, mm, dd, hourStr);
    } else if (s.length() == 8 && hourStr == null) {
      //yyyyMMdd
      String yyyy = s.substring(0, 4);
      String mm = s.substring(4, 6);
      String dd = s.substring(6, 8);
      d = getHiveDate(yyyy, mm, dd, null);
    }
    if (d == null) {
      throw new java.lang.IllegalArgumentException("String " + s + " can not be converted to Date");
    }
    return d;
  }

  private static Date getHiveDate(String yyyy, String mm, String dd, String hourStr) {
    final int YEAR_LENGTH = 4;
    final int MONTH_LENGTH = 2;
    final int DAY_LENGTH = 2;
    final int MAX_MONTH = 12;
    final int MAX_DAY = 31;

    final int HOUR_LENGTH = 2;
    final int MINUTE_LENGTH = 2;
    final int SECOND_LENGTH = 2;
    final int MS_LENGTH = 3;
    final int MAX_HOUR = 23;
    final int MAX_MINUTE = 59;
    final int MAX_SECOND = 59;
    final int MAX_MILLIS = 999;
    Date d = null;

    if (yyyy.length() == YEAR_LENGTH && mm.length() > 0
      && mm.length() <= MONTH_LENGTH && dd.length() <= DAY_LENGTH && dd.length() > 0) {
      int year = Integer.parseInt(yyyy);
      int month = Integer.parseInt(mm);
      int day = Integer.parseInt(dd);
      if (month >= 1 && month <= MAX_MONTH) {
        int maxDays = MAX_DAY;
        switch (month) {
          // February determine if a leap year or not
          case 2:
            if ((year % 4 == 0 && !(year % 100 == 0)) || (year % 400 == 0)) {
              maxDays = MAX_DAY - 2; // leap year so 29 days in February
            } else {
              maxDays = MAX_DAY - 3; // not a leap year so 28 days in February
            }
            break;
          // April, June, Sept, Nov 30 day months
          case 4:
          case 6:
          case 9:
          case 11:
            maxDays = MAX_DAY - 1;
            break;
        }
        if (day >= 1 && day <= maxDays) {
          if (hourStr == null) {
            d = new Date(year - 1900, month - 1, day);
          } else {
            // Process the hour string to set the hour, minute, second
            int firstColon = hourStr.indexOf(':');
            int secondColon = hourStr.indexOf(':', firstColon + 1);
            int period = hourStr.indexOf('.', secondColon + 1);
            if ((firstColon > 0) && (secondColon > 0) && (secondColon < hourStr.length() - 1)) {
              String hh = hourStr.substring(0, firstColon);
              String mM = hourStr.substring(firstColon + 1, secondColon);
              String ss = hourStr.substring(secondColon + 1);
              String ms = null;
              if (period > 0) {
                ss = hourStr.substring(secondColon + 1, period);
                ms = hourStr.substring(period + 1).trim();
              }

              if (hh.length() == HOUR_LENGTH && mM.length() == MINUTE_LENGTH
                && ss.length() == SECOND_LENGTH) {
                int hour = Integer.parseInt(hh);
                int minute = Integer.parseInt(mM);
                int second = Integer.parseInt(ss);
                if ((hour >= 0 && hour <= MAX_HOUR)
                  && (minute >= 0 && minute <= MAX_MINUTE)
                  && (second >= 0 && second <= MAX_SECOND)) {
                  d = new Date(year - 1900, month - 1, day, hour, minute, second);
                }
              }
              if (d != null && ms != null && !ms.isEmpty()) {
                char[] msList = {'0', '0', '0'};
                for (int i = 0; i < MS_LENGTH; i++) {
                  if (i < ms.length()) {
                    msList[i] = ms.charAt(i);
                  }
                }
                int millis = Integer.parseInt(new String(msList));
                if (millis < MAX_MILLIS) {
                  d.setTime(d.getTime() + millis);
                }
              }
            }
          }
        }
      }
    }
    return d;
  }

  public static void writeVLong(ByteBuffer byteStream, long l) {
    byte[] vLongBytes = new byte[9];
    int len = writeVLongToByteArray(vLongBytes, l);
    byteStream.put(vLongBytes, 0, len);
  }

  public static int writeVLongToByteArray(byte[] bytes, long l) {
    return writeVLongToByteArray(bytes, 0, l);
  }

  public static int writeVLongToByteArray(byte[] bytes, int offset, long l) {
    if (l >= -112L && l <= 127L) {
      bytes[offset] = (byte) ((int) l);
      return 1;
    } else {
      int len = -112;
      if (l < 0L) {
        l = ~l;
        len = -120;
      }

      for (long tmp = l; tmp != 0L; --len) {
        tmp >>= 8;
      }

      bytes[offset] = (byte) len;
      len = len < -120 ? -(len + 120) : -(len + 112);

      for (int idx = len; idx != 0; --idx) {
        int shiftbits = (idx - 1) * 8;
        long mask = 255L << shiftbits;
        bytes[offset + 1 - (idx - len)] = (byte) ((int) ((l & mask) >> shiftbits));
      }

      return 1 + len;
    }
  }

  public static void writeVInt(ByteBuffer stream, int i) {
    writeVLong(stream, (long) i);
  }
}
