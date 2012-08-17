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
 * Allows to do step-by-step LZ4 compression.
 */
interface LZ4PartialCompressor {

  /**
   * Compress as much data as possible and return <code>(sourceOffset &lt;&lt; 32) | destOffset</code>.
   */
  long greedyCompress(byte[] src, final int srcOrig, int sOff, int srcLen, byte[] dest, int dOff, int destEnd, int[] hashTable);

  /**
   * Write last literals and return <code>destOff</code>.
   */
  int lastLiterals(byte[] src, int sOff, int srcLen, byte[] dest, int dOff);

}
