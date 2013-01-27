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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.carrotsearch.randomizedtesting.RandomizedRunner;
import com.carrotsearch.randomizedtesting.annotations.Repeat;

@RunWith(RandomizedRunner.class)
public class LZ4BlockStreamingTest extends AbstractLZ4Test {

  @Test
  @Repeat(iterations=20)
  public void test() throws IOException {
    final int size = randomFloat() < 0.1f ? randomInt(5) : randomInt(1 << 20);
    final int blockSize = randomIntBetween(64, 1 << 17);
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final LZ4BlockOutputStream os = new LZ4BlockOutputStream(baos, blockSize);
    final byte[] data = randomArray(size, randomBoolean() ? randomIntBetween(1, 5) : randomIntBetween(6, 100));
    int written = 0;
    while (written < data.length) {
      final int w;
      if (randomBoolean()) {
        w = randomInt(data.length - written);
        os.write(data, written, w);
        os.flush();
      } else {
        os.write(data[written]);
        if (randomBoolean()) {
          os.flush();
        }
        w = 1;
      }
      written += w;
    }
    os.close();
    try {
      os.write(3);
      assertTrue(false);
    } catch (IllegalStateException e) {
      // OK
    }
    final ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    final LZ4BlockInputStream is = new LZ4BlockInputStream(bais);
    final byte[] restored = new byte[data.length];
    int read = 0;
    while (read < data.length) {
      final int r;
      if (randomBoolean()) {
        r = is.read(restored, read, randomInt(data.length - read));
        assertTrue(r >= 0);
      } else {
        final int b = is.read();
        assertTrue(b >= 0);
        restored[read] = (byte) b;
        r = 1;
      }
      read += r;
    }
    is.close();
    assertEquals(-1, is.read());
    assertEquals(-1, is.read(restored));
    assertArrayEquals(data, restored);
  }

}
