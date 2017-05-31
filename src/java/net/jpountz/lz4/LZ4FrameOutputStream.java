package net.jpountz.lz4;

/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import net.jpountz.util.Utils;
import net.jpountz.xxhash.StreamingXXHash32;
import net.jpountz.xxhash.XXHash32;
import net.jpountz.xxhash.XXHashFactory;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.BitSet;

/**
 * A partial implementation of the v1.5.1 LZ4 Frame format. This class is NOT thread safe
 * Not Supported:
 * * Dependent blocks
 * * Legacy streams
 * * Multiple frames (one LZ4FrameOutputStream is one frame)
 *
 * @see <a href="https://github.com/lz4/lz4/blob/dev/doc/lz4_Frame_format.md">LZ4 Framing
 *      Format Spec 1.5.1</a>
 * 
 * Originally based on kafka's KafkaLZ4BlockOutputStream
 */
public class LZ4FrameOutputStream extends FilterOutputStream {

  protected static final int INTEGER_BYTES = Integer.SIZE >>> 3; // or Integer.BYTES in Java 1.8
  protected static final int LONG_BYTES = Long.SIZE >>> 3; // or Long.BYTES in Java 1.8

  static final int MAGIC = 0x184D2204;
  static final int LZ4_MAX_HEADER_LENGTH =
      4 + // magic
      1 + // FLG
      1 + // BD
      8 + // Content Size
      1; // HC
  static final int LZ4_FRAME_INCOMPRESSIBLE_MASK = 0x80000000;
  static final FLG.Bits[] DEFAULT_FEATURES = new FLG.Bits[]{FLG.Bits.BLOCK_INDEPENDENCE};

  static final String CLOSED_STREAM = "The stream is already closed";

  public static enum BLOCKSIZE {
    SIZE_64KB(4), SIZE_256KB(5), SIZE_1MB(6), SIZE_4MB(7);
    private final int indicator;
    BLOCKSIZE(int indicator) {
      this.indicator = indicator;
    }
    public int getIndicator() {
      return this.indicator;
    }
    public static BLOCKSIZE valueOf(int indicator) {
      switch(indicator) {
        case 7: return SIZE_4MB;
        case 6: return SIZE_1MB;
        case 5: return SIZE_256KB;
        case 4: return SIZE_64KB;
        default: throw new IllegalArgumentException(String.format("Block size must be 4-7. Cannot use value of [%d]", indicator));
      }
    }
  }

  private final LZ4Compressor compressor;
  private final XXHash32 checksum;
  private final ByteBuffer buffer; // Buffer for uncompressed input data
  private final byte[] compressedBuffer; // Only allocated once so it can be reused
  private final int maxBlockSize;
  private final long knownSize;
  private final ByteBuffer intLEBuffer = ByteBuffer.allocate(INTEGER_BYTES).order(ByteOrder.LITTLE_ENDIAN);

  private FrameInfo frameInfo = null;


  /**
   * Create a new {@link OutputStream} that will compress data using the LZ4 algorithm.
   *
   * @param out The output stream to compress
   * @param blockSize The BLOCKSIZE to use
   * @param bits A set of features to use
   * @throws IOException
   */
  public LZ4FrameOutputStream(OutputStream out, BLOCKSIZE blockSize, FLG.Bits... bits) throws IOException {
    this(out, blockSize, -1L, bits);
  }

  /**
   * Create a new {@link OutputStream} that will compress data using the LZ4 algorithm.
   *
   * @param out The output stream to compress
   * @param blockSize The BLOCKSIZE to use
   * @param knownSize The size of the uncompressed data. A value less than zero means unknown.
   * @param bits A set of features to use
   * @throws IOException
   */
  public LZ4FrameOutputStream(OutputStream out, BLOCKSIZE blockSize, long knownSize, FLG.Bits... bits) throws IOException {
    super(out);
    compressor = LZ4Factory.fastestInstance().fastCompressor();
    checksum = XXHashFactory.fastestInstance().hash32();
    frameInfo = new FrameInfo(new FLG(FLG.DEFAULT_VERSION, bits), new BD(blockSize));
    maxBlockSize = frameInfo.getBD().getBlockMaximumSize();
    buffer = ByteBuffer.allocate(maxBlockSize).order(ByteOrder.LITTLE_ENDIAN);
    compressedBuffer = new byte[compressor.maxCompressedLength(maxBlockSize)];
    if (frameInfo.getFLG().isEnabled(FLG.Bits.CONTENT_SIZE) && knownSize < 0) {
      throw new IllegalArgumentException("Known size must be greater than zero in order to use the known size feature");
    }
    this.knownSize = knownSize;
    writeHeader();
  }

  /**
   * Create a new {@link OutputStream} that will compress data using the LZ4 algorithm.
   *
   * @param out The stream to compress
   * @param blockSize The BLOCKSIZE to use. Default: 4. The block size used during compression. 4=64kb, 5=256kb, 6=1mb, 7=4mb. All other
   *            values will generate an exception
   * @throws IOException
   */
  public LZ4FrameOutputStream(OutputStream out, BLOCKSIZE blockSize) throws IOException {
    this(out, blockSize, DEFAULT_FEATURES);
  }

  /**
   * Create a new {@link OutputStream} that will compress data using the LZ4 algorithm.
   *
   * @param out The output stream to compress
   * @throws IOException
   */
  public LZ4FrameOutputStream(OutputStream out) throws IOException {
    this(out, BLOCKSIZE.SIZE_4MB);
  }

  /**
   * Writes the magic number and frame descriptor to the underlying {@link OutputStream}.
   *
   * @throws IOException
   */
  private void writeHeader() throws IOException {
    final ByteBuffer headerBuffer = ByteBuffer.allocate(LZ4_MAX_HEADER_LENGTH).order(ByteOrder.LITTLE_ENDIAN);
    headerBuffer.putInt(MAGIC);
    headerBuffer.put(frameInfo.getFLG().toByte());
    headerBuffer.put(frameInfo.getBD().toByte());
    if (frameInfo.isEnabled(FLG.Bits.CONTENT_SIZE)) {
      headerBuffer.putLong(knownSize);
    }
    // compute checksum on all descriptor fields
    final int hash = (checksum.hash(headerBuffer.array(), INTEGER_BYTES, headerBuffer.position() - INTEGER_BYTES, 0) >> 8) & 0xFF;
    headerBuffer.put((byte) hash);
    // write out frame descriptor
    out.write(headerBuffer.array(), 0, headerBuffer.position());
  }

  /**
   * Compresses buffered data, optionally computes an XXHash32 checksum, and writes the result to the underlying
   * {@link OutputStream}.
   *
   * @throws IOException
   */
  private void writeBlock() throws IOException {
    if (buffer.position() == 0) {
      return;
    }
    // Make sure there's no stale data
    Arrays.fill(compressedBuffer, (byte) 0);

    int compressedLength = compressor.compress(buffer.array(), 0, buffer.position(), compressedBuffer, 0);
    final byte[] bufferToWrite;
    final int compressMethod;

    // Store block uncompressed if compressed length is greater (incompressible)
    if (compressedLength >= buffer.position()) {
      compressedLength = buffer.position();
      bufferToWrite = Arrays.copyOf(buffer.array(), compressedLength);
      compressMethod = LZ4_FRAME_INCOMPRESSIBLE_MASK;
    } else {
      bufferToWrite = compressedBuffer;
      compressMethod = 0;
    }

    // Write content
    intLEBuffer.putInt(0, compressedLength | compressMethod);
    out.write(intLEBuffer.array());
    out.write(bufferToWrite, 0, compressedLength);

    // Calculate and write block checksum
    if (frameInfo.isEnabled(FLG.Bits.BLOCK_CHECKSUM)) {
      intLEBuffer.putInt(0, checksum.hash(bufferToWrite, 0, compressedLength, 0));
      out.write(intLEBuffer.array());
    }
    buffer.rewind();
  }

  /**
   * Similar to the {@link #writeBlock()} method. Writes a 0-length block (without block checksum) to signal the end
   * of the block stream.
   *
   * @throws IOException
   */
  private void writeEndMark() throws IOException {
    intLEBuffer.putInt(0, 0);
    out.write(intLEBuffer.array());
    if (frameInfo.isEnabled(FLG.Bits.CONTENT_CHECKSUM)) {
      intLEBuffer.putInt(0, frameInfo.currentStreamHash());
      out.write(intLEBuffer.array());
    }
    frameInfo.finish();
  }

  @Override
  public void write(int b) throws IOException {
    ensureNotFinished();
    if (buffer.position() == maxBlockSize) {
      writeBlock();
    }
    buffer.put((byte) b);

    if (frameInfo.isEnabled(FLG.Bits.CONTENT_CHECKSUM)) {
      frameInfo.updateStreamHash(new byte[]{(byte) b}, 0, 1);
    }
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    if ((off < 0) || (len < 0) || (off + len > b.length)) {
      throw new IndexOutOfBoundsException();
    }
    ensureNotFinished();

    // while b will fill the buffer
    while (len > buffer.remaining()) {
      int sizeWritten = buffer.remaining();
      // fill remaining space in buffer
      buffer.put(b, off, sizeWritten);
      if (frameInfo.isEnabled(FLG.Bits.CONTENT_CHECKSUM)) {
        frameInfo.updateStreamHash(b, off, sizeWritten);
      }
      writeBlock();
      // compute new offset and length
      off += sizeWritten;
      len -= sizeWritten;
    }
    buffer.put(b, off, len);

    if (frameInfo.isEnabled(FLG.Bits.CONTENT_CHECKSUM)) {
      frameInfo.updateStreamHash(b, off, len);
    }
  }

  @Override
  public void flush() throws IOException {
    if (!frameInfo.isFinished()) {
      writeBlock();
    }
    super.flush();
  }

  /**
   * A simple state check to ensure the stream is still open.
   */
  private void ensureNotFinished() {
    if (frameInfo.isFinished()) {
      throw new IllegalStateException(CLOSED_STREAM);
    }
  }

  @Override
  public void close() throws IOException {
    if (!frameInfo.isFinished()) {
      flush();
      writeEndMark();
    }
    super.close();
  }

  public static class FLG {
    private static final int DEFAULT_VERSION = 1;

    private final BitSet bitSet;
    private final int version;

    public enum Bits {
      RESERVED_0(0),
      RESERVED_1(1),
      CONTENT_CHECKSUM(2),
      CONTENT_SIZE(3),
      BLOCK_CHECKSUM(4),
      BLOCK_INDEPENDENCE(5);

      private final int position;
      Bits(int position) {
        this.position = position;
      }
    }

    public FLG(int version, Bits... bits) {
      this.bitSet = new BitSet(8);
      this.version = version;
      if (bits != null) {
        for (Bits bit : bits) {
          bitSet.set(bit.position);
        }
      }
      validate();
    }

    private FLG(int version, byte b) {
      this.bitSet = BitSet.valueOf(new byte[]{b});
      this.version = version;
      validate();
    }

    public static FLG fromByte(byte flg) {
      final byte versionMask = (byte)(flg & (3 << 6));
      return new FLG(versionMask >>> 6, (byte) (flg ^ versionMask));
    }

    public byte toByte() {
      return (byte)(bitSet.toByteArray()[0] | ((version & 3) << 6));
    }

    private void validate() {
      if (bitSet.get(Bits.RESERVED_0.position)) {
        throw new RuntimeException("Reserved0 field must be 0");
      }
      if (bitSet.get(Bits.RESERVED_1.position)) {
        throw new RuntimeException("Reserved1 field must be 0");
      }
      if (!bitSet.get(Bits.BLOCK_INDEPENDENCE.position)) {
        throw new RuntimeException("Dependent block stream is unsupported");
      }
      if (version != DEFAULT_VERSION) {
        throw new RuntimeException(String.format("Version %d is unsupported", version));
      }
    }

    public boolean isEnabled(Bits bit) {
      return bitSet.get(bit.position);
    }

    public int getVersion() {
      return version;
    }
  }

  public static class BD {
    private static final int RESERVED_MASK = 0x8F;

    private final BLOCKSIZE blockSizeValue;

    private BD(BLOCKSIZE blockSizeValue) {
      this.blockSizeValue = blockSizeValue;
    }

    public static BD fromByte(byte bd) {
      int blockMaximumSize = (bd >>> 4) & 7;
      if ((bd & RESERVED_MASK) > 0) {
        throw new RuntimeException("Reserved fields must be 0");
      }

      return new BD(BLOCKSIZE.valueOf(blockMaximumSize));
    }

    // 2^(2n+8)
    public int getBlockMaximumSize() {
      return 1 << ((2 * blockSizeValue.getIndicator()) + 8);
    }

    public byte toByte() {
      return (byte) ((blockSizeValue.getIndicator() & 7) << 4);
    }
  }

  public static class FrameInfo {
    private final FLG flg;
    private final BD bd;
    private final StreamingXXHash32 streamHash;
    private boolean finished = false;

    public FrameInfo(FLG flg, BD bd) {
      this.flg = flg;
      this.bd = bd;
      this.streamHash = flg.isEnabled(FLG.Bits.CONTENT_CHECKSUM) ? XXHashFactory.fastestInstance().newStreamingHash32(0) : null;
    }

    public boolean isEnabled(FLG.Bits bit) {
      return flg.isEnabled(bit);
    }

    public FLG getFLG() {
      return this.flg;
    }

    public BD getBD() {
      return this.bd;
    }

    public void updateStreamHash(byte[] buff, int off, int len) {
      this.streamHash.update(buff, off, len);
    }

    public int currentStreamHash() {
      return this.streamHash.getValue();
    }

    public void finish() {
      this.finished = true;
    }

    public boolean isFinished() {
      return this.finished;
    }
  }
}
