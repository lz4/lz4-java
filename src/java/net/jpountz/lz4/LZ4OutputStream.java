package net.jpountz.lz4;

import static net.jpountz.lz4.LZ4Utils.*;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

class LZ4OutputStream extends OutputStream {

  private final LZ4PartialCompressor compressor;
  private final OutputStream os;
  private final int[] hashTable;
  private byte[] uncompressed;
  private byte[] compressed;
  private int compressedOffset, offset;

  public LZ4OutputStream(OutputStream os, LZ4PartialCompressor compressor) {
    this.os = os;
    this.compressor = compressor;
    hashTable = new int[HASH_TABLE_SIZE];
    uncompressed = new byte[MAX_DISTANCE << 1];
    compressed = new byte[maxCompressedLength(uncompressed.length)];
    compressedOffset = offset = 0;
  }

  @Override
  public void write(int b) throws IOException {
    ensureOpen();
    uncompressed[offset++] = (byte) b;
    if (offset - compressedOffset == MAX_DISTANCE || offset == uncompressed.length) {
      encode();
    }
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    if (offset - compressedOffset < MAX_DISTANCE) {
      final int toCopy = Math.min(len, MAX_DISTANCE - offset + compressedOffset);
      System.arraycopy(b, off, uncompressed, offset, toCopy);
      off += toCopy;
      len -= toCopy;
      offset += toCopy;
      if (offset - compressedOffset == MAX_DISTANCE) {
        encode();
      }
    }
    
    while (len > 0) {
      final int toCopy = Math.min(len, uncompressed.length - offset);
      System.arraycopy(b, off, uncompressed, offset, toCopy);
      off += toCopy;
      len -= toCopy;
      offset += toCopy;
      if (offset == uncompressed.length) {
        encode();
      }
    }
  }

  private void encode() throws IOException {
    long sdOff = compressor.greedyCompress(
        uncompressed, 0, compressedOffset, offset - compressedOffset,
        compressed, 0, hashTable);
    compressedOffset = (int) (sdOff >>> 32);
    int compressedLen = (int) (sdOff & 0xFFFFL);
    if (compressedLen == 0) {
      if (offset == uncompressed.length) {
        // no choice...
        uncompressed = Arrays.copyOf(uncompressed, uncompressed.length << 1);
        compressed = new byte[maxCompressedLength(uncompressed.length)];
      }

      // fix hashTable
      if (compressedOffset == 0) {
        Arrays.fill(hashTable, 0);
      } else {
        for (int i = 0; i < hashTable.length; ++i) {
          hashTable[i] = Math.min(hashTable[i], compressedOffset);
        }
      }
    } else {
      os.write(compressed, 0, compressedLen);
      if (compressedOffset > MAX_DISTANCE) {
        final int shift = compressedOffset - MAX_DISTANCE;
        offset -= shift;
        System.arraycopy(uncompressed, shift, uncompressed, 0, offset);
        compressedOffset = MAX_DISTANCE;

        // fix hashTable
        for (int i = 0; i < hashTable.length; ++i) {
          int newOffset = Math.min(hashTable[i] - shift, compressedOffset);
          if (newOffset < 0) {
            newOffset = compressedOffset;
          }
          hashTable[i] = compressedOffset;
        }
      } else {
        // fix hashTable
        for (int i = 0; i < hashTable.length; ++i) {
          hashTable[i] = Math.min(hashTable[i], compressedOffset);
        }
      }

    }
  }

  @Override
  public void close() throws IOException {
    ensureOpen();
    try {
      encode();
      final int compressedLen = compressor.lastLiterals(
          uncompressed, compressedOffset, offset - compressedOffset, compressed, 0);
      offset = -1;
      os.write(compressed, 0, compressedLen);
    } finally {
      os.close();
    }
  }

  private void ensureOpen() throws IOException {
    if (offset == -1) {
      throw new IOException("This outputstream is already closed");
    }
  }

}
