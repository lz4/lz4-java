package net.jpountz.lz4;

/*
 * Copyright 2020 Adrien Grand and the lz4-java contributors.
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

import net.jpountz.util.Native;


/**
 * JNI bindings to the original C implementation of LZ4.
 */
enum LZ4JNI {
  ;

  static {
    Native.load();
    init();
  }

  static native void init();
  static native int LZ4_compress_limitedOutput(byte[] srcArray, ByteBuffer srcBuffer, int srcOff, int srcLen, byte[] destArray, ByteBuffer destBuffer, int destOff, int maxDestLen);
  static native int LZ4_compress_fast_continue(byte[] srcArray, ByteBuffer srcBuffer, int srcOff, int srcLen, byte[] destArray, ByteBuffer destBuffer, int destOff, int maxDestLen, int acceleration);
  static native int LZ4_compressHC(byte[] srcArray, ByteBuffer srcBuffer, int srcOff, int srcLen, byte[] destArray, ByteBuffer destBuffer, int destOff, int maxDestLen, int compressionLevel);
  static native int LZ4_decompress_fast(byte[] srcArray, ByteBuffer srcBuffer, int srcOff, byte[] destArray, ByteBuffer destBuffer, int destOff, int destLen);
  static native int LZ4_decompress_safe(byte[] srcArray, ByteBuffer srcBuffer, int srcOff, int srcLen, byte[] destArray, ByteBuffer destBuffer, int destOff, int maxDestLen);
  static native int LZ4_compressBound(int len);

}

