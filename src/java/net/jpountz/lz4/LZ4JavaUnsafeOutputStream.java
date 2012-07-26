package net.jpountz.lz4;

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
