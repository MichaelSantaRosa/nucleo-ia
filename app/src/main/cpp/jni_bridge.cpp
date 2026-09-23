#include <jni.h>
#include <string>
#include "brain.h"

using namespace nucleo;

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_nucleo_ia_core_NativeBridge_nativeCreateBrain(
        JNIEnv* env, jobject /* thiz */,
        jint vocabSize, jint seqLen, jint embedDim,
        jint ffDim, jint hidden, jint numClasses) {
    Brain* b = new Brain(vocabSize, seqLen, embedDim, ffDim, hidden, numClasses);
    return reinterpret_cast<jlong>(b);
}

JNIEXPORT void JNICALL
Java_com_nucleo_ia_core_NativeBridge_nativeInfer(
        JNIEnv* env, jobject /* thiz */,
        jlong handle, jintArray tokenIds, jfloatArray scores) {
    Brain* b = reinterpret_cast<Brain*>(handle);
    jsize len = env->GetArrayLength(tokenIds);
    jint* ids = env->GetIntArrayElements(tokenIds, nullptr);
    float* out = env->GetFloatArrayElements(scores, nullptr);
    b->forward(ids, len, out);
    env->ReleaseIntArrayElements(tokenIds, ids, JNI_ABORT);
    env->ReleaseFloatArrayElements(scores, out, 0);
}

JNIEXPORT void JNICALL
Java_com_nucleo_ia_core_NativeBridge_nativeTrainStep(
        JNIEnv* env, jobject /* thiz */,
        jlong handle, jintArray tokenIds, jint target, jfloat lr) {
    Brain* b = reinterpret_cast<Brain*>(handle);
    jsize len = env->GetArrayLength(tokenIds);
    jint* ids = env->GetIntArrayElements(tokenIds, nullptr);
    b->trainStep(ids, len, target, static_cast<float>(lr));
    env->ReleaseIntArrayElements(tokenIds, ids, JNI_ABORT);
}

JNIEXPORT void JNICALL
Java_com_nucleo_ia_core_NativeBridge_nativeDestroyBrain(
        JNIEnv* env, jobject /* thiz */, jlong handle) {
    Brain* b = reinterpret_cast<Brain*>(handle);
    delete b;
}

JNIEXPORT jboolean JNICALL
Java_com_nucleo_ia_core_NativeBridge_nativeSaveWeights(
        JNIEnv* env, jobject /* thiz */, jlong handle, jstring path) {
    Brain* b = reinterpret_cast<Brain*>(handle);
    const char* p = env->GetStringUTFChars(path, nullptr);
    bool ok = b->saveWeights(p);
    env->ReleaseStringUTFChars(path, p);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_nucleo_ia_core_NativeBridge_nativeLoadWeights(
        JNIEnv* env, jobject /* thiz */, jlong handle, jstring path) {
    Brain* b = reinterpret_cast<Brain*>(handle);
    const char* p = env->GetStringUTFChars(path, nullptr);
    bool ok = b->loadWeights(p);
    env->ReleaseStringUTFChars(path, p);
    return ok ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"