package net.jpountz.xxhash;

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

import java.lang.reflect.Field;
import java.util.Random;

import net.jpountz.util.Native;
import net.jpountz.util.Utils;

/**
 * Entry point to get {@link XXHash32} and {@link StreamingXXHash32} instances.
 * <p>
 * This class has 3 instances<ul>
 * <li>a {@link #nativeInstance() native} instance which is a JNI binding to
 * <a href="http://code.google.com/p/xxhash/">the original LZ4 C implementation</a>.
 * <li>a {@link #safeInstance() safe Java} instance which is a pure Java port
 * of the original C library,</li>
 * <li>an {@link #unsafeInstance() unsafe Java} instance which is a Java port
 * using the unofficial {@link sun.misc.Unsafe} API.
 * </ul>
 * <p>
 * Only the {@link #safeInstance() safe instance} is guaranteed to work on your
 * JVM, as a consequence it is advised to use the {@link #fastestInstance()} or
 * {@link #fastestJavaInstance()} to pull a {@link XXHashFactory} instance.
 * <p>
 * All methods from this class are very costly, so you should get an instance
 * once, and then reuse it whenever possible. This is typically done by storing
 * a {@link XXHashFactory} instance in a static field.
 */
public final class XXHashFactory {

  private static XXHashFactory instance(String impl) {
    try {
      return new XXHashFactory(impl);
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  private static XXHashFactory NATIVE_INSTANCE,
                               JAVA_UNSAFE_INSTANCE,
                               JAVA_SAFE_INSTANCE;

  /**
   * Returns a {@link XXHashFactory} that returns {@link XXHash32} instances that
   *  are native bindings to the original C API.
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
   *
   * @return a {@link XXHashFactory} that returns {@link XXHash32} instances that
   *  are native bindings to the original C API.
   */
  public static synchronized XXHashFactory nativeInstance() {
    if (NATIVE_INSTANCE == null) {
      NATIVE_INSTANCE = instance("JNI");
    }
    return NATIVE_INSTANCE;
  }

  /**
   * Returns a {@link XXHashFactory} that returns {@link XXHash32} instances that
   *  are written with Java's official API.
   *
   * @return a {@link XXHashFactory} that returns {@link XXHash32} instances that
   *  are written with Java's official API.
   */
  public static synchronized XXHashFactory safeInstance() {
    if (JAVA_SAFE_INSTANCE == null) {
      JAVA_SAFE_INSTANCE = instance("JavaSafe");
    }
    return JAVA_SAFE_INSTANCE;
  }

  /**
   * Returns a {@link XXHashFactory} that returns {@link XXHash32} instances that
   *  may use {@link sun.misc.Unsafe} to speed up hashing.
   *
   * @return a {@link XXHashFactory} that returns {@link XXHash32} instances that
   *  may use {@link sun.misc.Unsafe} to speed up hashing.
   */
  public static synchronized XXHashFactory unsafeInstance() {
    if (JAVA_UNSAFE_INSTANCE == null) {
      JAVA_UNSAFE_INSTANCE = instance("JavaUnsafe");
    }
    return JAVA_UNSAFE_INSTANCE;
  }

  /**
   * Returns the fastest available {@link XXHashFactory} instance which does not
   * rely on JNI bindings. It first tries to load the
   * {@link #unsafeInstance() unsafe instance}, and then the
   * {@link #safeInstance() safe Java instance} if the JVM doesn't have a
   * working {@link sun.misc.Unsafe}.
   *
   * @return the fastest available {@link XXHashFactory} instance which does not
   * rely on JNI bindings.
   */
  public static XXHashFactory fastestJavaInstance() {
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
   * Returns the fastest available {@link XXHashFactory} instance. If the class
   * loader is the system class loader and if the
   * {@link #nativeInstance() native instance} loads successfully, then the
   * {@link #nativeInstance() native instance} is returned, otherwise the
   * {@link #fastestJavaInstance() fastest Java instance} is returned.
   * <p>
   * Please read {@link #nativeInstance() javadocs of nativeInstance()} before
   * using this method.
   *
   * @return the fastest available {@link XXHashFactory} instance.
   */
  public static XXHashFactory fastestInstance() {
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
    ClassLoader loader = XXHashFactory.class.getClassLoader();
    loader = loader == null ? ClassLoader.getSystemClassLoader() : loader;
    final Class<?> c = loader.loadClass(cls);
    Field f = c.getField("INSTANCE");
    return (T) f.get(null);
  }

  private final String impl;
  private final XXHash32 hash32;
  private final XXHash64 hash64;
  private final StreamingXXHash32.Factory streamingHash32Factory;
  private final StreamingXXHash64.Factory streamingHash64Factory;

  private XXHashFactory(String impl) throws ClassNotFoundException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
    this.impl = impl;
    hash32 = classInstance("net.jpountz.xxhash.XXHash32" + impl);
    streamingHash32Factory = classInstance("net.jpountz.xxhash.StreamingXXHash32" + impl + "$Factory");
    hash64 = classInstance("net.jpountz.xxhash.XXHash64" + impl);
    streamingHash64Factory = classInstance("net.jpountz.xxhash.StreamingXXHash64" + impl + "$Factory");

    // make sure it can run
    final byte[] bytes = new byte[100];
    final Random random = new Random();
    random.nextBytes(bytes);
    final int seed = random.nextInt();

    final int h1 = hash32.hash(bytes, 0, bytes.length, seed);
    final StreamingXXHash32 streamingHash32 = newStreamingHash32(seed);
    streamingHash32.update(bytes, 0, bytes.length);
    final int h2 = streamingHash32.getValue();
    final long h3 = hash64.hash(bytes, 0, bytes.length, seed);
    final StreamingXXHash64 streamingHash64 = newStreamingHash64(seed);
    streamingHash64.update(bytes, 0, bytes.length);
    final long h4 = streamingHash64.getValue();
    if (h1 != h2) {
      throw new AssertionError();
    }
    if (h3 != h4) {
      throw new AssertionError();
    }
  }

  /**
   * Returns a {@link XXHash32} instance.
   *
   * @return a {@link XXHash32} instance.
   */
  public XXHash32 hash32() {
    return hash32;
  }

  /**
   * Returns a {@link XXHash64} instance.
   *
   * @return a {@link XXHash64} instance.
   */
  public XXHash64 hash64() {
    return hash64;
  }

  /**
   * Return a new {@link StreamingXXHash32} instance.
   *
   * @param seed the seed to use
   * @return a {@link StreamingXXHash32} instance
   */
  public StreamingXXHash32 newStreamingHash32(int seed) {
    return streamingHash32Factory.newStreamingHash(seed);
  }

  /**
   * Return a new {@link StreamingXXHash32} instance that continues from the saved state.
   *
   * @param savedState the saved state to use.
   * @return a {@link StreamingXXHash32} instance
   */
  public StreamingXXHash32 newStreamingHash32(XXHash32State savedState) {
    if (this != NATIVE_INSTANCE && savedState instanceof XXHash32JavaState) {
      return streamingHash32Factory.newStreamingHash(savedState);
    } else {
      throw new UnsupportedOperationException("Resumable state is only supported by the Java implementations");
    }
  }

  /**
   * Return a new {@link StreamingXXHash64} instance.
   *
   * @param seed the seed to use
   * @return a {@link StreamingXXHash64} instance
   */
  public StreamingXXHash64 newStreamingHash64(long seed) {
    return streamingHash64Factory.newStreamingHash(seed);
  }

  /**
   * Return a new {@link StreamingXXHash64} instance that continues from the saved state.
   *
   * @param savedState the saved state to use.
   * @return a {@link StreamingXXHash64} instance
   */
  public StreamingXXHash64 newStreamingHash64(XXHash64State savedState) {
    if (this != NATIVE_INSTANCE && savedState instanceof XXHash64JavaState) {
      return streamingHash64Factory.newStreamingHash(savedState);
    } else {
      throw new UnsupportedOperationException("Resumable state is only supported by the Java implementations");
    }
  }

  /**
   * Prints the fastest instance.
   *
   * @param args no argument required
   */
  public static void main(String[] args) {
    System.out.println("Fastest instance is " + fastestInstance());
    System.out.println("Fastest Java instance is " + fastestJavaInstance());
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + ":" + impl;
  }

}
