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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Random;

import org.junit.Test;

public class LZ4Test {

  static LZ4Compressor[] COMPRESSORS = new LZ4Compressor[] {
    LZ4JNICompressor.FAST,
    LZ4JNICompressor.HIGH_COMPRESSION,
    LZ4JavaUnsafeCompressor.FAST,
    LZ4JavaSafeCompressor.FAST,
    new LZ4StreamCompressor(LZ4JavaSafeCompressor.FAST),
    new LZ4StreamCompressor(LZ4JavaUnsafeCompressor.FAST)
  };

  static LZ4Uncompressor[] UNCOMPRESSORS = new LZ4Uncompressor[] {
    LZ4JNIUncompressor.INSTANCE,
    LZ4JavaUnsafeUncompressor.INSTANCE,
    LZ4JavaSafeUncompressor.INSTANCE
  };

  static LZ4UnknwonSizeUncompressor[] UNCOMPRESSORS2 = new LZ4UnknwonSizeUncompressor[] {
    LZ4JNIUncompressor.INSTANCE,
    LZ4JavaUnsafeUncompressor.INSTANCE,
    LZ4JavaSafeUncompressor.INSTANCE,
    new LZ4StreamUncompressor() {
      @Override
      protected InputStream lz4InputStream(InputStream is) throws IOException {
        return new LZ4JavaSafeInputStream(is);
      }
    },
    new LZ4StreamUncompressor() {
      @Override
      protected InputStream lz4InputStream(InputStream is) throws IOException {
        return new LZ4JavaUnsafeInputStream(is);
      }
    }
  };

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
    for (int max : new int[] {3, 10, 128}) {
      for (int size : new int[] {19, 20, 45, 255, 4000, 65000, 300000}) {
        final Random random = new Random(0);
        final byte[] src = new byte[size];
        for (int i = 0; i < src.length; ++i) {
          // low values of max are more likely to produce repeated patterns...
          src[i] = (byte) random.nextInt(max);
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
    }
  }

  @Test
  public void testCompress() {
    for (LZ4Compressor compressor : COMPRESSORS) {
      testCompress(compressor, (LZ4Uncompressor) LZ4JNIUncompressor.INSTANCE);
    }
  }

  @Test
  public void testUncompress() {
    for (LZ4Uncompressor uncompressor : UNCOMPRESSORS) {
      testCompress(LZ4JNICompressor.HIGH_COMPRESSION, uncompressor);
    }
  }

  @Test
  public void testUncompressUnknownSize() {
    for (LZ4UnknwonSizeUncompressor uncompressor : UNCOMPRESSORS2) {
      testCompress(LZ4JNICompressor.HIGH_COMPRESSION, uncompressor);
    }
  }

  public void testUncompressUnknownSizeUnderflow(LZ4UnknwonSizeUncompressor uncompressor) {
    final byte[] data = new byte[100];
    Random r = new Random(0);
    for (int i = 0; i < data.length; ++i) {
      data[i] = (byte) r.nextInt(5);
    }
    final int maxCompressedLength = LZ4JNICompressor.FAST.maxCompressedLength(100);
    final byte[] compressed = new byte[maxCompressedLength];
    final int compressedLength = LZ4JNICompressor.FAST.compress(data, 0, data.length, compressed, 0);
    try {
      uncompressor.uncompressUnknownSize(compressed, 0, compressedLength, new byte[data.length - 1], 0);
      assertFalse(uncompressor.getClass().toString(), true);
    } catch (LZ4Exception e) {
      // ok
    }
  }

}

