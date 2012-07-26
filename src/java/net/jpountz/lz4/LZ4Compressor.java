package net.jpountz.lz4;

/**
 * A compressor for the LZ4 compression format.
 */
public interface LZ4Compressor {

  /** Return the maximum compressed length for an input of size <code>length</code>. */
  int maxCompressedLength(int length);

  /** Compress <code>src[srcOff:srcOff+srcLen]</code> into <code>dest[destOff:]</code> */
  int compress(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff);

}
