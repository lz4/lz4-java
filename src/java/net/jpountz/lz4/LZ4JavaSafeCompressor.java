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

import static net.jpountz.lz4.LZ4Utils.HASH_TABLE_SIZE;
import static net.jpountz.lz4.LZ4Utils.HASH_TABLE_SIZE_64K;
import static net.jpountz.lz4.LZ4Utils.LAST_LITERALS;
import static net.jpountz.lz4.LZ4Utils.LZ4_64K_LIMIT;
import static net.jpountz.lz4.LZ4Utils.MAX_DISTANCE;
import static net.jpountz.lz4.LZ4Utils.MF_LIMIT;
import static net.jpountz.lz4.LZ4Utils.MIN_LENGTH;
import static net.jpountz.lz4.LZ4Utils.MIN_MATCH;
import static net.jpountz.lz4.LZ4Utils.ML_BITS;
import static net.jpountz.lz4.LZ4Utils.ML_MASK;
import static net.jpountz.lz4.LZ4Utils.RUN_MASK;
import static net.jpountz.lz4.LZ4Utils.SKIP_STRENGTH;
import static net.jpountz.lz4.LZ4Utils.checkRange;
import static net.jpountz.lz4.LZ4Utils.commonBytes;
import static net.jpountz.lz4.LZ4Utils.commonBytesBackward;
import static net.jpountz.lz4.LZ4Utils.hash;
import static net.jpountz.lz4.LZ4Utils.hash64k;
import static net.jpountz.lz4.LZ4Utils.readIntEquals;
import static net.jpountz.lz4.LZ4Utils.wildArraycopy;

import java.util.Arrays;

/**
 * Compressors written in pure Java without using the unofficial
 * sun.misc.Unsafe API.
 */
public enum LZ4JavaSafeCompressor implements LZ4Compressor, LZ4PartialCompressor {

  FAST {

    public int maxCompressedLength(int length) {
      return LZ4Utils.maxCompressedLength(length);
    }

    @Override
    public long greedyCompress(byte[] src, int srcOrig, int sOff, int srcLen,
        byte[] dest, int dOff, int[] hashTable) {

      final int srcEnd = sOff + srcLen;
      final int srcLimit = srcEnd - LAST_LITERALS;
      final int mflimit = srcEnd - MF_LIMIT;

      int anchor = sOff;

      if (srcLen > MIN_LENGTH) {

        if (hashTable == null) {
          hashTable = new int[HASH_TABLE_SIZE];
          Arrays.fill(hashTable, sOff);
        }

        ++sOff;

        main:
        while (sOff < srcLimit) {

          // find a match
          int forwardOff = sOff;

          int ref;
          int findMatchAttempts = (1 << SKIP_STRENGTH) + 3;
          int back;
          while (true) {
            sOff = forwardOff;
            final int step = findMatchAttempts++ >> SKIP_STRENGTH;
            forwardOff += step;

            if (forwardOff > mflimit) {
              break main;
            }

            final int h = hash(src, sOff);
            ref = hashTable[h];
            back = sOff - ref;
            if (back >= MAX_DISTANCE) {
              continue;
            }
            hashTable[h] = sOff;

            if (readIntEquals(src, ref, sOff)) {
              break;
            }
          }

          // catch up
          final int excess = commonBytesBackward(src, ref, sOff, srcOrig, anchor);
          sOff -= excess;
          ref -= excess;

          // sequence == refsequence
          final int runLen = sOff - anchor;

          // encode literal length
          int tokenOff = dOff++;
          int token;
          if (runLen >= RUN_MASK) {
            token = RUN_MASK << ML_BITS;
            int len = runLen - RUN_MASK;
            while (len >= 255) {
              dest[dOff++] = (byte) 255;
              len -= 255;
            }
            dest[dOff++] = (byte) len;
          } else {
            token = runLen << ML_BITS;
          }

          // copy literals
          wildArraycopy(src, anchor, dest, dOff, runLen);
          dOff += runLen;

          while (true) {
            // encode offset
            dest[dOff++] = (byte) back;
            dest[dOff++] = (byte) (back >>> 8);

            // count nb matches
            sOff += MIN_MATCH;
            final int matchLen = commonBytes(src, ref + MIN_MATCH, sOff, srcLimit);
            sOff += matchLen;

            // encode match len
            if (matchLen >= ML_MASK) {
              token |= ML_MASK;
              int len = matchLen - ML_MASK;
              while (len >= 255) {
                dest[dOff++] = (byte) 255;
                len -= 255;
              }
              dest[dOff++] = (byte) len;
            } else {
              token |= matchLen;
            }
            dest[tokenOff] = (byte) token;

            // test end of chunk
            if (sOff > mflimit) {
              anchor = sOff;
              break main;
            }

            // fill table
            hashTable[hash(src, sOff - 2)] = sOff - 2;

            // test next position
            final int h = hash(src, sOff);
            ref = hashTable[h];
            hashTable[h] = sOff;
            back = sOff - ref;

            //if (back > MAX_DISTANCE || refSequence != sequence) {
            if (back >= MAX_DISTANCE || !readIntEquals(src, sOff, ref)) {
              break;
            }

            tokenOff = dOff++;
            token = 0;
          }

          // prepare next loop
          anchor = sOff++;
        }
      }

      return ((anchor & 0xFFFFFFFFL) << 32) | (dOff & 0xFFFFFFFFL);
    }

    @Override
    public int lastLiterals(byte[] src, int sOff, int srcLen, byte[] dest, int dOff) {
      return LZ4Utils.lastLiterals(src, sOff, srcLen, dest, dOff);
    }

    private int compress64k(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff) {
      final int srcEnd = srcOff + srcLen;
      final int srcLimit = srcEnd - LAST_LITERALS;
      final int mflimit = srcEnd - MF_LIMIT;

      int sOff = srcOff, dOff = destOff;

      int anchor = sOff;

      if (srcLen > MIN_LENGTH) {

        final short[] hashTable = new short[HASH_TABLE_SIZE_64K];

        ++sOff;

        main:
        while (sOff < srcLimit) {

          // find a match
          int forwardOff = sOff;

          int ref;
          int findMatchAttempts = (1 << SKIP_STRENGTH) + 3;
          while (true) {
            sOff = forwardOff;
            final int step = findMatchAttempts++ >> SKIP_STRENGTH;
            forwardOff += step;

            if (forwardOff > mflimit) {
              break main;
            }

            final int h = hash64k(src, sOff);
            ref = srcOff + (hashTable[h] & 0xFFFF);
            hashTable[h] = (short) (sOff - srcOff);

            if (readIntEquals(src, ref, sOff)) {
              break;
            }
          }

          // catch up
          final int excess = commonBytesBackward(src, ref, sOff, srcOff, anchor);
          sOff -= excess;
          ref -= excess;

          // sequence == refsequence
          final int runLen = sOff - anchor;

          // encode literal length
          int tokenOff = dOff++;
          int token;
          if (runLen >= RUN_MASK) {
            token = RUN_MASK << ML_BITS;
            int len = runLen - RUN_MASK;
            while (len >= 255) {
              dest[dOff++] = (byte) 255;
              len -= 255;
            }
            dest[dOff++] = (byte) len;
          } else {
            token = runLen << ML_BITS;
          }

          // copy literals
          wildArraycopy(src, anchor, dest, dOff, runLen);
          dOff += runLen;

          while (true) {
            // encode offset
            final int back = sOff - ref;
            dest[dOff++] = (byte) back;
            dest[dOff++] = (byte) (back >>> 8);

            // count nb matches
            sOff += MIN_MATCH;
            final int matchLen = commonBytes(src, ref + MIN_MATCH, sOff, srcLimit);
            sOff += matchLen;

            // encode match len
            if (matchLen >= ML_MASK) {
              token |= ML_MASK;
              int len = matchLen - ML_MASK;
              while (len >= 255) {
                dest[dOff++] = (byte) 255;
                len -= 255;
              }
              dest[dOff++] = (byte) len;
            } else {
              token |= matchLen;
            }
            dest[tokenOff] = (byte) token;

            // test end of chunk
            if (sOff > mflimit) {
              anchor = sOff;
              break main;
            }

            // fill table
            hashTable[hash(src, sOff - 2)] = (short) (sOff - 2 - srcOff);

            // test next position
            final int h = hash64k(src, sOff);
            ref = srcOff + (hashTable[h] & 0xFFFF);
            hashTable[h] = (short) (sOff - srcOff);

            if (!readIntEquals(src, sOff, ref)) {
              break;
            }

            tokenOff = dOff++;
            token = 0;
          }

          // prepare next loop
          anchor = sOff++;
        }
      }

      dOff = lastLiterals(src, anchor, srcLen - anchor + srcOff, dest, dOff);
      return dOff - destOff;
    }

    @Override
    public int compress(byte[] src, int srcOff, int srcLen, byte[] dest,
        int destOff) {
      checkRange(src, srcOff, srcLen);
      checkRange(dest, destOff, maxCompressedLength(srcLen));

      if (srcLen < LZ4_64K_LIMIT) {
        return compress64k(src, srcOff, srcLen, dest, destOff);
      }

      final long sdOff = greedyCompress(src, srcOff, srcOff, srcLen, dest, destOff, null);
      int sOff = (int) (sdOff >>> 32);
      int dOff = (int) (sdOff & 0xFFFFFFFFL);
      dOff = lastLiterals(src, sOff, srcLen - sOff + srcOff, dest, dOff);
      return dOff - destOff;
    }

  };

}
