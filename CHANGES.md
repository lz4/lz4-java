# Change log.

## 1.1.0

 - lz4 r88

 - [#7](http://github.com/jpountz/lz4-java/issues/7)
   LZ4Factory.fastestInstance() only tries to use the native bindings if:
   - they have already been loaded by the current class loader,
   - or if the current class loader is the system class loader.

## 1.0.0

 - First release.
 - Java ports and bindings of LZ4 and xxhash.
 - lz4 r87
 - xxhash r6
