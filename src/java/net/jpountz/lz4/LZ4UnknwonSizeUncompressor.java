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

/**
 * LZ4 uncompressor. Implementations of this class are usually slower than those
 * of {@link LZ4Uncompressor} but do not require to know the size of the
 * uncompressed data.
 */
public interface LZ4UnknwonSizeUncompressor {

  /**
   * Uncompress <code>src[srcOff:srcLen]</code> into <code>dest[destOff:]</code>.
   * Returns the number of uncompressed bytes written into <code>dest</code>.
   */
  int uncompressUnknownSize(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff);

}