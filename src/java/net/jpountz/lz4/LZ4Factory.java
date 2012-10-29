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

/**
 * Entry point for the LZ4 API.
 */
public final class LZ4Factory {

  private static LZ4Factory instance(String impl) {
    try {
      return new LZ4Factory(impl);
    } catch (ClassNotFoundException e) {
      throw new AssertionError(e);
    }
  }

  /** Return a {@link LZ4Factory} instance that returns compressors and
   *  uncompressors that are native bindings to the original C API. */
  public static LZ4Factory nativeInstance() {
    return instance("JNI");
  }

  /** Return a {@link LZ4Factory} instance that returns compressors and
   *  uncompressors that are written with Java's official API. */
  public static LZ4Factory safeInstance() {
    return instance("JavaSafe");
  }

  /** Return a {@link LZ4Factory} instance that returns compressors and
   *  uncompressors that may use {@link sun.misc.Unsafe} to speed up compression
   *  and uncompression. */
  public static LZ4Factory unsafeInstance() {
    return instance("JavaUnsafe");
  }

  private final LZ4Compressor fastCompressor;
  private final LZ4Compressor highCompressor;
  private final LZ4Uncompressor uncompressor;
  private final LZ4UnknownSizeUncompressor unknwonSizeUncompressor;

  private LZ4Factory(String impl) throws ClassNotFoundException {
    final Class<?> compressorEnum = Class.forName("net.jpountz.lz4.LZ4" + impl + "Compressor");
    if (!LZ4Compressor.class.isAssignableFrom(compressorEnum)) {
      throw new AssertionError();
    }
    @SuppressWarnings("unchecked")
    LZ4Compressor[] compressors = ((Class<? extends LZ4Compressor>) compressorEnum).getEnumConstants();
    if (compressors.length != 2) {
      throw new AssertionError();
    }
    fastCompressor = compressors[0];
    highCompressor = compressors[1];

    final Class<?> uncompressorEnum = Class.forName("net.jpountz.lz4.LZ4" + impl + "Uncompressor");
    if (!LZ4Uncompressor.class.isAssignableFrom(uncompressorEnum)) {
      throw new AssertionError();
    }
    @SuppressWarnings("unchecked")
    LZ4Uncompressor[] uncompressors = ((Class<? extends LZ4Uncompressor>) uncompressorEnum).getEnumConstants();
    if (uncompressors.length != 1) {
      throw new AssertionError();
    }
    uncompressor = uncompressors[0];
    if (!(uncompressor instanceof LZ4UnknownSizeUncompressor)) {
      throw new AssertionError();
    }
    unknwonSizeUncompressor = (LZ4UnknownSizeUncompressor) uncompressor;
  }

  /** Return a {@link LZ4Compressor} that uses a fast-scan method to compress
   *  data. */
  public LZ4Compressor fastCompressor() {
    return fastCompressor;
  }

  /** Return a {@link LZ4Compressor} that uses more CPU and memory to compress
   *  data in order to improve the compression ratio. */
  public LZ4Compressor highCompressor() {
    return highCompressor;
  }

  /** Return a {@link LZ4Uncompressor} instance. */
  public LZ4Uncompressor uncompressor() {
    return uncompressor;
  }

  /** Return a {@link LZ4UnknownSizeUncompressor} instance. */
  public LZ4UnknownSizeUncompressor unknwonSizeUncompressor() {
    return unknwonSizeUncompressor;
  }

}
