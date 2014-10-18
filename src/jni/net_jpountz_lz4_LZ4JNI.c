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

#include "lz4.h"
#include "lz4hc.h"
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
 * Signature: ([BII[BII)I
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

  if (destArray != NULL) {
	  (*env)->ReleasePrimitiveArrayCritical(env, destArray, out, 0);
  }
  if (srcArray != NULL) {
	  (*env)->ReleasePrimitiveArrayCritical(env, srcArray, in, 0);
  }

  return compressed;

}

/*
 * Class:     net_jpountz_lz4_LZ4
 * Method:    LZ4_compressHC
 * Signature: ([BII[BI)I
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

  if (destArray != NULL) {
	  (*env)->ReleasePrimitiveArrayCritical(env, destArray, out, 0);
  }
  if (srcArray != NULL) {
	  (*env)->ReleasePrimitiveArrayCritical(env, srcArray, in, 0);
  }

  return compressed;

}

/*
 * Class:     net_jpountz_lz4_LZ4
 * Method:    LZ4_decompress
 * Signature: ([BI[BII)I
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

  if (destArray != NULL) {
	  (*env)->ReleasePrimitiveArrayCritical(env, destArray, out, 0);
  }
  if (srcArray != NULL) {
	  (*env)->ReleasePrimitiveArrayCritical(env, srcArray, in, 0);
  }

  return compressed;

}

/*
 * Class:     net_jpountz_lz4_LZ4
 * Method:    LZ4_decompress_unknownOutputSize
 * Signature: ([BII[BI)I
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

  if (destArray != NULL) {
	  (*env)->ReleasePrimitiveArrayCritical(env, destArray, out, 0);
  }
  if (srcArray != NULL) {
	  (*env)->ReleasePrimitiveArrayCritical(env, srcArray, in, 0);
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
