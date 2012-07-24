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

  public void testStream(LZ4 lz4, int len, int bufSize, int max) throws IOException {
    byte[] buf = new byte[len];
    Random r = new Random(0);
    for (int i = 0; i < len; ++i) {
      buf[i] = (byte) r.nextInt(max);
    }

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    LZ4OutputStream lz4Os = new LZ4OutputStream(out, lz4, bufSize);

    int off = 0;
    while (off < len) {
      final int l = r.nextInt(1 - off + len);
      lz4Os.write(buf, off, l);
      off += l;
    }
    lz4Os.close();

    // test full read
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
    LZ4InputStream lz4Is = new LZ4InputStream(in, lz4);
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
    lz4Is = new LZ4InputStream(in, lz4);
    restored = new byte[len + 100];
    off = 0;
    while (off < len) {
      final int toRead = Math.min(r.nextInt(5), restored.length - off);
      final int read = lz4Is.read(restored, off, toRead);
      assertTrue(read >= 0);
      off+= read;
    }
    lz4Is.close();
    assertTrue(off == len);
    assertArrayEquals(buf, Arrays.copyOf(restored, len));
  }

  public void testStream(LZ4 lz4) throws IOException {
    for (int len : new int[] {/*0,*/ 1, 10, 1024, 65 * 1024}) {
      for (int bufSize : new int[] {/*1,*/ 100, 4096}) {
        for (int max : new int[] {5, 256}) {
          testStream(lz4, len, bufSize, max);
        }
      }
    }
  }

  @Test
  public void testLZ4JNIFast() throws IOException {
    testStream(LZ4JNI.FAST);
  }

  @Test
  public void testLZ4JNIHC() throws IOException {
    testStream(LZ4JNI.HIGH_COMPRESSION);
  }
  @Test
  public void testLZ4JavaFast() throws IOException {
    testStream(LZ4Java.FAST);
  }
  @Test
  public void testLZ4JavaUnsafeFast() throws IOException {
    testStream(LZ4JavaUnsafe.FAST);
  }
}
