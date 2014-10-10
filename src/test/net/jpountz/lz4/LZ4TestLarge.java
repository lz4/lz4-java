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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.jpountz.util.UnsafeBase;

import org.junit.Test;

public class LZ4TestLarge extends AbstractLZ4RoundtripTest {

  @Test
  public void testAbove2G() {
    if (UnsafeBase.POINTER_SIZE_SUFFIX.isEmpty()) {
      // Test makes no sense for 32-bits.
      return;
    }
    List<ByteBuffer> reserve = new ArrayList<ByteBuffer>();
    int size = 100*1000*1000;
    for (int i=0; i < 50; ++i) {
      ByteBuffer buf = ByteBuffer.allocateDirect(size);
      reserve.add(buf);
      reserve.get(randomInt(reserve.size()-1)).put(randomInt(size-1), randomByte());

      final int n = randomIntBetween(1, 15);
      final int len = randomBoolean() ? randomInt(1 << 10) : randomInt(1 << 12);
      final byte[] random = randomArray(len, n);
      byte[] data = new byte[size]; // assure direct allocations above current ptr.
      final int pos = randomInt(size - len - 1);
      System.arraycopy(random, 0, data, pos, len);
      testRoundTrip(data, pos, len);
    }
    
    // make sure 2Gs are not released.
    int v = reserve.get(randomInt(reserve.size())).get(randomInt(size-1));
    assertEquals(v, v);
  }

  protected List<Tester<?>> getTesters(LZ4Compressor compressor,
      LZ4FastDecompressor decompressor, LZ4SafeDecompressor decompressor2) {
    return Collections.<Tester<?>>singletonList(
        new DirectBufferTester(compressor, decompressor, decompressor2));
  }
}
