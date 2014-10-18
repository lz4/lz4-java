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

import static net.jpountz.util.Utils.checkRange;
import static net.jpountz.util.ByteBufferUtils.checkRange;
import static net.jpountz.util.ByteBufferUtils.checkNotReadOnly;

import java.nio.ByteBuffer;

import net.jpountz.util.ByteBufferUtils;

/**
 * {@link LZ4SafeDecompressor} implemented with JNI bindings to the original C
 * implementation of LZ4.
 */
final class LZ4JNISafeDecompressor extends LZ4SafeDecompressor {

  public static final LZ4SafeDecompressor INSTANCE = new LZ4JNISafeDecompressor();

  @Override
  public final int decompress(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff, int maxDestLen) {
    checkRange(src, srcOff, srcLen);
    checkRange(dest, destOff, maxDestLen);
    final int result = LZ4JNI.LZ4_decompress_safe(src, null, srcOff, srcLen, dest, null, destOff, maxDestLen);
    if (result < 0) {
      throw new LZ4Exception("Error decoding offset " + (srcOff - result) + " of input buffer");
    }
    return result;
  }

  @Override
  public int decompress(ByteBuffer src, int srcOff, int srcLen, ByteBuffer dest, int destOff, int maxDestLen) {
    checkRange(src, srcOff, srcLen);
    checkRange(dest, destOff, maxDestLen);
    checkNotReadOnly(dest);
    if (!src.isDirect() && src.isReadOnly()) {
      // JNI can't access data in this case. Fall back to Java implementation.
      return LZ4Factory.fastestJavaInstance().safeDecompressor().
          decompress(src, srcOff, srcLen, dest, destOff, maxDestLen);
    }

    int result = LZ4JNI.LZ4_decompress_safe(
        ByteBufferUtils.getArray(src), src, srcOff, srcLen,
        ByteBufferUtils.getArray(dest), dest, destOff, maxDestLen);
    if (result < 0) {
      throw new LZ4Exception("Error decoding offset " + (src.position() - result) + " of input buffer");
    }
    return result;
  }
}
