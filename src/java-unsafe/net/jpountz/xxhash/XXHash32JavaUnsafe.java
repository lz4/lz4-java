package net.jpountz.xxhash;

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

import static java.lang.Integer.rotateLeft;
import static net.jpountz.util.UnsafeUtils.readByte;
import static net.jpountz.util.UnsafeUtils.readIntLE;
import static net.jpountz.util.Utils.checkRange;
import static net.jpountz.xxhash.XXHashUtils.PRIME1;
import static net.jpountz.xxhash.XXHashUtils.PRIME2;
import static net.jpountz.xxhash.XXHashUtils.PRIME3;
import static net.jpountz.xxhash.XXHashUtils.PRIME4;
import static net.jpountz.xxhash.XXHashUtils.PRIME5;

/**
 * Safe Java implementation of {@link XXHash32}.
 */
final class XXHash32JavaUnsafe extends XXHash32 {

  public static final XXHash32 INSTANCE = new XXHash32JavaUnsafe();

  @Override
  public int hash(byte[] buf, int off, int len, int seed) {
    checkRange(buf, off, len);

    final int end = off + len;
    int h32;

    if (len >= 16) {
      final int limit = end - 16;
      int v1 = seed + PRIME1 + PRIME2;
      int v2 = seed + PRIME2;
      int v3 = seed + 0;
      int v4 = seed - PRIME1;
      do {
        v1 += readIntLE(buf, off) * PRIME2;
        v1 = rotateLeft(v1, 13);
        v1 *= PRIME1;
        off += 4;

        v2 += readIntLE(buf, off) * PRIME2;
        v2 = rotateLeft(v2, 13);
        v2 *= PRIME1;
        off += 4;

        v3 += readIntLE(buf, off) * PRIME2;
        v3 = rotateLeft(v3, 13);
        v3 *= PRIME1;
        off += 4;

        v4 += readIntLE(buf, off) * PRIME2;
        v4 = rotateLeft(v4, 13);
        v4 *= PRIME1;
        off += 4;
      } while (off <= limit);

      h32 = rotateLeft(v1, 1) + rotateLeft(v2, 7) + rotateLeft(v3, 12) + rotateLeft(v4, 18);
    } else {
      h32 = seed + PRIME5;
    }

    h32 += len;

    while (off <= end - 4) {
      h32 += readIntLE(buf, off) * PRIME3;
      h32 = rotateLeft(h32, 17) * PRIME4;
      off += 4;
    }

    while (off < end) {
      h32 += (readByte(buf, off) & 0xFF) * PRIME5;
      h32 = rotateLeft(h32, 11) * PRIME1;
      ++off;
    }

    h32 ^= h32 >>> 15;
    h32 *= PRIME2;
    h32 ^= h32 >>> 13;
    h32 *= PRIME3;
    h32 ^= h32 >>> 16;

    return h32;
  }

}
