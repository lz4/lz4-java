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

import net.jpountz.util.UnsafeBase;
import junit.framework.TestCase;

public class LZ4FactoryTest extends TestCase {

  public void test() {
    assertEquals(LZ4JNICompressor.INSTANCE, LZ4Factory.nativeInstance().fastCompressor());
    assertEquals(LZ4HCJNICompressor.INSTANCE, LZ4Factory.nativeInstance().highCompressor());
    assertEquals(LZ4JavaSafeCompressor.INSTANCE, LZ4Factory.safeInstance().fastCompressor());
    assertEquals(LZ4HCJavaSafeCompressor.INSTANCE, LZ4Factory.safeInstance().highCompressor());

    assertEquals(LZ4JNIFastDecompressor.INSTANCE, LZ4Factory.nativeInstance().fastDecompressor());
    assertEquals(LZ4JavaSafeFastDecompressor.INSTANCE, LZ4Factory.safeInstance().fastDecompressor());

    assertEquals(LZ4JNISafeDecompressor.INSTANCE, LZ4Factory.nativeInstance().safeDecompressor());
    assertEquals(LZ4JavaSafeSafeDecompressor.INSTANCE, LZ4Factory.safeInstance().safeDecompressor());

	//Calling with default level should return the same one as the constructor without a level option.
    assertEquals(LZ4Factory.nativeInstance().highCompressor(LZ4Constants.DEFAULT_COMPRESSION_LEVEL), LZ4Factory.nativeInstance().highCompressor());
    assertEquals(LZ4Factory.unsafeInstance().highCompressor(LZ4Constants.DEFAULT_COMPRESSION_LEVEL), LZ4Factory.unsafeInstance().highCompressor());
    assertEquals(LZ4Factory.safeInstance().highCompressor(LZ4Constants.DEFAULT_COMPRESSION_LEVEL), LZ4Factory.safeInstance().highCompressor());
	//Calling with a level too high should give back the highest possible one.
    assertEquals(LZ4Factory.nativeInstance().highCompressor(LZ4Constants.MAX_COMPRESSION_LEVEL+1), LZ4Factory.nativeInstance().highCompressor(LZ4Constants.MAX_COMPRESSION_LEVEL));
    assertEquals(LZ4Factory.unsafeInstance().highCompressor(LZ4Constants.MAX_COMPRESSION_LEVEL+1), LZ4Factory.unsafeInstance().highCompressor(LZ4Constants.MAX_COMPRESSION_LEVEL));
    assertEquals(LZ4Factory.safeInstance().highCompressor(LZ4Constants.MAX_COMPRESSION_LEVEL+1), LZ4Factory.safeInstance().highCompressor(LZ4Constants.MAX_COMPRESSION_LEVEL));
	//Calling with a level less than 1 should give back the default one.
    assertEquals(LZ4Factory.nativeInstance().highCompressor(LZ4Constants.DEFAULT_COMPRESSION_LEVEL), LZ4Factory.nativeInstance().highCompressor(0));
    assertEquals(LZ4Factory.unsafeInstance().highCompressor(LZ4Constants.DEFAULT_COMPRESSION_LEVEL), LZ4Factory.unsafeInstance().highCompressor(0));
    assertEquals(LZ4Factory.safeInstance().highCompressor(LZ4Constants.DEFAULT_COMPRESSION_LEVEL), LZ4Factory.safeInstance().highCompressor(0));

    if ("Long".equals(UnsafeBase.POINTER_SIZE_SUFFIX)) {
      assertEquals(LZ4JavaUnsafeLongCompressor.INSTANCE, LZ4Factory.unsafeInstance().fastCompressor());
      assertEquals(LZ4HCJavaUnsafeLongCompressor.INSTANCE, LZ4Factory.unsafeInstance().highCompressor());
      assertEquals(LZ4JavaUnsafeLongSafeDecompressor.INSTANCE, LZ4Factory.unsafeInstance().safeDecompressor());
      assertEquals(LZ4JavaUnsafeLongFastDecompressor.INSTANCE, LZ4Factory.unsafeInstance().fastDecompressor());
    } else {
      assertEquals(LZ4JavaUnsafeCompressor.INSTANCE, LZ4Factory.unsafeInstance().fastCompressor());
      assertEquals(LZ4HCJavaUnsafeCompressor.INSTANCE, LZ4Factory.unsafeInstance().highCompressor());
      assertEquals(LZ4JavaUnsafeSafeDecompressor.INSTANCE, LZ4Factory.unsafeInstance().safeDecompressor());
      assertEquals(LZ4JavaUnsafeFastDecompressor.INSTANCE, LZ4Factory.unsafeInstance().fastDecompressor());
    }
  }

}
