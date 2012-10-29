package net.jpountz.example;

import java.io.UnsupportedEncodingException;

import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4Uncompressor;
import net.jpountz.lz4.LZ4UnknownSizeUncompressor;

public class LZ4Example {

  public static void main(String[] args) throws Exception {
    example();
  }

  private static LZ4Factory factoryInstance() {
    LZ4Factory lz4Factory;
    try {
      lz4Factory = LZ4Factory.nativeInstance();
    } catch (Throwable t) {
      lz4Factory = LZ4Factory.safeInstance();
    }
    return lz4Factory;
  }

  private static void example() throws UnsupportedEncodingException {
    LZ4Factory factory = LZ4Factory.safeInstance();

    byte[] data = "12345345234572".getBytes("UTF-8");
    final int uncompressedLength = data.length;

    // compress data
    LZ4Compressor compressor = factory.fastCompressor();
    int maxCompressedLength = compressor.maxCompressedLength(data.length);
    byte[] compressed = new byte[maxCompressedLength];
    int compressedLength = compressor.compress(data, 0, data.length, compressed, 0, maxCompressedLength);

    // uncompress data
    // - method 1: when the uncompressed length is known
    LZ4Uncompressor uncompressor = factory.uncompressor();
    byte[] restored = new byte[uncompressedLength];
    int compressedLength2 = uncompressor.uncompress(compressed, 0, restored, 0, uncompressedLength);
    // compressedLength == compressedLength2

    // - method 2: when the compressed length is known (a little slower)
    // the destination buffer needs to be over-sized
    LZ4UnknownSizeUncompressor uncompressor2 = factory.unknwonSizeUncompressor();
    int uncompressedLength2 = uncompressor2.uncompressUnknownSize(compressed, 0, compressedLength, restored, 0);
    // uncompressedLength == uncompressedLength2
  }

}
