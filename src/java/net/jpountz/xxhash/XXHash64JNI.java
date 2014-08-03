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

import static net.jpountz.util.Utils.checkRange;

final class XXHash64JNI extends XXHash64 {

  public static final XXHash64 INSTANCE = new XXHash64JNI();

  @Override
  public long hash(byte[] buf, int off, int len, long seed) {
    checkRange(buf, off, len);
    return XXHashJNI.XXH64(buf, off, len, seed);
  }

}
