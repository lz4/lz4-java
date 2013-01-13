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

# xxhash Java

xxhash hashing for Java, based on Yann Collet's work available at
http://code.google.com/p/xxhash/. xxhash is a non-cryptographic, extremly fast
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

# Documentation

 - [lz4](http://jpountz.github.com/lz4-java/1.0.0/docs/net/jpountz/lz4/package-summary.html)
 - [xxhash](http://jpountz.github.com/lz4-java/1.0.0/docs/net/jpountz/xxhash/package-summary.html)
 - [changelog](http://github.com/jpountz/lz4-java/blob/master/CHANGES.md)

# Build

## Requirements

 - JDK version 6 or newer,
 - ant,
 - ivy,
 - cpptasks.

If ivy and/or cpptasks are not installed yet, ant can take care of it for you,
just run `ant ivy-bootstrap cpptasks-bootstrap`. Both libraries will be
installed under ${user.home}/.ant/lib.

## Instructions

Then run `ant`. It will compile C and Java code and generate a self-contained
JAR file under the dist directory.

