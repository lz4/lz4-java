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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.carrotsearch.randomizedtesting.RandomizedRunner;
import com.carrotsearch.randomizedtesting.RandomizedTest;
import com.carrotsearch.randomizedtesting.annotations.Repeat;

@RunWith(RandomizedRunner.class)
public class LZ4StreamsTest extends RandomizedTest {

  public void testStream(CompressionCodec compressionCodec, int len, int bufSize, int max) throws IOException {
    byte[] buf = new byte[len];
    Random r = new Random(0);
    for (int i = 0; i < len; ++i) {
      buf[i] = (byte) r.nextInt(max);
    }

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    LZ4ChunksOutputStream lz4Os = new LZ4ChunksOutputStream(out, compressionCodec, bufSize);

    int off = 0;
    while (off < len) {
      final int l = r.nextInt(1 - off + len);
      lz4Os.write(buf, off, l);
      off += l;
    }
    lz4Os.close();

    // test full read
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
    LZ4ChunksInputStream lz4Is = new LZ4ChunksInputStream(in, compressionCodec);
    byte[] restored = new byte[len + 100];
    off = 0;
    while (off < len) {
      final int read = lz4Is.read(restored, off, restored.length - off);
      assertTrue(read >= 0);
      off += read;
    }
    lz4Is.close();
    assertEquals(len, off);
    assertArrayEquals(buf, Arrays.copyOf(restored, len));

    // test partial reads
    in = new ByteArrayInputStream(out.toByteArray());
    lz4Is = new LZ4ChunksInputStream(in, compressionCodec);
    restored = new byte[len + 100];
    off = 0;
    while (off < len) {
      final int toRead = Math.min(r.nextInt(64), restored.length - off);
      final int read = lz4Is.read(restored, off, toRead);
      assertTrue(read >= 0);
      off+= read;
    }
    lz4Is.close();
    assertEquals(len, off);
    assertArrayEquals(buf, Arrays.copyOf(restored, len));
  }

  public void testStream(CompressionCodec compressionCodec) throws IOException {
    final int max = randomIntBetween(1, 256);
    final int bufSize = randomBoolean() ? randomIntBetween(1, 20) : randomIntBetween(100, 100 * 1024);
    final int len = randomBoolean() ? randomIntBetween(0, 10) : randomIntBetween(0, 1024 * 1024);
    testStream(compressionCodec, len, bufSize, max);
  }

  @Test
  @Repeat(iterations = 8)
  public void testStream() throws IOException {
    testStream(new LengthLZ4(COMPRESSORS[0], UNCOMPRESSORS[0]));
    testStream(new LengthBitsLZ4(COMPRESSORS[0], UNCOMPRESSORS2[0]));
  }

}
