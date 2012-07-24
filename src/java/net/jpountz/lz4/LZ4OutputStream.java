package net.jpountz.lz4;

import java.io.IOException;
import java.io.OutputStream;

public class LZ4OutputStream extends OutputStream {

  static byte[] MAGIC = new byte[] { 0x4c, 0x5a, 0x34, 0x2d};

  private final OutputStream os;
  private final CompressionCodec codec;
  private final byte[] buffer;
  private final byte[] encodedBuffer;
  private int offset;

  public LZ4OutputStream(OutputStream os, LZ4 lz4, int bufSize) throws IOException {
    if (bufSize < 1) {
      throw new IllegalArgumentException("bufSize must be >= 1");
    }
    this.os = os;
    this.codec = new LengthLZ4(lz4);
    this.buffer = new byte[bufSize];
    this.encodedBuffer = new byte[codec.maxCompressedLength(bufSize) + 4];
    offset = 0;
    os.write(MAGIC);
  }

  public LZ4OutputStream(OutputStream os, LZ4 lz4) throws IOException {
    this(os, lz4, 1 << 15);
  }

  public LZ4OutputStream(OutputStream os) throws IOException {
    this(os, LZ4Java.FAST);
  }

  @Override
  public void write(int b) throws IOException {
    ensureOpen();
    buffer[offset++] = (byte) b;
    if (offset == buffer.length) {
      encode();
    }
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    ensureOpen();
    if (len >= buffer.length - offset) {
      final int copied = buffer.length - offset;
      System.arraycopy(b, off, buffer, offset, copied);
      off += copied;
      len -= copied;
      offset = buffer.length;
      encode();
      while (len >= buffer.length) {
        encodeFrom(b, off, buffer.length);
        off += buffer.length;
        len -= buffer.length;
      }
      
    }
    System.arraycopy(b, off, buffer, offset, len);
    offset += len;
  }

  private void encodeFrom(byte[] b, int off, int len) throws IOException {
    ensureOpen();
    if (b != buffer && offset != 0) {
      throw new IllegalStateException();
    }
    if (len > 0) {
      final int compressedLen = codec.compress(b, off, len, encodedBuffer, 4);
      encodedBuffer[0] = (byte) (compressedLen >>> 24);
      encodedBuffer[1] = (byte) (compressedLen >>> 16);
      encodedBuffer[2] = (byte) (compressedLen >>> 8);
      encodedBuffer[3] = (byte) compressedLen;
      os.write(encodedBuffer, 0, 4 + compressedLen);
    }
  }

  private void encode() throws IOException {
    encodeFrom(buffer, 0, offset);
    offset = 0;
  }

  @Override
  public void flush() throws IOException {
    encode();
    os.flush();
  }

  @Override
  public void close() throws IOException {
    ensureOpen();
    try {
      flush();
    } finally {
      offset = -1;
      os.close();
    }
  }

  private void ensureOpen() throws IOException {
    if (offset == -1) {
      throw new IOException("This outputstream is already closed");
    }
  }

}
