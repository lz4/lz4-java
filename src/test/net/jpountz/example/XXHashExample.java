package net.jpountz.example;

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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import net.jpountz.xxhash.StreamingXXHash32;
import net.jpountz.xxhash.XXHash32;
import net.jpountz.xxhash.XXHashFactory;

public class XXHashExample {

  public static void main(String[] args) throws Exception {
    blockExample();
    streamingExample();
  }

  private static void blockExample() throws UnsupportedEncodingException {
    XXHashFactory factory = XXHashFactory.fastestInstance();

    byte[] data = "12345345234572".getBytes("UTF-8");

    XXHash32 hash32 = factory.hash32();
    int seed = 0x9747b28c; // used to initialize the hash value, use whatever
                           // value you want, but always the same
    int hash = hash32.hash(data, 0, data.length, seed);
    System.out.println("Block hash: " + hash);
  }

  private static void streamingExample() throws IOException {
    XXHashFactory factory = XXHashFactory.fastestInstance();

    byte[] data = "12345345234572".getBytes("UTF-8");
    ByteArrayInputStream in = new ByteArrayInputStream(data);

    int seed = 0x9747b28c; // used to initialize the hash value, use whatever
                           // value you want, but always the same
    StreamingXXHash32 hash32 = factory.newStreamingHash32(seed);
    byte[] buf = new byte[8]; // for real-world usage, use a larger buffer, like 8192 bytes
    for (;;) {
      int read = in.read(buf);
      if (read == -1) {
        break;
      }
      hash32.update(buf, 0, read);
    }
    int hash = hash32.getValue();
    System.out.println("Streaming hash: " + hash);
  }

}
