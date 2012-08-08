package net.jpountz.xxhash;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.carrotsearch.randomizedtesting.RandomizedRunner;
import com.carrotsearch.randomizedtesting.RandomizedTest;
import com.carrotsearch.randomizedtesting.annotations.Repeat;

@RunWith(RandomizedRunner.class)
public class XXHashTest extends RandomizedTest {

  private static XXHash[] INSTANCES = new XXHash[] {
    XXHashJNI.FAST,
    XXHashJNI.STRONG,
    XXHashJavaUnsafe.FAST,
    XXHashJavaUnsafe.STRONG,
    XXHashJavaSafe.FAST,
    XXHashJavaUnsafe.STRONG
  };

  @Test
  public void testEmpty() {
    final int seed = randomInt();
    for (XXHash xxHash : INSTANCES) {
      xxHash.hash(new byte[0], 0, 0, seed);
    }
  }

  @Test
  @Repeat(iterations = 5)
  public void testAIOOBE() {
    final int seed = randomInt();
    final int bufLen = randomBoolean() ? randomInt(32) : randomInt(1000);
    final byte[] buf = new byte[bufLen];
    for (int i = 0; i < buf.length; ++i) {
      buf[i] = randomByte();
    }
    final int off = randomInt(buf.length - 1);
    final int len = randomInt(buf.length - off);
    for (XXHash xxHash : INSTANCES) {
      xxHash.hash(buf, off, len, seed);
    }
  }

}
