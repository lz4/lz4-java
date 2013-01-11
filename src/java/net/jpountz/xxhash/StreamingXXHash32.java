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
 * Streaming interface for {@link XXHash32}.
 */
public abstract class StreamingXXHash32 {

  final int seed;

  StreamingXXHash32(int seed) {
    this.seed = seed;
  }

  /**
   * Get the value of the checksum.
   */
  public abstract int getValue();

  /**
   * Update the value of the hash with buf[off:off+len].
   */
  public abstract void update(byte[] buf, int off, int len);

  /**
   * Reset this instance.
   */
  public abstract void reset();

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(seed=" + seed + ")";
  }

}