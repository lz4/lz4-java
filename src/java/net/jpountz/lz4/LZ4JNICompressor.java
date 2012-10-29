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

import static net.jpountz.util.Utils.checkRange;

/**
 * {@link LZ4Compressor}s implemented with JNI bindings to the original C
 * implementation of LZ4.
 */
enum LZ4JNICompressor implements LZ4Compressor {
  FAST {
    public int compress(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff, int maxDestLen) {
      checkRange(src, srcOff, srcLen);
      checkRange(dest, destOff, maxDestLen);
      final int result = LZ4JNI.LZ4_compress_limitedOutput(src, srcOff, srcLen, dest, destOff, maxDestLen);
      if (result <= 0) {
        throw new LZ4Exception("maxDestLen is too small");
      }
      return result;
    }
  },

  HIGH_COMPRESSION {
    public int compress(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff, int maxDestLen) {
      checkRange(src, srcOff, srcLen);
      checkRange(dest, destOff, maxDestLen);
      if (maxDestLen < maxCompressedLength(srcLen)) {
        throw new LZ4Exception("This compressor does not support output buffers whose size is < maxCompressedLength(srcLen)");
      }
      final int result = LZ4JNI.LZ4_compressHC(src, srcOff, srcLen, dest, destOff);
      if (result <= 0) {
        throw new LZ4Exception();
      }
      return result;
    }
  };

  public final int maxCompressedLength(int length) {
    if (length < 0) {
      throw new IllegalArgumentException("length must be >= 0, got " + length);
    }
    return LZ4JNI.LZ4_compressBound(length);
  }
}
