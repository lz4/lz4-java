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

abstract class AbstractStreamingXXHash64Java extends StreamingXXHash64 {

  final XXHash64JavaState state;

  AbstractStreamingXXHash64Java(long seed) {
    super(seed);
    state = new XXHash64JavaState(seed);
  }

  AbstractStreamingXXHash64Java(XXHash64JavaState savedState) {
    super(savedState.seed);
    state = new XXHash64JavaState(savedState);
  }

  @Override
  public void reset() {
    state.reset();
  }

  @Override
  public XXHash64State getState() {
    // Return a copy of the internal state
    return new XXHash64JavaState(state);
  }
}
