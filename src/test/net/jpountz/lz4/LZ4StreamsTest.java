package net.jpountz.lz4;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import org.junit.Test;

public class LZ4StreamsTest {

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
      off+= read;
    }
    lz4Is.close();
    assertTrue(off == len);
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
    assertTrue(off == len);
    assertArrayEquals(buf, Arrays.copyOf(restored, len));
  }

  public void testStream(CompressionCodec compressionCodec) throws IOException {
    for (int len : new int[] {0, 1, 10, 1024, 512 * 1024}) {
      for (int bufSize : new int[] {1, 100, 2048, 32 * 1024}) {
        for (int max : new int[] {5, 10, 50, 256}) {
          testStream(compressionCodec, len, bufSize, max);
        }
      }
    }
  }

  @Test
  public void testStream() throws IOException {
    testStream(new LengthLZ4(LZ4Test.COMPRESSORS[0], LZ4Test.UNCOMPRESSORS[0]));
    testStream(new LengthBitsLZ4(LZ4Test.COMPRESSORS[0], LZ4Test.UNCOMPRESSORS2[0]));
  }

}
