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

import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;

import sun.misc.Unsafe;

public class UnsafeBase {

  protected static final Unsafe UNSAFE;
  protected static final long BYTE_ARRAY_OFFSET;
  protected static final int BYTE_ARRAY_SCALE;
  protected static final long INT_ARRAY_OFFSET;
  protected static final int INT_ARRAY_SCALE;
  protected static final long SHORT_ARRAY_OFFSET;
  protected static final int SHORT_ARRAY_SCALE;
  protected static final long BUFFER_ADDRESS_OFFSET;
  protected static final long BUFFER_ARRAY_OFFSET;
  protected static final long BUFFER_ARRAYOFFSET_OFFSET;

  public static final String POINTER_SIZE_SUFFIX;
  
  
  static {
    try {
      Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
      theUnsafe.setAccessible(true);
      UNSAFE = (Unsafe) theUnsafe.get(null);
      BYTE_ARRAY_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
      BYTE_ARRAY_SCALE = UNSAFE.arrayIndexScale(byte[].class);
      INT_ARRAY_OFFSET = UNSAFE.arrayBaseOffset(int[].class);
      INT_ARRAY_SCALE = UNSAFE.arrayIndexScale(int[].class);
      SHORT_ARRAY_OFFSET = UNSAFE.arrayBaseOffset(short[].class);
      SHORT_ARRAY_SCALE = UNSAFE.arrayIndexScale(short[].class);
      POINTER_SIZE_SUFFIX = UNSAFE.addressSize() == 4 ? "" : "Long";
      Field addressField = Buffer.class.getDeclaredField("address");
      BUFFER_ADDRESS_OFFSET = UNSAFE.objectFieldOffset(addressField);
      Field arrayField = ByteBuffer.class.getDeclaredField("hb");
      BUFFER_ARRAY_OFFSET = UNSAFE.objectFieldOffset(arrayField);
      Field arrayOffsetField = ByteBuffer.class.getDeclaredField("offset");
      BUFFER_ARRAYOFFSET_OFFSET = UNSAFE.objectFieldOffset(arrayOffsetField);
    } catch (IllegalAccessException e) {
      throw new ExceptionInInitializerError("Cannot access Unsafe");
    } catch (NoSuchFieldException e) {
      throw new ExceptionInInitializerError("Cannot access Unsafe");
    } catch (SecurityException e) {
      throw new ExceptionInInitializerError("Cannot access Unsafe");
    }
  }

  public static byte[] getReadOnlyBufferArray(ByteBuffer buffer)
  {
      return (byte[]) UNSAFE.getObject(buffer, BUFFER_ARRAY_OFFSET);
  }

  public static int getReadOnlyBufferArrayOffset(ByteBuffer buffer)
  {
      return UNSAFE.getInt(buffer, BUFFER_ARRAYOFFSET_OFFSET);
  }
}
