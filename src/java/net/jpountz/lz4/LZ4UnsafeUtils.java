package net.jpountz.lz4;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import static net.jpountz.lz4.LZ4Utils.COPY_LENGTH;

import java.lang.reflect.Field;
import java.nio.ByteOrder;

import sun.misc.Unsafe;

enum LZ4UnsafeUtils {
  ;

  static final ByteOrder NATIVE_BYTE_ORDER = ByteOrder.nativeOrder();
  private static final Unsafe UNSAFE;
  private static final long BYTE_ARRAY_OFFSET;
  private static final int BYTE_ARRAY_SCALE;
  private static final long INT_ARRAY_OFFSET;
  private static final int INT_ARRAY_SCALE;
  private static final long SHORT_ARRAY_OFFSET;
  private static final int SHORT_ARRAY_SCALE;
  
  static {
    try {
      Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
      theUnsafe.setAccessible(true);
      UNSAFE = (Unsafe) theUnsafe.get(null);
      BYTE_ARRAY_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
      BYTE_ARRAY_SCALE = UNSAFE.arrayIndexScale(byte[].class);
      INT_ARRAY_OFFSET = UNSAFE.arrayBaseOffset(int[].class);
      INT_ARRAY_SCALE = UNSAFE.arrayIndexScale(int[].class);
      SHORT_ARRAY_OFFSET = UNSAFE.arrayBaseOffset(short[].class);
      SHORT_ARRAY_SCALE = UNSAFE.arrayIndexScale(short[].class);
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
    LZ4Utils.naiveIncrementalCopy(dest, matchOff, dOff, matchCopyEnd - dOff);
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

  static int readByte(byte[] src, int srcOff) {
    return UNSAFE.getByte(src, BYTE_ARRAY_OFFSET + BYTE_ARRAY_SCALE * srcOff) & 0xFF;
  }

  static void writeByte(byte[] src, int srcOff, int value) {
    UNSAFE.putByte(src, BYTE_ARRAY_OFFSET + BYTE_ARRAY_SCALE * srcOff, (byte) value);
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

  static int readShort(short[] src, int srcOff) {
    return UNSAFE.getShort(src, SHORT_ARRAY_OFFSET + SHORT_ARRAY_SCALE * srcOff) & 0xFFFF;
  }

  static void writeShort(short[] dest, int destOff, int value) {
    UNSAFE.putShort(dest, SHORT_ARRAY_OFFSET + SHORT_ARRAY_SCALE * destOff, (short) value);
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

  static int hash64k(byte[] buf, int off) {
    return LZ4Utils.hash64k(readInt(buf, off));
  }

  static boolean readIntEquals(byte[] src, int ref, int sOff) {
    return readInt(src, ref) == readInt(src, sOff);
  }

}
