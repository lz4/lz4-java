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

import static net.jpountz.xxhash.XXHashConstants.PRIME1;
import static net.jpountz.xxhash.XXHashConstants.PRIME2;

abstract class AbstractStreamingXXHash32Java extends StreamingXXHash32 {

  int v1, v2, v3, v4, memSize;
  long totalLen;
  final byte[] memory;

  AbstractStreamingXXHash32Java(int seed) {
    super(seed);
    memory = new byte[16];
    reset();
  }

  @Override
  public void reset() {
    v1 = seed + PRIME1 + PRIME2;
    v2 = seed + PRIME2;
    v3 = seed + 0;
    v4 = seed - PRIME1;
    totalLen = 0;
    memSize = 0;
  }

}
