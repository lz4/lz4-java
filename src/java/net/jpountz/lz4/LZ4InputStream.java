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

import static net.jpountz.lz4.LZ4Utils.MAX_DISTANCE;
import static net.jpountz.lz4.LZ4Utils.maxCompressedLength;
import static net.jpountz.util.Utils.checkRange;

import java.io.IOException;
import java.io.InputStream;

/**
 * Stream decoder for the LZ4 compression format.
 */
abstract class LZ4InputStream extends InputStream {

  private final InputStream is;
  protected byte[] compressed;
  protected byte[] uncompressed;
  protected int compressedOff;
  protected int compressedLen;
  protected int uncompressedOff, uncompressedLen;

  private final byte[] singleByteBuffer = new byte[1];

  public LZ4InputStream(InputStream is) throws IOException {
    this.is = is;
    uncompressed = new byte[MAX_DISTANCE << 1];
    compressed = new byte[maxCompressedLength(MAX_DISTANCE)];
    fill();
    if (compressedLen == 0) {
      throw new IOException("A LZ4 stream cannot be empty");
    }
  }

  @Override
  public final int read() throws IOException {
    return read(singleByteBuffer) == -1 ? -1 : singleByteBuffer[0];
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    checkRange(b, off, len);

    if (len == 0) {
      if (compressedLen == 0) {
        fill();
        return compressedLen == 0 ? -1 : 0;
      } else {
        return 0;
      }
    }

    int count = 0;

    // check what has already been uncompressed
    if (uncompressedLen > 0) {
      final int toCopy = Math.min(uncompressedLen, len);
      System.arraycopy(uncompressed, uncompressedOff, b, off, len);
      off += toCopy;
      len -= toCopy;
      uncompressedOff += toCopy;
      uncompressedLen -= toCopy;
      count += toCopy;

      if (len == 0) {
        return count;
      }
    }

    // rewind buffers
    rewind();

    // fill compressed buffer
    fill();
    if (compressedLen == 0) {
      return count == 0 ? -1 : count;
    }

    // uncompress
    uncompress();

    // we were unable to produce anything from the compressed bytes
    if (uncompressedLen == 0 && compressedLen > 0) {
      throw new LZ4Exception("Malformed stream");
    }

    final int toCopy = Math.min(uncompressedLen, len);
    System.arraycopy(uncompressed, uncompressedOff, b, off, toCopy);
    uncompressedOff += toCopy;
    uncompressedLen -= toCopy;
    count += toCopy;

    return count;
  }

  protected abstract void uncompress() throws IOException;

  protected void fill() throws IOException {
    while (compressedLen < compressed.length) {
      final int read = is.read(compressed, compressedLen, compressed.length - compressedLen);
      if (read == -1) {
        break;
      }
      compressedLen += read;
    }
  }

  private void rewind() {
    // rewind compressed buffer
    if (compressedLen > 0) {
      System.arraycopy(compressed, compressedOff, compressed, 0, compressedLen);
    }
    compressedOff = 0;

    // rewind uncompressed buffer
    if (uncompressedOff > MAX_DISTANCE) {
      System.arraycopy(uncompressed, uncompressedOff - MAX_DISTANCE, uncompressed, 0, MAX_DISTANCE + uncompressedLen);
      uncompressedOff = MAX_DISTANCE;
    }
  }
}
