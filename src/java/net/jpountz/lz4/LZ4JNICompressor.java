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

/**
 * Fast {@link LZ4FastCompressor}s implemented with JNI bindings to the original C
 * implementation of LZ4.
 */
final class LZ4JNICompressor extends LZ4Compressor {

  public static final LZ4Compressor INSTANCE = new LZ4JNICompressor();

  @Override
  public int compress(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff, int maxDestLen) {
    checkRange(src, srcOff, srcLen);
    checkRange(dest, destOff, maxDestLen);
    final int result = LZ4JNI.LZ4_compress_limitedOutput(src, srcOff, srcLen, dest, destOff, maxDestLen);
    if (result <= 0) {
      throw new LZ4Exception("maxDestLen is too small");
    }
    return result;
  }
}
