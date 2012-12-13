package net.jpountz.xxhash;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.carrotsearch.randomizedtesting.RandomizedRunner;
import com.carrotsearch.randomizedtesting.RandomizedTest;
import com.carrotsearch.randomizedtesting.annotations.Repeat;

@RunWith(RandomizedRunner.class)
public class XXHashTest extends RandomizedTest {

  private static XXHash32[] INSTANCES = new XXHash32[] {
    XXHashFactory.nativeInstance().hash32(),
    XXHashFactory.unsafeInstance().hash32(),
    XXHashFactory.safeInstance().hash32()
  };

  @Test
  public void testEmpty() {
    final int seed = randomInt();
    for (XXHash32 xxHash : INSTANCES) {
      xxHash.hash(new byte[0], 0, 0, seed);
    }
  }

  @Test
  @Repeat(iterations = 20)
  public void testAIOOBE() {
    final int seed = randomInt();
    final int max = randomBoolean() ? 32 : 1000;
    final int bufLen = randomIntBetween(1, max);
    final byte[] buf = new byte[bufLen];
    for (int i = 0; i < buf.length; ++i) {
      buf[i] = randomByte();
    }
    final int off = randomInt(buf.length - 1);
    final int len = randomInt(buf.length - off);
    for (XXHash32 xxHash : INSTANCES) {
      xxHash.hash(buf, off, len, seed);
    }
  }

  @Test
  @Repeat(iterations=20)
  public void testInstances() {
    final int maxLenLog = randomInt(20);
    final int bufLen = randomInt(1 << maxLenLog);
    byte[] buf = new byte[bufLen];
    for (int i = 0; i < bufLen; ++i) {
      buf[i] = randomByte();
    }
    final int seed = randomInt();
    final int off = randomIntBetween(0, Math.max(0, bufLen - 1));
    final int len = randomIntBetween(0, bufLen - off);

    final int ref = XXHashFactory.nativeInstance().hash32().hash(buf, off, len, seed);
    for (XXHash32 hash : INSTANCES) {
      final int h = hash.hash(buf, off, len, seed);
      assertEquals(hash.toString(), ref, h);
    }
  }
  
}
