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

import static net.jpountz.lz4.LZ4Utils.vIntLength;
import static net.jpountz.util.Utils.checkRange;

/**
 * Utility class that writes decompressed length at the beginning of the stream
 * to speed up decompression.
 */
public class LengthLZ4 extends CompressionCodec {

  private final LZ4Compressor compressor;
  private final LZ4Decompressor decompressor;

  public LengthLZ4(LZ4Compressor compressor, LZ4Decompressor decompressor) {
    this.compressor = compressor;
    this.decompressor = decompressor;
  }

  @Override
  public int maxCompressedLength(int length) {
    return compressor.maxCompressedLength(length) + 4;
  }

  @Override
  public int maxUncompressedLength(byte[] src, int srcOff, int srcLen) {
    return readVInt(src, srcOff, srcLen);
  }

  @Override
  public int compress(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff) {
    checkRange(src, srcOff, srcLen);
    final int lengthBytes = writeVInt(srcLen, dest, destOff, dest.length - destOff);
    return lengthBytes + compressor.compress(src, srcOff, srcLen, dest, destOff + lengthBytes, dest.length - destOff - lengthBytes);
  }

  @Override
  public int decompress(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff) {
    final int decompressedLen = maxUncompressedLength(src, srcOff, srcLen);
    final int decompressedLenBytes = vIntLength(decompressedLen);
    final int compressedLen = decompressedLenBytes + decompressor.decompress(src, srcOff + decompressedLenBytes, dest, destOff, decompressedLen);
    if (compressedLen != srcLen) {
      throw new LZ4Exception("Uncompressed length mismatch " + srcLen + " != " + compressedLen);
    }
    return decompressedLen;
  }

  static int readVInt(byte[] buf, int off, int len) {
    checkRange(buf, off, len);
    int n = 0;
    for (int i = 0; i < 4; ++i) {
      if (i >= len) {
        throw new LZ4Exception("Malformed stream");
      }
      final byte next = buf[off + i];
      n |= (next & 0x7F) << (7 * i);
      if (next >= 0) {
        return n;
      }
    }
    if (4 >= len) {
      throw new LZ4Exception("Malformed stream");
    }
    final byte next = buf[off + 4];
    if (next < 0 || next >= 1 << 5) {
      throw new LZ4Exception("Malformed stream");
    }
    n |= next << (7 * 4);
    return n;
  }

  static int writeVInt(int n, byte[] buf, int off, int len) {
    if (n < 0) {
      throw new IllegalArgumentException("Cannot encode negative integers");
    }
    int i;
    for (i = 0; (n & ~0x7F) != 0; n >>>= 7, ++i) {
      if (i >= len) {
        throw new LZ4Exception("Destination buffer is too small");
      }
      buf[off + i] = (byte) ((n & 0x7F) | 0x80);
    }
    if (i >= len) {
      throw new LZ4Exception("Destination buffer is too small");
    }
    buf[off + i] = (byte) n;
    return i + 1;
  }

}
