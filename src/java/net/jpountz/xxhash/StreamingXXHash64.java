package net.jpountz.xxhash;

import java.util.zip.Checksum;

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
 * Streaming interface for {@link XXHash64}.
 * <p>
 * This API is compatible with the {@link XXHash64 block API} and the following
 * code samples are equivalent:
 * <pre class="prettyprint">
 *   long hash(XXHashFactory xxhashFactory, byte[] buf, int off, int len, long seed) {
 *     return xxhashFactory.hash64().hash(buf, off, len, seed);
 *   }
 * </pre>
 * <pre class="prettyprint">
 *   long hash(XXHashFactory xxhashFactory, byte[] buf, int off, int len, long seed) {
 *     StreamingXXHash64 sh64 = xxhashFactory.newStreamingHash64(seed);
 *     sh64.update(buf, off, len);
 *     return sh64.getValue();
 *   }
 * </pre>
 * <p>
 * Instances of this class are <b>not</b> thread-safe.
 */
public abstract class StreamingXXHash64 {

  interface Factory {

    StreamingXXHash64 newStreamingHash(long seed);

    StreamingXXHash64 newStreamingHash(XXHash64State savedState);
  }

  final long seed;

  StreamingXXHash64(long seed) {
    this.seed = seed;
  }

  /**
   * Returns the value of the checksum.
   *
   * @return the checksum
   */
  public abstract long getValue();

  /**
   * Updates the value of the hash with buf[off:off+len].
   *
   * @param buf the input data
   * @param off the start offset in buf
   * @param len the number of bytes to hash
   */
  public abstract void update(byte[] buf, int off, int len);

  /**
   * Resets this instance to the state it had right after instantiation. The
   * seed remains unchanged.
   */
  public abstract void reset();

  /**
   * Returns an opaque object that encapsulates the current state of the hash. This can be used later
   * to get a new StreamingXXHash64 object that can continue from there.
   */
  public abstract XXHash64State getState();

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(seed=" + seed + ")";
  }

  /**
   * Returns a {@link Checksum} view of this instance. Modifications to the view
   * will modify this instance too and vice-versa.
   *
   * @return the {@link Checksum} object representing this instance
   */
  public final Checksum asChecksum() {
    return new Checksum() {

      @Override
      public long getValue() {
        return StreamingXXHash64.this.getValue();
      }

      @Override
      public void reset() {
        StreamingXXHash64.this.reset();
      }

      @Override
      public void update(int b) {
        StreamingXXHash64.this.update(new byte[] {(byte) b}, 0, 1);
      }

      @Override
      public void update(byte[] b, int off, int len) {
        StreamingXXHash64.this.update(b, off, len);
      }

      @Override
      public String toString() {
        return StreamingXXHash64.this.toString();
      }

    };
  }

}
