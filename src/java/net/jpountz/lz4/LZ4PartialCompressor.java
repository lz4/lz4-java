package net.jpountz.lz4;

/**
 * Allows to do step-by-step LZ4 compression.
 */
interface LZ4PartialCompressor {

  /**
   * Compress as much data as possible and return <code>(sourceOffset &lt;&lt; 32) | destOffset</code>.
   */
  long greedyCompress(byte[] src, final int srcOrig, int sOff, int srcLen, byte[] dest, int dOff, int[] hashTable);

  /**
   * Write last literals and return <code>destOff</code>.
   */
  int lastLiterals(byte[] src, int sOff, int srcLen, byte[] dest, int dOff);

}
