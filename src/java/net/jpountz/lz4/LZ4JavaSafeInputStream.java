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
import static net.jpountz.lz4.LZ4Utils.MIN_MATCH;
import static net.jpountz.lz4.LZ4Utils.ML_BITS;
import static net.jpountz.lz4.LZ4Utils.ML_MASK;
import static net.jpountz.lz4.LZ4Utils.RUN_MASK;
import static net.jpountz.lz4.LZ4Utils.safeArraycopy;
import static net.jpountz.lz4.LZ4Utils.wildArraycopy;
import static net.jpountz.lz4.LZ4Utils.wildIncrementalCopy;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Stream decoder for the LZ4 compression format, safe implementation.
 */
public class LZ4JavaSafeInputStream extends LZ4InputStream {

  public LZ4JavaSafeInputStream(InputStream is) throws IOException {
    super(is);
  }

  protected void uncompress() throws IOException {
    int srcEnd = compressedOff + compressedLen;
    int sOff = compressedOff, dOff = uncompressedOff + uncompressedLen;

    main:
    while (sOff < srcEnd) {
      final int sAnchor = sOff;
      final int dAnchor = dOff;

      final int token = compressed[sOff++] & 0xFF;

      // literals
      int literalLen = token >>> ML_BITS;
      if (literalLen == RUN_MASK) {
        if (sOff == srcEnd) {
          break main;
        }
        int l;
        while ((l = compressed[sOff++] & 0xFF) == 255) {
          literalLen += 255;
          if (sOff == srcEnd) {
            break main;
          }
        }
        literalLen += l;
      }

      final int literalCopyEnd = dOff + literalLen;
      if (literalCopyEnd > uncompressed.length) {
        uncompressed = Arrays.copyOf(uncompressed, Math.max(uncompressed.length << 1, literalCopyEnd + COPY_LENGTH));
      }
      if (sOff + literalLen > srcEnd - COPY_LENGTH) {

        if (uncompressedLen > 0) {
          // we'll take care of this next time
          break;
        }

        if (sOff + literalLen > srcEnd) {
          compressed = Arrays.copyOf(compressed, Math.max(compressed.length << 1, sOff + literalLen + COPY_LENGTH));
          fill();
          srcEnd = compressedOff + compressedLen;
          if (sOff + literalLen > srcEnd) {
            throw new LZ4Exception("Malformed stream");
          }
        }

        safeArraycopy(compressed, sOff, uncompressed, dOff, literalLen);
        sOff += literalLen;
        dOff = literalCopyEnd;

        // checkpoint
        compressedLen -= sOff - sAnchor;
        compressedOff = sOff;
        uncompressedLen += dOff - dAnchor;

        break;
      }

      wildArraycopy(compressed, sOff, uncompressed, dOff, literalLen);
      sOff += literalLen;
      dOff = literalCopyEnd;

      // matchs
      if (srcEnd - sOff < 2) {
        break main;
      }
      final int matchDec = (compressed[sOff++] & 0xFF) | ((compressed[sOff++] & 0xFF) << 8);
      final int matchOff = dOff - matchDec;

      if (matchOff < 0) {
        throw new LZ4Exception("Malformed input at " + sOff);
      }

      int matchLen = token & ML_MASK;
      if (matchLen == ML_MASK) {
        if (sOff == srcEnd) {
          break main;
        }
        int l;
        while ((l = compressed[sOff++] & 0xFF) == 255) {
          matchLen += 255;
          if (sOff == srcEnd) {
            break main;
          }
        }
        matchLen += l;
      }
      matchLen += MIN_MATCH;

      final int matchCopyEnd = dOff + matchLen;
      if (matchCopyEnd > uncompressed.length - COPY_LENGTH) {
        uncompressed = Arrays.copyOf(uncompressed, Math.max(uncompressed.length << 1, matchCopyEnd + COPY_LENGTH));
      }

      wildIncrementalCopy(uncompressed, matchOff, dOff, matchLen);
      dOff = matchCopyEnd;

      // checkpoint
      compressedLen -= sOff - sAnchor;
      compressedOff = sOff;
      uncompressedLen += dOff - dAnchor;
    }
  }

}
