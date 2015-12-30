package net.jpountz.lz4;

import java.io.IOException;

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

import net.jpountz.util.Native;


/**
 * JNI bindings to the original C implementation of LZ4.
 */
enum LZ4JNI {
  ;
  
  /**
   * Error returned by lz4frame. If an error occurred, `message` will be non-null.
   */
  public static class LZ4FError {
    String message = null;

    /**
     * Check if the error was set.
     * 
     * @throws IOException
     *           If an error was set.
     */
    public void check() throws IOException {
      if (message != null)
        throw new IOException(message);
    }
  }

  static {
    Native.load();
    init();
  }

  static native void init();
  
  // Block-based compression
  static native int LZ4_compress_limitedOutput(byte[] srcArray, ByteBuffer srcBuffer, int srcOff, int srcLen, byte[] destArray, ByteBuffer destBuffer, int destOff, int maxDestLen);
  static native int LZ4_compressHC(byte[] srcArray, ByteBuffer srcBuffer, int srcOff, int srcLen, byte[] destArray, ByteBuffer destBuffer, int destOff, int maxDestLen, int compressionLevel);
  static native int LZ4_compressBound(int len);
  
  // Block-based decompression
  static native int LZ4_decompress_fast(byte[] srcArray, ByteBuffer srcBuffer, int srcOff, byte[] destArray, ByteBuffer destBuffer, int destOff, int destLen);
  static native int LZ4_decompress_safe(byte[] srcArray, ByteBuffer srcBuffer, int srcOff, int srcLen, byte[] destArray, ByteBuffer destBuffer, int destOff, int maxDestLen);
  

  // Frame-based compression
  static native long LZ4F_createCompressionContext(LZ4FError error);
  static native void LZ4F_freeCompressionContext(long context, LZ4FError error);
  
  static native int LZ4F_compressBegin(long context, int compressionLevel, byte[] dstArray, int dstOffset, int dstLen, LZ4FError error);
  static native int LZ4F_compressBound(int srcSize);
  static native int LZ4F_compressUpdate(long context, byte[] srcArray, int srcOffset, int srcLen, byte[] dstArray, int dstOffset, int dstLen, LZ4FError error);
  static native int LZ4F_compressEnd(long context, byte[] dstArray, int dstOffset, int dstLen, LZ4FError error);
  static native int LZ4F_compressFlush(long context, byte[] dstArray, int dstOffset, int dstLen, LZ4FError error);

  // Frame-based decompression
  static native long LZ4F_createDecompressionContext(LZ4FError error);
  static native void LZ4F_freeDecompressionContext(long context, LZ4FError error);
  
  /**
   * Try to decompress an array of bytes.
   * 
   * @param context Decompression context
   * @param srcArray Source buffer
   * @param srcOff Place to start in the source buffer
   * @param srcLen Number of bytes to read after the offset
   * @param dstArray Destination buffer
   * @param dstOff Place to start in the destination buffer
   * @param dstLen Number of bytes available after the offset
   * @param error Structure that will be filled if an error occur
   * @return <ul>
   * <li>A positive value indicating the number of bytes written if all the source could be read</li>
   * <li>A negative value indicating minus the number of bytes read if the destination buffer is full</li>
   * <li>0 if an error occurred. <code>error</code> will be filled in this case.
   * </ul>
   */
  static native int LZ4F_decompress(long context, 
     byte[] srcArray, int srcOff, int srcLen, 
     byte[] dstArray, int dstOff, int dstLen, 
     LZ4FError error);
}

