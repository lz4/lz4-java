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
import static net.jpountz.lz4.LZ4Utils.shortArraycopy;
import static net.jpountz.lz4.LZ4Utils.checkRange;

/**
 * Uncompressor written in pure Java without using the unofficial
 * sun.misc.Unsafe API.
 */
public enum LZ4JavaSafeUncompressor implements LZ4Uncompressor, LZ4UnknwonSizeUncompressor {

  INSTANCE {

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
            shortArraycopy(src, sOff, dest, dOff, literalLen);
            sOff += literalLen;
            break; // EOF
          }
        }

        shortArraycopy(src, sOff, dest, dOff, literalLen);
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

        incrementalCopy(dest, matchOff, dOff, matchDec, matchLen);
        dOff += matchLen;
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
            shortArraycopy(src, sOff, dest, dOff, literalLen);
            sOff += literalLen;
            dOff = literalCopyEnd;
            if (sOff < srcEnd) {
              throw new LZ4Exception("Malformed input at " + sOff);
            }
            break; // EOF
          }
        }

        shortArraycopy(src, sOff, dest, dOff, literalLen);
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
        if (matchCopyEnd > dest.length) {
          throw new LZ4Exception("Malformed input at " + sOff);
        }

        incrementalCopy(dest, matchOff, dOff, matchDec, matchLen);
        dOff += matchLen;
      }

      return dOff - destOff;
    }
  };

  private static void incrementalCopy(byte[] dest, int matchOff, int dOff, int matchDec, int matchLen) {
    if (matchDec >= matchLen) {
      System.arraycopy(dest, matchOff, dest, dOff, matchLen);
    } else {
      LZ4Utils.incrementalCopy(dest, matchOff, dOff, matchLen);
    }
  }
}
