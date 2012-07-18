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

import java.util.Arrays;

/**
 * Utility class that exposes 4 ways LZ4 functions can be used to compress and
 * decompress data.
 */
public enum LZ4Compression {
  /**
   * Fast compression, but high compression ratio.
   */
  NORMAL {
    @Override
    public int maxCompressedLength(int length) {
      return LZ4.maxCompressedLength(length);
    }

    @Override
    public int compress(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff) {
      return LZ4.compress(src, srcOff, srcLen, dest, destOff);
    }

    @Override
    public int uncompress(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff) {
      return LZ4.uncompressUnknownSize(src, srcOff, srcLen, dest, destOff, dest.length - destOff);
    }

    @Override
    public byte[] uncompress(byte[] src, int srcOff, int srcLen) {
      LZ4.checkRange(src, srcOff, srcLen);
      byte[] uncompressed = new byte[Math.max(64, srcLen * 2)];
      int uncompressedLength = -1;
      while (true) {
        uncompressedLength = LZ4.LZ4_uncompress_unknownOutputSize(src, srcOff, srcLen, uncompressed, 0, uncompressed.length);
        if (uncompressedLength < 0 || uncompressedLength == uncompressed.length) {
          uncompressed = new byte[uncompressed.length << 1];
        } else {
          break;
        }
      }
      return Arrays.copyOf(uncompressed, uncompressedLength);
    }
  },
  /**
   * Slow compression but low compression ratio.
   */
  HIGH_COMPRESSION {
    @Override
    public int maxCompressedLength(int length) {
      return NORMAL.maxCompressedLength(length);
    }

    @Override
    public int compress(byte[] src, int srcOff, int srcLen, byte[] dest,
        int destOff) {
      return LZ4.compressHC(src, srcOff, srcLen, dest, destOff);
    }

    @Override
    public int uncompress(byte[] src, int srcOff, int srcLen, byte[] dest,
        int destOff) {
      return NORMAL.uncompress(src, srcOff, srcLen, dest, destOff);
    }

    @Override
    public byte[] uncompress(byte[] src, int srcOff, int srcLen) {
      return NORMAL.uncompress(src, srcOff, srcLen);
    }
  },
  /**
   * Same as {@link #NORMAL} but writes the original length at the beginning of
   * the stream. Requires 4 more bytes but should improve decompression speed.
   */
  LENGTH_NORMAL {
    @Override
    public int maxCompressedLength(int length) {
      return 4 + LZ4.maxCompressedLength(length);
    }

    @Override
    public int compress(byte[] src, int srcOff, int srcLen, byte[] dest,
        int destOff) {
      writeInt(srcLen, dest, destOff);
      final int compressedLength = LZ4.compress(src, srcOff, srcLen, dest, destOff + 4);
      return 4 + compressedLength;
    }

    @Override
    public int uncompress(byte[] src, int srcOff, int srcLen, byte[] dest,
        int destOff) {
      final int destLen = readInt(src, srcOff);
      if (destLen == 0) {
        return 0;
      }
      LZ4.checkRange(dest, destOff + destLen - 1);
      final int compressedLength = LZ4.uncompress(src, srcOff + 4, dest, destOff, destLen);
      if (srcLen != compressedLength + 4) {
        throw new LZ4Exception("Malformed stream");
      }
      return destLen;
    }

    @Override
    public byte[] uncompress(byte[] src, int srcOff, int srcLen) {
      final int destLen = readInt(src, srcOff);
      if (destLen == 0) {
        return new byte[0];
      }
      final byte[] uncompressed = new byte[destLen];
      final int compressedLength = LZ4.uncompress(src, srcOff + 4, uncompressed, 0, uncompressed.length);
      if (srcLen != compressedLength + 4) {
        throw new LZ4Exception("Malformed stream");
      }
      return uncompressed;
    }
  },
  /**
   * Same as {@link #HIGH_COMPRESSION} but writes the original length at the
   * beginning of the stream. Requires 4 more bytes but should improve decompression speed.
   */
  LENGTH_HIGH_COMPRESSION {
    @Override
    public int maxCompressedLength(int length) {
      return LENGTH_NORMAL.maxCompressedLength(length);
    }

    @Override
    public int compress(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff) {
      writeInt(srcLen, dest, destOff);
      final int compressedLength = LZ4.compressHC(src, srcOff, srcLen, dest, destOff + 4);
      return 4 + compressedLength;
    }

    @Override
    public int uncompress(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff) {
      return LENGTH_NORMAL.uncompress(src, srcOff, srcLen, dest, destOff);
    }

    @Override
    public byte[] uncompress(byte[] src, int srcOff, int srcLen) {
      return LENGTH_NORMAL.uncompress(src, srcOff, srcLen);
    }
  };

  private static int readInt(byte[] buf, int off) {
    return ((buf[off++] & 0xFF) << 24)
        | ((buf[off++] & 0xFF) << 16)
        | ((buf[off++] & 0xFF) << 8)
        | (buf[off] & 0xFF);
  }

  private static void writeInt(int n, byte[] buf, int off) {
    buf[off++] = (byte) (n >>> 24);
    buf[off++] = (byte) (n >>> 16);
    buf[off++] = (byte) (n >>> 8);
    buf[off] = (byte) n;
  }

  /**
   * Return the maximum compressed length for an input of size <code>length</code>.
   */
  public abstract int maxCompressedLength(int length);

  /**
   * Compress <code>src[srcOff:srcOff+srcLen]</code> into <code>dest[destOff:]</code>.
   */
  public abstract int compress(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff);

  /**
   * Compress <code>src[srcOff:srcOff+srcLen]</code> and return the result.
   */
  public final byte[] compress(byte[] src, int srcOff, int srcLen) {
    final byte[] compressed = new byte[maxCompressedLength(srcLen)];
    final int compressedLength = compress(src, srcOff, srcLen, compressed, 0);
    return Arrays.copyOf(compressed, compressedLength);
  }

  /**
   * Uncompress <code>src[srcOff:srcOff+srcLen]</code> into <code>dest[destOff:]</code>.
   */
  public abstract int uncompress(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff);

  /**
   * Uncompress <code>src[srcOff:srcOff+srcLen]</code> and return the result.
   */
  public abstract byte[] uncompress(byte[] src, int srcOff, int srcLen);

  /**
   * Compress <code>src</code>.
   */
  public final byte[] compress(byte[] src) {
    return compress(src, 0, src.length);
  }

  /**
   * Uncompress <code>src</code>.
   */
  public final byte[] uncompress(byte[] src) {
    return uncompress(src, 0, src.length);
  }
}
