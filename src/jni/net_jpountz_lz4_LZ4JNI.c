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

#include <stdint.h>

#include "lz4.h"
#include "lz4hc.h"
#include "lz4frame.h"
#include "net_jpountz_lz4_LZ4JNI.h"

static jclass OutOfMemoryError;

/*
 * Class:     net_jpountz_lz4_LZ4
 * Method:    init
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_net_jpountz_lz4_LZ4JNI_init
  (JNIEnv *env, jclass cls) {
  OutOfMemoryError = (*env)->FindClass(env, "java/lang/OutOfMemoryError");
}

static void throw_OOM(JNIEnv *env) {
  (*env)->ThrowNew(env, OutOfMemoryError, "Out of memory");
}

/*
 * Class:     net_jpountz_lz4_LZ4JNI
 * Method:    LZ4_compress_limitedOutput
 * Signature: ([BLjava/nio/ByteBuffer;II[BLjava/nio/ByteBuffer;II)I
 */
JNIEXPORT jint JNICALL Java_net_jpountz_lz4_LZ4JNI_LZ4_1compress_1limitedOutput
  (JNIEnv *env, jclass cls, jbyteArray srcArray, jobject srcBuffer, jint srcOff, jint srcLen, jbyteArray destArray, jobject destBuffer, jint destOff, jint maxDestLen) {

  char* in;
  char* out;
  jint compressed;

  if (srcArray != NULL) {
    in = (char*) (*env)->GetPrimitiveArrayCritical(env, srcArray, 0);
  } else {
    in = (char*) (*env)->GetDirectBufferAddress(env, srcBuffer);
  }

  if (in == NULL) {
    throw_OOM(env);
    return 0;
  }

  if (destArray != NULL) {
    out = (char*) (*env)->GetPrimitiveArrayCritical(env, destArray, 0);
  } else {
    out = (char*) (*env)->GetDirectBufferAddress(env, destBuffer);
  }

  if (out == NULL) {
    throw_OOM(env);
    return 0;
  }

  compressed = LZ4_compress_limitedOutput(in + srcOff, out + destOff, srcLen, maxDestLen);

  if (srcArray != NULL) {
    (*env)->ReleasePrimitiveArrayCritical(env, srcArray, in, 0);
  }
  if (destArray != NULL) {
    (*env)->ReleasePrimitiveArrayCritical(env, destArray, out, 0);
  }

  return compressed;

}

void returnError(JNIEnv* env, jobject errorResult, LZ4F_errorCode_t errorCode)
{
  // Get the ID for the `message` field in the error result
  jclass errorClass = (*env)->GetObjectClass(env, errorResult);
  jfieldID fid = (*env)->GetFieldID(env, errorClass, "message", "Ljava/lang/String;");

  // The string message in java string format
  jstring message = (*env)->NewStringUTF(env, LZ4F_getErrorName(errorCode));

  // And set it into the error result
  (*env)->SetObjectField(env, errorResult, fid, message);
}

// Util function to hide the ugly cast
jlong dContextToLong(LZ4F_decompressionContext_t context)
{
  intptr_t readable_context = *(intptr_t*)&context;
  return (long) readable_context;
}

jlong cContextToLong(LZ4F_compressionContext_t context)
{
  intptr_t readable_context = *(intptr_t*)&context;
  return (long) readable_context;
}

// Util function to hide the ugly cast - reverse mode
LZ4F_decompressionContext_t longToDContext(jlong readable_context)
{
  intptr_t context = readable_context;
  return *(LZ4F_decompressionContext_t*)&context;
}
LZ4F_compressionContext_t longToCContext(jlong readable_context)
{
  intptr_t context = readable_context;
  return *(LZ4F_compressionContext_t*)&context;
}

JNIEXPORT jlong JNICALL Java_net_jpountz_lz4_LZ4JNI_LZ4F_1createDecompressionContext
  (JNIEnv *env, jclass cls, jobject errorResult)
{

  LZ4F_decompressionContext_t context;

  LZ4F_errorCode_t error = LZ4F_createDecompressionContext(&context, LZ4F_VERSION);
  if (LZ4F_isError(error)) {
    returnError(env, errorResult, error);
    return 0;
  }

  return dContextToLong(context);
}

JNIEXPORT void JNICALL Java_net_jpountz_lz4_LZ4JNI_LZ4F_1freeDecompressionContext
  (JNIEnv *env, jclass cls, jlong readable_context, jobject errorResult)
{
  LZ4F_errorCode_t error = LZ4F_freeDecompressionContext(longToDContext(readable_context));
  if (LZ4F_isError(error))
    returnError(env, errorResult, error);
}

// Return:
// * number of bytes written if all the source could be read
// * minus number of bytes read if the destination got filled.
JNIEXPORT jint JNICALL Java_net_jpountz_lz4_LZ4JNI_LZ4F_1decompress
  (JNIEnv* env, jclass cls, jlong readable_context,
   jbyteArray srcBuffer, jint srcOffset, jint srcSize,
   jbyteArray dstBuffer, jint dstOffset, jint dstSize,
   jobject errorResult)
{

  // Apply the offset here, because it's inconvenient in Java
  void* availableDstBuffer = (void*) (*env)->GetPrimitiveArrayCritical(env, dstBuffer, 0) + dstOffset;
  void* availableSrcBuffer = (void*) (*env)->GetPrimitiveArrayCritical(env, srcBuffer, 0) + srcOffset;

  LZ4F_decompressOptions_t options;

  size_t total_written = 0;
  size_t total_read = 0;

  LZ4F_decompressionContext_t context = longToDContext(readable_context);

  // Call `decompress` until all `availableSrcBuffer` has been read
  while (total_read < srcSize) {

    size_t finalDstSize = dstSize - total_written;
    size_t finalSrcSize = srcSize - total_read;

    // We have so much to write!
    if (finalDstSize == 0) {
      // Return a negative value, indicating error, with what we were able to read.
      return -total_read-1;
    }

    // TODO: listen to the hint and adapt the buffer size for next time.
    // (Could provide speed boost?)
    size_t result = LZ4F_decompress(context,
                    availableDstBuffer + total_written, &finalDstSize,
                    availableSrcBuffer + total_read, &finalSrcSize,
                    &options);

    if (LZ4F_isError(result)) {
      // Damnation! Now we have to present the message nicely...
      returnError(env, errorResult, result);

      // Aaand we're done.
      return 0;
    }

    total_written += finalDstSize;
    total_read += finalSrcSize;
  }

  return total_written;
}


JNIEXPORT jlong JNICALL Java_net_jpountz_lz4_LZ4JNI_LZ4F_1createCompressionContext
  (JNIEnv* env, jclass cls, jobject errorResult)
{
  LZ4F_compressionContext_t context;

  LZ4F_errorCode_t error = LZ4F_createCompressionContext(&context, LZ4F_VERSION);
  if (LZ4F_isError(error)) {
    returnError(env, errorResult, error);
    return 0;
  }

  return cContextToLong(context);
}

JNIEXPORT void JNICALL Java_net_jpountz_lz4_LZ4JNI_LZ4F_1freeCompressionContext
  (JNIEnv *env, jclass cls, jlong readable_context, jobject errorResult)
{
  LZ4F_errorCode_t error = LZ4F_freeCompressionContext(longToCContext(readable_context));
  if (LZ4F_isError(error))
    returnError(env, errorResult, error);
}

JNIEXPORT jint JNICALL Java_net_jpountz_lz4_LZ4JNI_LZ4F_1compressBegin
  (JNIEnv *env, jclass cls, jlong readable_context, jint compressionLevel,
   jbyteArray dstBuffer, jint dstOffset, jint dstSize,
   jobject errorResult)
{
  void* availableDstBuffer = (void*) (*env)->GetPrimitiveArrayCritical(env, dstBuffer, 0) + dstOffset;

  LZ4F_preferences_t prefs = {0};
  prefs.compressionLevel = compressionLevel;

  size_t result = LZ4F_compressBegin(longToCContext(readable_context), availableDstBuffer, dstSize, &prefs);
  if (LZ4F_isError(result)) {
    returnError(env, errorResult, result);
    // TODO: we could return the error code?
    return 0;
  }

  return result;
}

JNIEXPORT jint JNICALL Java_net_jpountz_lz4_LZ4JNI_LZ4F_1compressBound
  (JNIEnv* env, jclass cls, jint srcSize)
{
  return LZ4F_compressBound(srcSize, NULL);
}

JNIEXPORT jint JNICALL Java_net_jpountz_lz4_LZ4JNI_LZ4F_1compressUpdate
  (JNIEnv *env, jclass cls, jlong readable_context,
   jbyteArray srcBuffer, jint srcOffset, jint srcSize,
   jbyteArray dstBuffer, jint dstOffset, jint dstSize,
   jobject errorResult)
{
  void* availableDstBuffer = (void*) (*env)->GetPrimitiveArrayCritical(env, dstBuffer, 0) + dstOffset;
  void* availableSrcBuffer = (void*) (*env)->GetPrimitiveArrayCritical(env, srcBuffer, 0) + srcOffset;

  size_t result = LZ4F_compressUpdate(longToCContext(readable_context),
                      availableDstBuffer, dstSize,
                      availableSrcBuffer, srcSize,
                      NULL);
  if (LZ4F_isError(result)) {
    returnError(env, errorResult, result);
    return 0;
  }

  return result;
}

JNIEXPORT jint JNICALL Java_net_jpountz_lz4_LZ4JNI_LZ4F_1compressEnd
  (JNIEnv *env, jclass cls, jlong readable_context,
   jbyteArray dstBuffer, jint dstOffset, jint dstSize,
   jobject errorResult)
{
  void* availableDstBuffer = (void*) (*env)->GetPrimitiveArrayCritical(env, dstBuffer, 0) + dstOffset;

  size_t result = LZ4F_compressEnd(longToCContext(readable_context),
                                   availableDstBuffer, dstSize,
                                   NULL);

  if (LZ4F_isError(result)) {
    returnError(env, errorResult, result);
    return 0;
  }

  return result;
}

JNIEXPORT jint JNICALL Java_net_jpountz_lz4_LZ4JNI_LZ4F_1compressFlush
  (JNIEnv *env, jclass cls, jlong readable_context,
   jbyteArray dstBuffer, jint dstOffset, jint dstSize,
   jobject errorResult)
{
  void* availableDstBuffer = (void*) (*env)->GetPrimitiveArrayCritical(env, dstBuffer, 0) + dstOffset;

  size_t result = LZ4F_flush(longToCContext(readable_context),
                             availableDstBuffer, dstSize,
                             NULL);

  if (LZ4F_isError(result)) {
    returnError(env, errorResult, result);
    return 0;
  }

  return result;
}

/*
 * Class:     net_jpountz_lz4_LZ4JNI
 * Method:    LZ4_compressHC
 * Signature: ([BLjava/nio/ByteBuffer;II[BLjava/nio/ByteBuffer;III)I
 */
JNIEXPORT jint JNICALL Java_net_jpountz_lz4_LZ4JNI_LZ4_1compressHC
  (JNIEnv *env, jclass cls, jbyteArray srcArray, jobject srcBuffer, jint srcOff, jint srcLen, jbyteArray destArray, jobject destBuffer, jint destOff, jint maxDestLen, jint compressionLevel) {

  char* in;
  char* out;
  jint compressed;

  if (srcArray != NULL) {
    in = (char*) (*env)->GetPrimitiveArrayCritical(env, srcArray, 0);
  } else {
    in = (char*) (*env)->GetDirectBufferAddress(env, srcBuffer);
  }

  if (in == NULL) {
    throw_OOM(env);
    return 0;
  }

  if (destArray != NULL) {
    out = (char*) (*env)->GetPrimitiveArrayCritical(env, destArray, 0);
  } else {
    out = (char*) (*env)->GetDirectBufferAddress(env, destBuffer);
  }

  if (out == NULL) {
    throw_OOM(env);
    return 0;
  }

  compressed = LZ4_compressHC2_limitedOutput(in + srcOff, out + destOff, srcLen, maxDestLen, compressionLevel);

  if (srcArray != NULL) {
    (*env)->ReleasePrimitiveArrayCritical(env, srcArray, in, 0);
  }
  if (destArray != NULL) {
    (*env)->ReleasePrimitiveArrayCritical(env, destArray, out, 0);
  }

  return compressed;

}

/*
 * Class:     net_jpountz_lz4_LZ4JNI
 * Method:    LZ4_decompress_fast
 * Signature: ([BLjava/nio/ByteBuffer;I[BLjava/nio/ByteBuffer;II)I
 */
JNIEXPORT jint JNICALL Java_net_jpountz_lz4_LZ4JNI_LZ4_1decompress_1fast
  (JNIEnv *env, jclass cls, jbyteArray srcArray, jobject srcBuffer, jint srcOff, jbyteArray destArray, jobject destBuffer, jint destOff, jint destLen) {

  char* in;
  char* out;
  jint compressed;

  if (srcArray != NULL) {
    in = (char*) (*env)->GetPrimitiveArrayCritical(env, srcArray, 0);
  } else {
    in = (char*) (*env)->GetDirectBufferAddress(env, srcBuffer);
  }

  if (in == NULL) {
    throw_OOM(env);
    return 0;
  }

  if (destArray != NULL) {
    out = (char*) (*env)->GetPrimitiveArrayCritical(env, destArray, 0);
  } else {
    out = (char*) (*env)->GetDirectBufferAddress(env, destBuffer);
  }

  if (out == NULL) {
    throw_OOM(env);
    return 0;
  }

  compressed = LZ4_decompress_fast(in + srcOff, out + destOff, destLen);

  if (srcArray != NULL) {
    (*env)->ReleasePrimitiveArrayCritical(env, srcArray, in, 0);
  }
  if (destArray != NULL) {
    (*env)->ReleasePrimitiveArrayCritical(env, destArray, out, 0);
  }

  return compressed;

}

/*
 * Class:     net_jpountz_lz4_LZ4JNI
 * Method:    LZ4_decompress_safe
 * Signature: ([BLjava/nio/ByteBuffer;II[BLjava/nio/ByteBuffer;II)I
 */
JNIEXPORT jint JNICALL Java_net_jpountz_lz4_LZ4JNI_LZ4_1decompress_1safe
  (JNIEnv *env, jclass cls, jbyteArray srcArray, jobject srcBuffer, jint srcOff, jint srcLen, jbyteArray destArray, jobject destBuffer, jint destOff, jint maxDestLen) {

  char* in;
  char* out;
  jint decompressed;

  if (srcArray != NULL) {
    in = (char*) (*env)->GetPrimitiveArrayCritical(env, srcArray, 0);
  } else {
    in = (char*) (*env)->GetDirectBufferAddress(env, srcBuffer);
  }

  if (in == NULL) {
    throw_OOM(env);
    return 0;
  }

  if (destArray != NULL) {
    out = (char*) (*env)->GetPrimitiveArrayCritical(env, destArray, 0);
  } else {
    out = (char*) (*env)->GetDirectBufferAddress(env, destBuffer);
  }

  if (out == NULL) {
    throw_OOM(env);
    return 0;
  }

  decompressed = LZ4_decompress_safe(in + srcOff, out + destOff, srcLen, maxDestLen);

  if (srcArray != NULL) {
    (*env)->ReleasePrimitiveArrayCritical(env, srcArray, in, 0);
  }
  if (destArray != NULL) {
    (*env)->ReleasePrimitiveArrayCritical(env, destArray, out, 0);
  }

  return decompressed;

}

/*
 * Class:     net_jpountz_lz4_LZ4JNI
 * Method:    LZ4_compressBound
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_net_jpountz_lz4_LZ4JNI_LZ4_1compressBound
  (JNIEnv *env, jclass cls, jint len) {

  return LZ4_compressBound(len);

}
