package net.jpountz.lz4;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.carrotsearch.randomizedtesting.RandomizedRunner;
import com.carrotsearch.randomizedtesting.RandomizedTest;
import com.carrotsearch.randomizedtesting.annotations.Repeat;

@RunWith(RandomizedRunner.class)
public class LZ4UtilsTest extends RandomizedTest {

  public void testReadVInt(int n) {
    final int off = randomInt(10);
    final int len = randomInt(10);
    byte[] buf = new byte[off + len];
    boolean exception = true;
    int vIntLength = -1;
    try {
      vIntLength = LZ4Utils.writeVInt(n, buf, off, len);
      exception = false;
    } catch (LZ4Exception e) {
      // ignore
    } catch (IllegalArgumentException e) {
      // ignore
    }
    if (n < 0) {
      assertTrue(exception);
      return;
    }
    if (exception) {
      assertTrue(LZ4Utils.vIntLength(n) > len);
      return;
    }
    assertEquals(vIntLength, LZ4Utils.vIntLength(n));
    assertTrue(vIntLength <= len);
    assertEquals(n, LZ4Utils.readVInt(buf, off, len));
  }

  @Test
  @Repeat(iterations=50)
  public void testReadVInt() {
    testReadVInt(randomInt(1));
    testReadVInt(randomIntBetween(1, 256));
    testReadVInt(randomIntBetween(1, Integer.MAX_VALUE));
    testReadVInt(Integer.MAX_VALUE);
    testReadVInt(Integer.MIN_VALUE);
    testReadVInt(randomIntBetween(Integer.MIN_VALUE, -1));
  }

}
