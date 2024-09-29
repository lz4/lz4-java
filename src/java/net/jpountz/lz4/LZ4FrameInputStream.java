package net.jpountz.lz4;

/*
 * Copyright 2020 The Apache Software Foundation and the lz4-java contributors.
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

import net.jpountz.xxhash.XXHash32;
import net.jpountz.xxhash.XXHashFactory;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

/**
 * Implementation of the v1.5.1 LZ4 Frame format. This class is NOT thread safe.
 * <p>
 * Not Supported:<ul>
 * <li>Dependent blocks</li>
 * <li>Legacy streams</li>
 * </ul>
 * <p>
 * Originally based on kafka's KafkaLZ4BlockInputStream.
 *
 * @see <a href="https://github.com/lz4/lz4/blob/dev/doc/lz4_Frame_format.md">LZ4 Framing Format Spec 1.5.1</a>
 */
public class LZ4FrameInputStream extends FilterInputStream {

  static final String PREMATURE_EOS = "Stream ended prematurely";
  static final String NOT_SUPPORTED = "Stream unsupported";
  static final String BLOCK_HASH_MISMATCH = "Block checksum mismatch";
  static final String DESCRIPTOR_HASH_MISMATCH = "Stream frame descriptor corrupted";
  static final int MAGIC_SKIPPABLE_BASE = 0x184D2A50;

  private final LZ4SafeDecompressor decompressor;
  private final XXHash32 checksum;
  private final byte[] headerArray = new byte[LZ4FrameOutputStream.LZ4_MAX_HEADER_LENGTH];
  private final ByteBuffer headerBuffer = ByteBuffer.wrap(headerArray).order(ByteOrder.LITTLE_ENDIAN);
  private final boolean readSingleFrame;
  private byte[] compressedBuffer;
  private ByteBuffer buffer = null;
  private byte[] rawBuffer = null;
  private int maxBlockSize = -1;
  private long expectedContentSize = -1L;
  private long totalContentSize = 0L;
  private boolean firstFrameHeaderRead = false;

  private LZ4FrameOutputStream.FrameInfo frameInfo = null;

  /**
   * Creates a new {@link InputStream} that will decompress data using fastest instances of {@link LZ4SafeDecompressor} and {@link XXHash32}.
   * This instance will decompress all concatenated frames in their sequential order.
   *
   * @param in the stream to decompress
   * @throws IOException if an I/O error occurs
   *
   * @see #LZ4FrameInputStream(InputStream, LZ4SafeDecompressor,  XXHash32)
   * @see LZ4Factory#fastestInstance()
   * @see XXHashFactory#fastestInstance()
   */
  public LZ4FrameInputStream(InputStream in) throws IOException {
    this(in, LZ4Factory.fastestInstance().safeDecompressor(), XXHashFactory.fastestInstance().hash32());
  }

  /**
   * Creates a new {@link InputStream} that will decompress data using fastest instances of {@link LZ4SafeDecompressor} and {@link XXHash32}.
   *
   * @param in the stream to decompress
   * @param readSingleFrame whether read is stopped after the first non-skippable frame
   * @throws IOException if an I/O error occurs
   *
   * @see #LZ4FrameInputStream(InputStream, LZ4SafeDecompressor,  XXHash32)
   * @see LZ4Factory#fastestInstance()
   * @see XXHashFactory#fastestInstance()
   */
  public LZ4FrameInputStream(InputStream in, boolean readSingleFrame) throws IOException {
    this(in, LZ4Factory.fastestInstance().safeDecompressor(), XXHashFactory.fastestInstance().hash32(), readSingleFrame);
  }

  /**
   * Creates a new {@link InputStream} that will decompress data using the LZ4 algorithm.
   * This instance will decompress all concatenated frames in their sequential order.
   *
   * @param in the stream to decompress
   * @param decompressor the decompressor to use
   * @param checksum the hash function to use
   * @throws IOException if an I/O error occurs
   *
   * @see #LZ4FrameInputStream(InputStream, LZ4SafeDecompressor,  XXHash32, boolean)
   */
  public LZ4FrameInputStream(InputStream in, LZ4SafeDecompressor decompressor,  XXHash32 checksum) throws IOException {
    this(in, decompressor, checksum, false);
  }

  /**
   * Creates a new {@link InputStream} that will decompress data using the LZ4 algorithm.
   *
   * @param in the stream to decompress
   * @param decompressor the decompressor to use
   * @param checksum the hash function to use
   * @param readSingleFrame whether read is stopped after the first non-skippable frame
   * @throws IOException if an I/O error occurs
   */
  public LZ4FrameInputStream(InputStream in, LZ4SafeDecompressor decompressor,  XXHash32 checksum, boolean readSingleFrame) throws IOException {
    super(in);
    this.decompressor = decompressor;
    this.checksum = checksum;
    this.readSingleFrame = readSingleFrame;
  }



  /**
   * Try and load in the next valid frame info. This will skip over skippable frames.
   * @return True if a frame was loaded. False if there are no more frames in the stream.
   * @throws IOException On input stream read exception
   */
  private boolean nextFrameInfo() throws IOException {
    while (true) {
      int size = 0;
      do {
	final int mySize = in.read(readNumberBuff.array(), size, LZ4FrameOutputStream.INTEGER_BYTES - size);
	if (mySize < 0) {
          if (firstFrameHeaderRead) {
            if (size > 0) {
              throw new IOException(PREMATURE_EOS);
            } else {
              return false;
            }
          } else {
            throw new IOException(PREMATURE_EOS);
          }
	}
	size += mySize;
      } while (size < LZ4FrameOutputStream.INTEGER_BYTES);
      final int magic = readNumberBuff.getInt(0);
      if (magic == LZ4FrameOutputStream.MAGIC) {
	readHeader();
	return true;
      } else if ((magic >>> 4) == (MAGIC_SKIPPABLE_BASE >>> 4)) {
	skippableFrame();
      } else {
	throw new IOException(NOT_SUPPORTED);
      }
    }
  }

  private void skippableFrame() throws IOException {
    int skipSize = readInt(in);
    final byte[] skipBuffer = new byte[1 << 10];
    while (skipSize > 0) {
      final int mySize = in.read(skipBuffer, 0, Math.min(skipSize, skipBuffer.length));
      if (mySize < 0) {
        throw new IOException(PREMATURE_EOS);
      }
      skipSize -= mySize;
    }
    firstFrameHeaderRead = true;
  }

  /**
   * Reads the frame descriptor from the underlying {@link InputStream}.
   *
   * @throws IOException
   */
  private void readHeader() throws IOException {
    headerBuffer.rewind();

    final int flgRead = in.read();
    if (flgRead < 0) {
      throw new IOException(PREMATURE_EOS);
    }
    final int bdRead = in.read();
    if (bdRead < 0) {
      throw new IOException(PREMATURE_EOS);
    }

    final byte flgByte = (byte)(flgRead & 0xFF);
    final LZ4FrameOutputStream.FLG flg = LZ4FrameOutputStream.FLG.fromByte(flgByte);
    headerBuffer.put(flgByte);
    final byte bdByte = (byte)(bdRead & 0xFF);
    final LZ4FrameOutputStream.BD bd = LZ4FrameOutputStream.BD.fromByte(bdByte);
    headerBuffer.put(bdByte);

    this.frameInfo = new LZ4FrameOutputStream.FrameInfo(flg, bd);

    if (flg.isEnabled(LZ4FrameOutputStream.FLG.Bits.CONTENT_SIZE)) {
      expectedContentSize = readLong(in);
      headerBuffer.putLong(expectedContentSize);
    }
    totalContentSize = 0L;

    // check stream descriptor hash
    final byte hash = (byte) ((checksum.hash(headerArray, 0, headerBuffer.position(), 0) >> 8) & 0xFF);
    final int expectedHash = in.read();
    if (expectedHash < 0) {
      throw new IOException(PREMATURE_EOS);
    }

    if (hash != (byte)(expectedHash & 0xFF)) {
      throw new IOException(DESCRIPTOR_HASH_MISMATCH);
    }

    maxBlockSize = frameInfo.getBD().getBlockMaximumSize();
    compressedBuffer = new byte[maxBlockSize]; // Reused during different compressions
    rawBuffer = new byte[maxBlockSize];
    buffer = ByteBuffer.wrap(rawBuffer);
    buffer.limit(0);
    firstFrameHeaderRead = true;
  }

  private final ByteBuffer readNumberBuff = ByteBuffer.allocate(LZ4FrameOutputStream.LONG_BYTES).order(ByteOrder.LITTLE_ENDIAN);

  private long readLong(InputStream stream) throws IOException {
    int offset = 0;
    do {
      final int mySize = stream.read(readNumberBuff.array(), offset, LZ4FrameOutputStream.LONG_BYTES - offset);
      if (mySize < 0) {
        throw new IOException(PREMATURE_EOS);
      }
      offset += mySize;
    } while (offset < LZ4FrameOutputStream.LONG_BYTES);
    return readNumberBuff.getLong(0);
  }

  private int readInt(InputStream stream) throws IOException {
    int offset = 0;
    do {
      final int mySize = stream.read(readNumberBuff.array(), offset, LZ4FrameOutputStream.INTEGER_BYTES - offset);
      if (mySize < 0) {
        throw new IOException(PREMATURE_EOS);
      }
      offset += mySize;
    } while (offset < LZ4FrameOutputStream.INTEGER_BYTES);
    return readNumberBuff.getInt(0);
  }

  /**
   * Decompress (if necessary) buffered data, optionally computes and validates a XXHash32 checksum, and writes the
   * result to a buffer.
   *
   * @throws IOException
   */
  private void readBlock() throws IOException {
    int blockSize = readInt(in);
    final boolean compressed = (blockSize & LZ4FrameOutputStream.LZ4_FRAME_INCOMPRESSIBLE_MASK) == 0;
    blockSize &= ~LZ4FrameOutputStream.LZ4_FRAME_INCOMPRESSIBLE_MASK;

    // Check for EndMark
    if (blockSize == 0) {
      if (frameInfo.isEnabled(LZ4FrameOutputStream.FLG.Bits.CONTENT_CHECKSUM)) {
        final int contentChecksum = readInt(in);
        if (contentChecksum != frameInfo.currentStreamHash()) {
          throw new IOException("Content checksum mismatch");
        }
      }
      if (frameInfo.isEnabled(LZ4FrameOutputStream.FLG.Bits.CONTENT_SIZE) && expectedContentSize != totalContentSize) {
	throw new IOException("Size check mismatch");
      }
      frameInfo.finish();
      return;
    }

    final byte[] tmpBuffer; // Use a temporary buffer, potentially one used for compression
    if (compressed) {
      tmpBuffer = compressedBuffer;
    } else {
      tmpBuffer = rawBuffer;
    }
    if (blockSize > maxBlockSize) {
      throw new IOException(String.format(Locale.ROOT, "Block size %s exceeded max: %s", blockSize, maxBlockSize));
    }

    int offset = 0;
    while (offset < blockSize) {
      final int lastRead = in.read(tmpBuffer, offset, blockSize - offset);
      if (lastRead < 0) {
        throw new IOException(PREMATURE_EOS);
      }
      offset += lastRead;
    }

    // verify block checksum
    if (frameInfo.isEnabled(LZ4FrameOutputStream.FLG.Bits.BLOCK_CHECKSUM)) {
      final int hashCheck = readInt(in);
      if (hashCheck != checksum.hash(tmpBuffer, 0, blockSize, 0)) {
        throw new IOException(BLOCK_HASH_MISMATCH);
      }
    }

    final int currentBufferSize;
    if (compressed) {
      try {
        currentBufferSize = decompressor.decompress(tmpBuffer, 0, blockSize, rawBuffer, 0, rawBuffer.length);
      } catch (LZ4Exception e) {
        throw new IOException(e);
      }
    } else {
      currentBufferSize = blockSize;
    }
    if (frameInfo.isEnabled(LZ4FrameOutputStream.FLG.Bits.CONTENT_CHECKSUM)) {
      frameInfo.updateStreamHash(rawBuffer, 0, currentBufferSize);
    }
    totalContentSize += currentBufferSize;
    buffer.limit(currentBufferSize);
    buffer.rewind();
  }

  @Override
  public int read() throws IOException {
    while (!firstFrameHeaderRead || buffer.remaining() == 0) {
      if (!firstFrameHeaderRead || frameInfo.isFinished()) {
        if (firstFrameHeaderRead && readSingleFrame) {
          return -1;
        }
	if (!nextFrameInfo()) {
	  return -1;
	}
      }
      readBlock();
    }
    return (int)buffer.get() & 0xFF;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    if ((off < 0) || (len < 0) || (off + len > b.length)) {
      throw new IndexOutOfBoundsException();
    }
    while (!firstFrameHeaderRead || buffer.remaining() == 0) {
      if (!firstFrameHeaderRead || frameInfo.isFinished()) {
        if (firstFrameHeaderRead && readSingleFrame) {
          return -1;
        }
	if (!nextFrameInfo()) {
	  return -1;
	}
      }
      readBlock();
    }
    len = Math.min(len, buffer.remaining());
    buffer.get(b, off, len);
    return len;
  }

  @Override
  public long skip(long n) throws IOException {
    if (n <= 0) {
      return 0;
    }
    while (!firstFrameHeaderRead || buffer.remaining() == 0) {
      if (!firstFrameHeaderRead || frameInfo.isFinished()) {
        if (firstFrameHeaderRead && readSingleFrame) {
          return 0;
        }
	if (!nextFrameInfo()) {
	  return 0;
	}
      }
      readBlock();
    }
    n = Math.min(n, buffer.remaining());
    buffer.position(buffer.position() + (int)n);
    return n;
  }

  @Override
  public int available() throws IOException {
    //增加空判断，避免buffer为null时抛出 NullPointerException
    if (null == buffer) return 0;
    return buffer.remaining();
  }

  @Override
  public void close() throws IOException {
    super.close();
  }

  @Override
  public synchronized void mark(int readlimit) {
    throw new UnsupportedOperationException("mark not supported");
  }

  @Override
  public synchronized void reset() throws IOException {
    throw new UnsupportedOperationException("reset not supported");
  }

  @Override
  public boolean markSupported() {
    return false;
  }

  /**
   * Returns the optional Content Size value set in Frame Descriptor.
   * If the Content Size is not set (FLG.Bits.CONTENT_SIZE not enabled) in compressed stream, -1L is returned.
   * A call to this method is valid only when this instance is supposed to read only one frame (readSingleFrame == true).
   *
   * @return the expected content size, or -1L if no expected content size is set in the frame.
   * @throws IOException On input stream read exception
   *
   * @see #LZ4FrameInputStream(InputStream, LZ4SafeDecompressor,  XXHash32, boolean)
   */
  public long getExpectedContentSize() throws IOException {
    if (!readSingleFrame) {
      throw new UnsupportedOperationException("Operation not permitted when multiple frames can be read");
    }
    if (!firstFrameHeaderRead) {
      if (!nextFrameInfo()) {
	return -1L;
      }
    }
    return expectedContentSize;
  }

  /**
   * Checks if the optionnal Content Size is set (FLG.Bits.CONTENT_SIZE is enabled).
   *
   * @return true if this instance is supposed to read only one frame and if the optional content size is set in the frame.
   * @throws IOException On input stream read exception
   */
  public boolean isExpectedContentSizeDefined() throws IOException {
    if (readSingleFrame) {
      if (!firstFrameHeaderRead) {
	if (!nextFrameInfo()) {
	  return false;
	}
      }
      return expectedContentSize >= 0;
    } else {
      return false;
    }
  }

}
