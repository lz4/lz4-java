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

import static net.jpountz.lz4.LZ4ChunksOutputStream.MAGIC;
import static net.jpountz.util.Utils.checkRange;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Uncompress a stream which has been compressed with
 * {@link LZ4ChunksOutputStream}. You need to provide the same
 * {@link CompressionCodec} as the one which has been used for compression.
 */
public class LZ4ChunksInputStream extends InputStream {

  private final InputStream is;
  private final CompressionCodec codec;
  private byte[] buffer;
  private byte[] encodedBuffer;
  private int offset, length;
  private int encodedLength;

  private final byte[] singleByteBuffer = new byte[1];

  public LZ4ChunksInputStream(InputStream is, CompressionCodec codec) throws IOException {
    this.is = is;
    this.codec = codec;
    buffer = new byte[1024];
    encodedBuffer = new byte[codec.maxCompressedLength(buffer.length)];
    offset = length = 0;

    if (!readAtLeast(MAGIC.length)) {
      throw new EOFException("EOF reached prematurely");
    }
    for (int i = 0; i < MAGIC.length; ++i) {
      if (MAGIC[i] != encodedBuffer[i]) {
        throw new IOException("Malformed stream");
      }
    }
    encodedLength -= MAGIC.length;
    System.arraycopy(encodedBuffer, MAGIC.length, encodedBuffer, 0, encodedLength);
  }

  private void ensureOpen() throws IOException {
    if (offset == -1) {
      throw new IOException("Already closed");
    }
  }

  @Override
  public void close() throws IOException {
    try {
      ensureOpen();
      offset = -1;
    } finally {
      is.close();
    }
  }

  @Override
  public int available() throws IOException {
    ensureOpen();
    return length;
  }

  @Override
  public int read() throws IOException {
    return read(singleByteBuffer) == -1 ? -1 : singleByteBuffer[0];
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    ensureOpen();

    checkRange(b, off, len);

    if (len == 0) {
      return 0;
    }

    int count = 0;

    if (length > 0) {
      final int copied = Math.min(len, length);
      System.arraycopy(buffer, offset, b, off, copied);
      offset += copied;
      length -= copied;
      off += copied;
      len -= copied;
      count += copied;
      if (len == 0) {
        return count;
      }
    }

    while (true) {
      assert length == 0;

      // read the compressed length
      if (!readAtLeast(4)) {
        return count == 0 ? -1 : count;
      }

      final int compressedLength = ((encodedBuffer[0] & 0xFF) << 24) | ((encodedBuffer[1] & 0xFF) << 16) | ((encodedBuffer[2] & 0xFF) << 8) | (encodedBuffer[3] & 0xFF);
      if (encodedBuffer.length < 4 + compressedLength) {
        encodedBuffer = Arrays.copyOf(encodedBuffer, Math.max(compressedLength + 4, encodedBuffer.length * 2));
      }

      // read the data
      boolean success = readAtLeast(4 + compressedLength);
      assert success;

      // uncompress
      final int maxUncompressedLength = codec.maxUncompressedLength(encodedBuffer, 4, compressedLength);

      if (maxUncompressedLength <= len) {
        // uncompress directly into the user buffer
        final int uncompressedLength = codec.uncompress(encodedBuffer, 4, compressedLength, b, off);

        rewindEncodedBuffer(4 + compressedLength);

        off += uncompressedLength;
        len -= uncompressedLength;
        count += uncompressedLength;

      } else if (count > 0) {
        break;

      } else {
        if (buffer.length < maxUncompressedLength) {
          buffer = Arrays.copyOf(buffer, Math.max(maxUncompressedLength, buffer.length * 2));
        }

        offset = 0;
        length = codec.uncompress(encodedBuffer, 4, compressedLength, buffer, 0);

        rewindEncodedBuffer(4 + compressedLength);

        final int copied = Math.min(len, length);
        System.arraycopy(buffer, offset, b, off, copied);
        offset += copied;
        length -= copied;
        off += copied;
        len -= copied;
        count += copied;
        break;
      }
    }

    return count;
  }

  private boolean readAtLeast(int length) throws IOException {
    while (encodedLength < length) {
      final int inc = is.read(encodedBuffer, encodedLength, encodedBuffer.length - encodedLength);
      if (inc == -1) {
        if (encodedLength == 0) {
          return false;
        } else {
          throw new EOFException("Malformed input data");
        }
      }
      encodedLength += inc;
    }
    return true;
  }

  private void rewindEncodedBuffer(int l) {
    // move encoded data to the beginning of the buffer
    encodedLength -= l;
    System.arraycopy(encodedBuffer, l, encodedBuffer, 0, encodedLength);
  }
}
