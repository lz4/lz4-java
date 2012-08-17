package net.jpountz.lz4;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class LZ4StreamCompressor implements LZ4Compressor {

  private final LZ4PartialCompressor compressor;

  public LZ4StreamCompressor(LZ4PartialCompressor compressor) {
    this.compressor = compressor;
  }

  @Override
  public int maxCompressedLength(int length) {
    return LZ4Utils.maxCompressedLength(length);
  }

  @Override
  public int compress(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff, int maxDestLen) {
    ByteArrayOutputStream os = new ByteArrayOutputStream(maxCompressedLength(srcLen));
    LZ4OutputStream lz4Os = new LZ4OutputStream(os, compressor);
    try {
      lz4Os.write(src, srcOff, srcLen);
      lz4Os.close();
    } catch (IOException e) {
      throw new AssertionError();
    }
    byte[] compressed = os.toByteArray();
    if (compressed.length > maxDestLen) {
      throw new LZ4Exception();
    }
    System.arraycopy(compressed, 0, dest, destOff, compressed.length);
    return compressed.length;
  }

}
