package net.jpountz.lz4;

/*
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
import java.util.Arrays;

/**
 * Covenience class to include the length of the original decompressed data
 * in the output compressed data, so that the user does not need to save
 * the length at anywhere else.  The compressed data must be decompressed by
 * {@link LZ4DecompressorWithLength} and is NOT compatible with any other
 * decompressors in lz4-java or any other lz4 tools.  This class deliberately
 * does not extend {@link LZ4Compressor} because they are not interchangable.
 */

public class LZ4CompressorWithLength {

  private final LZ4Compressor compressor;

  /**
   * Creates a new compressor that includes the length of the original
   * decompressed data in the output compressed data.
   *
   * @param compressor compressor to use
   */
  public LZ4CompressorWithLength(LZ4Compressor compressor) {
    this.compressor = compressor;
  }

  private void putOriginalLength(byte[] dest, int destOff, int originalLength) {
    dest[destOff] = (byte)originalLength;
    dest[destOff + 1] = (byte)(originalLength >> 8);
    dest[destOff + 2] = (byte)(originalLength >> 16);
    dest[destOff + 3] = (byte)(originalLength >> 24);
  }

  private void putOriginalLength(ByteBuffer dest, int destOff, int originalLength) {
    dest.put(destOff, (byte)originalLength);
    dest.put(destOff + 1, (byte)(originalLength >> 8));
    dest.put(destOff + 2, (byte)(originalLength >> 16));
    dest.put(destOff + 3, (byte)(originalLength >> 24));
  }

  /**
   * Returns the maximum compressed length for an input of size <code>length</code>.
   *
   * @param length the input size in bytes
   * @return the maximum compressed length in bytes
   */
  public int maxCompressedLength(int length) {
    return compressor.maxCompressedLength(length) + 4;
  }

  /**
   * Convenience method, equivalent to calling
   * {@link #compress(byte[], int, int) compress(src, 0, src.length)}.
   *
   * @param src the source data
   * @return the compressed data
   */
  public byte[] compress(byte[] src) {
    return compress(src, 0, src.length);
  }

  /**
   * Convenience method which returns <code>src[srcOff:srcOff+srcLen]</code>
   * compressed.
   * <p><b><span style="color:red">Warning</span></b>: this method has an
   * important overhead due to the fact that it needs to allocate a buffer to
   * compress into, and then needs to resize this buffer to the actual
   * compressed length.</p>
   * <p>Here is how this method is implemented:</p>
   * <pre>
   * final int maxCompressedLength = maxCompressedLength(srcLen);
   * final byte[] compressed = new byte[maxCompressedLength];
   * final int compressedLength = compress(src, srcOff, srcLen, compressed, 0);
   * return Arrays.copyOf(compressed, compressedLength);
   * </pre>
   *
   * @param src the source data
   * @param srcOff the start offset in src
   * @param srcLen the number of bytes to compress
   * @return the compressed data
   */
  public byte[] compress(byte[] src, int srcOff, int srcLen) {
    final int maxCompressedLength = maxCompressedLength(srcLen);
    final byte[] compressed = new byte[maxCompressedLength];
    final int compressedLength = compress(src, srcOff, srcLen, compressed, 0);
    return Arrays.copyOf(compressed, compressedLength);
  }

  /**
   * Convenience method, equivalent to calling
   * {@link #compress(byte[], int, int, byte[], int) compress(src, 0, src.length, dest, 0)}.
   *
   * @param src the source data
   * @param dest the destination buffer
   * @throws LZ4Exception if dest is too small
   * @return the compressed size
   */
  public int compress(byte[] src, byte[] dest) {
    return compress(src, 0, src.length, dest, 0);
  }

  /**
   * Convenience method, equivalent to calling
   * {@link #compress(byte[], int, int, byte[], int, int) compress(src, srcOff, srcLen, dest, destOff, dest.length - destOff)}.
   *
   * @param src the source data
   * @param srcOff the start offset in src
   * @param srcLen the number of bytes to compress
   * @param dest the destination buffer
   * @param destOff the start offset in dest
   * @throws LZ4Exception if dest is too small
   * @return the compressed size
   */
  public int compress(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff) {
    return compress(src, srcOff, srcLen, dest, destOff, dest.length - destOff);
  }

  /**
   * Compresses <code>src[srcOff:srcOff+srcLen]</code> into
   * <code>dest[destOff:destOff+maxDestLen]</code> and returns the compressed
   * length.
   *
   * This method will throw a {@link LZ4Exception} if this compressor is unable
   * to compress the input into less than <code>maxDestLen</code> bytes. To
   * prevent this exception to be thrown, you should make sure that
   * <code>maxDestLen &gt;= maxCompressedLength(srcLen)</code>.
   *
   * @param src the source data
   * @param srcOff the start offset in src
   * @param srcLen the number of bytes to compress
   * @param dest the destination buffer
   * @param destOff the start offset in dest
   * @param maxDestLen the maximum number of bytes to write in dest
   * @throws LZ4Exception if maxDestLen is too small
   * @return the compressed size
   */
  public int compress(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff, int maxDestLen) {
    final int compressedLength = compressor.compress(src, srcOff, srcLen, dest, destOff + 4, maxDestLen - 4);
    putOriginalLength(dest, destOff, srcLen);
    return compressedLength + 4;
  }

  /**
   * Compresses <code>src</code> into <code>dest</code>. Calling this method
   * will update the positions of both {@link ByteBuffer}s.
   *
   * @param src the source data
   * @param dest the destination buffer
   * @throws LZ4Exception if dest is too small
   */
  public void compress(ByteBuffer src, ByteBuffer dest) {
    final int compressedLength = compress(src, src.position(), src.remaining(), dest, dest.position(), dest.remaining());
    src.position(src.limit());
    dest.position(dest.position() + compressedLength);
  }

  /**
   * Compresses <code>src[srcOff:srcOff+srcLen]</code> into
   * <code>dest[destOff:destOff+maxDestLen]</code> and returns the compressed
   * length.
   *
   * This method will throw a {@link LZ4Exception} if this compressor is unable
   * to compress the input into less than <code>maxDestLen</code> bytes. To
   * prevent this exception to be thrown, you should make sure that
   * <code>maxDestLen &gt;= maxCompressedLength(srcLen)</code>.
   *
   * {@link ByteBuffer} positions remain unchanged.
   *
   * @param src the source data
   * @param srcOff the start offset in src
   * @param srcLen the number of bytes to compress
   * @param dest the destination buffer
   * @param destOff the start offset in dest
   * @param maxDestLen the maximum number of bytes to write in dest
   * @throws LZ4Exception if maxDestLen is too small
   * @return the compressed size
   */
  public int compress(ByteBuffer src, int srcOff, int srcLen, ByteBuffer dest, int destOff, int maxDestLen) {
    final int compressedLength = compressor.compress(src, srcOff, srcLen, dest, destOff + 4, maxDestLen - 4);
    putOriginalLength(dest, destOff, srcLen);
    return compressedLength + 4;
  }
}
