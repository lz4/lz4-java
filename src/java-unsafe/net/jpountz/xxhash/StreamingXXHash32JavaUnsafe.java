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

class StreamingXXHash32JavaUnsafe extends AbstractStreamingXXHash32Java {

  StreamingXXHash32JavaUnsafe(int seed) {
    super(seed);
  }

  @Override
  public int getValue() {
    int h32;
    if (totalLen >= 16) {
      h32 = rotateLeft(v1, 1) + rotateLeft(v2, 7) + rotateLeft(v3, 12) + rotateLeft(v4, 18);
    } else {
      h32 = seed + PRIME5;
    }

    h32 += totalLen;

    int off = 0;
    while (off <= memSize - 4) {
      h32 += readIntLE(memory, off) * PRIME3;
      h32 = rotateLeft(h32, 17) * PRIME4;
      off += 4;
    }

    while (off < memSize) {
      h32 += (readByte(memory, off) & 0xFF) * PRIME5;
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

  @Override
  public void update(byte[] buf, int off, int len) {
    checkRange(buf, off, len);

    totalLen += len;

    if (memSize + len < 16) { // fill in tmp buffer
      System.arraycopy(buf, off, memory, memSize, len);
      memSize += len;
      return;
    }

    final int end = off + len;

    if (memSize > 0) { // data left from previous update
      System.arraycopy(buf, off, memory, memSize, 16 - memSize);

      v1 += readIntLE(memory, 0) * PRIME2;
      v1 = rotateLeft(v1, 13);
      v1 *= PRIME1;

      v2 += readIntLE(memory, 4) * PRIME2;
      v2 = rotateLeft(v2, 13);
      v2 *= PRIME1;

      v3 += readIntLE(memory, 8) * PRIME2;
      v3 = rotateLeft(v3, 13);
      v3 *= PRIME1;

      v4 += readIntLE(memory, 12) * PRIME2;
      v4 = rotateLeft(v4, 13);
      v4 *= PRIME1;

      off += 16 - memSize;
      memSize = 0;
    }

    {
      final int limit = end - 16;
      int v1 = this.v1;
      int v2 = this.v2;
      int v3 = this.v3;
      int v4 = this.v4;

      while (off <= limit) {
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
      }

      this.v1 = v1;
      this.v2 = v2;
      this.v3 = v3;
      this.v4 = v4;
    }

    if (off < end) {
      System.arraycopy(buf, off, memory, 0, end - off);
      memSize = end - off;
    }
  }

}
