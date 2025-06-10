package io.transwarp.connector.argo.sink.writable;


import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

/**
 * TimestampWritable
 * Writable equivalent of java.sq.Timestamp
 * <p>
 * Timestamps are of the format
 * YYYY-MM-DD HH:MM:SS.[fff...]
 * <p>
 * We encode Unix timestamp in seconds in 4 bytes, using the MSB to signify
 * whether the timestamp has a fractional portion.
 * <p>
 * The fractional portion is reversed, and encoded as a VInt
 * so timestamps with less precision use fewer bytes.
 * <p>
 * 0.1    -> 1
 * 0.01   -> 10
 * 0.001  -> 100
 */
public class ArgoDBTimestampWritable {

  static final public byte[] nullBytes = {0x0, 0x0, 0x0, 0x0};

  private static final int DECIMAL_OR_SECOND_VINT_FLAG = 0x80000000;
  private static final int LOWEST_31_BITS_OF_SEC_MASK = 0x7fffffff;

  private static final long SEVEN_BYTE_LONG_SIGN_FLIP = 0xff80L << 48;

  private static final BigDecimal BILLION_BIG_DECIMAL = BigDecimal.valueOf(1000000000);

  /**
   * The maximum number of bytes required for a TimestampWritable
   */
  public static final int MAX_BYTES = 13;

  public static final int BINARY_SORTABLE_LENGTH = 11;

  private static final ThreadLocal<DateFormat> threadLocalDateFormat =
    new ThreadLocal<DateFormat>() {
      @Override
      protected synchronized DateFormat initialValue() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      }
    };

  private static final ThreadLocal<Timestamp> threadLocalTimestamp =
    new ThreadLocal<Timestamp>() {
      @Override
      protected synchronized Timestamp initialValue() {
        return new Timestamp(0);
      }
    };

  private Timestamp timestamp = new Timestamp(0);

  /**
   * true if data is stored in timestamp field rather than byte arrays.
   * allows for lazy conversion to bytes when necessary
   * false otherwise
   */
  private boolean bytesEmpty;
  private boolean timestampEmpty;

  /* Allow use of external byte[] for efficiency */
  private byte[] currentBytes;
  private final byte[] internalBytes = new byte[MAX_BYTES];
  private byte[] externalBytes;
  private int offset;

  /* Constructors */
  public ArgoDBTimestampWritable() {
    bytesEmpty = false;
    currentBytes = internalBytes;
    offset = 0;

    clearTimestamp();
  }

  private void clearTimestamp() {
    timestampEmpty = true;
  }

  public ArgoDBTimestampWritable(Timestamp t) {
    set(t);
  }

  public void set(Timestamp t) {
    if (t == null) {
      timestamp.setTime(0);
      timestamp.setNanos(0);
      return;
    }
    this.timestamp = t;
    bytesEmpty = true;
    timestampEmpty = false;
  }


  public long getMillis() {
    long seconds = getSeconds();
    int nanos = getNanos();
    return ((seconds * 1000) + (nanos / 1000000));
  }

  /**
   * @return nanoseconds in this TimestampWritable
   */
  public int getNanos() {
    if (!timestampEmpty) {
      return timestamp.getNanos();
    } else if (!bytesEmpty) {
      return hasDecimalOrSecondVInt() ?
        getNanos(currentBytes, offset + 4) : 0;
    } else {
      throw new IllegalStateException("Both timestamp and bytes are empty");
    }
  }

  public long getSeconds() {
    if (!timestampEmpty) {
      return millisToSeconds(timestamp.getTime());
    } else if (!bytesEmpty) {
      return getSeconds(currentBytes, offset);
    } else {
      throw new IllegalStateException("Both timestamp and bytes are empty");
    }
  }

  public static class VInt {
    public VInt() {
      value = 0;
      length = 0;
    }

    public int value;
    public byte length;
  }

  ;

  public static int getNanos(byte[] bytes, int offset) {
    VInt vInt = new VInt();
    readVInt(bytes, offset, vInt);
    int val = vInt.value;
    if (val < 0) {
      // This means there is a second VInt present that specifies additional bits of the timestamp.
      // The reversed nanoseconds value is still encoded in this VInt.
      val = -val - 1;
    }
    int len = (int) Math.floor(Math.log10(val)) + 1;

    // Reverse the value
    int tmp = 0;
    while (val != 0) {
      tmp *= 10;
      tmp += val % 10;
      val /= 10;
    }
    val = tmp;

    if (len < 9) {
      val *= Math.pow(10, 9 - len);
    }
    return val;
  }

  public static void readVInt(byte[] bytes, int offset, VInt vInt) {
    byte firstByte = bytes[offset];
    vInt.length = (byte) decodeVIntSize(firstByte);
    if (vInt.length == 1) {
      vInt.value = firstByte;
      return;
    }
    int i = 0;
    for (int idx = 0; idx < vInt.length - 1; idx++) {
      byte b = bytes[offset + 1 + idx];
      i = i << 8;
      i = i | (b & 0xFF);
    }
    vInt.value = (isNegativeVInt(firstByte) ? (i ^ -1) : i);
  }

  private boolean hasDecimalOrSecondVInt() {
    return hasDecimalOrSecondVInt(currentBytes[offset]);
  }

  private static boolean hasDecimalOrSecondVInt(byte b) {
    return (b >> 7) != 0;
  }

  private static boolean hasSecondVInt(byte value) {
    return value < -120 || value >= -112 && value < 0;
  }

  public static long getSeconds(byte[] bytes, int offset) {
    int lowest31BitsOfSecondsAndFlag = bytesToInt(bytes, offset);
    if (lowest31BitsOfSecondsAndFlag >= 0 ||  // the "has decimal or second VInt" flag is not set
      !hasSecondVInt(bytes[offset + 4])) {
      // The entire seconds field is stored in the first 4 bytes.
      return lowest31BitsOfSecondsAndFlag & LOWEST_31_BITS_OF_SEC_MASK;
    }

    // We compose the seconds field from two parts. The lowest 31 bits come from the first four
    // bytes. The higher-order bits come from the second VInt that follows the nanos field.
    return ((long) (lowest31BitsOfSecondsAndFlag & LOWEST_31_BITS_OF_SEC_MASK)) |
      (readVLongFromByteArray(bytes, offset + 4 + decodeVIntSize(bytes[offset + 4])) << 31);
  }

  public static long readVLongFromByteArray(final byte[] bytes, int offset) {
    byte firstByte = bytes[offset++];
    int len = decodeVIntSize(firstByte);
    if (len == 1) {
      return firstByte;
    }
    long i = 0;
    for (int idx = 0; idx < len - 1; idx++) {
      byte b = bytes[offset++];
      i = i << 8;
      i = i | (b & 0xFF);
    }
    return (isNegativeVInt(firstByte) ? ~i : i);
  }

  public static boolean isNegativeVInt(byte value) {
    return value < -120 || value >= -112 && value < 0;
  }

  private static int decodeVIntSize(byte value) {
    if (value >= -112) {
      return 1;
    } else {
      return value < -120 ? -119 - value : -111 - value;
    }
  }

  private static int bytesToInt(byte[] bytes, int offset) {
    return ((0xFF & bytes[offset]) << 24)
      | ((0xFF & bytes[offset + 1]) << 16)
      | ((0xFF & bytes[offset + 2]) << 8)
      | (0xFF & bytes[offset + 3]);
  }

  /**
   * @return byte[] representation of TimestampWritable that is binary
   * sortable (7 bytes for seconds, 4 bytes for nanoseconds)
   */
  public byte[] getBinarySortable() {
    byte[] b = new byte[BINARY_SORTABLE_LENGTH];
    int nanos = getNanos();
    // We flip the highest-order bit of the seven-byte representation of seconds to make negative
    // values come before positive ones.
    long seconds = getSeconds() ^ SEVEN_BYTE_LONG_SIGN_FLIP;
    sevenByteLongToBytes(seconds, b, 0);
    intToBytes(nanos, b, 7);
    return b;
  }


  /**
   * Writes <code>value</code> into <code>dest</code> at <code>offset</code>
   *
   * @param value
   * @param dest
   * @param offset
   */
  private static void intToBytes(int value, byte[] dest, int offset) {
    dest[offset] = (byte) ((value >> 24) & 0xFF);
    dest[offset + 1] = (byte) ((value >> 16) & 0xFF);
    dest[offset + 2] = (byte) ((value >> 8) & 0xFF);
    dest[offset + 3] = (byte) (value & 0xFF);
  }

  /**
   * Writes <code>value</code> into <code>dest</code> at <code>offset</code> as a seven-byte
   * serialized long number.
   */
  static void sevenByteLongToBytes(long value, byte[] dest, int offset) {
    dest[offset] = (byte) ((value >> 48) & 0xFF);
    dest[offset + 1] = (byte) ((value >> 40) & 0xFF);
    dest[offset + 2] = (byte) ((value >> 32) & 0xFF);
    dest[offset + 3] = (byte) ((value >> 24) & 0xFF);
    dest[offset + 4] = (byte) ((value >> 16) & 0xFF);
    dest[offset + 5] = (byte) ((value >> 8) & 0xFF);
    dest[offset + 6] = (byte) (value & 0xFF);
  }

  /**
   * Rounds the number of milliseconds relative to the epoch down to the nearest whole number of
   * seconds. 500 would round to 0, -500 would round to -1.
   */
  static long millisToSeconds(long millis) {
    if (millis >= 0) {
      return millis / 1000;
    } else {
      return (millis - 999) / 1000;
    }
  }

  @Override
  public int hashCode() {
    long seconds = getSeconds();
    seconds <<= 30;  // the nanosecond part fits in 30 bits
    seconds |= getNanos();
    return (int) ((seconds >>> 32) ^ seconds);
  }
}

