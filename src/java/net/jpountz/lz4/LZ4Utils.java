package net.jpountz.lz4;

enum LZ4Utils {
  ;

  static final int COMPRESSION_LEVEL = 12;
  static final int NOT_COMPRESSIBLE_CONFIRMATION = 6;

  static final int MIN_MATCH = 4;

  static final int HASH_LOG = COMPRESSION_LEVEL;
  static final int HASH_TABLE_SIZE = 1 << HASH_LOG;

  static final int SKIP_STRENGTH = Math.max(NOT_COMPRESSIBLE_CONFIRMATION, 2);
  static final int COPY_LENGTH = 8;
  static final int LAST_LITERALS = 5;
  static final int MF_LIMIT = COPY_LENGTH + MIN_MATCH;
  static final int MIN_LENGTH = MF_LIMIT + 1;

  static final int MAX_DISTANCE = (1 << 16) - 1;

  static final int ML_BITS = 4;
  static final int ML_MASK = (1 << ML_BITS) - 1;
  static final int RUN_BITS = 8 - ML_BITS;
  static final int RUN_MASK = (1 << RUN_BITS) - 1;

  static void checkRange(byte[] buf, int off) {
    if (off < 0 || off >= buf.length) {
      throw new ArrayIndexOutOfBoundsException(off);
    }
  }

  static void checkRange(byte[] buf, int off, int len) {
    checkLength(len);
    if (len > 0) {
      checkRange(buf, off);
      checkRange(buf, off + len - 1);
    }
  }

  static void checkLength(int len) {
    if (len < 0) {
      throw new IllegalArgumentException("lengths must be >= 0");
    }
  }

  static int hash(int i) {
    return (i * -1640531535) >>> ((MIN_MATCH * 8) - HASH_LOG);
  }

  static int readInt(byte[] buf, int i) {
    return (buf[i] & 0xFF) | ((buf[i+1] & 0xFF) << 8) | ((buf[i+2] & 0xFF) << 16) | ((buf[i+3] & 0xFF) << 24);
  }

  static final int commonBytes(byte[] src, int sOff, int ref, int srcLimit) {
    final int anchor = sOff;
    while (sOff < srcLimit && src[sOff] == src[ref]) {
      ++sOff;
      ++ref;
    }
    return sOff - anchor;
  }

  static void incrementalCopy(byte[] dest, int matchOff, int dOff, int matchLen) {
    for (int i = 0; i < matchLen; ++i) {
      dest[dOff++] = dest[matchOff++];
    }
  }
}
