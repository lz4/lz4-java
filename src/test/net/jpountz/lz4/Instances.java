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

import java.io.IOException;
import java.io.InputStream;

enum Instances {
  ;

  static LZ4Compressor[] COMPRESSORS = new LZ4Compressor[] {
    LZ4JNICompressor.FAST,
    LZ4JNICompressor.HIGH_COMPRESSION,
    LZ4JavaUnsafeCompressor.FAST,
    LZ4JavaSafeCompressor.FAST,
    LZ4JavaSafeCompressor.HIGH_COMPRESSION,
    new LZ4StreamCompressor(LZ4JavaSafeCompressor.FAST),
    new LZ4StreamCompressor(LZ4JavaUnsafeCompressor.FAST)
  };

  static LZ4Uncompressor[] UNCOMPRESSORS = new LZ4Uncompressor[] {
    LZ4JNIUncompressor.INSTANCE,
    LZ4JavaUnsafeUncompressor.INSTANCE,
    LZ4JavaSafeUncompressor.INSTANCE
  };

  static LZ4UnknwonSizeUncompressor[] UNCOMPRESSORS2 = new LZ4UnknwonSizeUncompressor[] {
    LZ4JNIUncompressor.INSTANCE,
    LZ4JavaUnsafeUncompressor.INSTANCE,
    LZ4JavaSafeUncompressor.INSTANCE,
    new LZ4StreamUncompressor() {
      @Override
      protected InputStream lz4InputStream(InputStream is) throws IOException {
        return new LZ4JavaSafeInputStream(is);
      }
    },
    new LZ4StreamUncompressor() {
      @Override
      protected InputStream lz4InputStream(InputStream is) throws IOException {
        return new LZ4JavaUnsafeInputStream(is);
      }
    }
  };

}
