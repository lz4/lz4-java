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

/**
 * LZ4 decompressor that requires the size of the compressed data to be known.
 * <p>
 * Implementations of this class are usually a little slower than those of
 * {@link LZ4Decompressor} but do not require the size of the original data to
 * be known.
 */
public abstract class LZ4UnknownSizeDecompressor {

  /**
   * Uncompress <code>src[srcOff:srcLen]</code> into
   * <code>dest[destOff:destOff+maxDestLen]</code> and returns the number of
   * decompressed bytes written into <code>dest</code>.
   *
   * @param srcLen the exact size of the compressed stream
   * @return the original input size
   * @throws LZ4Exception if maxDestLen is too small
   */
  public abstract int decompress(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff, int maxDestLen);

  /**
   * Convenience method. Equivalent to calling
   * {@link #decompress(byte[], int, int, byte[], int, int)} with
   * <code>maxDestLen = dest.length - destOff</code>.
   */
  public final int decompress(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff) {
    return decompress(src, srcOff, srcLen, dest, destOff, dest.length - destOff);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName();
  }

}