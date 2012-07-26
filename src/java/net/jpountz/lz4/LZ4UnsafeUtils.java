package net.jpountz.lz4;

import static net.jpountz.lz4.LZ4Utils.COPY_LENGTH;

import java.lang.reflect.Field;
import java.nio.ByteOrder;

import sun.misc.Unsafe;

enum LZ4UnsafeUtils {
  ;

  static final ByteOrder NATIVE_BYTE_ORDER = ByteOrder.nativeOrder();
  private static final Unsafe UNSAFE;
  private static final long BYTE_ARRAY_OFFSET;
  private static final long INT_ARRAY_OFFSET;
  private static final int INT_ARRAY_SCALE;
  
  static {
    try {
      Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
      theUnsafe.setAccessible(true);
      UNSAFE = (Unsafe) theUnsafe.get(null);
      BYTE_ARRAY_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
      INT_ARRAY_OFFSET = UNSAFE.arrayBaseOffset(int[].class);
      INT_ARRAY_SCALE = UNSAFE.arrayIndexScale(int[].class);
    } catch (IllegalAccessException e) {
      throw new ExceptionInInitializerError("Cannot access Unsafe");
    } catch (NoSuchFieldException e) {
      throw new ExceptionInInitializerError("Cannot access Unsafe");
    } catch (SecurityException e) {
      throw new ExceptionInInitializerError("Cannot access Unsafe");
    }
  }

  static void safeArraycopy(byte[] src, int srcOff, byte[] dest, int destOff, int len) {
    final int fastLen = len & 0xFFFFFFF8;
    wildArraycopy(src, srcOff, dest, destOff, fastLen);
    for (int i = 0, slowLen = len & 0x7; i < slowLen; i += 1) {
      writeByte(dest, destOff + fastLen + i, readByte(src, srcOff + fastLen + i));
    }
  }

  static void wildArraycopy(byte[] src, int srcOff, byte[] dest, int destOff, int len) {
    for (int i = 0; i < len; i += 8) {
      writeLong(dest, destOff + i, readLong(src, srcOff + i));
    }
  }

  static void safeIncrementalCopy(byte[] dest, int matchOff, int dOff, int matchCopyEnd) {
    LZ4Utils.incrementalCopy(dest, matchOff, dOff, matchCopyEnd - dOff);
  }

  static void wildIncrementalCopy(byte[] dest, int matchOff, int dOff, int matchCopyEnd) {
    while (dOff - matchOff < COPY_LENGTH) {
      writeLong(dest, dOff, readLong(dest, matchOff));
      dOff += dOff - matchOff;
    }
    while (dOff < matchCopyEnd) {
      writeLong(dest, dOff, readLong(dest, matchOff));
      dOff += 8;
      matchOff += 8;
    }
  }

  static byte readByte(byte[] src, int srcOff) {
    return UNSAFE.getByte(src, BYTE_ARRAY_OFFSET + srcOff);
  }

  static void writeByte(byte[] dest, int destOff, byte value) {
    UNSAFE.putByte(dest, BYTE_ARRAY_OFFSET + destOff, value);
  }

  static long readLong(byte[] src, int srcOff) {
    return UNSAFE.getLong(src, BYTE_ARRAY_OFFSET + srcOff);
  }

  static void writeLong(byte[] dest, int destOff, long value) {
    UNSAFE.putLong(dest, BYTE_ARRAY_OFFSET + destOff, value);
  }

  static int readInt(byte[] src, int srcOff) {
    return UNSAFE.getInt(src, BYTE_ARRAY_OFFSET + srcOff);
  }

  static void writeInt(byte[] dest, int destOff, int value) {
    UNSAFE.putInt(dest, BYTE_ARRAY_OFFSET + destOff, value);
  }

  static short readShort(byte[] src, int srcOff) {
    return UNSAFE.getShort(src, BYTE_ARRAY_OFFSET + srcOff);
  }

  static void writeShort(byte[] dest, int destOff, short value) {
    UNSAFE.putShort(dest, BYTE_ARRAY_OFFSET + destOff, value);
  }

  static int readInt(int[] src, int srcOff) {
    return UNSAFE.getInt(src, INT_ARRAY_OFFSET + INT_ARRAY_SCALE * srcOff);
  }

  static void writeInt(int[] dest, int destOff, int value) {
    UNSAFE.putInt(dest, INT_ARRAY_OFFSET + INT_ARRAY_SCALE * destOff, value);
  }

  static int readShortLittleEndian(byte[] src, int srcOff) {
    short s = readShort(src, srcOff);
    if (NATIVE_BYTE_ORDER == ByteOrder.BIG_ENDIAN) {
      s = Short.reverseBytes(s);
    }
    return s & 0xFFFF;
  }

  static void writeShortLittleEndian(byte[] dest, int destOff, int value) {
    short s = (short) value;
    if (NATIVE_BYTE_ORDER == ByteOrder.BIG_ENDIAN) {
      s = Short.reverseBytes(s);
    }
    writeShort(dest, destOff, s);
  }

  static int hash(byte[] buf, int off) {
    return LZ4Utils.hash(readInt(buf, off));
  }

  static boolean readIntEquals(byte[] src, int ref, int sOff) {
    return readInt(src, ref) == readInt(src, sOff);
  }

}
