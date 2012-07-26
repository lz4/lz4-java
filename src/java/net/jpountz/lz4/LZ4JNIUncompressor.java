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

import static net.jpountz.lz4.LZ4Utils.checkRange;

/**
 * {@link LZ4Uncompressor} implemented with JNI bindings to the original C
 * implementation of LZ4.
 */
public enum LZ4JNIUncompressor implements LZ4Uncompressor, LZ4UnknwonSizeUncompressor {

  INSTANCE {

    public final int uncompress(byte[] src, int srcOff, byte[] dest, int destOff, int destLen) {
      checkRange(src, srcOff);
      checkRange(dest, destOff, destLen);
      final int result = LZ4JNI.LZ4_uncompress(src, srcOff, dest, destOff, destLen);
      if (result < 0) {
        throw new LZ4Exception("Error decoding offset " + (srcOff - result) + " of input buffer");
      }
      return result;
    }

    public final int uncompressUnknownSize(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff) {
      checkRange(src, srcOff, srcLen);
      checkRange(dest, destOff);
      final int result = LZ4JNI.LZ4_uncompress_unknownOutputSize(src, srcOff, srcLen, dest, destOff, dest.length - destOff);
      if (result < 0) {
        throw new LZ4Exception("Error decoding offset " + (srcOff - result) + " of input buffer");
      }
      return result;
    }
 
  };
}
