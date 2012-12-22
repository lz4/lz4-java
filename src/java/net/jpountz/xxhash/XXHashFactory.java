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

import java.util.Random;

/**
 * Entry point to get {@link XXHash32} and {@link StreamingXXHash32} instances.
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

  private final String impl;
  private final XXHash32 hash32;
  private final StreamingXXHash32Factory streamingHash32Factory;

  private XXHashFactory(String impl) throws ClassNotFoundException {
    this.impl = impl;
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

    final Class<?> streamingXXHashFactoryEnum = Class.forName(StreamingXXHash32Factory.class.getName() + impl);
    if (!StreamingXXHash32Factory.class.isAssignableFrom(streamingXXHashFactoryEnum)) {
      throw new AssertionError();
    }
    @SuppressWarnings("unchecked")
    StreamingXXHash32Factory[] streamingXXHash32Factories = ((Class<? extends StreamingXXHash32Factory>) streamingXXHashFactoryEnum).getEnumConstants();
    if (streamingXXHash32Factories.length != 1) {
      throw new AssertionError();
    }
    this.streamingHash32Factory = streamingXXHash32Factories[0];

    // make sure it can run
    final byte[] bytes = new byte[100];
    final Random random = new Random();
    random.nextBytes(bytes);
    final int seed = random.nextInt();

    final int h1 = hash32.hash(bytes, 0, bytes.length, seed);
    final StreamingXXHash32 streamingHash32 = newStreamingHash32(seed);
    streamingHash32.update(bytes, 0, bytes.length);
    final int h2 = streamingHash32.getValue();
    if (h1 != h2) {
      throw new AssertionError();
    }
  }

  /** Return a {@link XXHash32} instance. */
  public XXHash32 hash32() {
    return hash32;
  }

  /**
   * Return a new {@link StreamingXXHash32} instance.
   */
  public StreamingXXHash32 newStreamingHash32(int seed) {
    return streamingHash32Factory.newStreamingHash(seed);
  }

  /** Prints the fastest instance. */
  public static void main(String[] args) {
    System.out.println("Fastest instance is " + fastestInstance());
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + ":" + impl;
  }

}
