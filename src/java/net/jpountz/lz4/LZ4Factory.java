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
 * <p>
 * This class has 3 instances<ul>
 * <li>a {@link #nativeInstance() native} instance which is a JNI binding to
 * the original LZ4 implementation.</li>
 * <li>a {@link #safeInstance() safe Java} instance which is a pure Java port
 * of the original C library,</li>
 * <li>an {@link #unsafeInstance() unsafe Java} instance which is a Java port
 * using the unofficial {@link sun.misc.Unsafe} API.</li>
 * </ul>
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
   *  decompressors that are native bindings to the original C API.<p>
   *  Although this instance is likely to be slightly faster on large inputs,
   *  beware that the JNI overhead might make it much slower than its
   *  Java counterparts on small inputs. */
  public static LZ4Factory nativeInstance() {
    return instance("JNI");
  }

  /** Return a {@link LZ4Factory} instance that returns compressors and
   *  decompressors that are written with Java's official API. */
  public static LZ4Factory safeInstance() {
    return instance("JavaSafe");
  }

  /** Return a {@link LZ4Factory} instance that returns compressors and
   *  decompressors that may use {@link sun.misc.Unsafe} to speed up compression
   *  and decompression. */
  public static LZ4Factory unsafeInstance() {
    return instance("JavaUnsafe");
  }

  /** Return a default {@link LZ4Factory} instance. This method tries to return
   * the {@link #unsafeInstance()} and falls back on the {@link #safeInstance()}
   * in case an error occurred while loading the {@link #unsafeInstance() unsafe}
   * instance.
   * <pre>
   * try {
   *   return unsafeInstance();
   * } catch (Throwable t) {
   *   return safeInstance();
   * }
   * </pre>
   */
  public static LZ4Factory defaultInstance() {
    try {
      return unsafeInstance();
    } catch (Throwable t) {
      return safeInstance();
    }
  }

  private final LZ4Compressor fastCompressor;
  private final LZ4Compressor highCompressor;
  private final LZ4Decompressor decompressor;
  private final LZ4UnknownSizeDecompressor unknownSizeDecompressor;

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

    final Class<?> decompressorEnum = Class.forName("net.jpountz.lz4.LZ4" + impl + "Decompressor");
    if (!LZ4Decompressor.class.isAssignableFrom(decompressorEnum)) {
      throw new AssertionError();
    }
    @SuppressWarnings("unchecked")
    LZ4Decompressor[] decompressors = ((Class<? extends LZ4Decompressor>) decompressorEnum).getEnumConstants();
    if (decompressors.length != 1) {
      throw new AssertionError();
    }
    decompressor = decompressors[0];

    final Class<?> unknownSizeDecompressorEnum = Class.forName("net.jpountz.lz4.LZ4" + impl + "UnknownSizeDecompressor");
    if (!LZ4UnknownSizeDecompressor.class.isAssignableFrom(unknownSizeDecompressorEnum)) {
      throw new AssertionError();
    }
    @SuppressWarnings("unchecked")
    LZ4UnknownSizeDecompressor[] unknownSizeDecompressors = ((Class<? extends LZ4UnknownSizeDecompressor>) unknownSizeDecompressorEnum).getEnumConstants();
    if (decompressors.length != 1) {
      throw new AssertionError();
    }
    unknownSizeDecompressor = unknownSizeDecompressors[0];
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

  /** Return a {@link LZ4Decompressor} instance. */
  public LZ4Decompressor decompressor() {
    return decompressor;
  }

  /** Return a {@link LZ4UnknownSizeDecompressor} instance. */
  public LZ4UnknownSizeDecompressor unknwonSizeDecompressor() {
    return unknownSizeDecompressor;
  }

}
