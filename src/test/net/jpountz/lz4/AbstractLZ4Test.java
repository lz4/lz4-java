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
import java.nio.ByteOrder;
import java.util.Arrays;

import com.carrotsearch.randomizedtesting.RandomizedTest;
import com.carrotsearch.randomizedtesting.generators.RandomPicks;

public abstract class AbstractLZ4Test extends RandomizedTest {

  public interface Tester<T> {

    T allocate(int length);
    T copyOf(byte[] array);
    byte[] copyOf(T data, int off, int len);
    int compress(LZ4Compressor compressor, T src, int srcOff, int srcLen, T dest, int destOff, int maxDestLen);
    int decompress(LZ4FastDecompressor decompressor, T src, int srcOff, T dest, int destOff, int destLen);
    int decompress(LZ4SafeDecompressor decompressor, T src, int srcOff, int srcLen, T dest, int destOff, int maxDestLen);
    void fill(T instance, byte b);

    public static final Tester<byte[]> BYTE_ARRAY = new Tester<byte[]>() {

      @Override
      public byte[] allocate(int length) {
        return new byte[length];
      }

      @Override
      public byte[] copyOf(byte[] array) {
        return Arrays.copyOf(array, array.length);
      }

      @Override
      public byte[] copyOf(byte[] data, int off, int len) {
        return Arrays.copyOfRange(data, off, off + len);
      }

      @Override
      public int compress(LZ4Compressor compressor, byte[] src, int srcOff,
          int srcLen, byte[] dest, int destOff, int maxDestLen) {
        return compressor.compress(src, srcOff, srcLen, dest, destOff, maxDestLen);
      }
      
      @Override
      public int decompress(LZ4FastDecompressor decompressor,
          byte[] src, int srcOff, byte[] dest, int destOff, int destLen) {
        return decompressor.decompress(src, srcOff, dest, destOff, destLen);
      }

      @Override
      public int decompress(LZ4SafeDecompressor decompressor,
          byte[] src, int srcOff, int srcLen, byte[] dest, int destOff, int maxDestLen) {
        return decompressor.decompress(src, srcOff, srcLen, dest, destOff, maxDestLen);
      }

      @Override
      public void fill(byte[] instance, byte b) {
        Arrays.fill(instance, b);
      }
    };

    public static final Tester<ByteBuffer> BYTE_BUFFER = new Tester<ByteBuffer>() {

      @Override
      public ByteBuffer allocate(int length) {
        ByteBuffer bb;
        int slice = randomInt(5);
        if (randomBoolean()) {
          bb = ByteBuffer.allocate(length + slice);
        } else {
          bb = ByteBuffer.allocateDirect(length + slice);
        }
        bb.position(slice);
        bb = bb.slice();
        if (randomBoolean()) {
          bb.order(ByteOrder.LITTLE_ENDIAN);
        } else {
          bb.order(ByteOrder.BIG_ENDIAN);
        }
        return bb;
      }

      @Override
      public ByteBuffer copyOf(byte[] array) {
        ByteBuffer bb = allocate(array.length).put(array);
        if (randomBoolean()) {
          bb = bb.asReadOnlyBuffer();
        }
        bb.position(0);
        return bb;
      }

      @Override
      public byte[] copyOf(ByteBuffer data, int off, int len) {
        byte[] copy = new byte[len];
        data.position(off);
        data.get(copy);
        return copy;
      }

      @Override
      public int compress(LZ4Compressor compressor, ByteBuffer src, int srcOff,
          int srcLen, ByteBuffer dest, int destOff, int maxDestLen) {
        return compressor.compress(src, srcOff, srcLen, dest, destOff, maxDestLen);
      }

      @Override
      public int decompress(LZ4FastDecompressor decompressor, ByteBuffer src,
          int srcOff, ByteBuffer dest, int destOff, int destLen) {
        return decompressor.decompress(src, srcOff, dest, destOff, destLen);
      }

      @Override
      public int decompress(LZ4SafeDecompressor decompressor, ByteBuffer src,
          int srcOff, int srcLen, ByteBuffer dest, int destOff, int maxDestLen) {
        return decompressor.decompress(src, srcOff, srcLen, dest, destOff, maxDestLen);
      }

      @Override
      public void fill(ByteBuffer instance, byte b) {
        for (int i = 0; i < instance.capacity(); ++i) {
          instance.put(i, b);
        }
      }
      
    };

  }

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
