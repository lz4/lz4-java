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

import java.nio.ByteOrder;

public enum Utils {
  ;

  public static final ByteOrder NATIVE_BYTE_ORDER = ByteOrder.nativeOrder();

  private static final boolean unalignedAccessAllowed;
  static {
    String arch = System.getProperty("os.arch");
    unalignedAccessAllowed = arch.equals("i386") || arch.equals("x86")
            || arch.equals("amd64") || arch.equals("x86_64");
  }

  public static boolean isUnalignedAccessAllowed() {
    return unalignedAccessAllowed;
  }

  public static void checkRange(byte[] buf, int off) {
    if (off < 0 || off >= buf.length) {
      throw new ArrayIndexOutOfBoundsException(off);
    }
  }

  public static void checkRange(byte[] buf, int off, int len) {
    checkLength(len);
    if (len > 0) {
      checkRange(buf, off);
      checkRange(buf, off + len - 1);
    }
  }

  public static void checkLength(int len) {
    if (len < 0) {
      throw new IllegalArgumentException("lengths must be >= 0");
    }
  }

}
