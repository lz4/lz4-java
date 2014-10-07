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

import static net.jpountz.util.ByteBufferUtils.checkRange;
import static net.jpountz.util.Utils.checkRange;

import java.nio.ByteBuffer;

final class XXHash64JNI extends XXHash64 {

  public static final XXHash64 INSTANCE = new XXHash64JNI();
  private static XXHash64 SAFE_INSTANCE;

  @Override
  public long hash(byte[] buf, int off, int len, long seed) {
    checkRange(buf, off, len);
    return XXHashJNI.XXH64(buf, off, len, seed);
  }

  @Override
  public long hash(ByteBuffer buf, int off, int len, long seed) {
    if (buf.isDirect()) {
      checkRange(buf, off, len);
      return XXHashJNI.XXH64BB(buf, off, len, seed);
    } else if (buf.hasArray()) {
      return hash(buf.array(), off, len, seed);
    } else {
      XXHash64 safeInstance = SAFE_INSTANCE;
      if (safeInstance == null) {
        safeInstance = SAFE_INSTANCE = XXHashFactory.safeInstance().hash64();
      }
      return safeInstance.hash(buf, off, len, seed);
    }
  }

}
