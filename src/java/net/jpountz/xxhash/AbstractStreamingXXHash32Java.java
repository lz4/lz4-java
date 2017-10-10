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

abstract class AbstractStreamingXXHash32Java extends StreamingXXHash32 {

  final XXHash32JavaState state;

  AbstractStreamingXXHash32Java(int seed) {
    super(seed);
    state = new XXHash32JavaState(seed);
  }

  AbstractStreamingXXHash32Java(XXHash32JavaState savedState) {
    super(savedState.seed);
    state = new XXHash32JavaState(savedState);
  }

  @Override
  public void reset() {
    state.reset();
  }

  @Override
  public XXHash32State getState() {
    // Return a copy of the internal state
    return new XXHash32JavaState(state);
  }
}
