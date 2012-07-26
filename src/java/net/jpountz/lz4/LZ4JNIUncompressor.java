package net.jpountz.lz4;

import static net.jpountz.lz4.LZ4Utils.checkRange;

/**
 * {@link LZ4Uncompressor} implemented with JNI bindings to the original C
 * implementation of LZ4.
 */
public enum LZ4JNIUncompressor implements LZ4Uncompressor, LZ4UnknwonSizeUncompressor {

  INSTANCE {

    public final int uncompress(byte[] src, int srcOff, byte[] dest, int destOff, int destLen) {
      checkRange(src, srcOff);
      checkRange(dest, destOff, destLen);
      final int result = LZ4JNI.LZ4_uncompress(src, srcOff, dest, destOff, destLen);
      if (result < 0) {
        throw new LZ4Exception("Error decoding offset " + (srcOff - result) + " of input buffer");
      }
      return result;
    }

    public final int uncompressUnknownSize(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff) {
      checkRange(src, srcOff, srcLen);
      checkRange(dest, destOff);
      final int result = LZ4JNI.LZ4_uncompress_unknownOutputSize(src, srcOff, srcLen, dest, destOff, dest.length - destOff);
      if (result < 0) {
        throw new LZ4Exception("Error decoding offset " + (srcOff - result) + " of input buffer");
      }
      return result;
    }
 
  };
}
