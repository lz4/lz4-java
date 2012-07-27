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
import static net.jpountz.lz4.LZ4Utils.commonBytes;
import static net.jpountz.lz4.LZ4Utils.commonBytesBackward;
import static net.jpountz.lz4.LZ4Utils.hash;
import static net.jpountz.lz4.LZ4Utils.readIntEquals;
import static net.jpountz.lz4.LZ4Utils.safeArraycopy;
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
            final int h = hash(src, sOff);
            final int step = findMatchAttempts++ >> SKIP_STRENGTH;
            forwardOff += step;

            if (forwardOff > mflimit) {
              break main;
            }

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
            dest[dOff++] = (byte) back;
            dest[dOff++] = (byte) (back >>> 8);

            // count nb matches
            sOff += MIN_MATCH;
            final int matchLen = commonBytes(src, ref + MIN_MATCH, sOff, srcLimit);
            sOff += matchLen;

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
            dest[tokenOff] = 0;
          }

          // prepare next loop
          anchor = sOff++;
        }
      }

      return ((anchor & 0xFFFFFFFFL) << 32) | (dOff & 0xFFFFFFFFL);
    }

    @Override
    public int lastLiterals(byte[] src, int sOff, int srcLen, byte[] dest, int dOff) {
      final int runLen = srcLen;
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
      safeArraycopy(src, sOff, dest, dOff, runLen);
      dOff += runLen;

      return dOff;
    }

    @Override
    public int compress(byte[] src, int srcOff, int srcLen, byte[] dest,
        int destOff) {
      checkRange(src, srcOff, srcLen);
      checkRange(dest, destOff, maxCompressedLength(srcLen));
      
      final long sdOff = greedyCompress(src, srcOff, srcOff, srcLen, dest, destOff, null);
      int sOff = (int) (sdOff >>> 32);
      int dOff = (int) (sdOff & 0xFFFFFFFFL);
      dOff = lastLiterals(src, sOff, srcLen - sOff + srcOff, dest, dOff);
      return dOff - destOff;
    }

  };

}
