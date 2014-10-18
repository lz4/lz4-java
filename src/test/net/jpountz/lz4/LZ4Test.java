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

import static net.jpountz.lz4.Instances.COMPRESSORS;
import static net.jpountz.lz4.Instances.FAST_DECOMPRESSORS;
import static net.jpountz.lz4.Instances.SAFE_DECOMPRESSORS;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.carrotsearch.randomizedtesting.RandomizedRunner;
import com.carrotsearch.randomizedtesting.annotations.Repeat;

@RunWith(RandomizedRunner.class)
public class LZ4Test extends AbstractLZ4RoundtripTest {

  @Test
  @Repeat(iterations=50)
  public void testMaxCompressedLength() {
    final int len = randomBoolean() ? randomInt(16) : randomInt(1 << 30);
    for (LZ4Compressor compressor : COMPRESSORS) {
      assertEquals(LZ4JNI.LZ4_compressBound(len), compressor.maxCompressedLength(len));
    }
  }

  private static byte[] getCompressedWorstCase(byte[] decompressed) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    int len = decompressed.length;
    if (len >= LZ4Constants.RUN_MASK) {
      baos.write(LZ4Constants.RUN_MASK << LZ4Constants.ML_BITS);
      len -= LZ4Constants.RUN_MASK;
    }
    while (len >= 255) {
      baos.write(255);
      len -= 255;
    }
    baos.write(len);
    try {
      baos.write(decompressed);
    } catch (IOException e) {
      throw new AssertionError();
    }
    return baos.toByteArray();
  }

  @Test
  public void testEmpty() {
    testRoundTrip(new byte[0]);
  }

  public void testUncompressWorstCase(LZ4FastDecompressor decompressor) {
    final int len = randomInt(100 * 1024);
    final int max = randomIntBetween(1, 255);
    byte[] decompressed = randomArray(len, max);
    byte[] compressed = getCompressedWorstCase(decompressed);
    byte[] restored = new byte[decompressed.length];
    int cpLen = decompressor.decompress(compressed, 0, restored, 0, decompressed.length);
    assertEquals(compressed.length, cpLen);
    assertArrayEquals(decompressed, restored);
  }

  @Test
  public void testUncompressWorstCase() {
    for (LZ4FastDecompressor decompressor : FAST_DECOMPRESSORS) {
      testUncompressWorstCase(decompressor);
    }
  }

  public void testUncompressWorstCase(LZ4SafeDecompressor decompressor) {
    final int len = randomInt(100 * 1024);
    final int max = randomIntBetween(1, 256);
    byte[] decompressed = randomArray(len, max);
    byte[] compressed = getCompressedWorstCase(decompressed);
    byte[] restored = new byte[decompressed.length];
    int uncpLen = decompressor.decompress(compressed, 0, compressed.length, restored, 0);
    assertEquals(decompressed.length, uncpLen);
    assertArrayEquals(decompressed, restored);
  }

  @Test
  public void testUncompressSafeWorstCase() {
    for (LZ4SafeDecompressor decompressor : SAFE_DECOMPRESSORS) {
      testUncompressWorstCase(decompressor);
    }
  }
  
  @Test
  public void testRoundtripGeo() throws IOException {
    testRoundTrip("/calgary/geo");
  }

  @Test
  public void testRoundtripBook1() throws IOException {
    testRoundTrip("/calgary/book1");
  }

  @Test
  public void testRoundtripPic() throws IOException {
    testRoundTrip("/calgary/pic");
  }

  @Test
  public void testNullMatchDec() {
    // 1 literal, 4 matchs with matchDec=0, 8 literals
    final byte[] invalid = new byte[] { 16, 42, 0, 0, (byte) 128, 42, 42, 42, 42, 42, 42, 42, 42 };
    // decompression should neither throw an exception nor loop indefinitely
    for (LZ4FastDecompressor decompressor : FAST_DECOMPRESSORS) {
      decompressor.decompress(invalid, 0, new byte[13], 0, 13);
    }
    for (LZ4SafeDecompressor decompressor : SAFE_DECOMPRESSORS) {
      decompressor.decompress(invalid, 0, invalid.length, new byte[20], 0);
    }
  }

  @Test
  public void testEndsWithMatch() {
    // 6 literals, 4 matchs
    final byte[] invalid = new byte[] { 96, 42, 43, 44, 45, 46, 47, 5, 0 };
    final int decompressedLength = 10;

    for (LZ4FastDecompressor decompressor : FAST_DECOMPRESSORS) {
      try {
        // it is invalid to end with a match, should be at least 5 literals
        decompressor.decompress(invalid, 0, new byte[decompressedLength], 0, decompressedLength);
        assertTrue(decompressor.toString(), false);
      } catch (LZ4Exception e) {
        // OK
      }
    }

    for (LZ4SafeDecompressor decompressor : SAFE_DECOMPRESSORS) {
      try {
        // it is invalid to end with a match, should be at least 5 literals
        decompressor.decompress(invalid, 0, invalid.length, new byte[20], 0);
        assertTrue(false);
      } catch (LZ4Exception e) {
        // OK
      }
    }
  }

  @Test
  public void testEndsWithLessThan5Literals() {
    // 6 literals, 4 matchs
    final byte[] invalidBase = new byte[] { 96, 42, 43, 44, 45, 46, 47, 5, 0 };

    for (int i = 1; i < 5; ++i) {
      final byte[] invalid = Arrays.copyOf(invalidBase, invalidBase.length + 1 + i);
      invalid[invalidBase.length] = (byte) (i << 4); // i literals at the end

      for (LZ4FastDecompressor decompressor : FAST_DECOMPRESSORS) {
        try {
          // it is invalid to end with a match, should be at least 5 literals
          decompressor.decompress(invalid, 0, new byte[20], 0, 20);
          assertTrue(decompressor.toString(), false);
        } catch (LZ4Exception e) {
          // OK
        }
      }

      for (LZ4SafeDecompressor decompressor : SAFE_DECOMPRESSORS) {
        try {
          // it is invalid to end with a match, should be at least 5 literals
          decompressor.decompress(invalid, 0, invalid.length, new byte[20], 0);
          assertTrue(false);
        } catch (LZ4Exception e) {
          // OK
        }
      }
    }
  }

  @Test
  @Repeat(iterations=5)
  public void testAllEqual() {
    final int len = randomBoolean() ? randomInt(20) : randomInt(100000);
    final byte[] buf = new byte[len];
    Arrays.fill(buf, randomByte());
    testRoundTrip(buf);
  }

  @Test
  public void testMaxDistance() {
    final int len = randomIntBetween(1 << 17, 1 << 18);
    final int off = 0;//randomInt(len - (1 << 16) - (1 << 15));
    final byte[] buf = new byte[len];
    for (int i = 0; i < (1 << 15); ++i) {
      buf[off + i] = randomByte();
    }
    System.arraycopy(buf, off, buf, off + 65535, 1 << 15);
    testRoundTrip(buf);
  }

  @Test
  @Repeat(iterations=10)
  public void testCompressedArrayEqualsJNI() {
    final int n = randomIntBetween(1, 15);
    final int len = randomBoolean() ? randomInt(1 << 16) : randomInt(1 << 20);
    final byte[] data = randomArray(len, n);
    testRoundTrip(data);
  }

  @Test
  // https://github.com/jpountz/lz4-java/issues/12
  public void testRoundtripIssue12() {
    byte[] data = new byte[]{
        14, 72, 14, 85, 3, 72, 14, 85, 3, 72, 14, 72, 14, 72, 14, 85, 3, 72, 14, 72, 14, 72, 14, 72, 14, 72, 14, 72, 14, 85, 3, 72,
        14, 85, 3, 72, 14, 85, 3, 72, 14, 85, 3, 72, 14, 85, 3, 72, 14, 85, 3, 72, 14, 50, 64, 0, 46, -1, 0, 0, 0, 29, 3, 85,
        8, -113, 0, 68, -97, 3, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3,
        0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113,
        0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113,
        0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 50, 64, 0, 47, -105, 0, 0, 0, 30, 3, -97, 6, 0, 68, -113,
        0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3, 85,
        8, -113, 0, 68, -97, 3, 0, 2, -97, 6, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97,
        6, 0, 68, -113, 0, 120, 64, 0, 48, 4, 0, 0, 0, 31, 34, 72, 29, 72, 37, 72, 35, 72, 45, 72, 23, 72, 46, 72, 20, 72, 40, 72,
        33, 72, 25, 72, 39, 72, 38, 72, 26, 72, 28, 72, 42, 72, 24, 72, 27, 72, 36, 72, 41, 72, 32, 72, 18, 72, 30, 72, 22, 72, 31, 72,
        43, 72, 19, 72, 34, 72, 29, 72, 37, 72, 35, 72, 45, 72, 23, 72, 46, 72, 20, 72, 40, 72, 33, 72, 25, 72, 39, 72, 38, 72, 26, 72,
        28, 72, 42, 72, 24, 72, 27, 72, 36, 72, 41, 72, 32, 72, 18, 72, 30, 72, 22, 72, 31, 72, 43, 72, 19, 72, 34, 72, 29, 72, 37, 72,
        35, 72, 45, 72, 23, 72, 46, 72, 20, 72, 40, 72, 33, 72, 25, 72, 39, 72, 38, 72, 26, 72, 28, 72, 42, 72, 24, 72, 27, 72, 36, 72,
        41, 72, 32, 72, 18, 16, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 39, 24, 32, 34, 124, 0, 120, 64, 0, 48, 80, 0, 0, 0, 31, 30, 72, 22, 72, 31, 72, 43, 72, 19, 72, 34, 72, 29, 72, 37, 72,
        35, 72, 45, 72, 23, 72, 46, 72, 20, 72, 40, 72, 33, 72, 25, 72, 39, 72, 38, 72, 26, 72, 28, 72, 42, 72, 24, 72, 27, 72, 36, 72,
        41, 72, 32, 72, 18, 72, 30, 72, 22, 72, 31, 72, 43, 72, 19, 72, 34, 72, 29, 72, 37, 72, 35, 72, 45, 72, 23, 72, 46, 72, 20, 72,
        40, 72, 33, 72, 25, 72, 39, 72, 38, 72, 26, 72, 28, 72, 42, 72, 24, 72, 27, 72, 36, 72, 41, 72, 32, 72, 18, 72, 30, 72, 22, 72,
        31, 72, 43, 72, 19, 72, 34, 72, 29, 72, 37, 72, 35, 72, 45, 72, 23, 72, 46, 72, 20, 72, 40, 72, 33, 72, 25, 72, 39, 72, 38, 72,
        26, 72, 28, 72, 42, 72, 24, 72, 27, 72, 36, 72, 41, 72, 32, 72, 18, 72, 30, 72, 22, 72, 31, 72, 43, 72, 19, 72, 34, 72, 29, 72,
        37, 72, 35, 72, 45, 72, 23, 72, 46, 72, 20, 72, 40, 72, 33, 72, 25, 72, 39, 72, 38, 72, 26, 72, 28, 72, 42, 72, 24, 72, 27, 72,
        36, 72, 41, 72, 32, 72, 18, 72, 30, 72, 22, 72, 31, 72, 43, 72, 19, 72, 34, 72, 29, 72, 37, 72, 35, 72, 45, 72, 23, 72, 46, 72,
        20, 72, 40, 72, 33, 72, 25, 72, 39, 72, 38, 72, 26, 72, 28, 72, 42, 72, 24, 72, 27, 72, 36, 72, 41, 72, 32, 72, 18, 72, 30, 72,
        22, 72, 31, 72, 43, 72, 19, 72, 34, 72, 29, 72, 37, 72, 35, 72, 45, 72, 23, 72, 46, 72, 20, 72, 40, 72, 33, 72, 25, 72, 39, 72,
        38, 72, 26, 72, 28, 72, 42, 72, 24, 72, 27, 72, 36, 72, 41, 72, 32, 72, 18, 72, 30, 72, 22, 72, 31, 72, 43, 72, 19, 72, 34, 72,
        29, 72, 37, 72, 35, 72, 45, 72, 23, 72, 46, 72, 20, 72, 40, 72, 33, 72, 25, 72, 39, 72, 38, 72, 26, 72, 28, 72, 42, 72, 24, 72,
        27, 72, 36, 72, 41, 72, 32, 72, 18, 72, 30, 72, 22, 72, 31, 72, 43, 72, 19, 50, 64, 0, 49, 20, 0, 0, 0, 32, 3, -97, 6, 0,
        68, -113, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97,
        6, 0, 68, -113, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2,
        3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2,
        3, -97, 6, 0, 50, 64, 0, 50, 53, 0, 0, 0, 34, 3, -97, 6, 0, 68, -113, 0, 2, 3, 85, 8, -113, 0, 68, -113, 0, 2, 3, -97,
        6, 0, 68, -113, 0, 2, 3, 85, 8, -113, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3,
        -97, 6, 0, 68, -113, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, 85, 8, -113, 0, 68, -97,
        3, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3,
        85, 8, -113, 0, 68, -97, 3, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0,
        2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3,
        -97, 6, 0, 50, 64, 0, 51, 85, 0, 0, 0, 36, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97,
        6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, -97, 5, 0, 2, 3, 85, 8, -113, 0, 68,
        -97, 3, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0,
        68, -113, 0, 2, 3, -97, 6, 0, 50, -64, 0, 51, -45, 0, 0, 0, 37, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6,
        0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, 85, 8, -113, 0, 68, -113, 0, 2, 3, -97,
        6, 0, 68, -113, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 120, 64, 0, 52, -88, 0, 0,
        0, 39, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85, 5, 72,
        13, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 72, 13, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85,
        5, 72, 13, 85, 5, 72, 13, 72, 13, 72, 13, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85,
        5, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85,
        5, 72, 13, 85, 5, 72, 13, 72, 13, 72, 13, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 72, 13, 85, 5, 72, 13, 72,
        13, 85, 5, 72, 13, 72, 13, 85, 5, 72, 13, -19, -24, -101, -35
      };
    testRoundTrip(data, 9, data.length - 9);
  }
  
  @Test
  public void testWriteToReadOnlyBuffer() {
    byte[] input = "AAB AAAAAC BBAAAAAA.".getBytes();
    byte[] compressed = LZ4Factory.safeInstance().fastCompressor().compress(input);
    ByteBuffer src = ByteBuffer.allocate(100);
    ByteBuffer cmp = ByteBuffer.allocate(100);
    src.put(input);
    src.flip();
    cmp.put(compressed);
    cmp.flip();
    for (ByteBuffer dst: Arrays.asList(
        ByteBuffer.allocate(100).asReadOnlyBuffer(),
        ByteBuffer.allocateDirect(100).asReadOnlyBuffer())) {
      for (LZ4Factory lz4 : Arrays.asList(
          LZ4Factory.nativeInstance(),
          LZ4Factory.unsafeInstance(),
          LZ4Factory.safeInstance())) {
        try {
          lz4.fastCompressor().compress(src, 0, src.limit(), dst, 0, dst.capacity());
          fail("Should not write to read-only buffer.");
        } catch (ReadOnlyBufferException e) {
          // expected
        }
        try {
          lz4.highCompressor().compress(src, 0, src.limit(), dst, 0, dst.capacity());
          fail("Should not write to read-only buffer.");
        } catch (ReadOnlyBufferException e) {
          // expected
        }
        try {
          lz4.fastDecompressor().decompress(cmp, 0, dst, 0, src.remaining());
          fail("Should not write to read-only buffer.");
        } catch (ReadOnlyBufferException e) {
          // expected
        }
        try {
          lz4.safeDecompressor().decompress(cmp, 0, cmp.limit(), dst, 0);
          fail("Should not write to read-only buffer.");
        } catch (ReadOnlyBufferException e) {
          // expected
        }
      }
    }
  }
}
