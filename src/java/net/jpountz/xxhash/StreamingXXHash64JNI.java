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
 * Fast {@link StreamingXXHash64} implemented with JNI bindings.
 * The methods are synchronized to avoid a race condition
 * between freeing the native memory in finalize() and using it in
 * reset(), getValue(), and update().  Note that GC can call finalize()
 * after calling checkState() and before using XXHashJNI if the caller
 * does not retain a reference to this object.
 */
final class StreamingXXHash64JNI extends StreamingXXHash64 {

  static class Factory implements StreamingXXHash64.Factory {

    public static final StreamingXXHash64.Factory INSTANCE = new Factory();

    @Override
    public StreamingXXHash64 newStreamingHash(long seed) {
      return new StreamingXXHash64JNI(seed);
    }

    @Override
    public StreamingXXHash64 newStreamingHash(XXHash64State savedState) {
      throw new UnsupportedOperationException("The JNI implementation does not support resuming from a saved state");
    }

  }

  private long state;

  StreamingXXHash64JNI(long seed) {
    super(seed);
    state = XXHashJNI.XXH64_init(seed);
  }

  private void checkState() {
    if (state == 0) {
      throw new AssertionError("Already finalized");
    }
  }

  @Override
  public synchronized void reset() {
    checkState();
    XXHashJNI.XXH64_free(state);
    state = XXHashJNI.XXH64_init(seed);
  }

  /** JNI implementation does not support saving the internal state. */
  @Override
  public XXHash64State getState() {
    throw new UnsupportedOperationException("The JNI implementation does not support resuming from a saved state");
  }

  @Override
  public synchronized long getValue() {
    checkState();
    return XXHashJNI.XXH64_digest(state);
  }

  @Override
  public synchronized void update(byte[] bytes, int off, int len) {
    checkState();
    XXHashJNI.XXH64_update(state, bytes, off, len);
  }

  @Override
  protected synchronized void finalize() throws Throwable {
    super.finalize();
    // free memory
    XXHashJNI.XXH64_free(state);
    state = 0;
  }

}
