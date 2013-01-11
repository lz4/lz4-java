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

  char* in = (char*) (*env)->GetPrimitiveArrayCritical(env, buf, 0);
  if (in == NULL) {
    throw_OOM(env);
    return 0;
  }

  jint h32 = XXH32(in + off, len, seed);

  (*env)->ReleasePrimitiveArrayCritical(env, buf, in, 0);

  return h32;
}

/*
 * Class:     net_jpountz_xxhash_XXHashJNI
 * Method:    XXH32_init
 * Signature: (I)J
 */
JNIEXPORT jlong JNICALL Java_net_jpountz_xxhash_XXHashJNI_XXH32_1init
  (JNIEnv *env, jclass cls, jint seed) {

  return (jlong) XXH32_init(seed);

}

/*
 * Class:     net_jpountz_xxhash_XXHashJNI
 * Method:    XXH32_feed
 * Signature: (J[BII)V
 */
JNIEXPORT void JNICALL Java_net_jpountz_xxhash_XXHashJNI_XXH32_1feed
  (JNIEnv *env, jclass cls, jlong state, jbyteArray src, jint off, jint len) {

  char* in = (char*) (*env)->GetPrimitiveArrayCritical(env, src, 0);
  if (in == NULL) {
    throw_OOM(env);
    return;
  }

  XXH32_feed((void*) state, in + off, len);

  (*env)->ReleasePrimitiveArrayCritical(env, src, in, 0);

}

/*
 * Class:     net_jpountz_xxhash_XXHashJNI
 * Method:    XXH32_getIntermediateResult
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_net_jpountz_xxhash_XXHashJNI_XXH32_1getIntermediateResult
  (JNIEnv *env, jclass cls, jlong state) {

  return XXH32_getIntermediateResult((void*) state);

}

/*
 * Class:     net_jpountz_xxhash_XXHashJNI
 * Method:    XXH32_result
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_net_jpountz_xxhash_XXHashJNI_XXH32_1result
  (JNIEnv *env, jclass cls, jlong state) {

  return XXH32_result((void*) state);

}

/*
 * Class:     net_jpountz_xxhash_XXHashJNI
 * Method:    XXH32_free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_net_jpountz_xxhash_XXHashJNI_XXH32_1free
  (JNIEnv *env, jclass cls, jlong state) {

  free((void*) state);

}

