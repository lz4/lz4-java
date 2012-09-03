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

import static net.jpountz.lz4.LZ4UnsafeUtils.commonBytes;
import static net.jpountz.lz4.LZ4UnsafeUtils.commonBytesBackward;
import static net.jpountz.lz4.LZ4UnsafeUtils.encodeSequence;
import static net.jpountz.lz4.LZ4UnsafeUtils.hash;
import static net.jpountz.lz4.LZ4UnsafeUtils.hash64k;
import static net.jpountz.lz4.LZ4UnsafeUtils.readIntEquals;
import static net.jpountz.lz4.LZ4UnsafeUtils.wildArraycopy;
import static net.jpountz.lz4.LZ4UnsafeUtils.writeLen;
import static net.jpountz.lz4.LZ4UnsafeUtils.writeShortLittleEndian;
import static net.jpountz.lz4.LZ4Utils.HASH_TABLE_SIZE;
import static net.jpountz.lz4.LZ4Utils.HASH_TABLE_SIZE_64K;
import static net.jpountz.lz4.LZ4Utils.HASH_TABLE_SIZE_HC;
import static net.jpountz.lz4.LZ4Utils.LAST_LITERALS;
import static net.jpountz.lz4.LZ4Utils.LZ4_64K_LIMIT;
import static net.jpountz.lz4.LZ4Utils.MAX_DISTANCE;
import static net.jpountz.lz4.LZ4Utils.MF_LIMIT;
import static net.jpountz.lz4.LZ4Utils.MIN_LENGTH;
import static net.jpountz.lz4.LZ4Utils.MIN_MATCH;
import static net.jpountz.lz4.LZ4Utils.ML_BITS;
import static net.jpountz.lz4.LZ4Utils.ML_MASK;
import static net.jpountz.lz4.LZ4Utils.OPTIMAL_ML;
import static net.jpountz.lz4.LZ4Utils.RUN_MASK;
import static net.jpountz.lz4.LZ4Utils.SKIP_STRENGTH;
import static net.jpountz.lz4.LZ4Utils.copyTo;
import static net.jpountz.lz4.LZ4Utils.hashHC;
import static net.jpountz.lz4.LZ4Utils.lastLiterals;
import static net.jpountz.util.UnsafeUtils.readByte;
import static net.jpountz.util.UnsafeUtils.readInt;
import static net.jpountz.util.UnsafeUtils.readShort;
import static net.jpountz.util.UnsafeUtils.writeByte;
import static net.jpountz.util.UnsafeUtils.writeInt;
import static net.jpountz.util.UnsafeUtils.writeShort;
import static net.jpountz.util.Utils.checkRange;

import java.util.Arrays;

import net.jpountz.lz4.LZ4Utils.Match;

/**
 * Very fast compressors written in pure Java with the unofficial
 * sun.misc.Unsafe API.
 */
public enum LZ4JavaUnsafeCompressor implements LZ4Compressor {

  FAST {

    private int compress64k(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff, int destEnd) {
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
            ref = srcOff + readShort(hashTable, h);
            writeShort(hashTable, h, sOff - srcOff);

            if (readIntEquals(src, ref, sOff)) {
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

          if (dOff + runLen + (2 + 1 + LAST_LITERALS) + (runLen >>> 8) >= destEnd) {
            throw new LZ4Exception("maxDestLen is too small");
          }

          if (runLen >= RUN_MASK) {
            writeByte(dest, tokenOff, RUN_MASK << ML_BITS);
            dOff = writeLen(runLen - RUN_MASK, dest, dOff);
          } else {
            writeByte(dest, tokenOff, runLen << ML_BITS);
          }

          // copy literals
          wildArraycopy(src, anchor, dest, dOff, runLen);
          dOff += runLen;

          while (true) {
            // encode offset
            writeShortLittleEndian(dest, dOff, (short) (sOff - ref));
            dOff += 2;

            // count nb matches
            sOff += MIN_MATCH;
            ref += MIN_MATCH;
            final int matchLen = commonBytes(src, ref, sOff, srcLimit);

            // encode match len
            if (matchLen >= ML_MASK) {
              writeByte(dest, tokenOff, readByte(dest, tokenOff) | ML_MASK);
              dOff = writeLen(matchLen - ML_MASK, dest, dOff);
            } else {
              writeByte(dest, tokenOff, readByte(dest, tokenOff) | matchLen);
            }

            // test end of chunk
            if (sOff > mflimit) {
              anchor = sOff;
              break main;
            }

            // fill table
            writeShort(hashTable, hash64k(src, sOff - 2), sOff - 2 - srcOff);

            // test next position
            final int h = hash(src, sOff);
            ref = srcOff + readShort(hashTable, h);
            writeShort(hashTable, h, sOff - srcOff);

            if (!readIntEquals(src, sOff, ref)) {
              break;
            }

            tokenOff = dOff++;
            dest[tokenOff] = 0;
          }

          // prepare next loop
          anchor = sOff++;
        }
      }

      dOff = lastLiterals(src, anchor, srcEnd - anchor, dest, dOff, destEnd);
      return dOff - destOff;
    }

    @Override
    public int compress(byte[] src, final int srcOff, int srcLen, byte[] dest, final int destOff, int maxDestLen) {
      checkRange(src, srcOff, srcLen);
      checkRange(dest, destOff, maxDestLen);
      final int destEnd = destOff + maxDestLen;

      if (srcLen < LZ4_64K_LIMIT) {
        return compress64k(src, srcOff, srcLen, dest, destOff, destEnd);
      }

      final int srcEnd = srcOff + srcLen;
      final int srcLimit = srcEnd - LAST_LITERALS;
      final int mflimit = srcEnd - MF_LIMIT;

      int sOff = srcOff, dOff = destOff;
      int anchor = sOff++;
      
      if (srcLen > MIN_LENGTH) {
        final int[] hashTable = new int[HASH_TABLE_SIZE];
        Arrays.fill(hashTable, anchor);

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
            ref = readInt(hashTable, h);
            back = sOff - ref;
            if (back >= MAX_DISTANCE) {
              continue;
            }
            writeInt(hashTable, h, sOff);

            if (readIntEquals(src, ref, sOff)) {
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

          if (dOff + runLen + (2 + 1 + LAST_LITERALS) + (runLen >>> 8) >= destEnd) {
            throw new LZ4Exception("maxDestLen is too small");
          }

          if (runLen >= RUN_MASK) {
            writeByte(dest, tokenOff, RUN_MASK << ML_BITS);
            dOff = writeLen(runLen - RUN_MASK, dest, dOff);
          } else {
            writeByte(dest, tokenOff, runLen << ML_BITS);
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
            final int matchLen = commonBytes(src, ref + MIN_MATCH, sOff, srcLimit);
            sOff += matchLen;

            // encode match len
            if (matchLen >= ML_MASK) {
              writeByte(dest, tokenOff, readByte(dest, tokenOff) | ML_MASK);
              dOff = writeLen(matchLen - ML_MASK, dest, dOff);
            } else {
              writeByte(dest, tokenOff, readByte(dest, tokenOff) | matchLen);
            }

            // test end of chunk
            if (sOff > mflimit) {
              anchor = sOff;
              break main;
            }

            // fill table
            writeInt(hashTable, hash(src, sOff - 2), sOff - 2);

            // test next position
            final int h = hash(src, sOff);
            ref = readInt(hashTable, h);
            writeInt(hashTable, h, sOff);
            back = sOff - ref;

            if (back >= MAX_DISTANCE || !readIntEquals(src, ref, sOff)) {
              break;
            }

            tokenOff = dOff++;
            writeByte(dest, tokenOff, 0);
          }

          // prepare next loop
          anchor = sOff++;
        }
      }

      dOff = lastLiterals(src, anchor, srcEnd - anchor, dest, dOff, destEnd);
      return dOff - destOff;
    }
  },

  HIGH_COMPRESSION {

    class HashTable {
      static final int MAX_ATTEMPTS = 256;
      static final int MASK = MAX_DISTANCE - 1;
      int nextToUpdate;
      private final int base;
      private final int[] hashTable;
      private final short[] chainTable;

      HashTable(int base) {
        this.base = base;
        nextToUpdate = base;
        hashTable = new int[HASH_TABLE_SIZE_HC];
        Arrays.fill(hashTable, -1);
        chainTable = new short[MAX_DISTANCE];
      }

      private int hashPointer(byte[] bytes, int off) {
        final int v = readInt(bytes, off);
        final int h = hashHC(v);
        return base + hashTable[h];
      }

      private int next(int off) {
        return base + off - (chainTable[off & MASK] & 0xFFFF);
      }

      private void addHash(byte[] bytes, int off) {
        final int v = readInt(bytes, off);
        final int h = hashHC(v);
        int delta = off - hashTable[h];
        if (delta >= MAX_DISTANCE) {
          delta = MAX_DISTANCE - 1;
        }
        chainTable[off & MASK] = (short) delta;
        hashTable[h] = off - base;
      }

      void insert(int off, byte[] bytes) {
        for (; nextToUpdate < off; ++nextToUpdate) {
          addHash(bytes, nextToUpdate);
        }
      }

      boolean insertAndFindBestMatch(byte[] buf, int off, int matchLimit, Match match) {
        match.start = off;
        match.len = 0;

        insert(off, buf);

        int ref = hashPointer(buf, off);
        for (int i = 0; i < MAX_ATTEMPTS; ++i) {
          if (ref < Math.max(base, off - MAX_DISTANCE + 1)) {
            break;
          }
          if (buf[ref + match.len] == buf[off + match.len] && readIntEquals(buf, ref, off)) {
            final int matchLen = 4 + commonBytes(buf, ref + MIN_MATCH, off + MIN_MATCH, matchLimit);
            if (matchLen > match.len) {
              match.ref = ref;
              match.len = matchLen;
            }
          }
          ref = next(ref);
        }

        return match.len != 0;
      }

      boolean insertAndFindWiderMatch(byte[] buf, int off, int startLimit, int matchLimit, int minLen, Match match) {
        match.len = minLen;

        insert(off, buf);

        final int delta = off - startLimit;
        int ref = hashPointer(buf, off);
        for (int i = 0; i < MAX_ATTEMPTS; ++i) {
          if (ref < Math.max(base, off - MAX_DISTANCE + 1)) {
            break;
          }
          if (buf[ref - delta + match.len] == buf[startLimit + match.len]
              && readIntEquals(buf, ref, off)) {
            final int matchLenForward = MIN_MATCH + commonBytes(buf, ref + MIN_MATCH, off + MIN_MATCH, matchLimit);
            final int matchLenBackward = commonBytesBackward(buf, ref, off, base, startLimit);
            final int matchLen = matchLenBackward + matchLenForward;
            if (matchLen > match.len) {
              match.len = matchLen;
              match.ref = ref - matchLenBackward;
              match.start = off - matchLenBackward;
            }
          }
          ref = next(ref);
        }

        return match.len > minLen;
      }

    }

    @Override
    public int compress(byte[] src, int srcOff, int srcLen, byte[] dest,
        int destOff, int maxDestLen) {

      final int srcEnd = srcOff + srcLen;
      final int destEnd = destOff + maxDestLen;
      final int mfLimit = srcEnd - MF_LIMIT;
      final int matchLimit = srcEnd - LAST_LITERALS;

      int sOff = srcOff;
      int dOff = destOff;
      int anchor = sOff++;

      final HashTable ht = new HashTable(srcOff);
      final Match match0 = new Match();
      final Match match1 = new Match();
      final Match match2 = new Match();
      final Match match3 = new Match();

      main:
      while (sOff < mfLimit) {
        if (!ht.insertAndFindBestMatch(src, sOff, matchLimit, match1)) {
          ++sOff;
          continue;
        }

        // saved, in case we would skip too much
        copyTo(match1, match0);

        search2:
        while (true) {
          assert match1.start >= anchor;
          if (match1.end() >= mfLimit
              || !ht.insertAndFindWiderMatch(src, match1.end() - 2, match1.start + 1, matchLimit, match1.len, match2)) {
            // no better match
            dOff = encodeSequence(src, anchor, match1.start, match1.ref, match1.len, dest, dOff);
            anchor = sOff = match1.end();
            continue main;
          }

          if (match0.start < match1.start) {
            if (match2.start < match1.start + match0.len) { // empirical
              copyTo(match0, match1);
            }
          }
          assert match2.start > match1.start;

          if (match2.start - match1.start < 3) { // First Match too small : removed
            copyTo(match2, match1);
            continue search2;
          }

          search3:
          while (true) {
            if (match2.start - match1.start < OPTIMAL_ML) {
              int newMatchLen = match1.len;
              if (newMatchLen > OPTIMAL_ML) {
                newMatchLen = OPTIMAL_ML;
              }
              if (match1.start + newMatchLen > match2.end() - MIN_MATCH) {
                newMatchLen = match2.start - match1.start + match2.len - MIN_MATCH;
              }
              final int correction = newMatchLen - (match2.start - match1.start);
              if (correction > 0) {
                match2.fix(correction);
              }
            }

            if (match2.start + match2.len >= mfLimit
                || !ht.insertAndFindWiderMatch(src, match2.end() - 3, match2.start, matchLimit, match2.len, match3)) {
              // no better match -> 2 sequences to encode
              if (match2.start < match1.end()) {
                if (match2.start - match1.start < OPTIMAL_ML) {
                  if (match1.len > OPTIMAL_ML) {
                    match1.len = OPTIMAL_ML;
                  }
                  if (match1.end() > match2.end() - MIN_MATCH) {
                    match1.len = match2.end() - match1.start - MIN_MATCH;
                  }
                  final int correction = match1.len - (match2.start - match1.start);
                  if (correction > 0) {
                    match2.fix(correction);
                  }
                } else {
                  match1.len = match2.start - match1.start;
                }
              }
              // encode seq 1
              dOff = encodeSequence(src, anchor, match1.start, match1.ref, match1.len, dest, dOff);
              anchor = sOff = match1.end();
              // encode seq 2
              dOff = encodeSequence(src, anchor, match2.start, match2.ref, match2.len, dest, dOff);
              anchor = sOff = match2.end();
              continue main;
            }

            if (match3.start < match1.end() + 3) { // Not enough space for match 2 : remove it
              if (match3.start >= match1.end()) { // // can write Seq1 immediately ==> Seq2 is removed, so Seq3 becomes Seq1
                if (match2.start < match1.end()) {
                  final int correction = match1.end() - match2.start;
                  match2.fix(correction);
                  if (match2.len < MIN_MATCH) {
                    copyTo(match3, match2);
                  }
                }

                dOff = encodeSequence(src, anchor, match1.start, match1.ref, match1.len, dest, dOff);
                anchor = sOff = match1.end();

                copyTo(match3, match1);
                copyTo(match2, match0);

                continue search2;
              }

              copyTo(match3, match2);
              continue search3;
            }

            // OK, now we have 3 ascending matches; let's write at least the first one
            if (match2.start < match1.end()) {
              if (match2.start - match1.start < ML_MASK) {
                if (match1.len > OPTIMAL_ML) {
                  match1.len = OPTIMAL_ML;
                }
                if (match1.end() > match2.end() - MIN_MATCH) {
                  match1.len = match2.end() - match1.start - MIN_MATCH;
                }
                final int correction = match1.end() - match2.start;
                match2.fix(correction);
              } else {
                match1.len = match2.start - match1.start;
              }
            }

            dOff = encodeSequence(src, anchor, match1.start, match1.ref, match1.len, dest, dOff);
            anchor = sOff = match1.end();

            copyTo(match2, match1);
            copyTo(match3, match2);

            continue search3;
          }

        }

      }

      dOff = lastLiterals(src, anchor, srcEnd - anchor, dest, dOff, destEnd);
      return dOff - destOff;
    }

  };

  public int maxCompressedLength(int length) {
    return LZ4Utils.maxCompressedLength(length);
  }

}
