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

public class XXHash32Test extends AbstractLZ4Test {

  private static abstract class StreamingXXHash32Adapter extends XXHash32 {

    protected abstract StreamingXXHash32 streamingHash(int seed);

    @Override
    public int hash(byte[] buf, int off, int len, int seed) {
      Utils.checkRange(buf, off, len);
      int originalOff = off;
      int remainingPasses = randomInt(5);
      StreamingXXHash32 h = streamingHash(seed);
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
    public int hash(ByteBuffer buf, int off, int len, int seed) {
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

  private static XXHash32[] INSTANCES = new XXHash32[] {
    XXHashFactory.nativeInstance().hash32(),
    XXHashFactory.unsafeInstance().hash32(),
    XXHashFactory.safeInstance().hash32(),
    new StreamingXXHash32Adapter() {
      protected StreamingXXHash32 streamingHash(int seed) {
        return XXHashFactory.nativeInstance().newStreamingHash32(seed);
      }
    },
    new StreamingXXHash32Adapter() {
      protected StreamingXXHash32 streamingHash(int seed) {
        return XXHashFactory.unsafeInstance().newStreamingHash32(seed);
      }
    },
    new StreamingXXHash32Adapter() {
      protected StreamingXXHash32 streamingHash(int seed) {
        return XXHashFactory.safeInstance().newStreamingHash32(seed);
      }
    }
  };

  @Test
  public void testEmpty() {
    final int seed = randomInt();
    for (XXHash32 xxHash : INSTANCES) {
      xxHash.hash(new byte[0], 0, 0, seed);
      xxHash.hash(copyOf(new byte[0], 0, 0), 0, 0, seed);
    }
  }

  @Test
  @Repeat(iterations = 20)
  public void testAIOOBE() {
    final int seed = randomInt();
    final int max = randomBoolean() ? 32 : 1000;
    final int bufLen = randomIntBetween(1, max);
    final byte[] buf = randomArray(bufLen, 256);
    final int off = randomInt(buf.length - 1);
    final int len = randomInt(buf.length - off);
    for (XXHash32 xxHash : INSTANCES) {
      xxHash.hash(buf, off, len, seed);
      xxHash.hash(copyOf(buf, off, len), off, len, seed);
    }
  }

  @Test
  @Repeat(iterations=40)
  public void testInstances() {
    final int maxLenLog = randomInt(20);
    final int bufLen = randomInt(1 << maxLenLog);
    byte[] buf = randomArray(bufLen, 256);
    final int seed = randomInt();
    final int off = randomIntBetween(0, Math.max(0, bufLen - 1));
    final int len = randomIntBetween(0, bufLen - off);

    final int ref = XXHashFactory.nativeInstance().hash32().hash(buf, off, len, seed);
    for (XXHash32 hash : INSTANCES) {
      final int h = hash.hash(buf, off, len, seed);
      assertEquals(hash.toString(), ref, h);
      final ByteBuffer copy = copyOf(buf, off, len);
      final int h2 = hash.hash(copy, off, len, seed);
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
    final int seed = randomInt();
    StreamingXXHash32 hash1 = XXHashFactory.nativeInstance().newStreamingHash32(seed);
    StreamingXXHash32 hash2 = XXHashFactory.unsafeInstance().newStreamingHash32(seed);
    StreamingXXHash32 hash3 = XXHashFactory.safeInstance().newStreamingHash32(seed);
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
