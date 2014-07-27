package net.jpountz.lz4;

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

import java.util.Arrays;

/**
 * LZ4HC compressor.
 * <p>
 * Instances of this class are thread-safe.
 */
public abstract class LZ4HCCompressor extends LZ4Compressor {

  /**
   * Get the compression level in effect.
   *
   * @return the compression level in effect.
   */
  public abstract int getCompressionLevel();

  @Override
  public String toString() {
    return getClass().getSimpleName() + getCompressionLevel();
  }
}
