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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

import net.jpountz.util.Native;
import net.jpountz.util.Utils;
import static net.jpountz.lz4.LZ4Constants.DEFAULT_COMPRESSION_LEVEL;
import static net.jpountz.lz4.LZ4Constants.MAX_COMPRESSION_LEVEL;

/**
 * Entry point for the LZ4 API.
 * <p>
 * This class has 3 instances<ul>
 * <li>a {@link #nativeInstance() native} instance which is a JNI binding to
 * <a href="http://code.google.com/p/lz4/">the original LZ4 C implementation</a>.
 * <li>a {@link #safeInstance() safe Java} instance which is a pure Java port
 * of the original C library,</li>
 * <li>an {@link #unsafeInstance() unsafe Java} instance which is a Java port
 * using the unofficial {@link sun.misc.Unsafe} API.
 * </ul>
 * <p>
 * Only the {@link #safeInstance() safe instance} is guaranteed to work on your
 * JVM, as a consequence it is advised to use the {@link #fastestInstance()} or
 * {@link #fastestJavaInstance()} to pull a {@link LZ4Factory} instance.
 * <p>
 * All methods from this class are very costly, so you should get an instance
 * once, and then reuse it whenever possible. This is typically done by storing
 * a {@link LZ4Factory} instance in a static field.
 */
public final class LZ4Factory {

  private static LZ4Factory instance(String impl) {
    try {
      return new LZ4Factory(impl);
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  private static LZ4Factory NATIVE_INSTANCE,
                            JAVA_UNSAFE_INSTANCE,
                            JAVA_SAFE_INSTANCE;

  /**
   * Return a {@link LZ4Factory} instance that returns compressors and
   * decompressors that are native bindings to the original C library.
   * <p>
   * Please note that this instance has some traps you should be aware of:<ol>
   * <li>Upon loading this instance, files will be written to the temporary
   * directory of the system. Although these files are supposed to be deleted
   * when the JVM exits, they might remain on systems that don't support
   * removal of files being used such as Windows.
   * <li>The instance can only be loaded once per JVM. This can be a problem
   * if your application uses multiple class loaders (such as most servlet
   * containers): this instance will only be available to the children of the
   * class loader which has loaded it. As a consequence, it is advised to
   * either not use this instance in webapps or to put this library in the lib
   * directory of your servlet container so that it is loaded by the system
   * class loader.
   * </ol>
   */
  public static synchronized LZ4Factory nativeInstance() {
    if (NATIVE_INSTANCE == null) {
      NATIVE_INSTANCE = instance("JNI");
    }
    return NATIVE_INSTANCE;
  }

  /** Return a {@link LZ4Factory} instance that returns compressors and
   *  decompressors that are written with Java's official API. */
  public static synchronized LZ4Factory safeInstance() {
    if (JAVA_SAFE_INSTANCE == null) {
      JAVA_SAFE_INSTANCE = instance("JavaSafe");
    }
    return JAVA_SAFE_INSTANCE;
  }

  /** Return a {@link LZ4Factory} instance that returns compressors and
   *  decompressors that may use {@link sun.misc.Unsafe} to speed up compression
   *  and decompression. */
  public static synchronized LZ4Factory unsafeInstance() {
    if (JAVA_UNSAFE_INSTANCE == null) {
      JAVA_UNSAFE_INSTANCE = instance("JavaUnsafe");
    }
    return JAVA_UNSAFE_INSTANCE;
  }

  /**
   * Return the fastest available {@link LZ4Factory} instance which does not
   * rely on JNI bindings. It first tries to load the
   * {@link #unsafeInstance() unsafe instance}, and then the
   * {@link #safeInstance() safe Java instance} if the JVM doesn't have a
   * working {@link sun.misc.Unsafe}.
   */
  public static LZ4Factory fastestJavaInstance() {
    if (Utils.isUnalignedAccessAllowed()) {
      try {
        return unsafeInstance();
      } catch (Throwable t) {
        return safeInstance();
      }
    } else {
      return safeInstance();
    }
  }

  /**
   * Return the fastest available {@link LZ4Factory} instance. If the class
   * loader is the system class loader and if the
   * {@link #nativeInstance() native instance} loads successfully, then the
   * {@link #nativeInstance() native instance} is returned, otherwise the
   * {@link #fastestJavaInstance() fastest Java instance} is returned.
   * <p>
   * Please read {@link #nativeInstance() javadocs of nativeInstance()} before
   * using this method.
   */
  public static LZ4Factory fastestInstance() {
    if (Native.isLoaded()
        || Native.class.getClassLoader() == ClassLoader.getSystemClassLoader()) {
      try {
        return nativeInstance();
      } catch (Throwable t) {
        return fastestJavaInstance();
      }
    } else {
      return fastestJavaInstance();
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T classInstance(String cls) throws NoSuchFieldException, SecurityException, ClassNotFoundException, IllegalArgumentException, IllegalAccessException {
    ClassLoader loader = LZ4Factory.class.getClassLoader();
    loader = loader == null ? ClassLoader.getSystemClassLoader() : loader;
    final Class<?> c = loader.loadClass(cls);
    Field f = c.getField("INSTANCE");
    return (T) f.get(null);
  }

  private final String impl;
  private final LZ4Compressor fastCompressor;
  private final LZ4Compressor highCompressor;
  private final LZ4FastDecompressor fastDecompressor;
  private final LZ4SafeDecompressor safeDecompressor;
  private final LZ4Compressor[] highCompressors = new LZ4Compressor[MAX_COMPRESSION_LEVEL+1];

  private LZ4Factory(String impl) throws ClassNotFoundException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, NoSuchMethodException, InstantiationException, InvocationTargetException {
    this.impl = impl;
    fastCompressor = classInstance("net.jpountz.lz4.LZ4" + impl + "Compressor");
    highCompressor = classInstance("net.jpountz.lz4.LZ4HC" + impl + "Compressor");
    fastDecompressor = classInstance("net.jpountz.lz4.LZ4" + impl + "FastDecompressor");
    safeDecompressor = classInstance("net.jpountz.lz4.LZ4" + impl + "SafeDecompressor");
    Constructor<? extends LZ4Compressor> highConstructor = highCompressor.getClass().getDeclaredConstructor(int.class);
    highCompressors[DEFAULT_COMPRESSION_LEVEL] = highCompressor;
    for(int level = 1; level <= MAX_COMPRESSION_LEVEL; level++) {
      if(level == DEFAULT_COMPRESSION_LEVEL) continue;
      highCompressors[level] = highConstructor.newInstance(level);
    }

    // quickly test that everything works as expected
    final byte[] original = new byte[] {'a','b','c','d',' ',' ',' ',' ',' ',' ','a','b','c','d','e','f','g','h','i','j'};
    for (LZ4Compressor compressor : Arrays.asList(fastCompressor, highCompressor)) {
      final int maxCompressedLength = compressor.maxCompressedLength(original.length);
      final byte[] compressed = new byte[maxCompressedLength];
      final int compressedLength = compressor.compress(original, 0, original.length, compressed, 0, maxCompressedLength);
      final byte[] restored = new byte[original.length];
      fastDecompressor.decompress(compressed, 0, restored, 0, original.length);
      if (!Arrays.equals(original, restored)) {
        throw new AssertionError();
      }
      Arrays.fill(restored, (byte) 0);
      final int decompressedLength = safeDecompressor.decompress(compressed, 0, compressedLength, restored, 0);
      if (decompressedLength != original.length || !Arrays.equals(original, restored)) {
        throw new AssertionError();
      }
    }

  }

  /** Return a blazing fast {@link LZ4Compressor}. */
  public LZ4Compressor fastCompressor() {
    return fastCompressor;
  }

  /** Return a {@link LZ4Compressor} which requires more memory than
   * {@link #fastCompressor()} and is slower but compresses more efficiently. */
  public LZ4Compressor highCompressor() {
    return highCompressor;
  }

  /** Return a {@link LZ4Compressor} which requires more memory than
   * {@link #fastCompressor()} and is slower but compresses more efficiently.
   * The compression level can be customized.
   * <p>For current implementations, the following is true about compression level:</p>
   * <ol>
   *   <li>It should be in range [1, 17]</li>
   *   <li>A compression level higher than 17 would be treated as 17.</li>
   *   <li>A compression level lower than 1 would be treated as 9.</li>
   * </ol>
   */
  public LZ4Compressor highCompressor(int compressionLevel) {
    if(compressionLevel > MAX_COMPRESSION_LEVEL) {
      compressionLevel = MAX_COMPRESSION_LEVEL;
    } else if(compressionLevel < 1) {
      compressionLevel = DEFAULT_COMPRESSION_LEVEL;
    }
    return highCompressors[compressionLevel];
  }

  /** Return a {@link LZ4FastDecompressor} instance. */
  public LZ4FastDecompressor fastDecompressor() {
    return fastDecompressor;
  }

  /** Return a {@link LZ4SafeDecompressor} instance. */
  public LZ4SafeDecompressor safeDecompressor() {
    return safeDecompressor;
  }

  /** Return a {@link LZ4UnknownSizeDecompressor} instance.
   * @deprecated use {@link #safeDecompressor()} */
  public LZ4UnknownSizeDecompressor unknownSizeDecompressor() {
    return safeDecompressor();
  }

  /** Return a {@link LZ4Decompressor} instance.
   * @deprecated use {@link #fastDecompressor()} */
  public LZ4Decompressor decompressor() {
    return fastDecompressor();
  }

  /** Prints the fastest instance. */
  public static void main(String[] args) {
    System.out.println("Fastest instance is " + fastestInstance());
    System.out.println("Fastest Java instance is " + fastestJavaInstance());
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + ":" + impl;
  }

}
