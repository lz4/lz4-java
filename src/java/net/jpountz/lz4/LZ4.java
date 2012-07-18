package net.jpountz.lz4;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * LZ4 compression and decompression functions.
 */
public enum LZ4 {
  ;

  private enum OS {
    WINDOWS("windows", "dll"), LINUX("linux", "so"), MAC("mac", "dylib");
    public final String name, libExtension;

    private OS(String name, String libExtension) {
      this.name = name;
      this.libExtension = libExtension;
    }
  }

  private static String arch() {
    return System.getProperty("os.arch");
  }

  private static OS os() {
    String osName = System.getProperty("os.name");
    if (osName.contains("Linux")) {
      return OS.LINUX;
    } else if (osName.contains("Mac")) {
      return OS.MAC;
    } else if (osName.contains("Windows")) {
      return OS.WINDOWS;
    } else {
      throw new UnsupportedOperationException("Unsupported operating system: "
          + osName);
    }
  }

  private static String resourceName() {
    OS os = os();
    return "/" + os.name + "/" + arch() + "/liblz4-java." + os.libExtension;
  }

  static {
    String resourceName = resourceName();
    InputStream is = LZ4.class.getResourceAsStream(resourceName);
    if (is == null) {
      System.out.println(resourceName);
      throw new UnsupportedOperationException("Unsupported OS/arch, cannot find " + resourceName);
    }
    File tempLib;
    try {
      tempLib = File.createTempFile("liblz4-java", "." + os().libExtension);
      // copy to tempLib
      FileOutputStream out = new FileOutputStream(tempLib);
      try {
        byte[] buf = new byte[4096];
        while (true) {
          int read = is.read(buf);
          if (read == -1) {
            break;
          }
          out.write(buf, 0, read);
        }
        try {
          out.close();
          out = null;
        } catch (IOException e) {
          // ignore
        }
        System.load(tempLib.getAbsolutePath());

        // init library
        init();
      } finally {
        try {
          if (out != null) {
            out.close();
          }
        } catch (IOException e) {
          // ignore
        }
      }
    } catch (IOException e) {
        throw new ExceptionInInitializerError("Cannot unpack liblz4-java");
    }
  }

  static native void init();
  static native int LZ4_compress(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff);
  static native int LZ4_compressHC(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff);
  static native int LZ4_uncompress(byte[] src, int srcOff, byte[] dest, int destOff, int destLen);
  static native int LZ4_uncompress_unknownOutputSize(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff, int maxDestLen);
  static native int LZ4_compressBound(int length);

  static void checkRange(byte[] buf, int off) {
    if (off < 0 || off >= buf.length) {
      throw new ArrayIndexOutOfBoundsException(off);
    }
  }

  static void checkRange(byte[] buf, int off, int len) {
    checkLength(len);
    if (len > 0) {
      checkRange(buf, off);
      checkRange(buf, off + len - 1);
    }
  }

  static void checkLength(int len) {
    if (len < 0) {
      throw new IllegalArgumentException("lengths must be >= 0");
    }
  }

  /** Return the maximum compressed length for an input of size <code>length</code>. */
  public static int maxCompressedLength(int length) {
    if (length < 0) {
      throw new IllegalArgumentException("length must be >= 0, got " + length);
    }
    return LZ4_compressBound(length);
  }

  /** Compress <code>src[srcOff:srcOff+srcLen]</code> into <code>dest[destOff:]</code> */
  public static int compress(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff) {
    checkRange(src, srcOff, srcLen);
    checkRange(dest, destOff);
    final int result = LZ4_compress(src, srcOff, srcLen, dest, destOff);
    if (result <= 0) {
      throw new LZ4Exception();
    }
    return result;
  }

  /** Compress <code>src[srcOff:srcOff+srcLen]</code> into <code>dest[destOff:]</code> */
  public static int compressHC(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff) {
    checkRange(src, srcOff, srcLen);
    checkRange(dest, destOff);
    final int result = LZ4_compressHC(src, srcOff, srcLen, dest, destOff);
    if (result <= 0) {
      throw new LZ4Exception();
    }
    return result;
  }

  /** Decompress <code>src[srcOff:]</code> into <code>dest[destOff:destOff+destLen]</code>.
   * <code>destLen</code> must be exactly the size of the uncompressed data. Return the
   * number of bytes decompressed from <code>src</code>. */
  public static int uncompress(byte[] src, int srcOff, byte[] dest, int destOff, int destLen) {
    checkRange(src, srcOff);
    checkRange(dest, destOff, destLen);
    final int result = LZ4_uncompress(src, srcOff, dest, destOff, destLen);
    if (result < 0) {
      throw new LZ4Exception("Error decoding offset " + (srcOff - result) + " of input buffer");
    }
    return result;
  }

  /**
   * Decompress <code>src[srcOff:srcLen]</code> into <code>dest[destOff:destOff+maxDestLen]</code>.
   * Returns the number of uncompressed bytes written into <code>dest</code>.
   */
  public static int uncompressUnknownSize(byte[] src, int srcOff, int srcLen, byte[] dest, int destOff, int maxDestLen) {
    checkRange(src, srcOff, srcLen);
    checkRange(dest, destOff, maxDestLen);
    final int result = LZ4_uncompress_unknownOutputSize(src, srcOff, srcLen, dest, destOff, maxDestLen);
    if (result < 0) {
      throw new LZ4Exception("Error decoding offset " + (srcOff - result) + " of input buffer");
    }
    return result;
  }

}

