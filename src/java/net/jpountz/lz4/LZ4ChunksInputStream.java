package net.jpountz.lz4;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import static net.jpountz.lz4.LZ4ChunksOutputStream.MAGIC;

/**
 * Uncompress a stream which has been compressed with
 * {@link LZ4ChunksOutputStream}. You need to provide the same
 * {@link CompressionCodec} as the one which has been used for compression.
 */
public class LZ4ChunksInputStream extends InputStream {

  private final InputStream is;
  private final CompressionCodec codec;
  private byte[] buffer;
  private byte[] encodedBuffer;
  private int offset, length;
  private int encodedLength;

  private final byte[] singleByteBuffer = new byte[1];

  public LZ4ChunksInputStream(InputStream is, CompressionCodec codec) throws IOException {
    this.is = is;
    this.codec = codec;
    buffer = new byte[1024];
    encodedBuffer = new byte[codec.maxCompressedLength(buffer.length)];
    offset = length = 0;

    if (!readAtLeast(MAGIC.length)) {
      throw new EOFException("EOF reached prematurely");
    }
    for (int i = 0; i < MAGIC.length; ++i) {
      if (MAGIC[i] != encodedBuffer[i]) {
        throw new IOException("Malformed stream");
      }
    }
    encodedLength -= MAGIC.length;
    System.arraycopy(encodedBuffer, MAGIC.length, encodedBuffer, 0, encodedLength);
  }

  private void ensureOpen() throws IOException {
    if (offset == -1) {
      throw new IOException("Already closed");
    }
  }

  @Override
  public void close() throws IOException {
    try {
      ensureOpen();
      offset = -1;
    } finally {
      is.close();
    }
  }

  @Override
  public int available() throws IOException {
    ensureOpen();
    return length;
  }

  @Override
  public int read() throws IOException {
    return read(singleByteBuffer) == -1 ? -1 : singleByteBuffer[0];
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    ensureOpen();
    if (!refill()) {
      return -1;
    }
    final int copied = Math.min(len, length);
    System.arraycopy(buffer, offset, b, off, copied);
    offset += copied;
    length -= copied;
    return copied;
  }

  private boolean readAtLeast(int length) throws IOException {
    while (encodedLength < length) {
      final int inc = is.read(encodedBuffer, encodedLength, encodedBuffer.length - encodedLength);
      if (inc == -1) {
        if (encodedLength == 0) {
          return false;
        } else {
          throw new EOFException("Malformed input data");
        }
      }
      encodedLength += inc;
    }
    return true;
  }

  private boolean refill() throws IOException {
    if (length > 0) {
      return true;
    }

    // read the compressed length
    if (!readAtLeast(4)) {
      return false;
    }

    final int compressedLength = ((encodedBuffer[0] & 0xFF) << 24) | ((encodedBuffer[1] & 0xFF) << 16) | ((encodedBuffer[2] & 0xFF) << 8) | (encodedBuffer[3] & 0xFF);
    if (encodedBuffer.length < 4 + compressedLength) {
      encodedBuffer = Arrays.copyOf(encodedBuffer, Math.max(compressedLength + 4, encodedBuffer.length * 2));
    }

    // read the data
    boolean success = readAtLeast(4 + compressedLength);
    assert success;

    // uncompress
    final int maxUncompressedLength = codec.maxUncompressedLength(encodedBuffer, 4, compressedLength);
    if (buffer.length < maxUncompressedLength) {
      buffer = Arrays.copyOf(buffer, Math.max(maxUncompressedLength, buffer.length * 2));
    }

    offset = 0;
    length = codec.uncompress(encodedBuffer, 4, compressedLength, buffer, 0);

    // move encoded data to the beginning of the buffer
    encodedLength -= 4 + compressedLength;
    System.arraycopy(encodedBuffer, 4 + compressedLength, encodedBuffer, 0, encodedLength);

    return true;
  }

  @Override
  public long skip(long n) throws IOException {
    ensureOpen();
    if (n <= 0 || !refill()) {
      return 0;
    }

    final int skipped = (int) Math.min(length, n);
    offset += skipped;
    length -= skipped;
    return skipped;
  }
}
