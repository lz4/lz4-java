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
import static net.jpountz.lz4.LZ4Utils.incrementalCopy;
import static net.jpountz.lz4.LZ4Utils.readInt;

import java.util.Arrays;
import java.util.Random;

public enum LZ4Java implements LZ4 {

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
      checkRange(dest, destOff);

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
            ref = hashTable[h];
            back = sOff - ref;
            if (back > MAX_DISTANCE) {
              continue;
            }
            refSequence = readInt(src, ref);
            hashTable[h] = sOff;
            if (refSequence == sequence) {
              break;
            }
          }

          // catch up
          while (sOff > anchor && ref > srcOff && src[sOff - 1] == src[ref - 1]) {
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
          System.arraycopy(src, anchor, dest, dOff, runLen);
          dOff += runLen;

          while (true) {
            // encode offset
            dest[dOff++] = (byte) back;
            dest[dOff++] = (byte) (back >>> 8);

            // count nb matches
            sOff += MIN_MATCH;
            ref += MIN_MATCH;
            anchor = sOff;
            while (sOff < srcLimit && src[sOff] == src[ref]) {
              ++sOff;
              ++ref;
            }

            // encode match len
            final int matchLen = sOff - anchor;
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
            hashTable[hash(readInt(src, sOff - 2))] = sOff - 2;

            // test next position
            sequence = readInt(src, sOff);
            final int h = hash(sequence);
            ref = hashTable[h];
            hashTable[h] = sOff;
            back = sOff - ref;

            if (back > MAX_DISTANCE || refSequence != sequence) {
              break;
            }

            tokenOff = dOff++;
            dest[tokenOff] = 0;
          };

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
      System.arraycopy(src, anchor, dest, dOff, runLen);
      dOff += runLen;

      return dOff - destOff;
    }

  };

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
          System.arraycopy(src, sOff, dest, dOff, literalLen);
          sOff += literalLen;
          break; // EOF
        }
      }

      System.arraycopy(src, sOff, dest, dOff, literalLen);
      sOff += literalLen;
      dOff = literalCopyEnd;

      // matchs
      final int matchDec = (src[sOff++] & 0xFF) | ((src[sOff++] & 0xFF) << 8);
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
      if (matchCopyEnd > destEnd) {
        throw new LZ4Exception("Malformed input at " + sOff);
      }

      incrementalCopy(dest, matchOff, dOff, matchLen);
      dOff += matchLen;
    }

    return sOff - srcOff;
  }

  @Override
  public int uncompressUnknownSize(byte[] src, int srcOff, int srcLen,
      byte[] dest, int destOff, int maxDestLen) {
    checkRange(src, srcOff, srcLen);
    checkRange(dest, destOff, maxDestLen);

    final int srcEnd = srcOff + srcLen;
    final int destEnd = destOff + maxDestLen;

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
      if (literalCopyEnd > destEnd - COPY_LENGTH || sOff + literalLen > srcEnd - COPY_LENGTH) {
        if (literalCopyEnd > destEnd || sOff + literalLen > srcEnd) {
          throw new LZ4Exception("Malformed input at " + sOff);
        } else {
          System.arraycopy(src, sOff, dest, dOff, literalLen);
          sOff += literalLen;
          dOff += literalLen;
          if (sOff < srcEnd) {
            throw new LZ4Exception("Malformed input at " + sOff);
          }
          break; // EOF
        }
      }

      System.arraycopy(src, sOff, dest, dOff, literalLen);
      sOff += literalLen;
      dOff = literalCopyEnd;

      // matchs
      final int matchDec = (src[sOff++] & 0xFF) | ((src[sOff++] & 0xFF) << 8);
      final int matchOff = dOff - matchDec;

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
      if (matchCopyEnd > destEnd) {
        throw new LZ4Exception("Malformed input at " + sOff);
      }

      incrementalCopy(dest, matchOff, dOff, matchLen);
      dOff += matchLen;
    }

    return dOff - destOff;
  }

  public static void main(String[] args) {
    LZ4 lz4 = LZ4Java.FAST;
    byte[] data = new byte[1024 * 32];
    Random r = new Random();
    for (int i = 0; i < data.length; ++i) {
      data[i] = (byte) r.nextInt(5);
    }
    byte[] buf = new byte[lz4.maxCompressedLength(data.length)];
    int h = 0;
    long start = System.currentTimeMillis();
    for (int i = 0; i < 50000; ++i) {
      lz4.compress(data, 0, data.length, buf, 0);
      h = h ^ Arrays.hashCode(buf);
    }
    System.out.println(h);
    System.out.println(System.currentTimeMillis() - start);
    byte[] buf2 = new byte[data.length];
    start = System.currentTimeMillis();
    for (int i = 0; i < 50000; ++i) {
      lz4.uncompress(buf, 0, buf2, 0, buf2.length);
      h = h ^ Arrays.hashCode(buf);
    }
    System.out.println(h);
    System.out.println(System.currentTimeMillis() - start);
  }

}
