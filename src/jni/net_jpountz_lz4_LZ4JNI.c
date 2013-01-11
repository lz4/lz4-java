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
  (JNIEnv *env, jclass cls, jbyteArray src, jint srcOff, jint srcLen, jbyteArray dest, jint destOff, jint maxDestLen) {

  char* in = (char*) (*env)->GetPrimitiveArrayCritical(env, src, 0);
  if (in == NULL) {
    throw_OOM(env);
    return 0;
  }
  char* out = (char*) (*env)->GetPrimitiveArrayCritical(env, dest, 0);
  if (out == NULL) {
    throw_OOM(env);
    return 0;
  }

  jint compressed = LZ4_compress_limitedOutput(in + srcOff, out + destOff, srcLen, maxDestLen);

  (*env)->ReleasePrimitiveArrayCritical(env, src, in, 0);
  (*env)->ReleasePrimitiveArrayCritical(env, src, out, 0);

  return compressed;

}

/*
 * Class:     net_jpountz_lz4_LZ4
 * Method:    LZ4_compressHC
 * Signature: ([BII[BI)I
 */
JNIEXPORT jint JNICALL Java_net_jpountz_lz4_LZ4JNI_LZ4_1compressHC
  (JNIEnv *env, jclass cls, jbyteArray src, jint srcOff, jint srcLen, jbyteArray dest, jint destOff) {

  char* in = (char*) (*env)->GetPrimitiveArrayCritical(env, src, 0);
  if (in == NULL) {
    throw_OOM(env);
    return 0;
  } 
  char* out = (char*) (*env)->GetPrimitiveArrayCritical(env, dest, 0);
  if (out == NULL) {
    throw_OOM(env);
    return 0;
  }

  jint compressed = LZ4_compressHC(in + srcOff, out + destOff, srcLen);

  (*env)->ReleasePrimitiveArrayCritical(env, src, in, 0);
  (*env)->ReleasePrimitiveArrayCritical(env, src, out, 0);

  return compressed;

}

/*
 * Class:     net_jpountz_lz4_LZ4
 * Method:    LZ4_decompress
 * Signature: ([BI[BII)I
 */
JNIEXPORT jint JNICALL Java_net_jpountz_lz4_LZ4JNI_LZ4_1decompress
  (JNIEnv *env, jclass cls, jbyteArray src, jint srcOff, jbyteArray dest, jint destOff, jint destLen) {

  char* in = (char*) (*env)->GetPrimitiveArrayCritical(env, src, 0);
  if (in == NULL) {
    throw_OOM(env);
    return 0;
  }
  char* out = (char*) (*env)->GetPrimitiveArrayCritical(env, dest, 0);
  if (out == NULL) {
    throw_OOM(env);
    return 0;
  }

  jint compressed = LZ4_uncompress(in + srcOff, out + destOff, destLen);

  (*env)->ReleasePrimitiveArrayCritical(env, src, in, 0);
  (*env)->ReleasePrimitiveArrayCritical(env, src, out, 0);

  return compressed;

}

/*
 * Class:     net_jpountz_lz4_LZ4
 * Method:    LZ4_decompress_unknownOutputSize
 * Signature: ([BII[BI)I
 */
JNIEXPORT jint JNICALL Java_net_jpountz_lz4_LZ4JNI_LZ4_1decompress_1unknownOutputSize
  (JNIEnv *env, jclass cls, jbyteArray src, jint srcOff, jint srcLen, jbyteArray dest, jint destOff, jint maxDestLen) {

  char* in = (char*) (*env)->GetPrimitiveArrayCritical(env, src, 0);
  if (in == NULL) {
    throw_OOM(env);
    return 0;
  }
  char* out = (char*) (*env)->GetPrimitiveArrayCritical(env, dest, 0);
  if (out == NULL) {
    throw_OOM(env);
    return 0;
  }

  jint decompressed = LZ4_uncompress_unknownOutputSize(in + srcOff, out + destOff, srcLen, maxDestLen);

  (*env)->ReleasePrimitiveArrayCritical(env, src, in, 0);
  (*env)->ReleasePrimitiveArrayCritical(env, src, out, 0);

  return decompressed;

}

/*
 * Class:     net_jpountz_lz4_LZ4
 * Method:    LZ4_compressBound
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_net_jpountz_lz4_LZ4JNI_LZ4_1compressBound
  (JNIEnv *env, jclass cls, jint length) {

  return LZ4_compressBound(length);

}
