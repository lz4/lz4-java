package net.jpountz.util;

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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** FOR INTERNAL USE ONLY */
public enum Native {
  ;

  private enum OS {
    // Even on Windows, the default compiler from cpptasks (gcc) uses .so as a shared lib extension
    WINDOWS("win32", "so"), LINUX("linux", "so"), MAC("darwin", "dylib"), SOLARIS("solaris", "so");
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
    } else if (osName.contains("Solaris")) {
      return OS.SOLARIS;
    } else {
      throw new UnsupportedOperationException("Unsupported operating system: "
          + osName);
    }
  }

  private static String resourceName() {
    OS os = os();
    return "/" + os.name + "/" + arch() + "/liblz4-java." + os.libExtension;
  }

  private static boolean loaded = false;

  public static synchronized boolean isLoaded() {
    return loaded;
  }

  public static synchronized void load() {
    if (loaded) {
      return;
    }
    String resourceName = resourceName();
    InputStream is = Native.class.getResourceAsStream(resourceName);
    if (is == null) {
      throw new UnsupportedOperationException("Unsupported OS/arch, cannot find " + resourceName + ". Please try building from source.");
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
        loaded = true;
      } finally {
        try {
          if (out != null) {
            out.close();
          }
        } catch (IOException e) {
          // ignore
        }
        if (tempLib != null && tempLib.exists()) {
          if (!loaded) {
            tempLib.delete();
          } else {
            // try to delete on exit, does it work on Windows?
            tempLib.deleteOnExit();
          }
        }
      }
    } catch (IOException e) {
        throw new ExceptionInInitializerError("Cannot unpack liblz4-java");
    }
  }

}
