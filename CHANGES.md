# Change log

## 1.5.1

- [#135](https://github.com/lz4/lz4-java/issues/135)
  Updated the JNI binding for Win32/amd64 from old LZ4 r123
  to LZ4 1.8.3. (Rei Odaira)

- [#134](https://github.com/lz4/lz4-java/issues/134)
  Added Automatic-Module-Name to the manifest to support
  the Java 9 modularity. (Rei Odaira)

- [#131](https://github.com/lz4/lz4-java/issues/131)
  Made the StreamingXXHash*JNI methods synchronized to avoid
  a rare-case race condition with GC. (Roman Leventov, Rei Odaira)

- [#128](https://github.com/lz4/lz4-java/pull/128)
  Changed the Maven Central download link from a direct link
  to a search link. (Adam Retter)

## 1.5.0

 - Upgraded LZ4 to 1.8.3. Updated JNI bindings for Linux/amd64,
   Linux/ppc64le, Linux/s390x, Linux/aarch64, and Mac OS Darwin/x86_64.
   A speed-up is expected on these platforms.
   Note that the JNI bindings for Linux/i386 and Win32 still work
   but are based on old LZ4 r123. Contributions of the JNI
   bindings for these and other platforms are more than welcome.

 - [#119](https://github.com/lz4/lz4-java/issues/119)
   Implemented LZ4CompressorWithLength, which includes the length of
   the original decompressed data in the output compressed data,
   and corresponding LZ4DecompressorWithLength. (Rei Odaira)

 - [#118](https://github.com/lz4/lz4-java/pull/118)
   Added build status to README. (Daniel Yu)

 - [#116](https://github.com/lz4/lz4-java/issues/116)
   Changed LZ4BlockOutputStream and LZ4BlockInputStream to non-final
   for better testing support. (Rei Odaira)

 - [#113](https://github.com/lz4/lz4-java/pull/113)
   Compressor and checksum can be specified for LZ4FrameOutputStream.
   (Elan Kugelmass)

 - [#111](https://github.com/lz4/lz4-java/pull/111)
   Added lz4 sources as a git submodule. (Daniel Yu)

 - [#110](https://github.com/lz4/lz4-java/pull/110)
   Enabled Travis CI. (Daniel Yu)

 - Better test coverage. (Rei Odaira)

 - Supported build with Java 9. Note that the distribution is still
   built with Java 7. (Rei Odaira)

## 1.4.1

 - [#120](https://github.com/lz4/lz4-java/pull/120)
   Fixed LZ4{Block|Frame}InputStream.skip() to return 0 when n <= 0 or EOF. (Xiaoyan Lin)

 - [#117](https://github.com/lz4/lz4-java/issues/117)
   Fixed LZ4FrameInputStream.read() to return a correct value when reading a byte >= 128. (sorenop)

 - [#109](https://github.com/lz4/lz4-java/pull/109)
   Updated xxHash URL in README.md. (lyrachord)

 - Improved the documentation. (Rei Odaira)

## 1.4.0

 - The project page has been moved to
   [https://github.com/lz4/lz4-java](https://github.com/lz4/lz4-java).
   (Adrien Grand, Yann Collet)

 - groupId and artifactId have been changed from net.jpountz.lz4 and lz4
   to org.lz4 and lz4-java, respectively. (Rei Odaira)

 - [#105](https://github.com/lz4/lz4-java/pull/105)
   LZ4BlockInputStream can read concatenated streams.
   (Takeshi Yamamuro, Davies Liu)

 - [#99](https://github.com/lz4/lz4-java/pull/99)
   LZ4FrameInputStream allows EndMark to be incompressible. (Charles Allen)

 - [#95](https://github.com/lz4/lz4-java/pull/95)
   Added unsafe instance support for aarch64. (Yuqi Gu)

 - [#93](https://github.com/lz4/lz4-java/pull/93)
   Added unsafe instance support for ppc64le. (Madhusudanan Kandasamy)

 - [#90](https://github.com/lz4/lz4-java/issues/90)
   LZ4 Java now supports 64-bit JNI build on Solaris. (cndcourt)

 - [#86](https://github.com/lz4/lz4-java/pull/86)
   Added a pre-built JNI binding for aarch64. (Rocky Zhang)

 - [#85](https://github.com/lz4/lz4-java/pull/85)
   Added a pre-built JNI binding for s390x. (Rei Odaira)

 - [#84](https://github.com/lz4/lz4-java/pull/84)
   Added a pre-built JNI binding for ppc64le. (Rei Odaira)

 - [#83](https://github.com/lz4/lz4-java/pull/83)
   LZ4 Java tries to load a JNI binding from java.library.path first, and then
   fall back to bundled one. (Jakub Jirutka)

 - [#70](https://github.com/lz4/lz4-java/issues/70) and [#89](https://github.com/lz4/lz4-java/issues/89)
   Avoid over-allocating a buffer in LZ4BlockInputStream. (Marko Topolnik)

 - [#65](https://github.com/lz4/lz4-java/pull/65)
   Fixed ByteBuffer methods failing to apply arrayOffset() for
   array-backed buffers. (Branimir Lambov)

 - [#63](https://github.com/lz4/lz4-java/pull/63)
   All resources are placed under the net.jpountz package to support
   the maven shade plugin. (Chris Lockfort)

 - [#61](https://github.com/lz4/lz4-java/pull/61)
   Added LZ4 Frame version 1.5.1 support. (Charles Allen)

 - [#60](https://github.com/lz4/lz4-java/pull/60)
   Fixed NullPointerException in LZ4Factory and XXHashFactory when they are
   loaded by the bootstrap classloader. (Yilong Li)

 - [#53](https://github.com/lz4/lz4-java/issues/53)
   Fixed calling flush on closed LZ4BlockOutputStream.
   (Will Droste)

## 1.3.0

 - lz4 r123

 - xxhash r37

 - [#49](https://github.com/jpountz/lz4-java/pull/49)
   All compression and decompression routines as well as xxhash can now work
   with java.nio.ByteBuffer. (Branimir Lambov)

 - [#46](https://github.com/jpountz/lz4-java/pull/46)
   Fixed incorrect usage of ReleasePrimitiveArrayCritical. (Xiaoguang Sun)

 - [#44](https://github.com/jpountz/lz4-java/pull/44)
   Added support for xxhash64. (Linnaea Von Lavia)

 - [#43](https://github.com/jpountz/lz4-java/pull/43)
   The compression level for high compression is now configurable.
   (Linnaea Von Lavia)

 - [#39](https://github.com/jpountz/lz4-java/pull/39)
   The JAR is now a valid OSGI bundle. (Simon Chemouil)

 - [#33](https://github.com/jpountz/lz4-java/pull/33)
   The implementation based on Java's sun.misc.Unsafe relies on unaligned
   memory access and is now only used on platforms that support it.
   (Dmitry Shohov)


## 1.2.0

 - lz4 r100

 - [#16](http://github.com/jpountz/lz4-java/issues/16)
   Fix violation of the Closeable contract in LZ4BlockOutputStream: double close
   now works as specified in the Closeable interface documentation.
   (Steven Schlansker)

 - [#17](http://github.com/jpountz/lz4-java/issues/17)
   The JNI HC compressor now supports maxDestLen < maxCompressedLength.
   (Adrien Grand)

 - [#12](http://github.com/jpountz/lz4-java/issues/12)
   Fixed ArrayIndexOutOfBoundsException in the Java HC compressors on highly
   compressible inputs when srcOff is > 0. (Brian S. O'Neill, @foresteve,
   Adrien Grand)

 - Decompressors have been renamed to "safe" and "fast" to reflect changes in
   the C API. (Adrien Grand)

 - [#18](http://github.com/jpountz/lz4-java/issues/18)
   Added utility methods that take and return (de)compressed byte[]s.
   (Adrien Grand)

## 1.1.2

 - LZ4BlockInputStream does not support mark/reset anymore. (Adrien Grand)

 - LZ4BlockOutputStream supports a new syncFlush parameter to configure whether
   the flush method should flush pending data or just flush the underlying
   stream. (Adrien Grand)

 - [#14](http://github.com/jpountz/lz4-java/issues/14)
   Fixed misspelled API. (Brian S. O'Neill)

 - [#13](http://github.com/jpountz/lz4-java/issues/13)
   Header must be fully read. (Gabriel Ki)

## 1.1.1

 - [#11](http://github.com/jpountz/lz4-java/issues/11)
   Fixed bug in LZ4BlockOutputStream.write(int). (Adrien Grand, Brian Moore)

## 1.1.0

 - lz4 r88

 - [#7](http://github.com/jpountz/lz4-java/issues/7)
   LZ4Factory.fastestInstance() only tries to use the native bindings if:
   - they have already been loaded by the current class loader,
   - or if the current class loader is the system class loader.
   (Adrien Grand)

 - [#5](http://github.com/jpountz/lz4-java/issues/5)
   The native instances unpack a shared library to the temporary directory when
   they are first used. lz4-java now tries to remove this file on exist but
   this might fail on systems that don't support removal of open files such as
   Windows. (Adrien Grand)

 - Added LZ4Factory.fastestJavaInstance() and XXHash.fastestJavaInstance().
   (Adrien Grand)

 - Added StreamingXXHash32.asChecksum() to return a java.util.zip.Checksum
   view. (Adrien Grand)

 - [#10](http://github.com/jpountz/lz4-java/issues/10)
   Added LZ4BlockOutputStream which compresses data into fixed-size blocks of
   configurable size.
   (Adrien Grand, Brian Moore)

 - [#5](http://github.com/jpountz/lz4-java/issues/5)
   Fixed Windows build. (Rui Gonçalves)

 - Fixed Mac build. (Adrien Maglo)

 - [#8](http://github.com/jpountz/lz4-java/issues/5)
   Provided pre-built JNI bindings for some major platforms: Windows/64,
   Linux/32, Linux/64 and Mac Intel/64. (Rui Gonçalves, Adrien Maglo,
   Adrien Grand)

## 1.0.0

 - lz4 r87

 - xxhash r6
