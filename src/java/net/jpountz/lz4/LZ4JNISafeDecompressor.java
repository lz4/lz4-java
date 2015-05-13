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
 * {@link LZ4SafeDecompressor} implemented with JNI bindings to the original C
 * implementation of LZ4.
 */
final class LZ4JNISafeDecompressor extends LZ4SafeDecompressor {

  public static final LZ4JNISafeDecompressor INSTANCE = new LZ4JNISafeDecompressor();
  private static LZ4SafeDecompressor SAFE_INSTANCE;

  @Override
  public final int decompress(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff, int maxDestLen) {
    SafeUtils.checkRange(src, srcOff, srcLen);
    SafeUtils.checkRange(dest, destOff, maxDestLen);
    final int result = LZ4JNI.LZ4_decompress_safe(src, null, srcOff, srcLen, dest, null, destOff, maxDestLen);
    if (result < 0) {
      throw new LZ4Exception("Error decoding offset " + (srcOff - result) + " of input buffer");
    }
    return result;
  }

  @Override
  public int decompress(ByteBuffer src, int srcOff, int srcLen, ByteBuffer dest, int destOff, int maxDestLen) {
    ByteBufferUtils.checkNotReadOnly(dest);
    ByteBufferUtils.checkRange(src, srcOff, srcLen);
    ByteBufferUtils.checkRange(dest, destOff, maxDestLen);

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

      final int result = LZ4JNI.LZ4_decompress_safe(srcArr, srcBuf, srcOff, srcLen, destArr, destBuf, destOff, maxDestLen);
      if (result < 0) {
        throw new LZ4Exception("Error decoding offset " + (srcOff - result) + " of input buffer");
      }
      return result;
    } else {
      LZ4SafeDecompressor safeInstance = SAFE_INSTANCE;
      if (safeInstance == null) {
        safeInstance = SAFE_INSTANCE = LZ4Factory.safeInstance().safeDecompressor();
      }
      return safeInstance.decompress(src, srcOff, srcLen, dest, destOff, maxDestLen);
    }
  }

}
