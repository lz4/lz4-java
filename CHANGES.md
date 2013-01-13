# Change log

## 1.1.0

 - lz4 r88

 - LZ4Factory.fastestInstance() only tries to use the native bindings if:
   - they have already been loaded by the current class loader,
   - or if the current class loader is the system class loader.

 - Added LZ4Factory.fastestJavaInstance() and XXHash.fastestJavaInstance().

 - Added StreamingXXHash32.asChecksum() to return a java.util.zip.Checksum
   view.

## 1.0.0

 - lz4 r87

 - xxhash r6
