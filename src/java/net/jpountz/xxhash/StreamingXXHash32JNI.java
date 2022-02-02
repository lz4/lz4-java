package net.jpountz.xxhash;

import static net.jpountz.util.ByteBufferUtils.checkRange;
import static net.jpountz.util.SafeUtils.checkRange;
import java.nio.ByteBuffer;

/*
 * Copyright 2020 Adrien Grand and the lz4-java contributors.
 *
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
 * Fast {@link StreamingXXHash32} implemented with JNI bindings.
 * The methods are synchronized to avoid a race condition
 * between freeing the native memory in finalize() and using it in
 * reset(), getValue(), and update().  Note that GC can call finalize()
 * after calling checkState() and before using XXHashJNI if the caller
 * does not retain a reference to this object.
 */
final class StreamingXXHash32JNI extends StreamingXXHash32 {

  static class Factory implements StreamingXXHash32.Factory {

    public static final StreamingXXHash32.Factory INSTANCE = new Factory();

    @Override
    public StreamingXXHash32 newStreamingHash(int seed) {
      return new StreamingXXHash32JNI(seed);
    }

  }

  private long state;

  StreamingXXHash32JNI(int seed) {
    super(seed);
    state = XXHashJNI.XXH32_init(seed);
  }

  private void checkState() {
    if (state == 0) {
      throw new AssertionError("Already finalized");
    }
  }

  @Override
  public synchronized void reset() {
    checkState();
    XXHashJNI.XXH32_free(state);
    state = XXHashJNI.XXH32_init(seed);
  }

  @Override
  public synchronized int getValue() {
    checkState();
    return XXHashJNI.XXH32_digest(state);
  }

  @Override
  public synchronized void update(byte[] bytes, int off, int len) {
    checkState();
    XXHashJNI.XXH32_update(state, bytes, off, len);
  }

  public synchronized void update(ByteBuffer buf, int off, int len) {
    checkState();
    if (buf.isDirect()) {
        checkRange(buf, off, len);
        XXHashJNI.XXH32BB_update(state, buf, off, len);
    } else if (buf.hasArray()) {
        XXHashJNI.XXH32_update(state, buf.array(), off + buf.arrayOffset(), len);
    } else {
        // XXX: What to do here?
        throw new RuntimeException("unsupported");
    }

  }

  @Override
  public synchronized void close() {
    if (state != 0) {
      super.close();
      XXHashJNI.XXH32_free(state);
      state = 0;
    }
  }

  @Override
  protected synchronized void finalize() throws Throwable {
    super.finalize();
    if (state != 0) {
      // free memory
      XXHashJNI.XXH32_free(state);
      state = 0;
    }
  }

}
