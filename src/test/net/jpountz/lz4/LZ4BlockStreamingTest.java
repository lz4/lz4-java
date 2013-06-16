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
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.zip.Adler32;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import net.jpountz.xxhash.XXHashFactory;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.carrotsearch.randomizedtesting.RandomizedRunner;
import com.carrotsearch.randomizedtesting.annotations.Repeat;

@RunWith(RandomizedRunner.class)
public class LZ4BlockStreamingTest extends AbstractLZ4Test {

  // An input stream that might read less data than it is able to
  class MockInputStream extends FilterInputStream {

    MockInputStream(InputStream in) {
      super(in);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      return super.read(b, off, randomIntBetween(0, len));
    }

    @Override
    public long skip(long n) throws IOException {
      return super.skip(randomInt((int) Math.min(n, Integer.MAX_VALUE)));
    }

  }

  // an output stream that delays the actual writes
  class MockOutputStream extends FilterOutputStream {

    private final byte[] buffer;
    private int delayedBytes;

    MockOutputStream(OutputStream out) {
      super(out);
      buffer = new byte[randomIntBetween(10, 1000)];
      delayedBytes = 0;
    }

    private void flushIfNecessary() throws IOException {
      if (delayedBytes == buffer.length) {
        flushPendingData();
      }
    }

    private void flushPendingData() throws IOException {
      out.write(buffer, 0, delayedBytes);
      delayedBytes = 0;
    }

    @Override
    public void write(int b) throws IOException {
      if (rarely()) {
        flushPendingData();
      } else {
        flushIfNecessary();
      }
      buffer[delayedBytes++] = (byte) b;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      if (rarely()) {
        flushPendingData();
      }
      if (len + delayedBytes > buffer.length) {
        flushPendingData();
        delayedBytes = randomInt(Math.min(len, buffer.length));
        out.write(b, off, len - delayedBytes);
        System.arraycopy(b, off + len - delayedBytes, buffer, 0, delayedBytes);
      } else {
        System.arraycopy(b, off, buffer, delayedBytes, len);
        delayedBytes += len;
      }
    }

    @Override
    public void write(byte[] b) throws IOException {
      write(b, 0, b.length);
    }

    @Override
    public void flush() throws IOException {
      // no-op
    }

    @Override
    public void close() throws IOException {
      flushPendingData();
      out.close();
    }

  }

  private InputStream open(byte[] data) {
    return new MockInputStream(new ByteArrayInputStream(data));
  }

  private OutputStream wrap(OutputStream other) {
    return new MockOutputStream(other);
  }

  @Test
  @Repeat(iterations=5)
  public void testRoundtripGeo() throws IOException {
    testRoundTrip("/calgary/geo");
  }

  @Test
  @Repeat(iterations=5)
  public void testRoundtripBook1() throws IOException {
    testRoundTrip("/calgary/book1");
  }

  @Test
  @Repeat(iterations=5)
  public void testRoundtripPic() throws IOException {
    testRoundTrip("/calgary/pic");
  }

  public void testRoundTrip(String resource) throws IOException {
    testRoundTrip(readResource(resource));
  }

  public void testRoundTrip(byte[] data) throws IOException {
    final ByteArrayOutputStream compressed = new ByteArrayOutputStream();
    final int blockSize;
    switch (randomInt(2)) {
    case 0:
      blockSize = LZ4BlockOutputStream.MIN_BLOCK_SIZE;
      break;
    case 1:
      blockSize = LZ4BlockOutputStream.MAX_BLOCK_SIZE;
      break;
    default:
      blockSize = randomIntBetween(LZ4BlockOutputStream.MIN_BLOCK_SIZE, LZ4BlockOutputStream.MAX_BLOCK_SIZE);
      break;
    }
    final LZ4Compressor compressor = randomBoolean()
        ? LZ4Factory.fastestInstance().fastCompressor()
        : LZ4Factory.fastestInstance().highCompressor();
    final Checksum checksum;
    switch (randomInt(2)) {
    case 0:
      checksum = new Adler32();
      break;
    case 1:
      checksum = new CRC32();
      break;
    default:
      checksum = XXHashFactory.fastestInstance().newStreamingHash32(randomInt()).asChecksum();
      break;
    }
    final boolean syncFlush = randomBoolean();
    final LZ4BlockOutputStream os = new LZ4BlockOutputStream(wrap(compressed), blockSize, compressor, checksum, syncFlush);
    final int half = data.length / 2;
    switch (randomInt(2)) {
    case 0:
      os.write(data, 0, half);
      for (int i = half; i < data.length; ++i) {
        os.write(data[i]);
      }
      break;
    case 1:
      for (int i = 0; i < half; ++i) {
        os.write(data[i]);
      }
      os.write(data, half, data.length - half);
      break;
    case 2:
      os.write(data, 0, data.length);
      break;
    }
    os.close();

    final LZ4FastDecompressor decompressor = LZ4Factory.fastestInstance().fastDecompressor();
    InputStream is = new LZ4BlockInputStream(open(compressed.toByteArray()), decompressor, checksum);
    assertFalse(is.markSupported());
    try {
      is.mark(1);
      is.reset();
      assertFalse(true);
    } catch (IOException e) {
      // OK
    }
    byte[] restored = new byte[data.length + 1000];
    int read = 0;
    while (true) {
      if (randomFloat() < 0.01f) {
        final int r = is.read(restored, read, restored.length - read);
        if (r == -1) {
          break;
        } else {
          read += r;
        }
      } else {
        final int b = is.read();
        if (b == -1) {
          break;
        } else {
          restored[read++] = (byte) b;
        }
      }
    }
    is.close();
    assertEquals(data.length, read);
    assertArrayEquals(data, Arrays.copyOf(restored, read));

    // test skip
    final int offset = data.length <= 1 ? 0 : randomInt(data.length - 1);
    final int length = randomInt(data.length - offset);
    is = new LZ4BlockInputStream(open(compressed.toByteArray()), decompressor, checksum);
    restored = new byte[length + 1000];
    read = 0;
    while (read < offset) {
      final long skipped = is.skip(offset - read);
      assertTrue(skipped >= 0);
      read += skipped;
    }
    read = 0;
    while (read < length) {
      final int r = is.read(restored, read, length - read);
      assertTrue(r >= 0);
      read += r;
    }
    is.close();
    assertArrayEquals(Arrays.copyOfRange(data, offset, offset + length), Arrays.copyOfRange(restored, 0, length));
  }

  @Test
  @Repeat(iterations=20)
  public void testRoundtripRandom() throws IOException {
    final int size = randomFloat() < 0.1f ? randomInt(5) : randomInt(1 << 20);
    final byte[] data = randomArray(size, randomBoolean() ? randomIntBetween(1, 5) : randomIntBetween(6, 100));
    testRoundTrip(data);
  }

  @Test
  public void testRoundtripEmpty() throws IOException {
    testRoundTrip(new byte[0]);
  }

  @Test
  public void testDoubleClose() throws IOException {
    final byte[] testBytes = "Testing!".getBytes(Charset.forName("UTF-8"));

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    LZ4BlockOutputStream out = new LZ4BlockOutputStream(bytes);

    out.write(testBytes);

    out.close();
    out.close();

    LZ4BlockInputStream in = new LZ4BlockInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    byte[] actual = new byte[testBytes.length];
    in.read(actual);

    assertArrayEquals(testBytes, actual);

    in.close();
    in.close();
  }
}
