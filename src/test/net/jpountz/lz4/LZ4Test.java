package net.jpountz.lz4;

/*
 * Copyright 2020 Adrien Grand and the lz4-java contributors.
 *
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

import static net.jpountz.lz4.Instances.COMPRESSORS;
import static net.jpountz.lz4.Instances.FAST_DECOMPRESSORS;
import static net.jpountz.lz4.Instances.SAFE_DECOMPRESSORS;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.io.File;
import java.io.FilenameFilter;

import org.junit.Test;
import static org.junit.Assert.*;

import com.carrotsearch.randomizedtesting.annotations.Repeat;
import com.carrotsearch.randomizedtesting.annotations.Seed;
import com.carrotsearch.randomizedtesting.annotations.Seeds;

public class LZ4Test extends AbstractLZ4Test {

  @Test
  public void testLockFileOfTemporaryNativeLibrary() {
    // Load the native library
    LZ4JNI.LZ4_compressBound(100);
    String tempFolder = new File(System.getProperty("java.io.tmpdir")).getAbsolutePath();
    File tempDir = new File(new File(System.getProperty("java.io.tmpdir")).getAbsolutePath());
    if (!System.getProperty("os.name").contains("Windows")) {
	// A temporary library must be accompanied by a lock file.
	// On Windows, JVM does not remove a temporary library on exit.
	// This means on Windows, there might be a temporary library
	// that is not accompanied by a lock file when there was
	// a Java process using lz4-java that was running concurrently
	// to this test process.
	File[] tempLibFiles = tempDir.listFiles(new FilenameFilter() {
		public boolean accept(File dir, String name) {
		    return name.startsWith("liblz4-java-") && !name.endsWith(".lck");
		}
	    });
	if (tempLibFiles != null) {
	    for (File tempLibFile : tempLibFiles) {
		File lckFile = new File(tempLibFile.getAbsolutePath() + ".lck");
		assertTrue(tempLibFile.getAbsolutePath(), lckFile.exists());
	    }
	}
    }
    // A lock file must be accompanied by a temporary library.
    File[] tempLockFiles = tempDir.listFiles(new FilenameFilter() {
	public boolean accept(File dir, String name) {
	  return name.startsWith("liblz4-java-") && name.endsWith(".lck");
	}
      });
    if (tempLockFiles != null) {
      for (File tempLockFile : tempLockFiles) {
	String tempLockFilePath = tempLockFile.getAbsolutePath();
	File libFile = new File(tempLockFilePath.substring(0, tempLockFilePath.length() - 4));
	assertTrue(tempLockFilePath, libFile.exists());
      }
    }
  }

  @Test
  @Repeat(iterations=50)
  public void testMaxCompressedLength() {
    final int len = randomBoolean() ? randomInt(16) : randomInt(1 << 30);
    for (LZ4Compressor compressor : COMPRESSORS) {
      assertEquals(LZ4JNI.LZ4_compressBound(len), compressor.maxCompressedLength(len));
    }
  }

  private static byte[] getCompressedWorstCase(byte[] decompressed) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    int len = decompressed.length;
    if (len >= LZ4Constants.RUN_MASK) {
      baos.write(LZ4Constants.RUN_MASK << LZ4Constants.ML_BITS);
      len -= LZ4Constants.RUN_MASK;
      while (len >= 255) {
        baos.write(255);
        len -= 255;
      }
      baos.write(len);
    } else {
      baos.write(len << LZ4Constants.ML_BITS);
    }
    try {
      baos.write(decompressed);
    } catch (IOException e) {
      throw new AssertionError();
    }
    return baos.toByteArray();
  }

  @Test
  public void testEmpty() {
    testRoundTrip(new byte[0]);
  }

  public void testUncompressWorstCase(LZ4FastDecompressor decompressor) {
    final int len = randomInt(100 * 1024);
    final int max = randomIntBetween(1, 255);
    byte[] decompressed = randomArray(len, max);
    byte[] compressed = getCompressedWorstCase(decompressed);
    byte[] restored = new byte[decompressed.length];
    int cpLen = decompressor.decompress(compressed, 0, restored, 0, decompressed.length);
    assertEquals(compressed.length, cpLen);
    assertArrayEquals(decompressed, restored);
  }

  @Test
  public void testUncompressWorstCase() {
    for (LZ4FastDecompressor decompressor : FAST_DECOMPRESSORS) {
      testUncompressWorstCase(decompressor);
    }
  }

  public void testUncompressWorstCase(LZ4SafeDecompressor decompressor) {
    final int len = randomInt(100 * 1024);
    final int max = randomIntBetween(1, 256);
    byte[] decompressed = randomArray(len, max);
    byte[] compressed = getCompressedWorstCase(decompressed);
    byte[] restored = new byte[decompressed.length];
    int uncpLen = decompressor.decompress(compressed, 0, compressed.length, restored, 0);
    assertEquals(decompressed.length, uncpLen);
    assertArrayEquals(decompressed, restored);
  }

  @Seeds({
    @Seed("69CCC65267F3C588"),  // Seed that generates len < 16
    @Seed()
  })
  @Test
  public void testUncompressSafeWorstCase() {
    for (LZ4SafeDecompressor decompressor : SAFE_DECOMPRESSORS) {
      testUncompressWorstCase(decompressor);
    }
  }

  public void testRoundTrip(byte[] data, int off, int len,
      LZ4Compressor compressor,
      LZ4FastDecompressor decompressor,
      LZ4SafeDecompressor decompressor2) {
    for (Tester<?> tester : Arrays.asList(Tester.BYTE_ARRAY, Tester.BYTE_BUFFER, Tester.BYTE_ARRAY_WITH_LENGTH, Tester.BYTE_BUFFER_WITH_LENGTH)) {
      testRoundTrip(tester, data, off, len, compressor, decompressor, decompressor2);
    }
  }

  public <T> void testRoundTrip(
      Tester<T> tester,
      byte[] data, int off, int len,
      LZ4Compressor compressor,
      LZ4FastDecompressor decompressor,
      LZ4SafeDecompressor decompressor2) {
    final int maxCompressedLength = tester.maxCompressedLength(len);
    // "maxCompressedLength + 1" for the over-estimated compressed length test below
    final T compressed = tester.allocate(maxCompressedLength + 1);
    final int compressedLen = tester.compress(compressor,
        tester.copyOf(data), off, len,
        compressed, 0, maxCompressedLength);

    // test decompression
    final T restored = tester.allocate(len);
    assertEquals(compressedLen, tester.decompress(decompressor, compressed, 0, restored, 0, len));
    assertArrayEquals(Arrays.copyOfRange(data, off, off + len), tester.copyOf(restored, 0, len));

    // make sure it fails if the compression dest is not large enough
    tester.fill(restored, randomByte());
    final T compressed2 = tester.allocate(compressedLen-1);
    try {
      final int compressedLen2 = tester.compress(compressor,
          tester.copyOf(data), off, len,
          compressed2, 0, compressedLen - 1);
      // Compression can succeed even with the smaller dest
      // because the compressor is allowed to return different compression results
      // even when it is invoked with the same input data.
      // In this case, just make sure the compressed data can be successfully decompressed.
      assertEquals(compressedLen2, tester.decompress(decompressor, compressed2, 0, restored, 0, len));
      assertArrayEquals(Arrays.copyOfRange(data, off, off + len), tester.copyOf(restored, 0, len));
    } catch (LZ4Exception e) {
      // OK
    }

    if (tester != Tester.BYTE_ARRAY_WITH_LENGTH && tester != Tester.BYTE_BUFFER_WITH_LENGTH) {
      if (len > 0) {
	// decompression dest is too small
	try {
	  tester.decompress(decompressor, compressed, 0, restored, 0, len - 1);
	  fail();
	} catch (LZ4Exception e) {
	  // OK
	}
      }

      // decompression dest is too large
      final T restored2 = tester.allocate(len+1);
      try {
	final int cpLen = tester.decompress(decompressor, compressed, 0, restored2, 0, len + 1);
	fail("compressedLen=" + cpLen);
      } catch (LZ4Exception e) {
	// OK
      }

      // try decompression when only the size of the compressed buffer is known
      if (len > 0) {
	tester.fill(restored, randomByte());
	assertEquals(len, tester.decompress(decompressor2, compressed, 0, compressedLen, restored, 0, len));
	assertArrayEquals(Arrays.copyOfRange(data, off, off + len), tester.copyOf(restored, 0, len));
	tester.fill(restored, randomByte());
      } else {
	assertEquals(0, tester.decompress(decompressor2, compressed, 0, compressedLen, tester.allocate(1), 0, 1));
      }

      // over-estimated compressed length
      try {
	final int decompressedLen = tester.decompress(decompressor2, compressed, 0, compressedLen + 1, tester.allocate(len + 100), 0, len + 100);
	fail("decompressedLen=" + decompressedLen);
      } catch (LZ4Exception e) {
	// OK
      }

      // under-estimated compressed length
      try {
	final int decompressedLen = tester.decompress(decompressor2, compressed, 0, compressedLen - 1, tester.allocate(len + 100), 0, len + 100);
	if (!(decompressor2 instanceof LZ4JNISafeDecompressor)) {
	  fail("decompressedLen=" + decompressedLen);
	}
      } catch (LZ4Exception e) {
	// OK
      }
    }
  }

  public void testRoundTrip(byte[] data, int off, int len, LZ4Factory compressorFactory, LZ4Factory decompressorFactory) {
    for (LZ4Compressor compressor : Arrays.asList(
        compressorFactory.fastCompressor(), compressorFactory.highCompressor())) {
      testRoundTrip(data, off, len, compressor, decompressorFactory.fastDecompressor(), decompressorFactory.safeDecompressor());
    }
  }

  public void testRoundTrip(byte[] data, int off, int len) {
    for (LZ4Factory compressorFactory : Arrays.asList(
        LZ4Factory.nativeInstance(),
        LZ4Factory.unsafeInstance(),
        LZ4Factory.safeInstance())) {
      for (LZ4Factory decompressorFactory : Arrays.asList(
          LZ4Factory.nativeInstance(),
          LZ4Factory.unsafeInstance(),
          LZ4Factory.safeInstance())) {
	testRoundTrip(data, off, len, compressorFactory, decompressorFactory);
      }
    }
  }

  public void testRoundTrip(byte[] data) {
    testRoundTrip(data, 0, data.length);
  }

  public void testRoundTrip(String resource) throws IOException {
    final byte[] data = readResource(resource);
    testRoundTrip(data);
  }

  @Test
  public void testRoundtripGeo() throws IOException {
    testRoundTrip("/calgary/geo");
  }

  @Test
  public void testRoundtripBook1() throws IOException {
    testRoundTrip("/calgary/book1");
  }

  @Test
  public void testRoundtripPic() throws IOException {
    testRoundTrip("/calgary/pic");
  }

  @Test
  public void testNullMatchDec() {
    // 1 literal, 4 matchs with matchDec=0, 8 literals
    final byte[] invalid = new byte[] { 16, 42, 0, 0, (byte) 128, 42, 42, 42, 42, 42, 42, 42, 42 };
    // decompression should neither throw an exception nor loop indefinitely
    for (LZ4FastDecompressor decompressor : FAST_DECOMPRESSORS) {
      decompressor.decompress(invalid, 0, new byte[13], 0, 13);
    }
    for (LZ4SafeDecompressor decompressor : SAFE_DECOMPRESSORS) {
      decompressor.decompress(invalid, 0, invalid.length, new byte[20], 0);
    }
  }

  @Test
  public void testEndsWithMatch() {
    // 6 literals, 4 matchs
    final byte[] invalid = new byte[] { 96, 42, 43, 44, 45, 46, 47, 5, 0 };
    final int decompressedLength = 10;

    for (LZ4FastDecompressor decompressor : FAST_DECOMPRESSORS) {
      try {
        // it is invalid to end with a match, should be at least 5 literals
        decompressor.decompress(invalid, 0, new byte[decompressedLength], 0, decompressedLength);
        assertTrue(decompressor.toString(), false);
      } catch (LZ4Exception e) {
        // OK
      }
    }

    for (LZ4SafeDecompressor decompressor : SAFE_DECOMPRESSORS) {
      try {
        // it is invalid to end with a match, should be at least 5 literals
        decompressor.decompress(invalid, 0, invalid.length, new byte[20], 0);
        assertTrue(false);
      } catch (LZ4Exception e) {
        // OK
      }
    }
  }

  @Test
  public void testEndsWithLessThan5Literals() {
    // 6 literals, 4 matchs
    final byte[] invalidBase = new byte[] { 96, 42, 43, 44, 45, 46, 47, 5, 0 };

    for (int i = 1; i < 5; ++i) {
      final byte[] invalid = Arrays.copyOf(invalidBase, invalidBase.length + 1 + i);
      invalid[invalidBase.length] = (byte) (i << 4); // i literals at the end

      for (LZ4FastDecompressor decompressor : FAST_DECOMPRESSORS) {
        try {
          // it is invalid to end with a match, should be at least 5 literals
          decompressor.decompress(invalid, 0, new byte[20], 0, 20);
          assertTrue(decompressor.toString(), false);
        } catch (LZ4Exception e) {
          // OK
        }
      }

      for (LZ4SafeDecompressor decompressor : SAFE_DECOMPRESSORS) {
        try {
          // it is invalid to end with a match, should be at least 5 literals
          decompressor.decompress(invalid, 0, invalid.length, new byte[20], 0);
          assertTrue(false);
        } catch (LZ4Exception e) {
          // OK
        }
      }
    }
  }

  @Test
  public void testWriteToReadOnlyBuffer() {
    for (LZ4Compressor compressor : COMPRESSORS) {
      ByteBuffer in = Tester.BYTE_BUFFER.copyOf(new byte[] {2, 3});
      ByteBuffer out = Tester.BYTE_BUFFER.allocate(100).asReadOnlyBuffer();
      try {
        compressor.compress(in, out);
        fail();
      } catch (ReadOnlyBufferException e) {
        // ok
      }
    }
    for (LZ4SafeDecompressor decompressor : SAFE_DECOMPRESSORS) {
      ByteBuffer in = Tester.BYTE_BUFFER.copyOf(COMPRESSORS[0].compress(new byte[] {2, 3}));
      ByteBuffer out = Tester.BYTE_BUFFER.allocate(100).asReadOnlyBuffer();
      try {
        decompressor.decompress(in, out);
        fail();
      } catch (ReadOnlyBufferException e) {
        // ok
      }
    }
    for (LZ4FastDecompressor decompressor : FAST_DECOMPRESSORS) {
      ByteBuffer in = Tester.BYTE_BUFFER.copyOf(COMPRESSORS[0].compress(new byte[] {2, 3}));
      ByteBuffer out = Tester.BYTE_BUFFER.allocate(100).asReadOnlyBuffer();
      out.limit(2);
      try {
        decompressor.decompress(in, out);
        fail();
      } catch (ReadOnlyBufferException e) {
        // ok
      }
    }
  }

  @Test
  @Repeat(iterations=5)
  public void testAllEqual() {
    final int len = randomBoolean() ? randomInt(20) : randomInt(100000);
    final byte[] buf = new byte[len];
    Arrays.fill(buf, randomByte());
    testRoundTrip(buf);
  }

  @Test
  public void testMaxDistance() {
    final int len = randomIntBetween(1 << 17, 1 << 18);
    final int off = randomInt(len - (1 << 16) - (1 << 15));
    final byte[] buf = new byte[len];
    for (int i = 0; i < (1 << 15); ++i) {
      buf[off + i] = randomByte();
    }
    System.arraycopy(buf, off, buf, off + 65535, 1 << 15);
    testRoundTrip(buf);
  }

  @Test
  @Repeat(iterations=10)
  public void testRandomData() {
    final int n = randomIntBetween(1, 15);
    final int off = randomInt(1000);
    final int len = randomBoolean() ? randomInt(1 << 16) : randomInt(1 << 20);
    final byte[] data = randomArray(off + len + randomInt(100), n);
    testRoundTrip(data, off, len);
  }

  @Test
  // https://github.com/jpountz/lz4-java/issues/12
  public void testRoundtripIssue12() {
    byte[] data = new byte[]{
        14, 72, 14, 85, 3, 72, 14, 85, 3, 72, 14, 72, 14, 72, 14, 85, 3, 72, 14, 72, 14, 72, 14, 72, 14, 72, 14, 72, 14, 85, 3, 72,
        14, 85, 3, 72, 14, 85, 3, 72, 14, 85, 3, 72, 14, 85, 3, 72, 14, 85, 3, 72, 14, 50, 64, 0, 46, -1, 0, 0, 0, 29, 3, 85,
        8, -113, 0, 68, -97, 3, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3,
        0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113,
        0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113,
        0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 50, 64, 0, 47, -105, 0, 0, 0, 30, 3, -97, 6, 0, 68, -113,
        0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3, 85,
        8, -113, 0, 68, -97, 3, 0, 2, -97, 6, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97,
        6, 0, 68, -113, 0, 120, 64, 0, 48, 4, 0, 0, 0, 31, 34, 72, 29, 72, 37, 72, 35, 72, 45, 72, 23, 72, 46, 72, 20, 72, 40, 72,
        33, 72, 25, 72, 39, 72, 38, 72, 26, 72, 28, 72, 42, 72, 24, 72, 27, 72, 36, 72, 41, 72, 32, 72, 18, 72, 30, 72, 22, 72, 31, 72,
        43, 72, 19, 72, 34, 72, 29, 72, 37, 72, 35, 72, 45, 72, 23, 72, 46, 72, 20, 72, 40, 72, 33, 72, 25, 72, 39, 72, 38, 72, 26, 72,
        28, 72, 42, 72, 24, 72, 27, 72, 36, 72, 41, 72, 32, 72, 18, 72, 30, 72, 22, 72, 31, 72, 43, 72, 19, 72, 34, 72, 29, 72, 37, 72,
        35, 72, 45, 72, 23, 72, 46, 72, 20, 72, 40, 72, 33, 72, 25, 72, 39, 72, 38, 72, 26, 72, 28, 72, 42, 72, 24, 72, 27, 72, 36, 72,
        41, 72, 32, 72, 18, 16, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 39, 24, 32, 34, 124, 0, 120, 64, 0, 48, 80, 0, 0, 0, 31, 30, 72, 22, 72, 31, 72, 43, 72, 19, 72, 34, 72, 29, 72, 37, 72,
        35, 72, 45, 72, 23, 72, 46, 72, 20, 72, 40, 72, 33, 72, 25, 72, 39, 72, 38, 72, 26, 72, 28, 72, 42, 72, 24, 72, 27, 72, 36, 72,
        41, 72, 32, 72, 18, 72, 30, 72, 22, 72, 31, 72, 43, 72, 19, 72, 34, 72, 29, 72, 37, 72, 35, 72, 45, 72, 23, 72, 46, 72, 20, 72,
        40, 72, 33, 72, 25, 72, 39, 72, 38, 72, 26, 72, 28, 72, 42, 72, 24, 72, 27, 72, 36, 72, 41, 72, 32, 72, 18, 72, 30, 72, 22, 72,
        31, 72, 43, 72, 19, 72, 34, 72, 29, 72, 37, 72, 35, 72, 45, 72, 23, 72, 46, 72, 20, 72, 40, 72, 33, 72, 25, 72, 39, 72, 38, 72,
        26, 72, 28, 72, 42, 72, 24, 72, 27, 72, 36, 72, 41, 72, 32, 72, 18, 72, 30, 72, 22, 72, 31, 72, 43, 72, 19, 72, 34, 72, 29, 72,
        37, 72, 35, 72, 45, 72, 23, 72, 46, 72, 20, 72, 40, 72, 33, 72, 25, 72, 39, 72, 38, 72, 26, 72, 28, 72, 42, 72, 24, 72, 27, 72,
        36, 72, 41, 72, 32, 72, 18, 72, 30, 72, 22, 72, 31, 72, 43, 72, 19, 72, 34, 72, 29, 72, 37, 72, 35, 72, 45, 72, 23, 72, 46, 72,
        20, 72, 40, 72, 33, 72, 25, 72, 39, 72, 38, 72, 26, 72, 28, 72, 42, 72, 24, 72, 27, 72, 36, 72, 41, 72, 32, 72, 18, 72, 30, 72,
        22, 72, 31, 72, 43, 72, 19, 72, 34, 72, 29, 72, 37, 72, 35, 72, 45, 72, 23, 72, 46, 72, 20, 72, 40, 72, 33, 72, 25, 72, 39, 72,
        38, 72, 26, 72, 28, 72, 42, 72, 24, 72, 27, 72, 36, 72, 41, 72, 32, 72, 18, 72, 30, 72, 22, 72, 31, 72, 43, 72, 19, 72, 34, 72,
        29, 72, 37, 72, 35, 72, 45, 72, 23, 72, 46, 72, 20, 72, 40, 72, 33, 72, 25, 72, 39, 72, 38, 72, 26, 72, 28, 72, 42, 72, 24, 72,
        27, 72, 36, 72, 41, 72, 32, 72, 18, 72, 30, 72, 22, 72, 31, 72, 43, 72, 19, 50, 64, 0, 49, 20, 0, 0, 0, 32, 3, -97, 6, 0,
        68, -113, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97,
        6, 0, 68, -113, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2,
        3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2,
        3, -97, 6, 0, 50, 64, 0, 50, 53, 0, 0, 0, 34, 3, -97, 6, 0, 68, -113, 0, 2, 3, 85, 8, -113, 0, 68, -113, 0, 2, 3, -97,
        6, 0, 68, -113, 0, 2, 3, 85, 8, -113, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3,
        -97, 6, 0, 68, -113, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, 85, 8, -113, 0, 68, -97,
        3, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3,
        85, 8, -113, 0, 68, -97, 3, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0,
        2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3,
        -97, 6, 0, 50, 64, 0, 51, 85, 0, 0, 0, 36, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97,
        6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, -97, 5, 0, 2, 3, 85, 8, -113, 0, 68,
        -97, 3, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0,
        68, -113, 0, 2, 3, -97, 6, 0, 50, -64, 0, 51, -45, 0, 0, 0, 37, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6,
        0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, 85, 8, -113, 0, 68, -113, 0, 2, 3, -97,
        6, 0, 68, -113, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 120, 64, 0, 52, -88, 0, 0,
        0, 39, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85, 5, 72,
        13, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 72, 13, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85,
        5, 72, 13, 85, 5, 72, 13, 72, 13, 72, 13, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85,
        5, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85,
        5, 72, 13, 85, 5, 72, 13, 72, 13, 72, 13, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 72, 13, 85, 5, 72, 13, 72,
        13, 85, 5, 72, 13, 72, 13, 85, 5, 72, 13, -19, -24, -101, -35
      };
    testRoundTrip(data, 9, data.length - 9);
  }

  @Test
  public void testNativeFastContinue() {
    final byte[] src = "01234567890123456789".getBytes(StandardCharsets.US_ASCII);
    final byte[] dest = new byte[LZ4JNI.LZ4_compressBound(src.length)];
    final int compressLen = LZ4JNI.LZ4_compress_fast_continue(src, null, 0, src.length, dest, null, 0, dest.length, 1);
    final byte[] compressed = Arrays.copyOf(dest, compressLen);

    final byte[] decompressed = new byte[src.length];
    final int decompressedLen = LZ4JNI.LZ4_decompress_fast(compressed, null, 0, decompressed, null, 0, decompressed.length);
    assertEquals(compressLen, decompressedLen);
    assertArrayEquals(src, decompressed);
  }

  private static void assertCompressedArrayEquals(String message, byte[] expected, byte[] actual) {
    int off = 0;
    int decompressedOff = 0;
    while (true) {
      if (off == expected.length) {
        break;
      }
      final Sequence sequence1 = readSequence(expected, off);
      final Sequence sequence2 = readSequence(actual, off);
      assertEquals(message + ", off=" + off + ", decompressedOff=" + decompressedOff, sequence1, sequence2);
      off += sequence1.length;
      decompressedOff += sequence1.literalLen + sequence1.matchLen;
    }
  }

  private static Sequence readSequence(byte[] buf, int off) {
    final int start = off;
    final int token = buf[off++] & 0xFF;
    int literalLen = token >>> 4;
    if (literalLen >= 0x0F) {
      int len;
      while ((len = buf[off++] & 0xFF) == 0xFF) {
        literalLen += 0xFF;
      }
      literalLen += len;
    }
    off += literalLen;
    if (off == buf.length) {
      return new Sequence(literalLen, -1, -1, off - start);
    }
    int matchDec = (buf[off++] & 0xFF) | ((buf[off++] & 0xFF) << 8);
    int matchLen = token & 0x0F;
    if (matchLen >= 0x0F) {
      int len;
      while ((len = buf[off++] & 0xFF) == 0xFF) {
        matchLen += 0xFF;
      }
      matchLen += len;
    }
    matchLen += 4;
    return new Sequence(literalLen, matchDec, matchLen, off - start);
  }

  private static class Sequence {
    final int literalLen, matchDec, matchLen, length;

    public Sequence(int literalLen, int matchDec, int matchLen, int length) {
      this.literalLen = literalLen;
      this.matchDec = matchDec;
      this.matchLen = matchLen;
      this.length = length;
    }

    @Override
    public String toString() {
      return "Sequence [literalLen=" + literalLen + ", matchDec=" + matchDec
          + ", matchLen=" + matchLen + "]";
    }

    @Override
    public int hashCode() {
      return 42;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      Sequence other = (Sequence) obj;
      if (literalLen != other.literalLen)
        return false;
      if (matchDec != other.matchDec)
        return false;
      if (matchLen != other.matchLen)
        return false;
      return true;
    }

  }

}
