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
import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.carrotsearch.randomizedtesting.RandomizedRunner;
import com.carrotsearch.randomizedtesting.RandomizedTest;
import com.carrotsearch.randomizedtesting.annotations.Repeat;

@RunWith(RandomizedRunner.class)
public class LZ4Test extends RandomizedTest {

  private static byte[] getCompressedWorstCase(byte[] uncompressed) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    int len = uncompressed.length;
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
      baos.write(uncompressed);
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
    assertEquals(0, compressionCodec.uncompress(compressed, 0, compressedLength, new byte[3], 1));
  }

  @Test
  public void testEmpty() {
    for (LZ4Compressor compressor : COMPRESSORS) {
      for (LZ4Uncompressor uncompressor : UNCOMPRESSORS) {
        testEmpty(new LengthLZ4(compressor, uncompressor));
      }
    }
    for (LZ4Compressor compressor : COMPRESSORS) {
      for (LZ4UnknwonSizeUncompressor uncompressor : UNCOMPRESSORS2) {
        testEmpty(new LengthBitsLZ4(compressor, uncompressor));
      }
    }
  }

  public void testCompress(LZ4Compressor compressor, LZ4Uncompressor uncompressor) {
    testCompress(new LengthLZ4(compressor, uncompressor));
  }

  public void testCompress(LZ4Compressor compressor, LZ4UnknwonSizeUncompressor uncompressor) {
    testCompress(new LengthBitsLZ4(compressor, uncompressor));
  }

  public void testCompress(CompressionCodec compressionCodec) {
    final int max = randomInt(256);
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
    byte[] uncompressed = new byte[src.length];
    final int uncompressedLength = compressionCodec.uncompress(compressed, 2, compressedLength, uncompressed, 3);
    assertEquals(src.length - 15, uncompressedLength);
    final byte[] original = Arrays.copyOfRange(src, 4, 4 + src.length - 15);
    compressed = Arrays.copyOfRange(compressed, 2, compressedLength + 2);
    final byte[] restored = Arrays.copyOfRange(uncompressed, 3, 3 + uncompressedLength);
    assertArrayEquals(original, restored);
    assertArrayEquals(compressed, compressionCodec.compress(src, 4, src.length - 15));
    assertArrayEquals(original, compressionCodec.uncompress(compressed));
  }

  @Test
  @Repeat(iterations=5)
  public void testCompress() {
    final LZ4Compressor compressor = randomFrom(COMPRESSORS);
    testCompress(compressor, (LZ4Uncompressor) LZ4JNIUncompressor.INSTANCE);
  }

  @Test
  @Repeat(iterations=5)
  public void testUncompress() {
    final LZ4Uncompressor uncompressor = randomFrom(UNCOMPRESSORS);
    testCompress(LZ4JNICompressor.HIGH_COMPRESSION, uncompressor);
  }

  @Test
  @Repeat(iterations=5)
  public void testUncompressUnknownSize() {
    final LZ4UnknwonSizeUncompressor uncompressor = randomFrom(UNCOMPRESSORS2);
    testCompress(LZ4JNICompressor.HIGH_COMPRESSION, uncompressor);
  }

  public void testUncompressWorstCase(LZ4Uncompressor uncompressor) {
    final int len = randomInt(100 * 1024);
    final int max = randomInt(256);
    byte[] uncompressed = randomArray(len, max);
    byte[] compressed = getCompressedWorstCase(uncompressed);
    byte[] restored = new byte[uncompressed.length];
    int cpLen = uncompressor.uncompress(compressed, 0, restored, 0, uncompressed.length);
    assertEquals(compressed.length, cpLen);
    assertArrayEquals(uncompressed, restored);
  }

  @Test
  public void testUncompressWorstCase() {
    for (LZ4Uncompressor uncompressor : UNCOMPRESSORS) {
      testUncompressWorstCase(uncompressor);
    }
  }

  public void testUncompressWorstCase(LZ4UnknwonSizeUncompressor uncompressor) {
    final int len = randomInt(100 * 1024);
    final int max = randomInt(256);
    byte[] uncompressed = randomArray(len, max);
    byte[] compressed = getCompressedWorstCase(uncompressed);
    byte[] restored = new byte[uncompressed.length];
    int uncpLen = uncompressor.uncompressUnknownSize(compressed, 0, compressed.length, restored, 0);
    assertEquals(uncompressed.length, uncpLen);
    assertArrayEquals(uncompressed, restored);
  }

  @Test
  public void testUncompressUnknownSizeWorstCase() {
    for (LZ4UnknwonSizeUncompressor uncompressor : UNCOMPRESSORS2) {
      testUncompressWorstCase(uncompressor);
    }
  }

  // doesn't work, should we fix it?
  //@Test(expected=LZ4Exception.class)
  @Repeat(iterations=5)
  public void testUncompressUnknownSizeUnderflow() {
    final LZ4UnknwonSizeUncompressor uncompressor = randomFrom(UNCOMPRESSORS2);
    final int len = randomInt(100000);
    final int max = randomInt(256);
    final byte[] data = new byte[len];
    for (int i = 0; i < data.length; ++i) {
      data[i] = (byte) randomInt(max);
    }
    final int maxCompressedLength = LZ4JNICompressor.FAST.maxCompressedLength(len);
    final byte[] compressed = new byte[maxCompressedLength];
    final int compressedLength = LZ4JNICompressor.FAST.compress(data, 0, data.length, compressed, 0);
    uncompressor.uncompressUnknownSize(compressed, 0, compressedLength, new byte[data.length - 1], 0);
  }
}
