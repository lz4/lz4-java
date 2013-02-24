package net.jpountz.lz4;

/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import static net.jpountz.lz4.LZ4Utils.HASH_TABLE_SIZE_HC;
import static net.jpountz.lz4.LZ4Utils.LAST_LITERALS;
import static net.jpountz.lz4.LZ4Utils.MAX_DISTANCE;
import static net.jpountz.lz4.LZ4Utils.MF_LIMIT;
import static net.jpountz.lz4.LZ4Utils.MIN_MATCH;
import static net.jpountz.lz4.LZ4Utils.ML_MASK;
import static net.jpountz.lz4.LZ4Utils.OPTIMAL_ML;
import static net.jpountz.lz4.LZ4Utils.commonBytes;
import static net.jpountz.lz4.LZ4Utils.commonBytesBackward;
import static net.jpountz.lz4.LZ4Utils.copyTo;
import static net.jpountz.lz4.LZ4Utils.encodeSequence;
import static net.jpountz.lz4.LZ4Utils.hashHC;
import static net.jpountz.lz4.LZ4Utils.lastLiterals;
import static net.jpountz.lz4.LZ4Utils.readIntEquals;
import static net.jpountz.util.Utils.readInt;

import java.util.Arrays;

import net.jpountz.lz4.LZ4Utils.Match;

/**
 * High compression compressor written in pure Java without using the unofficial
 * sun.misc.Unsafe API.
 */
final class LZ4HCJavaSafeCompressor extends LZ4Compressor {

  public static final LZ4Compressor INSTANCE = new LZ4HCJavaSafeCompressor();

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

      if (ref >= off - 4 && ref >= base) { // potential repetition
        if (readIntEquals(buf, ref, off)) { // confirmed
          final int delta = off - ref;
          int ptr = off;
          match.len = MIN_MATCH + commonBytes(buf, ref + MIN_MATCH, off + MIN_MATCH, matchLimit);
          final int end = off + match.len - (MIN_MATCH - 1);
          while (ptr < end - delta) {
            chainTable[ptr & MASK] = (short) delta; // pre load
            ++ptr;
          }
          do {
            chainTable[ptr & MASK] = (short) delta;
            hashTable[hashHC(readInt(buf, ptr))] = ptr - base; // head of table
            ++ptr;
          } while (ptr < end);
          nextToUpdate = end;
          match.ref = ref;
        }
        ref = next(ref);
      }

      for (int i = 0; i < MAX_ATTEMPTS; ++i) {
        if (ref < Math.max(base, off - MAX_DISTANCE + 1)) {
          break;
        }
        if (buf[ref + match.len] == buf[off + match.len] && readIntEquals(buf, ref, off)) {
          final int matchLen = MIN_MATCH + commonBytes(buf, ref + MIN_MATCH, off + MIN_MATCH, matchLimit);
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
          dOff = encodeSequence(src, anchor, match1.start, match1.ref, match1.len, dest, dOff, destEnd);
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
              match1.len = match2.start - match1.start;
            }
            // encode seq 1
            dOff = encodeSequence(src, anchor, match1.start, match1.ref, match1.len, dest, dOff, destEnd);
            anchor = sOff = match1.end();
            // encode seq 2
            dOff = encodeSequence(src, anchor, match2.start, match2.ref, match2.len, dest, dOff, destEnd);
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

              dOff = encodeSequence(src, anchor, match1.start, match1.ref, match1.len, dest, dOff, destEnd);
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

          dOff = encodeSequence(src, anchor, match1.start, match1.ref, match1.len, dest, dOff, destEnd);
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

}
