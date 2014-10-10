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

import java.nio.ByteBuffer;

import net.jpountz.lz4.AbstractLZ4Test;
import net.jpountz.util.Utils;

import org.junit.Test;

import com.carrotsearch.randomizedtesting.annotations.Repeat;

public class XXHash64Test extends AbstractLZ4Test {

  private static abstract class StreamingXXHash64Adapter extends XXHash64 {

    protected abstract StreamingXXHash64 streamingHash(long seed);

    @Override
    public long hash(byte[] buf, int off, int len, long seed) {
      Utils.checkRange(buf, off, len);
      int originalOff = off;
      int remainingPasses = randomInt(5);
      StreamingXXHash64 h = streamingHash(seed);
      final int end = off + len;
      while (off < end) {
        final int l = randomIntBetween(off, end) - off;
        h.update(buf, off, l);
        off += l;
        if (remainingPasses > 0 && randomInt(5) == 0) {
          h.reset();
          --remainingPasses;
          off = originalOff;
        }
        if (randomBoolean()) {
          h.getValue();
        }
      }
      return h.getValue();
    }

    @Override
    public long hash(ByteBuffer buf, int off, int len, long seed) {
      byte[] bytes = new byte[len];
      int originalPosition = buf.position();
      try {
        buf.position(off);
        buf.get(bytes, 0, len);
        return hash(bytes, 0, len, seed);
      } finally {
        buf.position(originalPosition);
      }
    }

    public String toString() {
      return streamingHash(0).toString();
    }

  }

  private static XXHash64[] INSTANCES = new XXHash64[] {
    XXHashFactory.nativeInstance().hash64(),
    XXHashFactory.unsafeInstance().hash64(),
    XXHashFactory.safeInstance().hash64(),
    new StreamingXXHash64Adapter() {
      protected StreamingXXHash64 streamingHash(long seed) {
        return XXHashFactory.nativeInstance().newStreamingHash64(seed);
      }
    },
    new StreamingXXHash64Adapter() {
      protected StreamingXXHash64 streamingHash(long seed) {
        return XXHashFactory.unsafeInstance().newStreamingHash64(seed);
      }
    },
    new StreamingXXHash64Adapter() {
      protected StreamingXXHash64 streamingHash(long seed) {
        return XXHashFactory.safeInstance().newStreamingHash64(seed);
      }
    }
  };

  @Test
  public void testEmpty() {
    final long seed = randomLong();
    for (XXHash64 xxHash : INSTANCES) {
      xxHash.hash(new byte[0], 0, 0, seed);
      xxHash.hash(copyOf(new byte[0], 0, 0), 0, 0, seed);
    }
  }

  @Test
  @Repeat(iterations = 20)
  public void testAIOOBE() {
    final long seed = randomLong();
    final int max = randomBoolean() ? 64 : 1000;
    final int bufLen = randomIntBetween(1, max);
    final byte[] buf = new byte[bufLen];
    for (int i = 0; i < buf.length; ++i) {
      buf[i] = randomByte();
    }
    final int off = randomInt(buf.length - 1);
    final int len = randomInt(buf.length - off);
    for (XXHash64 xxHash : INSTANCES) {
      xxHash.hash(buf, off, len, seed);
    }
  }

  @Test
  @Repeat(iterations=40)
  public void testInstances() {
    final int maxLenLog = randomInt(20);
    final int bufLen = randomInt(1 << maxLenLog);
    byte[] buf = new byte[bufLen];
    for (int i = 0; i < bufLen; ++i) {
      buf[i] = randomByte();
    }
    final long seed = randomLong();
    final int off = randomIntBetween(0, Math.max(0, bufLen - 1));
    final int len = randomIntBetween(0, bufLen - off);

    final long ref = XXHashFactory.nativeInstance().hash64().hash(buf, off, len, seed);
    for (XXHash64 hash : INSTANCES) {
      final long h = hash.hash(buf, off, len, seed);
      assertEquals(hash.toString(), ref, h);
      final ByteBuffer copy = copyOf(buf, off, len);
      final long h2 = hash.hash(copy, off, len, seed);
      assertEquals(off, copy.position());
      assertEquals(len, copy.remaining());
      assertEquals(hash.toString(), ref, h2);
    }
  }

  @Test
  public void test4GB() {
    byte[] bytes = new byte[randomIntBetween(1 << 22, 1 << 26)];
    for (int i = 0; i < bytes.length; ++i) {
      bytes[i] = randomByte();
    }
    final int off = randomInt(5);
    final int len = randomIntBetween(bytes.length - off - 1024, bytes.length - off);
    long totalLen = 0;
    final long seed = randomLong();
    StreamingXXHash64 hash1 = XXHashFactory.nativeInstance().newStreamingHash64(seed);
    StreamingXXHash64 hash2 = XXHashFactory.unsafeInstance().newStreamingHash64(seed);
    StreamingXXHash64 hash3 = XXHashFactory.safeInstance().newStreamingHash64(seed);
    while (totalLen < (1L << 33)) {
      hash1.update(bytes, off, len);
      hash2.update(bytes, off, len);
      hash3.update(bytes, off, len);
      assertEquals(hash2.toString() + " " + totalLen, hash1.getValue(), hash2.getValue());
      assertEquals(hash3.toString() + " " + totalLen, hash1.getValue(), hash3.getValue());
      totalLen += len;
    }
  }

}
