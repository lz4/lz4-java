# LZ4 Java

LZ4 compression for Java, based on Yann Collet's work available at
http://code.google.com/p/lz4/.

This library provides access to two compression methods that both generate a
valid LZ4 stream:
 - fast scan (LZ4):
   - low memory footprint (~ 16 KB),
   - very fast (fast scan with skipping heuristics in case the input looks
     incompressible),
   - reasonable compression ratio (depending on the redundancy of the input).
 - high compression (LZ4 HC):
   - medium memory footprint (~ 256 KB),
   - rather slow (~ 10 times slower than LZ4),
   - good compression ratio (depending on the size and the redundancy of the
     input).

The streams produced by those 2 compression algorithms use the same compression
format, are very fast to decompress and can be decompressed by the same
decompressor instance.

## Implementations

For LZ4 compressors, LZ4 HC compressors and decompressors, 3 implementations are
available:
 - JNI bindings to the original C implementation by Yann Collet,
 - a pure Java port of the compression and decompression algorithms,
 - a Java port that uses the sun.misc.Unsafe API in order to achieve compression
   and decompression speeds close to the C implementation.

Have a look at LZ4Factory for more information.

## Compatibility notes

 - Compressors and decompressors are interchangeable: it is perfectly correct
   to compress with the JNI bindings and to decompress with a Java port, or the
   other way around.

 - Compressors might not generate the same compressed streams on all platforms,
   especially if CPU endianness differs, but the compressed streams can be
   safely decompressed by any decompressor implementation on any platform.

## Examples

```java
LZ4Factory factory = LZ4Factory.fastestInstance();

byte[] data = "12345345234572".getBytes("UTF-8");
final int decompressedLength = data.length;

// compress data
LZ4Compressor compressor = factory.fastCompressor();
int maxCompressedLength = compressor.maxCompressedLength(decompressedLength);
byte[] compressed = new byte[maxCompressedLength];
int compressedLength = compressor.compress(data, 0, decompressedLength, compressed, 0, maxCompressedLength);

// decompress data
// - method 1: when the decompressed length is known
LZ4FastDecompressor decompressor = factory.fastDecompressor();
byte[] restored = new byte[decompressedLength];
int compressedLength2 = decompressor.decompress(compressed, 0, restored, 0, decompressedLength);
// compressedLength == compressedLength2

// - method 2: when the compressed length is known (a little slower)
// the destination buffer needs to be over-sized
LZ4SafeDecompressor decompressor2 = factory.safeDecompressor();
int decompressedLength2 = decompressor2.decompress(compressed, 0, compressedLength, restored, 0);
// decompressedLength == decompressedLength2
```

```java
byte[] data = "12345345234572".getBytes("UTF-8");
final int decompressedLength = data.length;

LZ4FrameOutputStream outStream = new LZ4FrameOutputStream(new FileOutputStream(new File("test.lz4")));
outStream.write(data);
outStream.close();

byte[] restored = new byte[decompressedLength];
LZ4FrameInputStream inStream = new LZ4FrameInputStream(new FileInputStream(new File("test.lz4")));
inStream.read(restored);
inStream.close();
```

# xxhash Java

xxhash hashing for Java, based on Yann Collet's work available at https://github.com/Cyan4973/xxHash (old version
http://code.google.com/p/xxhash/). xxhash is a non-cryptographic, extremly fast
and high-quality ([SMHasher](http://code.google.com/p/smhasher/wiki/SMHasher)
score of 10) hash function.

## Implementations

Similarly to LZ4, 3 implementations are available: JNI bindings, pure Java port
and pure Java port that uses sun.misc.Unsafe.

Have a look at XXHashFactory for more information.

## Compatibility notes

 - All implementation return the same hash for the same input bytes:
   - on any JVM,
   - on any platform (even if the endianness or integer size differs).

## Example

```java
XXHashFactory factory = XXHashFactory.fastestInstance();

byte[] data = "12345345234572".getBytes("UTF-8");
ByteArrayInputStream in = new ByteArrayInputStream(data);

int seed = 0x9747b28c; // used to initialize the hash value, use whatever
                       // value you want, but always the same
StreamingXXHash32 hash32 = factory.newStreamingHash32(seed);
byte[] buf = new byte[8]; // for real-world usage, use a larger buffer, like 8192 bytes
for (;;) {
  int read = in.read(buf);
  if (read == -1) {
    break;
  }
  hash32.update(buf, 0, read);
}
int hash = hash32.getValue();
```

# Download

You can download released artifacts from [Maven Central](https://search.maven.org/search?q=g:org.lz4%20a:lz4-java).

You can download pure-Java lz4-java from [Maven Central](https://search.maven.org/search?q=g:org.lz4%20a:lz4-pure-java). These artifacts include the Safe and Unsafe Java versions but not JNI bindings. (Experimental)

# Documentation

 - [lz4](https://lz4.github.io/lz4-java/1.8.0/docs/net/jpountz/lz4/package-summary.html)
 - [xxhash](https://lz4.github.io/lz4-java/1.8.0/docs/net/jpountz/xxhash/package-summary.html)
 - [changelog](https://github.com/lz4/lz4-java/blob/master/CHANGES.md)

# Performance

Both lz4 and xxhash focus on speed. Although compression, decompression and
hashing performance can depend a lot on the input (there are lies, damn lies
and benchmarks), here are some benchmarks that try to give a sense of the
speed at which they compress/decompress/hash bytes.

 - [lz4 compression](https://lz4.github.io/lz4-java/1.8.0/lz4-compression-benchmark/)
 - [lz4 decompression](https://lz4.github.io/lz4-java/1.8.0/lz4-decompression-benchmark/)
 - [xxhash hashing](https://lz4.github.io/lz4-java/1.3.0/xxhash-benchmark/)

# Build

## Requirements

 - JDK version 7 or newer,
 - ant version 1.10.2 or newer,
 - ivy.

If ivy is not installed yet, ant can take care of it for you, just run
`ant ivy-bootstrap`. The library will be installed under ${user.home}/.ant/lib.

You might hit an error like the following when the ivy in ${user.home}/.ant/lib is old. You can delete it and then run `ant ivy-bootstrap` again to install the latest version.
```
[ivy:resolve] 		::::::::::::::::::::::::::::::::::::::::::::::
[ivy:resolve] 		::          UNRESOLVED DEPENDENCIES         ::
[ivy:resolve] 		::::::::::::::::::::::::::::::::::::::::::::::
```

## Instructions

For lz4-java 1.5.0 or newer, first run `git submodule init` and then `git submodule update`
to initialize the `lz4` submodule in `src/lz4`.

Then run `ant`. It will:

 - generate some Java source files in `build/java` from the templates that are
   located under `src/build`,
 - compile the lz4 and xxhash libraries and their JNI (Java Native Interface)
   bindings,
 - compile Java sources in `src/java` (normal sources), `src/java-unsafe`
   (sources that make use of `sun.misc.Unsafe`) and `build/java`
   (auto-generated sources) to `build/classes`, `build/unsafe-classes` and
   `build/generated-classes`,
 - generate a JAR file called lz4-${version}.jar under the `dist` directory.

The JAR file that is generated contains Java class files, the native library
and the JNI bindings. If you add this JAR to your classpath, the native library
will be copied to a temporary directory and dynamically linked to your Java
application.
