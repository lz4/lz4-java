package net.jpountz.lz4;

import java.util.Arrays;

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

/**
 * A compression codec built on top of a compressor and an uncompressor.
 */
public abstract class CompressionCodec {

  /** Return the maximum compressed length for an input of size <code>length</code>. */
  public abstract int maxCompressedLength(int length);

  /** Return the maximum uncompressed length for the specified input. */
  public abstract int maxUncompressedLength(byte[] src, int srcOff, int srcLen);

  /** Compress <code>src[srcOff:srcOff+srcLen]</code> into <code>dest[destOff:]</code> */
  public abstract int compress(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff);

  /** Uncompress <code>src[srcOff:srcOff+srcLen]</code> into <code>dest[destOff:]</code>.
   * Return the length of the uncompressed data. */
  public abstract int uncompress(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff);

  /** Compress <code>src[srcOff:srcOff+srcLen]</code>. */
  public final byte[] compress(byte[] src, int srcOff, int srcLen) {
    final byte[] compressed = new byte[maxCompressedLength(srcLen)];
    final int compressedLen = compress(src, srcOff, srcLen, compressed, 0);
    return Arrays.copyOf(compressed, compressedLen);
  }

  /** Compress <code>src</code>. */
  public final byte[] compress(byte[] src) {
    return compress(src, 0, src.length);
  }

  /** Uncompress <code>src[srcOff:srcOff+srcLen]</code>. */
  public final byte[] uncompress(byte[] src, int srcOff, int srcLen) {
    final int maxUncompressedLength = maxUncompressedLength(src, srcOff, srcLen);
    final byte[] uncompressed = new byte[maxUncompressedLength];
    final int uncompressedLen = uncompress(src, srcOff, srcLen, uncompressed, 0);
    if (uncompressedLen == maxUncompressedLength) {
      return uncompressed;
    } else {
      return Arrays.copyOf(uncompressed, uncompressedLen);
    }
  }

  /** Uncompress <code>src</code>. */
  public final byte[] uncompress(byte[] src) {
    return uncompress(src, 0, src.length);
  }
}