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

#include <stdlib.h>
#include "xxhash.h"
#include "net_jpountz_xxhash_XXHashJNI.h"

static jclass OutOfMemoryError;

/*
 * Class:     net_jpountz_xxhash_XXHashJNI
 * Method:    init
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_net_jpountz_xxhash_XXHashJNI_init
  (JNIEnv *env, jclass cls) {
  OutOfMemoryError = (*env)->FindClass(env, "java/lang/OutOfMemoryError");
}

static void throw_OOM(JNIEnv *env) {
  (*env)->ThrowNew(env, OutOfMemoryError, "Out of memory");
}

/*
 * Class:     net_jpountz_xxhash_XXHashJNI
 * Method:    XXH32
 * Signature: ([BIII)I
 */
JNIEXPORT jint JNICALL Java_net_jpountz_xxhash_XXHashJNI_XXH32
  (JNIEnv *env, jclass cls, jbyteArray buf, jint off, jint len, jint seed) {

  char* in;
  jint h32;

  in = (char*) (*env)->GetPrimitiveArrayCritical(env, buf, 0);
  if (in == NULL) {
    throw_OOM(env);
    return 0;
  }

  h32 = XXH32(in + off, len, seed);

  (*env)->ReleasePrimitiveArrayCritical(env, buf, in, 0);

  return h32;
}

/*
 * Class:     net_jpountz_xxhash_XXHashJNI
 * Method:    XXH32BB
 * Signature: (Ljava/nio/ByteBuffer;III)I
 */
JNIEXPORT jint JNICALL Java_net_jpountz_xxhash_XXHashJNI_XXH32BB
  (JNIEnv *env, jclass cls, jobject buf, jint off, jint len, jint seed) {

  char* in;
  jint h32;

  in = (char*) (*env)->GetDirectBufferAddress(env, buf);
  if (in == NULL) {
    throw_OOM(env);
    return 0;
  }

  h32 = XXH32(in + off, len, seed);

  return h32;

}

/*
 * Class:     net_jpountz_xxhash_XXHashJNI
 * Method:    XXH32_init
 * Signature: (I)J
 */
JNIEXPORT jlong JNICALL Java_net_jpountz_xxhash_XXHashJNI_XXH32_1init
  (JNIEnv *env, jclass cls, jint seed) {

  XXH32_state_t *state = XXH32_createState();
  if (XXH32_reset(state, seed) != XXH_OK) {
    XXH32_freeState(state);
    throw_OOM(env);
    return 0;
  }

  return (jlong) state;

}

/*
 * Class:     net_jpountz_xxhash_XXHashJNI
 * Method:    XXH32_update
 * Signature: (J[BII)V
 */
JNIEXPORT void JNICALL Java_net_jpountz_xxhash_XXHashJNI_XXH32_1update
  (JNIEnv *env, jclass cls, jlong state, jbyteArray src, jint off, jint len) {

  char* in = (char*) (*env)->GetPrimitiveArrayCritical(env, src, 0);
  if (in == NULL) {
    throw_OOM(env);
    return;
  }

  XXH32_update((void*) state, in + off, len);

  (*env)->ReleasePrimitiveArrayCritical(env, src, in, 0);

}

/*
 * Class:     net_jpountz_xxhash_XXHashJNI
 * Method:    XXH32_digest
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_net_jpountz_xxhash_XXHashJNI_XXH32_1digest
  (JNIEnv *env, jclass cls, jlong state) {

  return XXH32_digest((XXH32_state_t*) state);

}

/*
 * Class:     net_jpountz_xxhash_XXHashJNI
 * Method:    XXH32_free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_net_jpountz_xxhash_XXHashJNI_XXH32_1free
  (JNIEnv *env, jclass cls, jlong state) {

  XXH32_freeState((XXH32_state_t*) state);

}

/*
 * Class:     net_jpountz_xxhash_XXHashJNI
 * Method:    XXH64
 * Signature: ([BIIJ)J
 */
JNIEXPORT jlong JNICALL Java_net_jpountz_xxhash_XXHashJNI_XXH64
  (JNIEnv *env, jclass cls, jbyteArray buf, jint off, jint len, jlong seed) {

  char* in;
  jlong h64;

  in = (char*) (*env)->GetPrimitiveArrayCritical(env, buf, 0);
  if (in == NULL) {
    throw_OOM(env);
    return 0;
  }

  h64 = XXH64(in + off, len, seed);

  (*env)->ReleasePrimitiveArrayCritical(env, buf, in, 0);

  return h64;
}

/*
 * Class:     net_jpountz_xxhash_XXHashJNI
 * Method:    XXH64BB
 * Signature: (Ljava/nio/ByteBuffer;IIJ)J
 */
JNIEXPORT jlong JNICALL Java_net_jpountz_xxhash_XXHashJNI_XXH64BB
  (JNIEnv *env, jclass cls, jobject buf, jint off, jint len, jlong seed) {

  char* in;
  jlong h64;

  in = (char*) (*env)->GetDirectBufferAddress(env, buf);
  if (in == NULL) {
    throw_OOM(env);
    return 0;
  }

  h64 = XXH64(in + off, len, seed);

  return h64;

}

/*
 * Class:     net_jpountz_xxhash_XXHashJNI
 * Method:    XXH64_init
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_net_jpountz_xxhash_XXHashJNI_XXH64_1init
  (JNIEnv *env, jclass cls, jlong seed) {

  XXH64_state_t *state = XXH64_createState();
  if (XXH64_reset(state, seed) != XXH_OK) {
    XXH64_freeState(state);
    throw_OOM(env);
    return 0;
  }

  return (jlong) state;

}

/*
 * Class:     net_jpountz_xxhash_XXHashJNI
 * Method:    XXH64_update
 * Signature: (J[BII)V
 */
JNIEXPORT void JNICALL Java_net_jpountz_xxhash_XXHashJNI_XXH64_1update
  (JNIEnv *env, jclass cls, jlong state, jbyteArray src, jint off, jint len) {

  char* in = (char*) (*env)->GetPrimitiveArrayCritical(env, src, 0);
  if (in == NULL) {
    throw_OOM(env);
    return;
  }

  XXH64_update((XXH64_state_t*) state, in + off, len);

  (*env)->ReleasePrimitiveArrayCritical(env, src, in, 0);

}

/*
 * Class:     net_jpountz_xxhash_XXHashJNI
 * Method:    XXH64_digest
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_net_jpountz_xxhash_XXHashJNI_XXH64_1digest
  (JNIEnv *env, jclass cls, jlong state) {

  return XXH64_digest((XXH64_state_t*) state);

}

/*
 * Class:     net_jpountz_xxhash_XXHashJNI
 * Method:    XXH64_free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_net_jpountz_xxhash_XXHashJNI_XXH64_1free
  (JNIEnv *env, jclass cls, jlong state) {

  XXH64_freeState((XXH64_state_t*) state);

}
