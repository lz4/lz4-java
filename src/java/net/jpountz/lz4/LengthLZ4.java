package net.jpountz.lz4;

/**
 * Utility class that writes uncompressed lentgh at the beginning of the stream
 * to speed up decompression.
 */
public class LengthLZ4 extends CompressionCodec {

  private final LZ4 lz4;

  public LengthLZ4(LZ4 lz4) {
    this.lz4 = lz4;
  }

  @Override
  public int maxCompressedLength(int length) {
    return lz4.maxCompressedLength(length) + 4;
  }

  @Override
  public int maxUncompressedLength(byte[] src, int srcOff, int srcLen) {
    if (srcLen <= 4) {
      throw new LZ4Exception("Malformed stream");
    }
    LZ4Utils.checkRange(src, srcOff, 4);
    return ((src[srcOff++] & 0xFF) << 24)
        | ((src[srcOff++] & 0xFF) << 16)
        | ((src[srcOff++] & 0xFF) << 8)
        | (src[srcOff] & 0xFF);
  }

  @Override
  public int compress(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff) {
    LZ4Utils.checkRange(dest, destOff, 4);
    dest[destOff++] = (byte) (srcLen >>> 24);
    dest[destOff++] = (byte) (srcLen >>> 16);
    dest[destOff++] = (byte) (srcLen >>> 8);
    dest[destOff++] = (byte) srcLen;
    return 4 + lz4.compress(src, srcOff, srcLen, dest, destOff);
  }

  @Override
  public int uncompress(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff) {
    final int uncompressedLen = maxUncompressedLength(src, srcOff, srcLen);
    final int compressedLen = 4 + lz4.uncompress(src, srcOff + 4, dest, destOff, uncompressedLen);
    if (compressedLen != srcLen) {
      throw new LZ4Exception("Uncompressed length mismatch " + srcLen + " != " + compressedLen);
    }
    return uncompressedLen;
  }

}
