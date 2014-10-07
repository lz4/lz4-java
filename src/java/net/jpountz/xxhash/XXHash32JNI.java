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

final class XXHash32JNI extends XXHash32 {

  public static final XXHash32 INSTANCE = new XXHash32JNI();
  private static XXHash32 SAFE_INSTANCE;

  @Override
  public int hash(byte[] buf, int off, int len, int seed) {
    checkRange(buf, off, len);
    return XXHashJNI.XXH32(buf, off, len, seed);
  }

  @Override
  public int hash(ByteBuffer buf, int off, int len, int seed) {
    if (buf.isDirect()) {
      checkRange(buf, off, len);
      return XXHashJNI.XXH32BB(buf, off, len, seed);
    } else if (buf.hasArray()) {
      return hash(buf.array(), off, len, seed);
    } else {
      XXHash32 safeInstance = SAFE_INSTANCE;
      if (safeInstance == null) {
        safeInstance = SAFE_INSTANCE = XXHashFactory.safeInstance().hash32();
      }
      return safeInstance.hash(buf, off, len, seed);
    }
  }

}
