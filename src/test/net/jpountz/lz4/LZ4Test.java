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

import java.util.Arrays;
import java.util.Random;

import org.junit.Test;

import static org.junit.Assert.*;

public class LZ4Test {

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
  public void testEmptyJNIFast1() {
    testEmpty(new LengthLZ4(LZ4JNI.FAST));
  }

  @Test
  public void testEmptyJNIFast2() {
    testEmpty(new LengthBitsLZ4(LZ4JNI.FAST));
  }

  @Test
  public void testEmptyJavaFast1() {
    testEmpty(new LengthLZ4(LZ4Java.FAST));
  }

  @Test
  public void testEmptyJavaFast2() {
    testEmpty(new LengthBitsLZ4(LZ4Java.FAST));
  }

  @Test
  public void testEmptyJavaUnsafeFast1() {
    testEmpty(new LengthLZ4(LZ4JavaUnsafe.FAST));
  }

  @Test
  public void testEmptyJavaUnsafeFast2() {
    testEmpty(new LengthBitsLZ4(LZ4JavaUnsafe.FAST));
  }

  @Test
  public void testEmptyJNIHC1() {
    testEmpty(new LengthLZ4(LZ4JNI.HIGH_COMPRESSION));
  }

  @Test
  public void testEmptyJNIHC2() {
    testEmpty(new LengthBitsLZ4(LZ4JNI.HIGH_COMPRESSION));
  }

  public void testCompress(CompressionCodec compressionCodec, int max) {
    testCompress(compressionCodec, compressionCodec, max);
  }

  public void testCompress(CompressionCodec compressionCodec, CompressionCodec uncompressionCodec, int max) {
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
      final int uncompressedLength = uncompressionCodec.uncompress(compressed, 2, compressedLength, uncompressed, 3);
      assertEquals(src.length - 15, uncompressedLength);
      final byte[] original = Arrays.copyOfRange(src, 4, 4 + src.length - 15);
      compressed = Arrays.copyOfRange(compressed, 2, compressedLength + 2);
      final byte[] restored = Arrays.copyOfRange(uncompressed, 3, 3 + uncompressedLength);
      assertArrayEquals(original, restored);
      assertArrayEquals(compressed, compressionCodec.compress(src, 4, src.length - 15));
      assertArrayEquals(original, compressionCodec.uncompress(compressed));
    }
  }

  @Test
  public void testCompressJNIFast1Random() {
    testCompress(new LengthLZ4(LZ4JNI.FAST), 256);
  }

  @Test
  public void testCompressJNIFast1() {
    testCompress(new LengthLZ4(LZ4JNI.FAST), 3);
  }

  @Test
  public void testCompressJNIFast2Random() {
    testCompress(new LengthBitsLZ4(LZ4JNI.FAST), 256);
  }

  @Test
  public void testCompressJNIFast2() {
    testCompress(new LengthBitsLZ4(LZ4JNI.FAST), 3);
  }

  @Test
  public void testCompressJavaFast1Random() {
    testCompress(new LengthLZ4(LZ4Java.FAST), 256);
  }

  @Test
  public void testCompressJavaFast1() {
    testCompress(new LengthLZ4(LZ4Java.FAST), 3);
  }

  @Test
  public void testCompressJavaFast2Random() {
    testCompress(new LengthBitsLZ4(LZ4Java.FAST), 256);
  }

  @Test
  public void testCompressJavaFast2() {
    testCompress(new LengthBitsLZ4(LZ4Java.FAST), 3);
  }

  @Test
  public void testCompressJavaUnsafeFast1Random() {
    testCompress(new LengthLZ4(LZ4JavaUnsafe.FAST), 256);
  }

  @Test
  public void testCompressJavaUnsafeFast1() {
    testCompress(new LengthLZ4(LZ4JavaUnsafe.FAST), 3);
  }

  @Test
  public void testCompressJavaUnsafeFast2Random() {
    testCompress(new LengthBitsLZ4(LZ4JavaUnsafe.FAST), 256);
  }

  @Test
  public void testCompressJavaUnsafeFast2() {
    testCompress(new LengthBitsLZ4(LZ4JavaUnsafe.FAST), 3);
  }

  @Test
  public void testCompressJNIHC1Random() {
    testCompress(new LengthLZ4(LZ4JNI.HIGH_COMPRESSION), 256);
  }

  @Test
  public void testCompressJNIHC1() {
    testCompress(new LengthLZ4(LZ4JNI.HIGH_COMPRESSION), 3);
  }

  @Test
  public void testCompressJNIHC2Random() {
    testCompress(new LengthBitsLZ4(LZ4JNI.HIGH_COMPRESSION), 256);
  }

  @Test
  public void testCompressJNIHC2() {
    testCompress(new LengthBitsLZ4(LZ4JNI.HIGH_COMPRESSION), 3);
  }

  public void testUncompressUnknownSizeUnderflow(LZ4 lz4) {
    final byte[] data = new byte[100];
    Random r = new Random();
    for (int i = 0; i < data.length; ++i) {
      data[i] = (byte) r.nextInt(5);
    }
    final int maxCompressedLength = LZ4JNI.FAST.maxCompressedLength(100);
    final byte[] compressed = new byte[maxCompressedLength];
    final int compressedLength = LZ4JNI.FAST.compress(data, 0, data.length, compressed, 0);
    try {
      LZ4JNI.FAST.uncompressUnknownSize(compressed, 0, compressedLength, new byte[data.length - 1], 0);
      assertFalse(true);
    } catch (LZ4Exception e) {
      // ok
    }
  }

  @Test
  public void testUncompressUnknownSizeUnderflowLZ4JNIFast() {
    testUncompressUnknownSizeUnderflow(LZ4JNI.FAST);
  }

  @Test
  public void testUncompressUnknownSizeUnderflowLZ4JavaFast() {
    testUncompressUnknownSizeUnderflow(LZ4Java.FAST);
  }

  @Test
  public void testUncompressUnknownSizeUnderflowLZ4JavaUnsafeFast() {
    testUncompressUnknownSizeUnderflow(LZ4JavaUnsafe.FAST);
  }

  @Test
  public void testCrossJNIFastJavaFast() {
    testCompress(new LengthLZ4(LZ4JNI.FAST), new LengthLZ4(LZ4Java.FAST), 4);
  }

  @Test
  public void testCrossJNIFastJavaUnsafeFast() {
    testCompress(new LengthLZ4(LZ4JNI.FAST), new LengthLZ4(LZ4JavaUnsafe.FAST), 4);
  }

  @Test
  public void testCrossJNIFastJavaFastRandom() {
    testCompress(new LengthLZ4(LZ4JNI.FAST), new LengthLZ4(LZ4Java.FAST), 127);
  }

  @Test
  public void testCrossJNIFastJavaUnsafeFastRandom() {
    testCompress(new LengthLZ4(LZ4JNI.FAST), new LengthLZ4(LZ4JavaUnsafe.FAST), 127);
  }

  @Test
  public void testCrossJavaFastJNIFast() {
    testCompress(new LengthLZ4(LZ4Java.FAST), new LengthLZ4(LZ4JNI.FAST), 4);
  }
  
  @Test
  public void testCrossJavaUnsafeFastJNIFast() {
    testCompress(new LengthLZ4(LZ4JavaUnsafe.FAST), new LengthLZ4(LZ4JNI.FAST), 4);
  }
  
  @Test
  public void testCrossJavaFastJNIFastRandom() {
    testCompress(new LengthLZ4(LZ4Java.FAST), new LengthLZ4(LZ4JNI.FAST), 127);
  }

  @Test
  public void testCrossJavaUnsafeFastJNIFastRandom() {
    testCompress(new LengthLZ4(LZ4JavaUnsafe.FAST), new LengthLZ4(LZ4JNI.FAST), 127);
  }
  
  @Test
  public void testCrossJNIHCJavaFast() {
    testCompress(new LengthLZ4(LZ4JNI.HIGH_COMPRESSION), new LengthLZ4(LZ4Java.FAST), 4);
  }
  
  @Test
  public void testCrossJNIHCJavaUnsafeFast() {
    testCompress(new LengthLZ4(LZ4JNI.HIGH_COMPRESSION), new LengthLZ4(LZ4JavaUnsafe.FAST), 4);
  }
  
  @Test
  public void testCrossJNIHCJavaFastRandom() {
    testCompress(new LengthLZ4(LZ4JNI.HIGH_COMPRESSION), new LengthLZ4(LZ4Java.FAST), 127);
  }

  @Test
  public void testCrossJNIHCJavaUnsafeFastRandom() {
    testCompress(new LengthLZ4(LZ4JNI.HIGH_COMPRESSION), new LengthLZ4(LZ4JavaUnsafe.FAST), 127);
  }
  
  @Test
  public void testCrossJavaFastJNIHC() {
    testCompress(new LengthLZ4(LZ4Java.FAST), new LengthLZ4(LZ4JNI.HIGH_COMPRESSION), 4);
  }
  
  @Test
  public void testCrossJavaUnsafeFastJNIHC() {
    testCompress(new LengthLZ4(LZ4JavaUnsafe.FAST), new LengthLZ4(LZ4JNI.HIGH_COMPRESSION), 4);
  }
  
  @Test
  public void testCrossJavaFastJNIHCRandom() {
    testCompress(new LengthLZ4(LZ4Java.FAST), new LengthLZ4(LZ4JNI.HIGH_COMPRESSION), 127);
  }

  @Test
  public void testCrossJavaUnsafeFastJNIHCRandom() {
    testCompress(new LengthLZ4(LZ4JavaUnsafe.FAST), new LengthLZ4(LZ4JNI.HIGH_COMPRESSION), 127);
  }
  
}

