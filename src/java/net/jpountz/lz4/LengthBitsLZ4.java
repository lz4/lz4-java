package net.jpountz.lz4;

/**
 * Utility class that writes the number of bits of the uncompressed length at
 * the beginning of the stream and uses
 * {@link LZ4#uncompressUnknownSize(byte[], int, int, byte[], int, int)} to
 * uncompress data.
 *
 * Only for testing purposes.
 */
class LengthBitsLZ4 extends CompressionCodec {

  private final LZ4 lz4;

  public LengthBitsLZ4(LZ4 lz4) {
    this.lz4 = lz4;
  }

  @Override
  public int maxCompressedLength(int length) {
    return lz4.maxCompressedLength(length) + 1;
  }

  @Override
  public int maxUncompressedLength(byte[] src, int srcOff, int srcLen) {
    if (srcOff < 0 || srcOff >= src.length || srcLen == 0) {
      throw new LZ4Exception("Malformed data");
    }
    int nbits = src[srcOff];
    return (1 << (nbits + 1)) - 1;
  }

  @Override
  public int compress(byte[] src, int srcOff, int srcLen, byte[] dest,
      int destOff) {
    final int bitsRequired = Math.max(1, 32 - Integer.numberOfLeadingZeros(srcLen));
    dest[destOff++] = (byte) (bitsRequired - 1);
    return 1 + lz4.compress(src, srcOff, srcLen, dest, destOff);
  }

  @Override
  public int uncompress(byte[] src, int srcOff, int srcLen, byte[] dest,
      int destOff) {
    return lz4.uncompressUnknownSize(src, srcOff + 1, srcLen - 1, dest, destOff);
  }

}
