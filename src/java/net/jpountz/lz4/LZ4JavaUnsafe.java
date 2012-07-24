package net.jpountz.lz4;

import static net.jpountz.lz4.LZ4Utils.COPY_LENGTH;
import static net.jpountz.lz4.LZ4Utils.HASH_TABLE_SIZE;
import static net.jpountz.lz4.LZ4Utils.LAST_LITERALS;
import static net.jpountz.lz4.LZ4Utils.MAX_DISTANCE;
import static net.jpountz.lz4.LZ4Utils.MF_LIMIT;
import static net.jpountz.lz4.LZ4Utils.MIN_LENGTH;
import static net.jpountz.lz4.LZ4Utils.MIN_MATCH;
import static net.jpountz.lz4.LZ4Utils.ML_BITS;
import static net.jpountz.lz4.LZ4Utils.ML_MASK;
import static net.jpountz.lz4.LZ4Utils.RUN_MASK;
import static net.jpountz.lz4.LZ4Utils.SKIP_STRENGTH;
import static net.jpountz.lz4.LZ4Utils.checkRange;
import static net.jpountz.lz4.LZ4Utils.hash;

import java.lang.reflect.Field;
import java.nio.ByteOrder;
import java.util.Arrays;

import sun.misc.Unsafe;

public enum LZ4JavaUnsafe implements LZ4 {

  FAST {

    public int maxCompressedLength(int length) {
      if (length < 0) {
        throw new IllegalArgumentException("length must be >= 0, got " + length);
      }
      return length + length / 255 + 16;
    }

    @Override
    public int compress(byte[] src, final int srcOff, int srcLen, byte[] dest, final int destOff) {
      checkRange(src, srcOff, srcLen);
      checkRange(dest, destOff, maxCompressedLength(srcLen));

      final int srcEnd = srcOff + srcLen;
      final int srcLimit = srcEnd - LAST_LITERALS;
      final int mflimit = srcEnd - MF_LIMIT;

      int sOff = srcOff;
      int dOff = destOff;

      int anchor = sOff;

      if (srcLen > MIN_LENGTH) {

        final int[] hashTable = new int[HASH_TABLE_SIZE];
        Arrays.fill(hashTable, srcOff);

        ++sOff;

        int forwardSequence = readInt(src, sOff);
        int forwardH = hash(forwardSequence);

        main:
        while (sOff < srcLimit) {

          // find a match
          int forwardOff = sOff;

          int ref;
          int sequence, refSequence;
          int findMatchAttempts = (1 << SKIP_STRENGTH) + 3;
          int back;
          while (true) {
            sOff = forwardOff;
            sequence = forwardSequence;
            final int h = forwardH;
            final int step = findMatchAttempts++ >> SKIP_STRENGTH;
            forwardOff += step;

            if (forwardOff > mflimit) {
              break main;
            }

            forwardSequence = readInt(src, forwardOff);
            forwardH = hash(forwardSequence);
            ref = readInt(hashTable, h);
            back = sOff - ref;
            if (back > MAX_DISTANCE) {
              continue;
            }
            refSequence = readInt(src, ref);
            writeInt(hashTable, h, sOff);
            if (refSequence == sequence) {
              break;
            }
          }

          // catch up
          while (sOff > anchor && ref > srcOff && readByte(src, sOff - 1) == readByte(src, ref - 1)) {
            --sOff;
            --ref;
          }

          // sequence == refsequence
          final int runLen = sOff - anchor;

          // encode literal length
          int tokenOff = dOff++;
          if (runLen >= RUN_MASK) {
            dest[tokenOff] = (byte) (RUN_MASK << ML_BITS);
            int len = runLen - RUN_MASK;
            while (len >= 255) {
              dest[dOff++] = (byte) 255;
              len -= 255;
            }
            dest[dOff++] = (byte) len;
          } else {
            dest[tokenOff] = (byte) (runLen << ML_BITS);
          }

          // copy literals
          wildArraycopy(src, anchor, dest, dOff, runLen);
          dOff += runLen;

          while (true) {
            // encode offset
            writeShortLittleEndian(dest, dOff, back);
            dOff += 2;

            // count nb matches
            sOff += MIN_MATCH;
            ref += MIN_MATCH;
            int matchLen = 0;
            while (sOff < srcLimit - 8) {
              final long diff = readLong(src, sOff) - readLong(src, ref);
              final int zeroBits;
              if (NATIVE_BYTE_ORDER == ByteOrder.BIG_ENDIAN) {
                zeroBits = Long.numberOfLeadingZeros(diff);
              } else {
                zeroBits = Long.numberOfTrailingZeros(diff);
              }
              if (zeroBits == 64) {
                matchLen += 8;
                sOff += 8;
                ref += 8;
              } else {
                final int inc = zeroBits >>> 3;
                matchLen += inc;
                sOff += inc;
                break;
              }
            }

            // encode match len
            if (matchLen >= ML_MASK) {
              dest[tokenOff] |= ML_MASK;
              int len = matchLen - ML_MASK;
              while (len >= 255) {
                dest[dOff++] = (byte) 255;
                len -= 255;
              }
              dest[dOff++] = (byte) len;
            } else {
              dest[tokenOff] |= matchLen;
            }

            // test end of chunk
            if (sOff > mflimit) {
              anchor = sOff;
              break main;
            }

            // fill table
            writeInt(hashTable, hash(readInt(src, sOff - 2)), sOff - 2);

            // test next position
            sequence = readInt(src, sOff);
            final int h = hash(sequence);
            ref = readInt(hashTable, h);
            writeInt(hashTable, h, sOff);
            back = sOff - ref;

            if (back > MAX_DISTANCE || refSequence != sequence) {
              break;
            }

            tokenOff = dOff++;
            dest[tokenOff] = 0;
          }

          // prepare next loop
          anchor = sOff++;
          forwardSequence = readInt(src, sOff);
          forwardH = hash(forwardSequence);
        }
      }

      // encode last literals
      final int runLen = srcEnd - anchor;
      if (runLen >= RUN_MASK) {
        dest[dOff++] = (byte) (RUN_MASK << ML_BITS);
        int len = runLen - RUN_MASK;
        while (len >= 255) {
          dest[dOff++] = (byte) 255;
          len -= 255;
        }
        dest[dOff++] = (byte) len;
      } else {
        dest[dOff++] = (byte) (runLen << ML_BITS);
      }
      // copy literals
      safeArraycopy(src, anchor, dest, dOff, runLen);
      dOff += runLen;

      return dOff - destOff;
    }

  };

  private static final ByteOrder NATIVE_BYTE_ORDER = ByteOrder.nativeOrder();
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

  public int uncompress(byte[] src, final int srcOff, byte[] dest, final int destOff, int destLen) {
    checkRange(src, srcOff);
    checkRange(dest, destOff, destLen);

    final int destEnd = destOff + destLen;

    int sOff = srcOff;
    int dOff = destOff;

    while (true) {
      final int token = src[sOff++] & 0xFF;

      // literals
      int literalLen = token >>> ML_BITS;
      if (literalLen == RUN_MASK) {
          int len;
          while ((len = src[sOff++] & 0xFF) == 255) {
            literalLen += len;
          }
          literalLen += len;
      }

      final int literalCopyEnd = dOff + literalLen;
      if (literalCopyEnd > destEnd - COPY_LENGTH) {
        if (literalCopyEnd > destEnd) {
          throw new LZ4Exception("Malformed input at " + sOff);
        } else {
          safeArraycopy(src, sOff, dest, dOff, literalLen);
          sOff += literalLen;
          break; // EOF
        }
      }

      wildArraycopy(src, sOff, dest, dOff, literalLen);
      sOff += literalLen;
      dOff = literalCopyEnd;

      // matchs
      final int matchDec = readShortLittleEndian(src, sOff);
      sOff += 2;
      int matchOff = dOff - matchDec;

      if (matchOff < destOff) {
        throw new LZ4Exception("Malformed input at " + sOff);
      }

      int matchLen = token & ML_MASK;
      if (matchLen == ML_MASK) {
        int len;
        while ((len = src[sOff++] & 0xFF) == 255) {
          matchLen += len;
        }
        matchLen += len;
      }
      matchLen += MIN_MATCH;

      final int matchCopyEnd = dOff + matchLen;

      if (matchCopyEnd > dest.length - COPY_LENGTH) {
        if (matchCopyEnd > destEnd) {
          throw new LZ4Exception("Malformed input at " + sOff);
        }
        safeIncrementalCopy(dest, matchOff, dOff, matchCopyEnd);
      } else {
        wildIncrementalCopy(dest, matchOff, dOff, matchCopyEnd);
      }
      dOff = matchCopyEnd;
    }

    return sOff - srcOff;
  }

  @Override
  public int uncompressUnknownSize(byte[] src, int srcOff, int srcLen,
      byte[] dest, int destOff) {
    checkRange(src, srcOff, srcLen);
    checkRange(dest, destOff);

    final int srcEnd = srcOff + srcLen;

    int sOff = srcOff;
    int dOff = destOff;

    while (sOff < srcEnd) {
      final int token = src[sOff++] & 0xFF;

      // literals
      int literalLen = token >>> ML_BITS;
      if (literalLen == RUN_MASK) {
          int len;
          while ((len = src[sOff++] & 0xFF) == 255) {
            literalLen += len;
          }
          literalLen += len;
      }

      final int literalCopyEnd = dOff + literalLen;
      if (literalCopyEnd > dest.length - COPY_LENGTH || sOff + literalLen > srcEnd - COPY_LENGTH) {
        if (literalCopyEnd > dest.length || sOff + literalLen > srcEnd) {
          throw new LZ4Exception("Malformed input at " + sOff);
        } else {
          safeArraycopy(src, sOff, dest, dOff, literalLen);
          sOff += literalLen;
          dOff += literalLen;
          if (sOff < srcEnd) {
            throw new LZ4Exception("Malformed input at " + sOff);
          }
          break; // EOF
        }
      }

      wildArraycopy(src, sOff, dest, dOff, literalLen);
      sOff += literalLen;
      dOff = literalCopyEnd;

      // matchs
      final int matchDec = readShortLittleEndian(src, sOff);
      sOff += 2;
      int matchOff = dOff - matchDec;

      if (matchOff < destOff) {
        throw new LZ4Exception("Malformed input at " + sOff);
      }

      int matchLen = token & ML_MASK;
      if (matchLen == ML_MASK) {
        int len;
        while ((len = src[sOff++] & 0xFF) == 255) {
          matchLen += len;
        }
        matchLen += len;
      }
      matchLen += MIN_MATCH;

      final int matchCopyEnd = dOff + matchLen;

      if (matchCopyEnd > dest.length - COPY_LENGTH) {
        if (matchCopyEnd > dest.length) {
          throw new LZ4Exception("Malformed input at " + sOff);
        }
        safeIncrementalCopy(dest, matchOff, dOff, matchCopyEnd);
      } else {
        wildIncrementalCopy(dest, matchOff, dOff, matchCopyEnd);
      }
      dOff = matchCopyEnd;
    }

    return dOff - destOff;
  }

}
