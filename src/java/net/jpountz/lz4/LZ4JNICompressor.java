package net.jpountz.lz4;

import static net.jpountz.lz4.LZ4Utils.checkRange;

/**
 * {@link LZ4Compressor}s implemented with JNI bindings to the original C
 * implementation of LZ4.
 */
public enum LZ4JNICompressor implements LZ4Compressor {
  FAST {
    public int compress(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff) {
      checkRange(src, srcOff, srcLen);
      checkRange(dest, destOff, maxCompressedLength(srcLen));
      final int result = LZ4JNI.LZ4_compress(src, srcOff, srcLen, dest, destOff);
      if (result <= 0) {
        throw new LZ4Exception();
      }
      return result;
    }
  },

  HIGH_COMPRESSION {
    public int compress(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff) {
      checkRange(src, srcOff, srcLen);
      checkRange(dest, destOff, maxCompressedLength(srcLen));
      final int result = LZ4JNI.LZ4_compressHC(src, srcOff, srcLen, dest, destOff);
      if (result <= 0) {
        throw new LZ4Exception();
      }
      return result;
    }
  };

  public final int maxCompressedLength(int length) {
    if (length < 0) {
      throw new IllegalArgumentException("length must be >= 0, got " + length);
    }
    return LZ4JNI.LZ4_compressBound(length);
  }
}
