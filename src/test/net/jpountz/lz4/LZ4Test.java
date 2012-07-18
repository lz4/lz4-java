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

  public void testEmpty(LZ4Compression compressionMode) {
    final byte[] data = new byte[0];
    final int maxCompressedLength = compressionMode.maxCompressedLength(0);
    final byte[] compressed = new byte[maxCompressedLength];
    final int compressedLength = compressionMode.compress(data, 0, 0, compressed, 0);
    assertTrue(compressedLength > 0);
    assertTrue(compressedLength <= maxCompressedLength);
    assertEquals(0, compressionMode.uncompress(compressed, 0, compressedLength, new byte[3], 1));
  }

  @Test
  public void testEmptyNormal() {
    testEmpty(LZ4Compression.NORMAL);
  }

  @Test
  public void testEmptyHC() {
    testEmpty(LZ4Compression.HIGH_COMPRESSION);
  }

  @Test
  public void testEmptyLengthNormal() {
    testEmpty(LZ4Compression.LENGTH_NORMAL);
  }

  @Test
  public void testEmptyLengthHC() {
    testEmpty(LZ4Compression.LENGTH_HIGH_COMPRESSION);
  }

  public void testCompress(LZ4Compression compressionMode, int max) {
    final Random random = new Random(0);
    for (int size : new int[] {19, 20, 45, 255, 4000, 65000, 300000}) {
      final byte[] src = new byte[size];
      for (int i = 0; i < src.length; ++i) {
        // low values of max are more likely to produce repeated patterns...
        src[i] = (byte) random.nextInt(max);
      }
      final int maxCompressedLength = compressionMode.maxCompressedLength(src.length - 15);
      byte[] compressed = new byte[maxCompressedLength + 10];
      final int compressedLength = compressionMode.compress(src, 4, src.length - 15, compressed, 2);
      assertTrue(compressedLength <= maxCompressedLength);
      byte[] uncompressed = new byte[src.length];
      final int uncompressedLength = compressionMode.uncompress(compressed, 2, compressedLength, uncompressed, 3);
      assertEquals(uncompressedLength, src.length - 15);
      final byte[] original = Arrays.copyOfRange(src, 4, 4 + src.length - 15);
      compressed = Arrays.copyOfRange(compressed, 2, compressedLength + 2);
      final byte[] restored = Arrays.copyOfRange(uncompressed, 3, 3 + uncompressedLength);
      assertArrayEquals(original, restored);
      assertArrayEquals(compressed, compressionMode.compress(src, 4, src.length - 15));
      assertArrayEquals(original, compressionMode.uncompress(compressed));
    }
  }

  @Test
  public void testCompressNormalRandom() {
    testCompress(LZ4Compression.NORMAL, 256);
  }

  @Test
  public void testCompressNormal() {
    testCompress(LZ4Compression.NORMAL, 3);
  }

  @Test
  public void testCompressHighCompressionRandom() {
    testCompress(LZ4Compression.HIGH_COMPRESSION, 256);
  }

  @Test
  public void testCompressHighCompression() {
    testCompress(LZ4Compression.HIGH_COMPRESSION, 3);
  }

  @Test
  public void testCompressLengthNormalRandom() {
    testCompress(LZ4Compression.LENGTH_NORMAL, 256);
  }

  @Test
  public void testCompressLengthNormal() {
    testCompress(LZ4Compression.LENGTH_NORMAL, 3);
  }

  @Test
  public void testCompressLengthHighCompressionRandom() {
    testCompress(LZ4Compression.LENGTH_HIGH_COMPRESSION, 256);
  }

  @Test
  public void testCompressLengthHighCompression() {
    testCompress(LZ4Compression.LENGTH_HIGH_COMPRESSION, 3);
  }

  @Test
  public void testUncompressUnknownSizeUnderflow() {
    final byte[] data = new byte[100];
    Random r = new Random();
    for (int i = 0; i < data.length; ++i) {
      data[i] = (byte) r.nextInt(5);
    }
    final int maxCompressedLength = LZ4.maxCompressedLength(100);
    final byte[] compressed = new byte[maxCompressedLength];
    final int compressedLength = LZ4.compress(data, 0, data.length, compressed, 0);
    try {
      LZ4.uncompressUnknownSize(compressed, 0, compressedLength, new byte[data.length + 100], 0, data.length - 1);
      assertFalse(true);
    } catch (LZ4Exception e) {
      // ok
    }
    try {
      LZ4.uncompressUnknownSize(compressed, 0, compressedLength, new byte[data.length - 1], 0, data.length - 1);
      assertFalse(true);
    } catch (LZ4Exception e) {
      // ok
    }
  }

  public static void main(String[] args) {
    
  }
}

