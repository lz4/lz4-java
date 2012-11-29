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

import static net.jpountz.lz4.Instances.COMPRESSORS;
import static net.jpountz.lz4.Instances.UNCOMPRESSORS;
import static net.jpountz.lz4.Instances.UNCOMPRESSORS2;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.carrotsearch.randomizedtesting.RandomizedRunner;
import com.carrotsearch.randomizedtesting.RandomizedTest;
import com.carrotsearch.randomizedtesting.annotations.Repeat;

@RunWith(RandomizedRunner.class)
public class LZ4Test extends RandomizedTest {

  private static byte[] getCompressedWorstCase(byte[] decompressed) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    int len = decompressed.length;
    if (len >= LZ4Utils.RUN_MASK) {
      baos.write(LZ4Utils.RUN_MASK << LZ4Utils.ML_BITS);
      len -= LZ4Utils.RUN_MASK;
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

  private static byte[] randomArray(int len, int max) {
    byte[] result = new byte[len];
    for (int i = 0; i < result.length; ++i) {
      result[i] = (byte) randomInt(max);
    }
    return result;
  }

  public void testEmpty(CompressionCodec compressionCodec) {
    final byte[] data = new byte[0];
    final int maxCompressedLength = compressionCodec.maxCompressedLength(0);
    final byte[] compressed = new byte[maxCompressedLength];
    final int compressedLength = compressionCodec.compress(data, 0, 0, compressed, 0);
    assertTrue(compressedLength > 0);
    assertTrue(compressedLength <= maxCompressedLength);
    assertEquals(0, compressionCodec.decompress(compressed, 0, compressedLength, new byte[3], 1));
  }

  @Test
  public void testEmpty() {
    for (LZ4Compressor compressor : COMPRESSORS) {
      for (LZ4Decompressor decompressor : UNCOMPRESSORS) {
        testEmpty(new LengthLZ4(compressor, decompressor));
      }
    }
    for (LZ4Compressor compressor : COMPRESSORS) {
      for (LZ4UnknownSizeDecompressor decompressor : UNCOMPRESSORS2) {
        testEmpty(new LengthBitsLZ4(compressor, decompressor));
      }
    }
  }

  public void testCompress(LZ4Compressor compressor, LZ4Decompressor decompressor) {
    testCompress(new LengthLZ4(compressor, decompressor));
  }

  public void testCompress(LZ4Compressor compressor, LZ4UnknownSizeDecompressor decompressor) {
    testCompress(new LengthBitsLZ4(compressor, decompressor));
  }

  public void testCompress(CompressionCodec compressionCodec) {
    final int max = randomBoolean()
        ? randomInt(3)
        : randomInt(256);
    final int size = randomBoolean()
        ? randomBoolean() ? 19 : 20
            : randomBoolean() ? randomIntBetween(45, 64000) : randomIntBetween(200000, 1000000);
    final byte[] src = new byte[size];
    for (int i = 0; i < src.length; ++i) {
      // low values of max are more likely to produce repeated patterns...
      src[i] = (byte) randomInt(max);
    }
    final int maxCompressedLength = compressionCodec.maxCompressedLength(src.length - 15);
    byte[] compressed = new byte[maxCompressedLength + 10];
    final int compressedLength = compressionCodec.compress(src, 4, src.length - 15, compressed, 2);
    assertTrue(compressedLength <= maxCompressedLength);
    byte[] decompressed = new byte[src.length];
    final int decompressedLength = compressionCodec.decompress(compressed, 2, compressedLength, decompressed, 3);
    assertEquals(src.length - 15, decompressedLength);
    final byte[] original = Arrays.copyOfRange(src, 4, 4 + src.length - 15);
    compressed = Arrays.copyOfRange(compressed, 2, compressedLength + 2);
    final byte[] restored = Arrays.copyOfRange(decompressed, 3, 3 + decompressedLength);
    assertArrayEquals(original, restored);
    assertArrayEquals(compressed, compressionCodec.compress(src, 4, src.length - 15));
    assertArrayEquals(original, compressionCodec.decompress(compressed));
  }

  @Test
  @Repeat(iterations=5)
  public void testCompress() {
    final LZ4Compressor compressor = randomFrom(COMPRESSORS);
    testCompress(compressor, (LZ4Decompressor) LZ4JNIDecompressor.INSTANCE);
  }

  @Test
  @Repeat(iterations=5)
  public void testUncompress() {
    final LZ4Decompressor decompressor = randomFrom(UNCOMPRESSORS);
    testCompress(LZ4JNICompressor.HIGH_COMPRESSION, decompressor);
  }

  @Test
  @Repeat(iterations=5)
  public void testUncompressUnknownSize() {
    final LZ4UnknownSizeDecompressor decompressor = randomFrom(UNCOMPRESSORS2);
    testCompress(LZ4JNICompressor.HIGH_COMPRESSION, decompressor);
  }

  public void testUncompressWorstCase(LZ4Decompressor decompressor) {
    final int len = randomInt(100 * 1024);
    final int max = randomInt(256);
    byte[] decompressed = randomArray(len, max);
    byte[] compressed = getCompressedWorstCase(decompressed);
    byte[] restored = new byte[decompressed.length];
    int cpLen = decompressor.decompress(compressed, 0, restored, 0, decompressed.length);
    assertEquals(compressed.length, cpLen);
    assertArrayEquals(decompressed, restored);
  }

  @Test
  public void testUncompressWorstCase() {
    for (LZ4Decompressor decompressor : UNCOMPRESSORS) {
      testUncompressWorstCase(decompressor);
    }
  }

  public void testUncompressWorstCase(LZ4UnknownSizeDecompressor decompressor) {
    final int len = randomInt(100 * 1024);
    final int max = randomInt(256);
    byte[] decompressed = randomArray(len, max);
    byte[] compressed = getCompressedWorstCase(decompressed);
    byte[] restored = new byte[decompressed.length];
    int uncpLen = decompressor.decompress(compressed, 0, compressed.length, restored, 0);
    assertEquals(decompressed.length, uncpLen);
    assertArrayEquals(decompressed, restored);
  }

  @Test
  public void testUncompressUnknownSizeWorstCase() {
    for (LZ4UnknownSizeDecompressor decompressor : UNCOMPRESSORS2) {
      testUncompressWorstCase(decompressor);
    }
  }

  // doesn't work, should we fix it?
  //@Test(expected=LZ4Exception.class)
  @Repeat(iterations=5)
  public void testUncompressUnknownSizeUnderflow() {
    final LZ4UnknownSizeDecompressor decompressor = randomFrom(UNCOMPRESSORS2);
    final int len = randomInt(100000);
    final int max = randomInt(256);
    final byte[] data = new byte[len];
    for (int i = 0; i < data.length; ++i) {
      data[i] = (byte) randomInt(max);
    }
    final int maxCompressedLength = LZ4JNICompressor.FAST.maxCompressedLength(len);
    final byte[] compressed = new byte[maxCompressedLength];
    final int compressedLength = LZ4JNICompressor.FAST.compress(data, 0, data.length, compressed, 0, compressed.length);
    decompressor.decompress(compressed, 0, compressedLength, new byte[data.length - 1], 0);
  }

  private static byte[] readResource(String resource) throws IOException {
    InputStream is = LZ4Test.class.getResourceAsStream(resource);
    if (is == null) {
      throw new IllegalStateException("Cannot find " + resource);
    }
    byte[] buf = new byte[4096];
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try {
      while (true) {
        final int read = is.read(buf);
        if (read == -1) {
          break;
        }
        baos.write(buf, 0, read);
      }
    } finally {
      is.close();
    }
    return baos.toByteArray();
  }

  public void testRoundTrip(String resource,
      LZ4Compressor compressor,
      LZ4Decompressor decompressor,
      LZ4UnknownSizeDecompressor decompressor2) throws IOException {
    final byte[] decompressed = readResource(resource);
    final byte[] compressed = new byte[LZ4Utils.maxCompressedLength(decompressed.length)];
    final int compressedLen = compressor.compress(
        decompressed, 0, decompressed.length,
        compressed, 0, compressed.length);

    final byte[] restored = new byte[decompressed.length];
    assertEquals(compressedLen, decompressor.decompress(compressed, 0, restored, 0, decompressed.length));
    assertArrayEquals(decompressed, restored);

    Arrays.fill(restored, (byte) 0);
    decompressor2.decompress(compressed, 0, compressedLen, restored, 0);
    assertEquals(decompressed.length, decompressor2.decompress(compressed, 0, compressedLen, restored, 0));

    LZ4Compressor refCompressor = null;
    if (compressor == LZ4Factory.unsafeInstance().fastCompressor()
        || compressor == LZ4Factory.safeInstance().fastCompressor()) {
      refCompressor = LZ4Factory.nativeInstance().fastCompressor();
    } else if (compressor == LZ4Factory.unsafeInstance().highCompressor()
        || compressor == LZ4Factory.safeInstance().highCompressor()) {
      refCompressor = LZ4Factory.nativeInstance().highCompressor();
    }
    if (refCompressor != null) {
      final byte[] compressed2 = new byte[refCompressor.maxCompressedLength(decompressed.length)];
      final int compressedLen2 = refCompressor.compress(decompressed, 0, decompressed.length, compressed2, 0, compressed2.length);
      assertCompressedArrayEquals(compressor.toString(),
          Arrays.copyOf(compressed2,  compressedLen2),
          Arrays.copyOf(compressed,  compressedLen));
    }
  }

  public void testRoundTrip(String resource, LZ4Factory lz4) throws IOException {
    for (LZ4Compressor compressor : Arrays.asList(
        lz4.fastCompressor(), lz4.highCompressor())) {
      testRoundTrip(resource, compressor, lz4.decompressor(), lz4.unknwonSizeDecompressor());
    }
  }

  public void testRoundTrip(String resource) throws IOException {
    for (LZ4Factory lz4 : Arrays.asList(
        LZ4Factory.nativeInstance(),
        LZ4Factory.unsafeInstance(),
        LZ4Factory.safeInstance())) {
      testRoundTrip(resource, lz4);
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
    // 1 literal, 4 matchs with matchDec=0, 5 literals
    final byte[] invalid = new byte[] { 16, 42, 0, 0, 42, 42, 42, 42, 42 };
    for (LZ4Decompressor decompressor : UNCOMPRESSORS) {
      try {
        decompressor.decompress(invalid, 0, new byte[10], 0, 10);
        // free not to fail, but do not throw something else than a LZ4Exception
      } catch (LZ4Exception e) {
        // OK
      }
    }
    for (LZ4UnknownSizeDecompressor decompressor : UNCOMPRESSORS2) {
      try {
        decompressor.decompress(invalid, 0, invalid.length, new byte[10], 0);
        assertTrue(decompressor.toString(), false);
      } catch (LZ4Exception e) {
        // OK
      }
    }
  }

  @Test
  public void testEndsWithMatch() {
    // 6 literals, 4 matchs
    final byte[] invalid = new byte[] { 96, 42, 43, 44, 45, 46, 47, 5, 0 };
    final int decompressedLength = 10;

    for (LZ4Decompressor decompressor : UNCOMPRESSORS) {
      try {
        // it is invalid to end with a match, should be at least 5 literals
        decompressor.decompress(invalid, 0, new byte[decompressedLength], 0, decompressedLength);
        assertTrue(decompressor.toString(), false);
      } catch (LZ4Exception e) {
        // OK
      }
    }

    for (LZ4UnknownSizeDecompressor decompressor : UNCOMPRESSORS2) {
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

      for (LZ4Decompressor decompressor : UNCOMPRESSORS) {
        try {
          // it is invalid to end with a match, should be at least 5 literals
          decompressor.decompress(invalid, 0, new byte[20], 0, 20);
          assertTrue(decompressor.toString(), false);
        } catch (LZ4Exception e) {
          // OK
        }
      }

      for (LZ4UnknownSizeDecompressor decompressor : UNCOMPRESSORS2) {
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
  @Repeat(iterations=50)
  public void testCompressExactSize() {
    final byte[] data = randomArray(randomInt(rarely() ? 100000 : 200), randomIntBetween(1, 10));
    for (LZ4Compressor compressor : COMPRESSORS) {
      final byte[] buf = new byte[compressor.maxCompressedLength(data.length)];
      final int compressedLength = compressor.compress(data, 0, data.length, buf, 0, buf.length);
      final byte[] buf2 = new byte[compressedLength];
      try {
        final int compressedLength2 = compressor.compress(data, 0, data.length, buf2, 0, buf2.length);
        assertEquals(compressedLength, compressedLength2);
        assertArrayEquals(Arrays.copyOf(buf, compressedLength), buf2);

        try {
          compressor.compress(data, 0, data.length, buf2, 0, buf2.length - 1);
          assertFalse(true);
        } catch (LZ4Exception e) {
          // ok
        }
      } catch (IllegalArgumentException e) {
        // the JNI high compressor does not support exact size compression
        assert compressor == LZ4Factory.nativeInstance().highCompressor();
      }
    }
  }

  @Test
  @Repeat(iterations=5)
  public void testAllEqual() {
    final int len = randomBoolean() ? randomInt(20) : randomInt(100000);
    final byte[] buf = new byte[len];
    Arrays.fill(buf, randomByte());
    for (LZ4Compressor compressor : COMPRESSORS) {
      final byte[] compressed = new byte[compressor.maxCompressedLength(len)];
      final int compressedLength = compressor.compress(buf, 0, len, compressed, 0, compressed.length);
      final int lastLiterals;
      if (len < LZ4Utils.MIN_LENGTH) {
        lastLiterals = len;
      } else {
        lastLiterals = LZ4Utils.LAST_LITERALS;
      }
      assertEquals(compressor.toString(), lastLiterals << 4, compressed[compressedLength - 1 - lastLiterals] & 0xFF);
    }
  }

  private static void assertCompressedArrayEquals(String message, byte[] expected, byte[] actual) {
    int off = 0;
    int decompressedOff = 0;
    while (true) {
      if (off == expected.length) {
        break;
      }
      final Sequence sequence1 = readSequence(expected, off);
      final Sequence sequence2 = readSequence(actual, off);
      assertEquals(message + ", off=" + off + ", decompressedOff=" + decompressedOff, sequence1, sequence2);
      off += sequence1.length;
      decompressedOff += sequence1.literalLen + sequence1.matchLen;
    }
  }

  private static Sequence readSequence(byte[] buf, int off) {
    final int start = off;
    final int token = buf[off++] & 0xFF;
    int literalLen = token >>> 4;
    if (literalLen >= 0x0F) {
      int len;
      while ((len = buf[off++] & 0xFF) == 0xFF) {
        literalLen += 0xFF;
      }
      literalLen += len;
    }
    off += literalLen;
    if (off == buf.length) {
      return new Sequence(literalLen, -1, -1, off - start);
    }
    int matchDec = (buf[off++] & 0xFF) | ((buf[off++] & 0xFF) << 8);
    int matchLen = token & 0x0F;
    if (matchLen >= 0x0F) {
      int len;
      while ((len = buf[off++] & 0xFF) == 0xFF) {
        matchLen += 0xFF;
      }
      matchLen += len;
    }
    return new Sequence(literalLen, matchDec, matchLen, off - start);
  }

  private static class Sequence {
    final int literalLen, matchDec, matchLen, length;

    public Sequence(int literalLen, int matchDec, int matchLen, int length) {
      this.literalLen = literalLen;
      this.matchDec = matchDec;
      this.matchLen = matchLen;
      this.length = length;
    }

    @Override
    public String toString() {
      return "Sequence [literalLen=" + literalLen + ", matchDec=" + matchDec
          + ", matchLen=" + matchLen + "]";
    }

    @Override
    public int hashCode() {
      return 42;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      Sequence other = (Sequence) obj;
      if (literalLen != other.literalLen)
        return false;
      if (matchDec != other.matchDec)
        return false;
      if (matchLen != other.matchLen)
        return false;
      return true;
    }

  }

}
