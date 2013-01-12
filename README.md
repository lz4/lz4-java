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

For LZ4 compressors, LZ4 HC compressors and decompressors, 3 implementations are
available:
 - JNI bindings to the original C implementation by Yann Collet,
 - a pure Java port of the compression and decompression algorithms,
 - a Java port that uses the sun.misc.Unsafe API in order to achieve compression
   and decompression speeds similar to the C implementation.

Please have a look at LZ4Factory for more information.

On 64-bits platforms, the Java compressor implementations compress data the same
way as the C implementation (meaning that the resulting arrays are identical).
However, similarly to the C implementation, compression might return different
compressed arrays on a machine of the opposite endianness.

# xxhash Java

xxhash hashing for Java, based on Yann Collet's work available at
http://code.google.com/p/xxhash/.

Similarly to LZ4, 3 implementations are available: JNI bindings, pure Java port
and pure Java port that uses sun.misc.Unsafe.

All implementations return the same result for the same input bytes.

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

