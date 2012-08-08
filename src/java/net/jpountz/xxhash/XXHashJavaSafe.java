package net.jpountz.xxhash;

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

import static net.jpountz.util.Utils.checkRange;
import static net.jpountz.util.Utils.readInt;
import static net.jpountz.xxhash.XXHashUtils.PRIME1;
import static net.jpountz.xxhash.XXHashUtils.PRIME2;
import static net.jpountz.xxhash.XXHashUtils.PRIME3;
import static net.jpountz.xxhash.XXHashUtils.PRIME4;
import static net.jpountz.xxhash.XXHashUtils.PRIME5;
import static net.jpountz.xxhash.XXHashUtils.rotL;

/**
 * Safe Java implementation of {@link XXHash}.
 */
public enum XXHashJavaSafe implements XXHash {

  FAST {

    @Override
    public int hash(byte[] buf, int off, int len, int seed) {
      checkRange(buf, off, len);
      if (len < 16) {
        return smallHash(buf, off, len, seed);
      }

      final int end = off + len;
      final int limit = end - 16;

      int v1 = seed + PRIME1;
      int v2 = v1 * PRIME2 + len;
      int v3 = v2 * PRIME3;
      int v4 = v3 * PRIME4;

      int o = off;
      while (o < limit) {
        v1 = rotL(v1, 13) + readInt(buf, o);
        o += 4;
        v2 = rotL(v2, 11) + readInt(buf, o);
        o += 4;
        v3 = rotL(v3, 17) + readInt(buf, o);
        o += 4;
        v4 += rotL(v4, 19) + readInt(buf, o);
        o += 4;
      }

      o = end - 16;

      v1 += rotL(v1, 17);
      v2 += rotL(v2, 19);
      v3 += rotL(v3, 13);
      v4 += rotL(v4, 11);

      v1 *= PRIME1;
      v2 *= PRIME1;
      v3 *= PRIME1;
      v4 *= PRIME1;

      v1 = rotL(v1, 13) + readInt(buf, o);
      o += 4;
      v2 = rotL(v2, 11) + readInt(buf, o);
      o += 4;
      v3 = rotL(v3, 17) + readInt(buf, o);
      o += 4;
      v4 += rotL(v4, 19) + readInt(buf, o);

      v1 *= PRIME2;
      v2 *= PRIME2;
      v3 *= PRIME2;
      v4 *= PRIME2;

      v1 += rotL(v1, 11);
      v2 += rotL(v2, 17);
      v3 += rotL(v3, 19);
      v4 += rotL(v4, 13);

      v1 *= PRIME3;
      v2 *= PRIME3;
      v3 *= PRIME3;
      v4 *= PRIME3;

      int crc = v1 + rotL(v2, 3) + rotL(v3, 6) + rotL(v4, 9);
      crc ^= crc >> 11;
      crc += (PRIME4 + len) * PRIME1;
      crc ^= crc >> 15;
      crc *= PRIME2;
      crc ^= crc >> 13;

      return crc;
    }

  },

  STRONG {

    @Override
    public int hash(byte[] buf, int off, int len, int seed) {
      checkRange(buf, off, len);
      if (len < 16) {
        return smallHash(buf, off, len, seed);
      }

      final int end = off + len;
      final int limit = end - 16;

      int v1 = seed + PRIME1;
      int v2 = v1 * PRIME2 + len;
      int v3 = v2 * PRIME3;
      int v4 = v3 * PRIME4;

      int o = off;
      while (o < limit) {
        v1 = rotL(v1, 13) + readInt(buf, o);
        o += 4;
        v2 = rotL(v2, 11) + readInt(buf, o);
        o += 4;
        v3 = rotL(v3, 17) + readInt(buf, o);
        o += 4;
        v4 += rotL(v4, 19) + readInt(buf, o);
        o += 4;
      }

      o = end - 16;

      v1 += rotL(v1, 17);
      v2 += rotL(v2, 19);
      v3 += rotL(v3, 13);
      v4 += rotL(v4, 11);

      v1 *= PRIME1;
      v2 *= PRIME1;
      v3 *= PRIME1;
      v4 *= PRIME1;

      v1 += readInt(buf, o);
      o += 4;
      v2 += readInt(buf, o);
      o += 4;
      v3 += readInt(buf, o);
      o += 4;
      v4 += readInt(buf, o);

      v1 *= PRIME2;
      v2 *= PRIME2;
      v3 *= PRIME2;
      v4 *= PRIME2;

      v1 += rotL(v1, 11);
      v2 += rotL(v2, 17);
      v3 += rotL(v3, 19);
      v4 += rotL(v4, 13);

      v1 *= PRIME3;
      v2 *= PRIME3;
      v3 *= PRIME3;
      v4 *= PRIME3;

      int crc = v1 + rotL(v2, 3) + rotL(v3, 6) + rotL(v4, 9);
      crc ^= crc >> 11;
      crc += (PRIME4 + len) * PRIME1;
      crc ^= crc >> 15;
      crc *= PRIME2;
      crc ^= crc >> 13;

      return crc;
    }

  };

  private static int smallHash(byte[] buf, int off, int len, int seed) {
    final int end = off + len;
    int idx = seed + PRIME1;
    int crc = PRIME5;
    final int limit = end - 4;

    int o = off;
    while (o < limit) {
      crc += readInt(buf, o) + idx++;
      crc += rotL(crc, 17) * PRIME4;
      crc *= PRIME1;
      o += 4;
    }
    while (o < end) {
      crc += buf[o] + idx++;
      crc *= PRIME1;
      ++o;
    }
    crc += len;

    crc ^= crc >>> 15;
    crc *= PRIME2;
    crc ^= crc >>> 13;
    crc *= PRIME3;
    crc ^= crc >>> 16;

    return crc;
  }

}
