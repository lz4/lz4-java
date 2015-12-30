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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import net.jpountz.util.SafeUtils;

/**
 * <p>{@link InputStream} implementation to decode data written by any other implementation.</p> 
 * 
 * <p>This class is not thread-safe and does not
 * support {@link #mark(int)}/{@link #reset()}.</p>
 */
public final class LZ4CompatibleInputStream extends FilterInputStream {
  
  // Use 64kb block size
  // TODO: Adapt this while de-compressing using the hint returned by lz4f
  // Use a 1mb buffer for uncompressed data.
  // TODO: Adapt this after reading the block frame info

  private byte[] outputBuffer;
  private byte[] inputBuffer;
  
  private int inputOffset = 0;
  private int outputOffset = 0;
  
  private int inputAvailable = 0;
  private int outputAvailable = 0;
  
  private long lz4fContext;
  
  // Used to read errors from JNI
  private LZ4JNI.LZ4FError error = new LZ4JNI.LZ4FError();
  
  /**
   * Create a new {@link InputStream}.
   *
   * @param in
   *          the {@link InputStream} to poll
   * @throws IOException
   *           If a decompression context could not be created.
   */
  public LZ4CompatibleInputStream(InputStream in, int inputBlockSize, int outputBlockSize) throws IOException {
    super(in);
    
    this.outputBuffer = new byte[outputBlockSize];
    this.inputBuffer = new byte[inputBlockSize];
    
    // The error structure is filled if an error occurs.
    lz4fContext = LZ4JNI.LZ4F_createDecompressionContext(error);
    error.check();
  }
  
  public LZ4CompatibleInputStream(InputStream in) throws IOException {
    this(in, 64 * 1024, 64 * 1024);
  }
  
  @Override
  public int available() throws IOException {
    return outputBuffer.length - outputOffset;
  }

  @Override
  public int read() throws IOException {
    
    if (outputOffset == outputAvailable) {
      refill();
    }
    
    if (inputAvailable == -1 && outputOffset == outputAvailable) {
      return -1;
    }

    // The binary AND helps fix sign issues when casting from byte to int
    return outputBuffer[outputOffset++] & 0xFF;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    SafeUtils.checkRange(b, off, len);

    if (outputOffset == outputAvailable) {
      refill();
    }
    
    if (inputAvailable == -1 && outputOffset == outputAvailable) {
      return -1;
    }
    len = Math.min(len, outputAvailable - outputOffset);
    System.arraycopy(outputBuffer, outputOffset, b, off, len);
    
    outputOffset += len;
    return len;
  }

  @Override
  public long skip(long n) throws IOException {
    
    if (outputOffset == outputAvailable) {
      refill();
    }
    
    if (inputAvailable == -1 && outputOffset == outputAvailable) {
      return -1;
    }
    
    final int skipped = (int) Math.min(n, outputAvailable - outputOffset);
    outputOffset += skipped;
    return skipped;
  }
  
  @Override
  public void close() throws IOException {
    super.close();
    
    LZ4JNI.LZ4F_freeDecompressionContext(lz4fContext, error);
    error.check();
  }

  /**
   * <p>Refill the internal output (decompressed) buffer.</p> 
   * <p>In the process, refill the internal input (compressed)
   * buffer as many times as required.</p>
   * @throws IOException
   */
  private void refill() throws IOException {
    // All our output buffer has been read, so we can discard it.
    outputOffset = 0;
    outputAvailable = 0;
    
    // Keep reading while there is still some left, and we haven't filled our buffer
    while (outputAvailable < outputBuffer.length && inputAvailable != -1) {

      if (inputOffset == inputAvailable) {
        // We've read everything, slurp in a new page
        inputAvailable = in.read(inputBuffer, 0, inputBuffer.length - inputOffset);
        inputOffset = 0;
        
        if (inputAvailable == -1) {
          // Couldn't read any more? Means we're done!
          return;
        }
      }
      
      int result = LZ4JNI.LZ4F_decompress(lz4fContext, 
          inputBuffer, inputOffset, inputAvailable - inputOffset, 
          outputBuffer, outputOffset, outputBuffer.length - outputOffset, 
          error);
      error.check();
      
      // If result >= 0, it's the number of bytes written (and the source has been exhausted)
      if (result >= 0) {
        // All input got read before filling the output buffer. Ok, next!
        outputAvailable += result;
        inputOffset = inputAvailable;
      } else {
        // Else, it's the number of bytes read (and the destination is full)
        // The destination buffer is ready!
        int bytesRead = -result-1;
        inputOffset += bytesRead;
        outputAvailable = outputBuffer.length;
      }
    }
  }

  @Override
  public boolean markSupported() {
    return false;
  }

  @SuppressWarnings("sync-override")
  @Override
  public void mark(int readlimit) {
    // unsupported
  }

  @SuppressWarnings("sync-override")
  @Override
  public void reset() throws IOException {
    throw new IOException("mark/reset not supported");
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(in=" + in + ")";
  }
}
