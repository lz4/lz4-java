# Change log

## 1.1.1

 - [#11](http://github.com/jpountz/lz4-java/issues/11)
   Fixed bug in LZ4BlockOutputStream.write(int).

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
