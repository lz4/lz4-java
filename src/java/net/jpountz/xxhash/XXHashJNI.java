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

import static net.jpountz.util.Utils.checkRange;
import net.jpountz.util.Native;

enum XXHashJNI implements XXHash {

  FAST {

    @Override
    public int hash(byte[] buf, int off, int len, int seed) {
      checkRange(buf, off, len);
      return XXH_fast32(buf, off, len, seed);
    }

  },

  STRONG {

    @Override
    public int hash(byte[] buf, int off, int len, int seed) {
      checkRange(buf, off, len);
      return XXH_strong32(buf, off, len, seed);
    }

  };

  static {
    Native.load();
    init();
  }

  private static native void init();
  private static native int XXH_fast32(byte[] input, int offset, int len, int seed);
  private static native int XXH_strong32(byte[] input, int offset, int len, int seed);

  @Override
  public String toString() {
    return getDeclaringClass().getSimpleName() + "-" + super.toString();
  }

}
