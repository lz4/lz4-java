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

import java.io.OutputStream;

/**
 * {@link OutputStream} that compresses data on the fly.
 * 
 * It can be useful to compress large amounts of data since it never allocates
 * more than ~300K of memory provided that the input is compressible (ie if the
 * scan is likely to find at least one repeated sequence of 4 bytes in every
 * 64kb block).
 */
public class LZ4JavaUnsafeOutputStream extends LZ4OutputStream {

  public LZ4JavaUnsafeOutputStream(OutputStream os) {
    super(os, LZ4JavaUnsafeCompressor.FAST);
  }

}
