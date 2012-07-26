package net.jpountz.lz4;

import static net.jpountz.lz4.LZ4UnsafeUtils.readShortLittleEndian;
import static net.jpountz.lz4.LZ4UnsafeUtils.safeArraycopy;
import static net.jpountz.lz4.LZ4UnsafeUtils.safeIncrementalCopy;
import static net.jpountz.lz4.LZ4UnsafeUtils.wildArraycopy;
import static net.jpountz.lz4.LZ4UnsafeUtils.wildIncrementalCopy;
import static net.jpountz.lz4.LZ4Utils.COPY_LENGTH;
import static net.jpountz.lz4.LZ4Utils.MIN_MATCH;
import static net.jpountz.lz4.LZ4Utils.ML_BITS;
import static net.jpountz.lz4.LZ4Utils.ML_MASK;
import static net.jpountz.lz4.LZ4Utils.RUN_MASK;
import static net.jpountz.lz4.LZ4Utils.checkRange;

/**
 * Very fast uncompressor written in pure Java with the unofficial
 * sun.misc.Unsafe API.
 */
public enum LZ4JavaUnsafeUncompressor implements LZ4Uncompressor, LZ4UnknwonSizeUncompressor {

  INSTANCE {

    public int uncompress(byte[] src, final int srcOff, byte[] dest, final int destOff, int destLen) {
      checkRange(src, srcOff);
      checkRange(dest, destOff, destLen);

      final int destEnd = destOff + destLen;

      int sOff = srcOff;
      int dOff = destOff;

      while (true) {
        final int token = src[sOff++] & 0xFF;

        // literals
        int literalLen = token >>> ML_BITS;
        if (literalLen == RUN_MASK) {
            int len;
            while ((len = src[sOff++] & 0xFF) == 255) {
              literalLen += len;
            }
            literalLen += len;
        }

        final int literalCopyEnd = dOff + literalLen;
        if (literalCopyEnd > destEnd - COPY_LENGTH) {
          if (literalCopyEnd > destEnd) {
            throw new LZ4Exception("Malformed input at " + sOff);
          } else {
            safeArraycopy(src, sOff, dest, dOff, literalLen);
            sOff += literalLen;
            break; // EOF
          }
        }

        wildArraycopy(src, sOff, dest, dOff, literalLen);
        sOff += literalLen;
        dOff = literalCopyEnd;

        // matchs
        final int matchDec = readShortLittleEndian(src, sOff);
        sOff += 2;
        int matchOff = dOff - matchDec;

        if (matchOff < destOff) {
          throw new LZ4Exception("Malformed input at " + sOff);
        }

        int matchLen = token & ML_MASK;
        if (matchLen == ML_MASK) {
          int len;
          while ((len = src[sOff++] & 0xFF) == 255) {
            matchLen += len;
          }
          matchLen += len;
        }
        matchLen += MIN_MATCH;

        final int matchCopyEnd = dOff + matchLen;

        if (matchCopyEnd > dest.length - COPY_LENGTH) {
          if (matchCopyEnd > destEnd) {
            throw new LZ4Exception("Malformed input at " + sOff);
          }
          safeIncrementalCopy(dest, matchOff, dOff, matchCopyEnd);
        } else {
          wildIncrementalCopy(dest, matchOff, dOff, matchCopyEnd);
        }
        dOff = matchCopyEnd;
      }

      return sOff - srcOff;
    }
    
    @Override
    public int uncompressUnknownSize(byte[] src, int srcOff, int srcLen,
        byte[] dest, int destOff) {
      checkRange(src, srcOff, srcLen);
      checkRange(dest, destOff);

      final int srcEnd = srcOff + srcLen;

      int sOff = srcOff;
      int dOff = destOff;

      while (sOff < srcEnd) {
        final int token = src[sOff++] & 0xFF;

        // literals
        int literalLen = token >>> ML_BITS;
        if (literalLen == RUN_MASK) {
            int len;
            while ((len = src[sOff++] & 0xFF) == 255) {
              literalLen += len;
            }
            literalLen += len;
        }

        final int literalCopyEnd = dOff + literalLen;
        if (literalCopyEnd > dest.length - COPY_LENGTH || sOff + literalLen > srcEnd - COPY_LENGTH) {
          if (literalCopyEnd > dest.length || sOff + literalLen > srcEnd) {
            throw new LZ4Exception("Malformed input at " + sOff);
          } else {
            safeArraycopy(src, sOff, dest, dOff, literalLen);
            sOff += literalLen;
            dOff += literalLen;
            if (sOff < srcEnd) {
              throw new LZ4Exception("Malformed input at " + sOff);
            }
            break; // EOF
          }
        }

        wildArraycopy(src, sOff, dest, dOff, literalLen);
        sOff += literalLen;
        dOff = literalCopyEnd;

        // matchs
        final int matchDec = readShortLittleEndian(src, sOff);
        sOff += 2;
        int matchOff = dOff - matchDec;

        if (matchOff < destOff) {
          throw new LZ4Exception("Malformed input at " + sOff);
        }

        int matchLen = token & ML_MASK;
        if (matchLen == ML_MASK) {
          int len;
          while ((len = src[sOff++] & 0xFF) == 255) {
            matchLen += len;
          }
          matchLen += len;
        }
        matchLen += MIN_MATCH;

        final int matchCopyEnd = dOff + matchLen;

        if (matchCopyEnd > dest.length - COPY_LENGTH) {
          if (matchCopyEnd > dest.length) {
            throw new LZ4Exception("Malformed input at " + sOff);
          }
          safeIncrementalCopy(dest, matchOff, dOff, matchCopyEnd);
        } else {
          wildIncrementalCopy(dest, matchOff, dOff, matchCopyEnd);
        }
        dOff = matchCopyEnd;
      }

      return dOff - destOff;
    }
  };

}
