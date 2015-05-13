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

import net.jpountz.util.ByteBufferUtils;
import net.jpountz.util.SafeUtils;


/**
 * {@link LZ4FastDecompressor} implemented with JNI bindings to the original C
 * implementation of LZ4.
 */
final class LZ4JNIFastDecompressor extends LZ4FastDecompressor {

  public static final LZ4JNIFastDecompressor INSTANCE = new LZ4JNIFastDecompressor();
  private static LZ4FastDecompressor SAFE_INSTANCE;

  @Override
  public final int decompress(byte[] src, int srcOff, byte[] dest, int destOff, int destLen) {
    SafeUtils.checkRange(src, srcOff);
    SafeUtils.checkRange(dest, destOff, destLen);
    final int result = LZ4JNI.LZ4_decompress_fast(src, null, srcOff, dest, null, destOff, destLen);
    if (result < 0) {
      throw new LZ4Exception("Error decoding offset " + (srcOff - result) + " of input buffer");
    }
    return result;
  }

  @Override
  public int decompress(ByteBuffer src, int srcOff, ByteBuffer dest, int destOff, int destLen) {
    ByteBufferUtils.checkNotReadOnly(dest);
    ByteBufferUtils.checkRange(src, srcOff);
    ByteBufferUtils.checkRange(dest, destOff, destLen);

    if ((src.hasArray() || src.isDirect()) && (dest.hasArray() || dest.isDirect())) {
      byte[] srcArr = null, destArr = null;
      ByteBuffer srcBuf = null, destBuf = null;
      if (src.hasArray()) {
        srcArr = src.array();
        srcOff += src.arrayOffset();
      } else {
        assert src.isDirect();
        srcBuf = src;
      }
      if (dest.hasArray()) {
        destArr = dest.array();
        destOff += dest.arrayOffset();
      } else {
        assert dest.isDirect();
        destBuf = dest;
      }

      final int result = LZ4JNI.LZ4_decompress_fast(srcArr, srcBuf, srcOff, destArr, destBuf, destOff, destLen);
      if (result < 0) {
        throw new LZ4Exception("Error decoding offset " + (srcOff - result) + " of input buffer");
      }
      return result;
    } else {
      LZ4FastDecompressor safeInstance = SAFE_INSTANCE;
      if (safeInstance == null) {
        safeInstance = SAFE_INSTANCE = LZ4Factory.safeInstance().fastDecompressor();
      }
      return safeInstance.decompress(src, srcOff, dest, destOff, destLen);
    }
  }

}
