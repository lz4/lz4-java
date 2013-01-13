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

/**
 * A 32-bits hash.
 * <p>
 * Instances of this class are thread-safe.
 */
public abstract class XXHash32 {

  /**
   * Compute the 32-bits hash of <code>buf[off:off+len]</code> using seed
   * <code>seed</code>.
   */
  public abstract int hash(byte[] buf, int off, int len, int seed);

  @Override
  public String toString() {
    return getClass().getSimpleName();
  }

}
