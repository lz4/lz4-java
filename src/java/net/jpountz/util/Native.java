package net.jpountz.util;

/*
 * Copyright 2020 Adrien Grand and the lz4-java contributors.
 *
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
import java.io.FilenameFilter;

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
    } else if (osName.contains("Solaris") || osName.contains("SunOS")) {
      return OS.SOLARIS;
    } else {
      throw new UnsupportedOperationException("Unsupported operating system: "
          + osName);
    }
  }

  private static String resourceName() {
    OS os = os();
    String packagePrefix = Native.class.getPackage().getName().replace('.', '/');

    return "/" + packagePrefix + "/" + os.name + "/" + arch() + "/liblz4-java." + os.libExtension;
  }

  private static volatile boolean loaded = false;

  public static boolean isLoaded() {
    return loaded;
  }

  private static void cleanupOldTempLibs() {
    String tempFolder = new File(System.getProperty("java.io.tmpdir")).getAbsolutePath();
    File dir = new File(tempFolder);

    File[] tempLibFiles = dir.listFiles(new FilenameFilter() {
	private final String searchPattern = "liblz4-java-";
	public boolean accept(File dir, String name) {
	  return name.startsWith(searchPattern) && !name.endsWith(".lck");
	}
      });
    if(tempLibFiles != null) {
      for(File tempLibFile : tempLibFiles) {
	File lckFile = new File(tempLibFile.getAbsolutePath() + ".lck");
	if(!lckFile.exists()) {
	  try {
	    tempLibFile.delete();
	  }
	  catch(SecurityException e) {
	    System.err.println("Failed to delete old temp lib" + e.getMessage());
	  }
	}
      }
    }
  }

  public static synchronized void load() {
    if (loaded) {
      return;
    }

    cleanupOldTempLibs();

    // Try to load lz4-java (liblz4-java.so on Linux) from the java.library.path.
    try {
      System.loadLibrary("lz4-java");
      loaded = true;
      return;
    } catch (UnsatisfiedLinkError ex) {
      // Doesn't exist, so proceed to loading bundled library.
    }

    String resourceName = resourceName();
    InputStream is = Native.class.getResourceAsStream(resourceName);
    if (is == null) {
      throw new UnsupportedOperationException("Unsupported OS/arch, cannot find " + resourceName + ". Please try building from source.");
    }
    File tempLib = null;
    File tempLibLock = null;
    try {
      // Create the .lck file first to avoid a race condition
      // with other concurrently running Java processes using lz4-java.
      tempLibLock = File.createTempFile("liblz4-java-", "." + os().libExtension + ".lck");
      tempLib = new File(tempLibLock.getAbsolutePath().replaceFirst(".lck$", ""));
      // copy to tempLib
      try (FileOutputStream out = new FileOutputStream(tempLib)) {
	byte[] buf = new byte[4096];
	while (true) {
	  int read = is.read(buf);
	  if (read == -1) {
	    break;
	  }
	  out.write(buf, 0, read);
	}
      }
      System.load(tempLib.getAbsolutePath());
      loaded = true;
    } catch (IOException e) {
      throw new ExceptionInInitializerError("Cannot unpack liblz4-java: " + e);
    } finally {
      if (!loaded) {
	if (tempLib != null && tempLib.exists()) {
	  if (!tempLib.delete()) {
	    throw new ExceptionInInitializerError("Cannot unpack liblz4-java / cannot delete a temporary native library " + tempLib);
	  }
	}
	if (tempLibLock != null && tempLibLock.exists()) {
	  if (!tempLibLock.delete()) {
	    throw new ExceptionInInitializerError("Cannot unpack liblz4-java / cannot delete a temporary lock file " + tempLibLock);
	  }
	}
      } else {
        final String keepEnv = System.getenv("LZ4JAVA_KEEP_TEMP_JNI_LIB");
        final String keepProp = System.getProperty("lz4java.jnilib.temp.keep");
        if ((keepEnv == null || !keepEnv.equals("true")) &&
            (keepProp == null || !keepProp.equals("true")))
          tempLib.deleteOnExit();
	tempLibLock.deleteOnExit();
      }
    }
  }
}
