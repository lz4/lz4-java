package net.jpountz.xxhash;



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
 * Entry point to get {@link XXHash32} instances.
 */
public final class XXHashFactory {

  private static XXHashFactory instance(String impl) {
    try {
      return new XXHashFactory(impl);
    } catch (ClassNotFoundException e) {
      throw new AssertionError(e);
    }
  }

  /** Return a {@link XXHashFactory} that returns {@link XXHash32} instances that
   *  are native bindings to the original C API. */
  public static XXHashFactory nativeInstance() {
    return instance("JNI");
  }

  /** Return a {@link XXHashFactory} that returns {@link XXHash32} instances that
   *  are written with Java's official API. */
  public static XXHashFactory safeInstance() {
    return instance("JavaSafe");
  }

  /** Return a {@link XXHashFactory} that returns {@link XXHash32} instances that
   *  may use {@link sun.misc.Unsafe} to speed up hashing. */
  public static XXHashFactory unsafeInstance() {
    return instance("JavaUnsafe");
  }

  /**
   * Return the fastest available {@link XXHashFactory} instance. This method
   * will first try to load the native instance, then the unsafe java one and
   * finally the safe java one if the JVM doesn't have {@link sun.misc.Unsafe}
   * support.
   */
  public static XXHashFactory fastestInstance() {
    try {
      return nativeInstance();
    } catch (Throwable t1) {
      try {
        return unsafeInstance();
      } catch (Throwable t2) {
        return safeInstance();
      }
    }
  }

  private final XXHash32 hash32;

  private XXHashFactory(String impl) throws ClassNotFoundException {
    final Class<?> xxHashEnum = Class.forName(XXHash32.class.getName() + impl);
    if (!XXHash32.class.isAssignableFrom(xxHashEnum)) {
      throw new AssertionError();
    }
    @SuppressWarnings("unchecked")
    XXHash32[] xxHashs32 = ((Class<? extends XXHash32>) xxHashEnum).getEnumConstants();
    if (xxHashs32.length != 1) {
      throw new AssertionError();
    }
    this.hash32 = xxHashs32[0];

    // make sure it can run
    hash32.hash(new byte[18], 0, 18, 42);
  }

  /** Return a {@link XXHash32} instance. */
  public XXHash32 hash32() {
    return hash32;
  }

}
