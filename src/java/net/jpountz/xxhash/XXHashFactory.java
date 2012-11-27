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
 * Entry point to get {@link XXHash} instances.
 */
public final class XXHashFactory {

  private static XXHashFactory instance(String impl) {
    try {
      return new XXHashFactory(impl);
    } catch (ClassNotFoundException e) {
      throw new AssertionError(e);
    }
  }

  /** Return a {@link XXHashFactory} that returns {@link XXHash} instances that
   *  are native bindings to the original C API. */
  public static XXHashFactory nativeInstance() {
    return instance("JNI");
  }

  /** Return a {@link XXHashFactory} that returns {@link XXHash} instances that
   *  are written with Java's official API. */
  public static XXHashFactory safeInstance() {
    return instance("JavaSafe");
  }

  /** Return a {@link XXHashFactory} that returns {@link XXHash} instances that
   *  may use {@link sun.misc.Unsafe} to speed up hashing. */
  public static XXHashFactory unsafeInstance() {
    return instance("JavaUnsafe");
  }

  /** Return a default {@link XXHashFactory} instance. This method tries to return
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
  public static XXHashFactory defaultInstance() {
    try {
      return unsafeInstance();
    } catch (Throwable t) {
      return safeInstance();
    }
  }

  private final XXHash fastHash;
  private final XXHash strongHash;

  private XXHashFactory(String impl) throws ClassNotFoundException {
    final Class<?> xxHashEnum = Class.forName(XXHash.class.getName() + impl);
    if (!XXHash.class.isAssignableFrom(xxHashEnum)) {
      throw new AssertionError();
    }
    @SuppressWarnings("unchecked")
    XXHash[] xxHashs = ((Class<? extends XXHash>) xxHashEnum).getEnumConstants();
    if (xxHashs.length != 2) {
      throw new AssertionError();
    }
    this.fastHash = xxHashs[0];
    this.strongHash = xxHashs[1];
  }

  /** Return a fast {@link XXHash} instance. */
  public XXHash fastHash() {
    return fastHash;
  }

  /** Return a strong {@link XXHash} instance. */
  public XXHash strongHash() {
    return strongHash;
  }

}
