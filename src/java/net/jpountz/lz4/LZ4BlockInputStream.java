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

import static net.jpountz.lz4.LZ4BlockOutputStream.COMPRESSION_LEVEL_BASE;
import static net.jpountz.lz4.LZ4BlockOutputStream.COMPRESSION_METHOD_LZ4;
import static net.jpountz.lz4.LZ4BlockOutputStream.COMPRESSION_METHOD_RAW;
import static net.jpountz.lz4.LZ4BlockOutputStream.DEFAULT_SEED;
import static net.jpountz.lz4.LZ4BlockOutputStream.HEADER_LENGTH;
import static net.jpountz.lz4.LZ4BlockOutputStream.MAGIC;
import static net.jpountz.lz4.LZ4BlockOutputStream.MAGIC_LENGTH;

import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.Checksum;

import net.jpountz.util.SafeUtils;
import net.jpountz.xxhash.StreamingXXHash32;
import net.jpountz.xxhash.XXHash32;
import net.jpountz.xxhash.XXHashFactory;

/**
 * {@link InputStream} implementation to decode data written with
 * {@link LZ4BlockOutputStream}. This class is not thread-safe and does not
 * support {@link #mark(int)}/{@link #reset()}.
 * @see LZ4BlockOutputStream
 */
public class LZ4BlockInputStream extends FilterInputStream {

  private final LZ4FastDecompressor decompressor;
  private final Checksum checksum;
  private final boolean stopOnEmptyBlock;
  private byte[] buffer;
  private byte[] compressedBuffer;
  private int originalLen;
  private int o;
  private boolean finished;

  /**
   * Creates a new LZ4 input stream to read from the specified underlying InputStream.
   *
   * @param in                the {@link InputStream} to poll
   * @param decompressor      the {@link LZ4FastDecompressor decompressor} instance to
   *                          use
   * @param checksum          the {@link Checksum} instance to use, must be
   *                          equivalent to the instance which has been used to
   *                          write the stream
   * @param stopOnEmptyBlock  whether read is stopped on an empty block
   */
  public LZ4BlockInputStream(InputStream in, LZ4FastDecompressor decompressor, Checksum checksum, boolean stopOnEmptyBlock) {
    super(in);
    this.decompressor = decompressor;
    this.checksum = checksum;
    this.stopOnEmptyBlock = stopOnEmptyBlock;
    this.buffer = new byte[0];
    this.compressedBuffer = new byte[HEADER_LENGTH];
    o = originalLen = 0;
    finished = false;
  }

  /**
   * Creates a new LZ4 input stream to read from the specified underlying InputStream.
   *
   * @param in                the {@link InputStream} to poll
   * @param decompressor      the {@link LZ4FastDecompressor decompressor} instance to
   *                          use
   * @param checksum          the {@link Checksum} instance to use, must be
   *                          equivalent to the instance which has been used to
   *                          write the stream
   *
   * @see #LZ4BlockInputStream(InputStream, LZ4FastDecompressor, Checksum, boolean)
   */
  public LZ4BlockInputStream(InputStream in, LZ4FastDecompressor decompressor, Checksum checksum) {
    this(in, decompressor, checksum, true);
  }

  /**
   * Creates a new LZ4 input stream to read from the specified underlying InputStream, using {@link XXHash32} for checksuming.
   *
   * @param in                the {@link InputStream} to poll
   * @param decompressor      the {@link LZ4FastDecompressor decompressor} instance to
   *                          use
   *
   * @see #LZ4BlockInputStream(InputStream, LZ4FastDecompressor, Checksum, boolean)
   * @see StreamingXXHash32#asChecksum()
   */
  public LZ4BlockInputStream(InputStream in, LZ4FastDecompressor decompressor) {
    this(in, decompressor, XXHashFactory.fastestInstance().newStreamingHash32(DEFAULT_SEED).asChecksum(), true);
  }

  /**
   * Creates a new LZ4 input stream to read from the specified underlying InputStream, using {@link XXHash32} for checksuming.
   *
   * @param in                the {@link InputStream} to poll
   * @param stopOnEmptyBlock  whether read is stopped on an empty block
   *
   * @see #LZ4BlockInputStream(InputStream, LZ4FastDecompressor, Checksum, boolean)
   * @see LZ4Factory#fastestInstance()
   * @see StreamingXXHash32#asChecksum()
   */
  public LZ4BlockInputStream(InputStream in, boolean stopOnEmptyBlock) {
    this(in, LZ4Factory.fastestInstance().fastDecompressor(), new LZ4BlockOutputStream.Drop4BitsChecksum(XXHashFactory.fastestInstance().newStreamingHash32(DEFAULT_SEED).asChecksum()), stopOnEmptyBlock);
  }

  /**
   * Creates a new LZ4 input stream to read from the specified underlying InputStream, using {@link XXHash32} for checksuming.
   *
   * @param in                the {@link InputStream} to poll
   *
   * @see #LZ4BlockInputStream(InputStream, LZ4FastDecompressor)
   * @see LZ4Factory#fastestInstance()
   */
  public LZ4BlockInputStream(InputStream in) {
    this(in, LZ4Factory.fastestInstance().fastDecompressor());
  }

  @Override
  public int available() throws IOException {
    return originalLen - o;
  }

  @Override
  public int read() throws IOException {
    if (finished) {
      return -1;
    }
    if (o == originalLen) {
      refill();
    }
    if (finished) {
      return -1;
    }
    return buffer[o++] & 0xFF;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    SafeUtils.checkRange(b, off, len);
    if (finished) {
      return -1;
    }
    if (o == originalLen) {
      refill();
    }
    if (finished) {
      return -1;
    }
    len = Math.min(len, originalLen - o);
    System.arraycopy(buffer, o, b, off, len);
    o += len;
    return len;
  }

  @Override
  public int read(byte[] b) throws IOException {
    return read(b, 0, b.length);
  }

  @Override
  public long skip(long n) throws IOException {
    if (n <= 0 || finished) {
      return 0;
    }
    if (o == originalLen) {
      refill();
    }
    if (finished) {
      return 0;
    }
    final int skipped = (int) Math.min(n, originalLen - o);
    o += skipped;
    return skipped;
  }

  private void refill() throws IOException {
    if (!tryReadFully(compressedBuffer, HEADER_LENGTH)) {
      if (!stopOnEmptyBlock) {
        finished = true;
      } else {
        throw new EOFException("Stream ended prematurely");
      }
      return;
    }
    for (int i = 0; i < MAGIC_LENGTH; ++i) {
      if (compressedBuffer[i] != MAGIC[i]) {
        throw new IOException("Stream is corrupted");
      }
    }
    final int token = compressedBuffer[MAGIC_LENGTH] & 0xFF;
    final int compressionMethod = token & 0xF0;
    final int compressionLevel = COMPRESSION_LEVEL_BASE + (token & 0x0F);
    if (compressionMethod != COMPRESSION_METHOD_RAW && compressionMethod != COMPRESSION_METHOD_LZ4) {
      throw new IOException("Stream is corrupted");
    }
    final int compressedLen = SafeUtils.readIntLE(compressedBuffer, MAGIC_LENGTH + 1);
    originalLen = SafeUtils.readIntLE(compressedBuffer, MAGIC_LENGTH + 5);
    final int check = SafeUtils.readIntLE(compressedBuffer, MAGIC_LENGTH + 9);
    assert HEADER_LENGTH == MAGIC_LENGTH + 13;
    if (originalLen > 1 << compressionLevel
        || originalLen < 0
        || compressedLen < 0
        || (originalLen == 0 && compressedLen != 0)
        || (originalLen != 0 && compressedLen == 0)
        || (compressionMethod == COMPRESSION_METHOD_RAW && originalLen != compressedLen)) {
      throw new IOException("Stream is corrupted");
    }
    if (originalLen == 0 && compressedLen == 0) {
      if (check != 0) {
        throw new IOException("Stream is corrupted");
      }
      if (!stopOnEmptyBlock) {
        refill();
      } else {
        finished = true;
      }
      return;
    }
    if (buffer.length < originalLen) {
      buffer = new byte[Math.max(originalLen, buffer.length * 3 / 2)];
    }
    switch (compressionMethod) {
    case COMPRESSION_METHOD_RAW:
      readFully(buffer, originalLen);
      break;
    case COMPRESSION_METHOD_LZ4:
      if (compressedBuffer.length < compressedLen) {
        compressedBuffer = new byte[Math.max(compressedLen, compressedBuffer.length * 3 / 2)];
      }
      readFully(compressedBuffer, compressedLen);
      try {
        final int compressedLen2 = decompressor.decompress(compressedBuffer, 0, buffer, 0, originalLen);
        if (compressedLen != compressedLen2) {
          throw new IOException("Stream is corrupted");
        }
      } catch (LZ4Exception e) {
        throw new IOException("Stream is corrupted", e);
      }
      break;
    default:
      throw new AssertionError();
    }
    checksum.reset();
    checksum.update(buffer, 0, originalLen);
    if ((int) checksum.getValue() != check) {
      throw new IOException("Stream is corrupted");
    }
    o = 0;
  }

  // Like readFully(), except it signals incomplete reads by returning
  // false instead of throwing EOFException.
  private boolean tryReadFully(byte[] b, int len) throws IOException {
    int read = 0;
    while (read < len) {
      final int r = in.read(b, read, len - read);
      if (r < 0) {
        return false;
      }
      read += r;
    }
    assert len == read;
    return true;
  }

  private void readFully(byte[] b, int len) throws IOException {
    if (!tryReadFully(b, len)) {
      throw new EOFException("Stream ended prematurely");
    }
  }

  @Override
  public boolean markSupported() {
    return false;
  }

  @SuppressWarnings("sync-override")
  @Override
  public void mark(int readlimit) {
    // unsupported
  }

  @SuppressWarnings("sync-override")
  @Override
  public void reset() throws IOException {
    throw new IOException("mark/reset not supported");
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(in=" + in
        + ", decompressor=" + decompressor + ", checksum=" + checksum + ")";
  }

}
