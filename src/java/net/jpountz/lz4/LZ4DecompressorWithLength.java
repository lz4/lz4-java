package net.jpountz.lz4;

/*
 * Copyright 2020 Rei Odaira and the lz4-java contributors.
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

import java.nio.ByteBuffer;

/**
 * Convenience class to decompress data compressed by {@link LZ4CompressorWithLength}.
 * This decompressor is NOT compatible with any other compressors in lz4-java
 * or any other lz4 tools.
 * The user does not need to specify the length of the compressed data or
 * original data because the length of the original decompressed data is
 * included in the compressed data.
 */

public class LZ4DecompressorWithLength {

  private final LZ4FastDecompressor fastDecompressor;
  private final LZ4SafeDecompressor safeDecompressor;

  /**
   * Returns the decompressed length of compressed data in <code>src</code>.
   *
   * @param src the compressed data
   * @return the decompressed length
   */
  public static int getDecompressedLength(byte[] src) {
    return getDecompressedLength(src, 0);
  }

  /**
   * Returns the decompressed length of compressed data in <code>src[srcOff:]</code>.
   *
   * @param src the compressed data
   * @param srcOff the start offset in src
   * @return the decompressed length
   */
  public static int getDecompressedLength(byte[] src, int srcOff) {
    return (src[srcOff] & 0xFF) | (src[srcOff + 1] & 0xFF) << 8 | (src[srcOff + 2] & 0xFF) << 16 | src[srcOff + 3] << 24;
  }

  /**
   * Returns the decompressed length of compressed data in <code>src</code>.
   *
   * @param src the compressed data
   * @return the decompressed length
   */
  public static int getDecompressedLength(ByteBuffer src) {
    return getDecompressedLength(src, src.position());
  }

  /**
   * Returns the decompressed length of compressed data in <code>src[srcOff:]</code>.
   *
   * @param src the compressed data
   * @param srcOff the start offset in src
   * @return the decompressed length
   */
  public static int getDecompressedLength(ByteBuffer src, int srcOff) {
    return (src.get(srcOff) & 0xFF) | (src.get(srcOff + 1) & 0xFF) << 8 | (src.get(srcOff + 2) & 0xFF) << 16 | src.get(srcOff + 3) << 24;
  }

  /**
   * Creates a new decompressor to decompress data compressed by {@link LZ4CompressorWithLength}.
   * Note that it is deprecated to use a JNI-binding instance of {@link LZ4FastDecompressor}.
   * Please see {@link LZ4Factory#nativeInstance()} for details.
   *
   * @param fastDecompressor fast decompressor to use
   */
  public LZ4DecompressorWithLength(LZ4FastDecompressor fastDecompressor) {
    this.fastDecompressor = fastDecompressor;
    this.safeDecompressor = null;
  }

  /**
   * Creates a new decompressor to decompress data compressed by {@link LZ4CompressorWithLength}.
   *
   * @param safeDecompressor safe decompressor to use
   */
  public LZ4DecompressorWithLength(LZ4SafeDecompressor safeDecompressor) {
    this.fastDecompressor = null;
    this.safeDecompressor = safeDecompressor;
  }

  /**
   * Convenience method, equivalent to calling
   * {@link #decompress(byte[], int, byte[], int) decompress(src, 0, dest, 0)}.
   *
   * @param src the compressed data
   * @param dest the destination buffer to store the decompressed data
   * @return the number of bytes read to restore the original input
   */
  public int decompress(byte[] src, byte[] dest) {
    return decompress(src, 0, dest, 0);
  }

  /**
   * When {@link LZ4FastDecompressor} was specified to the constructor,
   * decompresses <code>src[srcOff:]</code> into <code>dest[destOff:]</code>
   * and returns the number of bytes read from <code>src</code>, and
   * when {@link LZ4SafeDecompressor} was specified to the constructor,
   * decompresses <code>src[srcOff:src.length]</code> into <code>dest[destOff:]</code>
   * and returns the number of decompressed bytes written into <code>dest</code>.
   *
   * @param src the compressed data
   * @param srcOff the start offset in src
   * @param dest the destination buffer to store the decompressed data
   * @param destOff the start offset in dest
   * @return the number of bytes read to restore the original input (when {@link LZ4FastDecompressor} is used), or the number of decompressed bytes (when  {@link LZ4SafeDecompressor} is used)
   */
  public int decompress(byte[] src, int srcOff, byte[] dest, int destOff) {
    if (safeDecompressor != null) {
      return decompress(src, srcOff, src.length - srcOff, dest, destOff);
    }
    final int destLen = getDecompressedLength(src, srcOff);
    return fastDecompressor.decompress(src, srcOff + 4, dest, destOff, destLen) + 4;
  }

  /**
   * When {@link LZ4FastDecompressor} was specified to the constructor,
   * decompresses <code>src[srcOff:]</code> into <code>dest[destOff:]</code>
   * and returns the number of bytes read from <code>src</code>, and
   * when {@link LZ4SafeDecompressor} was specified to the constructor,
   * decompresses <code>src[srcOff:srcOff+srcLen]</code> into <code>dest[destOff:]</code>
   * and returns the number of decompressed bytes written into <code>dest</code>.
   *
   * @param src the compressed data
   * @param srcOff the start offset in src
   * @param srcLen the exact size of the compressed data (ignored when {@link LZ4FastDecompressor} is used)
   * @param dest the destination buffer to store the decompressed data
   * @param destOff the start offset in dest
   * @return the number of bytes read to restore the original input (when {@link LZ4FastDecompressor} is used), or the number of decompressed bytes (when  {@link LZ4SafeDecompressor} is used)
   */
  public int decompress(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff) {
    if (safeDecompressor == null) {
      return decompress(src, srcOff, dest, destOff);
    }
    final int destLen = getDecompressedLength(src, srcOff);
    return safeDecompressor.decompress(src, srcOff + 4, srcLen - 4, dest, destOff, destLen);
  }

  /**
   * Convenience method, equivalent to calling
   * {@link #decompress(byte[], int) decompress(src, 0)}.
   *
   * @param src the compressed data
   * @return the decompressed data
   */
  public byte[] decompress(byte[] src) {
    return decompress(src, 0);
  }

  /**
   * Convenience method which returns <code>src[srcOff:]</code>
   * decompressed when {@link LZ4FastDecompressor} was specified to the constructor,
   * or <code>src[srcOff:src.length]</code> decompressed when
   * {@link LZ4SafeDecompressor} was specified to the constructor.
   * <p><b><span style="color:red">Warning</span></b>: this method has an
   * important overhead due to the fact that it needs to allocate a buffer to
   * decompress into.
   *
   * @param src the compressed data
   * @param srcOff the start offset in src
   * @return the decompressed data
   */
  public byte[] decompress(byte[] src, int srcOff) {
    if (safeDecompressor != null) {
      return decompress(src, srcOff, src.length - srcOff);
    }
    final int destLen = getDecompressedLength(src, srcOff);
    return fastDecompressor.decompress(src, srcOff + 4, destLen);
  }

  /**
   * Convenience method which returns <code>src[srcOff:]</code>
   * decompressed when {@link LZ4FastDecompressor} was specified to the constructor,
   * or <code>src[srcOff:srcOff+srcLen]</code> decompressed when
   * {@link LZ4SafeDecompressor} was specified to the constructor.
   * <p><b><span style="color:red">Warning</span></b>: this method has an
   * important overhead due to the fact that it needs to allocate a buffer to
   * decompress into.
   *
   * @param src the compressed data
   * @param srcOff the start offset in src
   * @param srcLen the exact size of the compressed data (ignored when {@link LZ4FastDecompressor} is used)
   * @return the decompressed data
   */
  public byte[] decompress(byte[] src, int srcOff, int srcLen) {
    if (safeDecompressor == null) {
      return decompress(src, srcOff);
    }
    final int destLen = getDecompressedLength(src, srcOff);
    return safeDecompressor.decompress(src, srcOff + 4, srcLen - 4, destLen);
  }

  /**
   * Decompresses <code>src</code> into <code>dest</code>.
   * When {@link LZ4SafeDecompressor} was specified to the constructor,
   * <code>src</code>'s {@link ByteBuffer#remaining()} must be exactly the size
   * of the compressed data. This method moves the positions of the buffers.
   *
   * @param src the compressed data
   * @param dest the destination buffer to store the decompressed data
   */
  public void decompress(ByteBuffer src, ByteBuffer dest) {
    final int destLen = getDecompressedLength(src, src.position());
    if (safeDecompressor == null) {
      final int read = fastDecompressor.decompress(src, src.position() + 4, dest, dest.position(), destLen);
      src.position(src.position() + 4 + read);
      dest.position(dest.position() + destLen);
    } else {
      final int written = safeDecompressor.decompress(src, src.position() + 4, src.remaining() - 4, dest, dest.position(), destLen);
      src.position(src.limit());
      dest.position(dest.position() + written);
    }
  }

  /** When {@link LZ4FastDecompressor} was specified to the constructor,
   * decompresses <code>src[srcOff:]</code> into <code>dest[destOff:]</code>
   * and returns the number of bytes read from <code>src</code>, and
   * when {@link LZ4SafeDecompressor} was specified to the constructor,
   * decompresses <code>src[srcOff:src.remaining()]</code> into <code>dest[destOff:]</code>
   * and returns the number of decompressed bytes written into <code>dest</code>.
   * The positions and limits of the {@link ByteBuffer}s remain unchanged.
   *
   * @param src the compressed data
   * @param srcOff the start offset in src
   * @param dest the destination buffer to store the decompressed data
   * @param destOff the start offset in dest
   * @return the number of bytes read to restore the original input (when {@link LZ4FastDecompressor} is used), or the number of decompressed bytes (when  {@link LZ4SafeDecompressor} is used)
   */
  public int decompress(ByteBuffer src, int srcOff, ByteBuffer dest, int destOff) {
    if (safeDecompressor != null) {
      return decompress(src, srcOff, src.remaining() - srcOff, dest, destOff);
    }
    final int destLen = getDecompressedLength(src, srcOff);
    return fastDecompressor.decompress(src, srcOff + 4, dest, destOff, destLen) + 4;
  }

  /**
   * When {@link LZ4FastDecompressor} was specified to the constructor,
   * decompresses <code>src[srcOff:]</code> into <code>dest[destOff:]</code>
   * and returns the number of bytes read from <code>src</code>, and
   * when {@link LZ4SafeDecompressor} was specified to the constructor,
   * decompresses <code>src[srcOff:srcOff+srcLen]</code> into <code>dest[destOff:]</code>
   * and returns the number of decompressed bytes written into <code>dest</code>.
   * The positions and limits of the {@link ByteBuffer}s remain unchanged.
   *
   * @param src the compressed data
   * @param srcOff the start offset in src
   * @param srcLen the exact size of the compressed data (ignored when {@link LZ4FastDecompressor} is used)
   * @param dest the destination buffer to store the decompressed data
   * @param destOff the start offset in dest
   * @return the number of bytes read to restore the original input (when {@link LZ4FastDecompressor} is used), or the number of decompressed bytes (when  {@link LZ4SafeDecompressor} is used)
   */
  public int decompress(ByteBuffer src, int srcOff, int srcLen, ByteBuffer dest, int destOff) {
    if (safeDecompressor == null) {
      return decompress(src, srcOff, dest, destOff);
    }
    final int destLen = getDecompressedLength(src, srcOff);
    return safeDecompressor.decompress(src, srcOff + 4, srcLen - 4, dest, destOff, destLen);
  }
}
