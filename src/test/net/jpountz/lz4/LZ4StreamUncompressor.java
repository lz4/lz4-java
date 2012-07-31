package net.jpountz.lz4;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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
import java.io.InputStream;

abstract class LZ4StreamUncompressor implements LZ4UnknwonSizeUncompressor {

  protected abstract InputStream lz4InputStream(InputStream is) throws IOException;

  @Override
  public int uncompressUnknownSize(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff) {
    ByteArrayInputStream bais = new ByteArrayInputStream(src, srcOff, srcLen);
    InputStream lz4Is;
    try {
      lz4Is = lz4InputStream(bais);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
    int off = destOff;
    while (true) {
      int read;
      try {
        read = lz4Is.read(dest, off, dest.length - off);
      } catch (IOException e) {
        throw new AssertionError(e);
      }
      if (read == -1) {
        break;
      }
      off += read;
    }
    return off - destOff;
  }

}
