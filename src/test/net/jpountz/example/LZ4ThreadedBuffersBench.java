package net.jpountz.example;

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

import java.nio.ByteBuffer;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Random;
import java.util.Stack;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;

public class LZ4ThreadedBuffersBench<T> {
  
  static final int THREADS = 4;
  static final int IN_FLIGHT = 80;
  static final int TOTAL = 200;
  
  LZ4Compressor compressor;
  LZ4FastDecompressor decompressor;
  final T source;
  final T compressed;
  int sourceLength;
  int compressedLength;
  final Tester<T> tester;
  Stack<T> buffers;
  
  byte[] bytes;
  

  public static void main(String[] args) throws Exception {
    byte[] bytes = Files.readAllBytes(
        FileSystems.getDefault().getPath("src/test-resources/calgary/book1"));

    for (LZ4Factory factory: Arrays.asList(
        LZ4Factory.nativeInstance(), LZ4Factory.unsafeInstance(), LZ4Factory.safeInstance())) {
      for (Tester<?> tester: Arrays.asList(
          new ByteArrays(), new HeapBuffers(), new DirectBuffers())) try {
        System.out.format("Factory %s storage %s init... ", factory.toString(), tester.getClass().getSimpleName());
        new LZ4ThreadedBuffersBench<>(bytes, factory, tester);
        System.out.format("success.\n");
      } catch ( Throwable t ) {
        System.out.flush();
        t.printStackTrace();
      }
    }
    
    for (LZ4Factory factory: Arrays.asList(
        LZ4Factory.nativeInstance(), LZ4Factory.unsafeInstance(), LZ4Factory.safeInstance())) {
      for (Tester<?> tester: Arrays.asList(
          new ByteArrays(), new HeapBuffers(), new DirectBuffers())) try {
        System.out.format("\nFactory %s storage %s\n", factory.toString(), tester.getClass().getSimpleName());
        LZ4ThreadedBuffersBench<?> tt = new LZ4ThreadedBuffersBench<>(bytes, factory, tester);
        for (int i=0;i<10;++i)
          tt.run(false);
        System.out.format("now with GC churn\n", factory.toString(), tester.getClass().getSimpleName());
        for (int i=0;i<10;++i)
          tt.run(true);
      } catch ( Throwable t ) {
        System.out.flush();
        t.printStackTrace();
      }
    }
  }
  
  public LZ4ThreadedBuffersBench(byte[] bytes, LZ4Factory factory, Tester<T> tester) throws Exception {
    sourceLength = bytes.length;
    source = tester.copyAsBuffer(bytes, bytes.length);
    compressor = factory.highCompressor();
    compressed = tester.compress(compressor, source);
    compressedLength = tester.length(compressed);
    decompressor = factory.fastDecompressor();
    tester.test(this);
    this.tester = tester;
    buffers = new Stack<T>();
    byte[] bb = new byte[sourceLength];
    new Random().nextBytes(bb);
    for (int i=0; i<=THREADS; ++i) {
      buffers.push(tester.copyAsBuffer(bb, sourceLength));
    }
  }
  
  public void run(boolean gcChurn) throws InterruptedException, ExecutionException {
    ExecutorService e = Executors.newFixedThreadPool(THREADS);
    int count = 0;
    long time = System.nanoTime();
    Deque<byte[]> allocs = new ArrayDeque<>();
    Deque<Future<T>> tasks = new ArrayDeque<>();
    byte val = 0;
    for (int i=0; i<IN_FLIGHT; ++i) {
      tasks.push(e.submit(tester.createDecompressionTask(this)));
      ++count;
    }
    Future<T> task = tasks.poll();
    while (task != null) {
        while (!task.isDone()) {
          byte[] v = new byte[1<<10];
          Arrays.fill(v, val++);
          allocs.push(v);
          if (allocs.size() > (gcChurn ? 1<<16 : 1)) allocs.poll();
        }
      task.get();
      if (count < TOTAL) {
        tasks.push(e.submit(tester.createDecompressionTask(this)));
        ++count;
      }
      task = tasks.poll();
    }
    time = System.nanoTime() - time;
    e.shutdown();
    System.out.format("Decompressing %d arrays of %d->%d bytes in %d threads with queue of %d   ",
        TOTAL, sourceLength, compressedLength, THREADS, IN_FLIGHT);
    System.out.format("Time %.3f s, Rate %.3f MB/s\n", time / 1.e9, TOTAL * sourceLength * 1.e3 / time);
  }
  
  interface Tester<T> {
    T copyAsBuffer(byte[] content, int len);
    T compress(LZ4Compressor compressor, T source);
    void test(LZ4ThreadedBuffersBench<T> test);
    int length(T buf);
    Callable<T> createDecompressionTask(LZ4ThreadedBuffersBench<T> test);
  }
  
  static class ByteArrays implements Tester<byte[]> {

    @Override
    public byte[] copyAsBuffer(byte[] content, int len) {
      return Arrays.copyOf(content, len);
    }

    @Override
    public Callable<byte[]> createDecompressionTask(
        final LZ4ThreadedBuffersBench<byte[]> test) {
      return new Callable<byte[]>() {

        @Override
        public byte[] call() throws Exception {
          byte[] uncompressed = test.buffers.pop();
          test.decompressor.decompress(test.compressed, uncompressed);
          test.buffers.push(uncompressed);
          return null;
        }
        
      };
    }

    @Override
    public byte[] compress(LZ4Compressor compressor, byte[] source) {
      return compressor.compress(source);
    }

    @Override
    public int length(byte[] buf) {
      return buf.length;
    }

    @Override
    public void test(LZ4ThreadedBuffersBench<byte[]> test) {
      byte[] uncompressed = new byte[test.sourceLength];
      test.decompressor.decompress(test.compressed, uncompressed);
      if (!Arrays.equals(uncompressed, test.source))
        throw new AssertionError("Decompression error.");
    }
    
  }
  
  abstract static class ByteBuffersBase implements Tester<ByteBuffer> {
    
    abstract public ByteBuffer allocate(int size);

    @Override
    public ByteBuffer copyAsBuffer(byte[] content, int len) {
      ByteBuffer buf = allocate(len);
      buf.put(content, 0, len).flip();
      return buf;
    }

    @Override
    public Callable<ByteBuffer> createDecompressionTask(
        final LZ4ThreadedBuffersBench<ByteBuffer> test) {
      return new Callable<ByteBuffer>() {

        @Override
        public ByteBuffer call() throws Exception {
          ByteBuffer uncompressed = test.buffers.pop();
          test.decompressor.decompress(test.compressed, 0, uncompressed, 0, test.sourceLength);
          test.buffers.push(uncompressed);
          return null;
        }
        
      };
    }

    @Override
    public ByteBuffer compress(LZ4Compressor compressor, ByteBuffer source) {
      ByteBuffer dest = allocate(compressor.maxCompressedLength(source.remaining()));
      compressor.compress(source, dest);
      dest.flip();
      source.position(0);
      return dest;
    }

    @Override
    public int length(ByteBuffer buf) {
      return buf.remaining();
    }
    

    @Override
    public void test(LZ4ThreadedBuffersBench<ByteBuffer> test) {
      ByteBuffer uncompressed = allocate(test.sourceLength);
      test.decompressor.decompress(test.compressed, 0, uncompressed, 0, test.sourceLength);
      if (!uncompressed.equals(test.source))
        throw new AssertionError("Decompression error.");
    }
  }
  
  static class HeapBuffers extends ByteBuffersBase {

    @Override
    public ByteBuffer allocate(int size) {
      return ByteBuffer.allocate(size);
    }
    
  }
  
  static class DirectBuffers extends ByteBuffersBase {

    @Override
    public ByteBuffer allocate(int size) {
      return ByteBuffer.allocateDirect(size);
    }
    
  }

}
