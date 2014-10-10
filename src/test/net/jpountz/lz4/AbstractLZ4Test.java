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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import com.carrotsearch.randomizedtesting.RandomizedTest;

public abstract class AbstractLZ4Test extends RandomizedTest {

  protected class RandomBytes {
    private final byte[] bytes;
    RandomBytes(int n) {
      assert n > 0 && n <= 256;
      bytes = new byte[n];
      for (int i = 0; i < n; ++i) {
        bytes[i] = (byte) randomInt(255);
      }
    }
    byte next() {
      final int i = randomInt(bytes.length - 1);
      return bytes[i];
    }
  }

  protected static byte[] readResource(String resource) throws IOException {
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

  protected byte[] randomArray(int len, int n) {
    byte[] result = new byte[len];
    RandomBytes randomBytes = new RandomBytes(n);
    for (int i = 0; i < result.length; ++i) {
      result[i] = randomBytes.next();
    }
    return result;
  }

  protected ByteBuffer copyOf(byte[] bytes, int offset, int len) {
    ByteBuffer buffer;
    if (randomBoolean()) {
      buffer = ByteBuffer.allocate(bytes.length);
    } else {
      buffer = ByteBuffer.allocateDirect(bytes.length);
    }
    buffer.put(bytes);
    buffer.position(offset);
    buffer.limit(offset + len);
    if (randomBoolean()) {
      buffer = buffer.asReadOnlyBuffer();
    }
    return buffer;
  }

}
