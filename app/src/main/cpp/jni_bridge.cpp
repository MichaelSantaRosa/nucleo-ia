#include <jni.h>
#include "brain.h"

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_nucleo_ia_NativeBridge_nativeCreateBrain(
    JNIEnv* env, jobject thiz,
    jint vocabSize, jint seqLen, jint embedDim, jint ffDim, jint hiddenDim, jint numClasses) {
    nucleo::BrainConfig cfg;
    cfg.vocabSize = vocabSize;
    cfg.seqLen = seqLen;
    cfg.embedDim = embedDim;
    cfg.ffDim = ffDim;
    cfg.hidden = hiddenDim;
    cfg.numClasses = numClasses;
    auto* brain = new nucleo::Brain(cfg);
    return (jlong)brain;
}

JNIEXPORT void JNICALL
Java_com_nucleo_ia_NativeBridge_nativeDestroyBrain(
    JNIEnv* env, jobject thiz, jlong handle) {
    auto* brain = reinterpret_cast<nucleo::Brain*>(handle);
    delete brain;
}

JNIEXPORT void JNICALL
Java_com_nucleo_ia_NativeBridge_nativeInfer(
    JNIEnv* env, jobject thiz, jlong handle, jintArray ids, jfloatArray outScores) {
    auto* brain = reinterpret_cast<nucleo::Brain*>(handle);
    int len = env->GetArrayLength(ids);
    std::vector<int32_t> idsVec(len);
    env->GetIntArrayRegion(ids, 0, len, (int*)idsVec.data());
    float* scores = env->GetFloatArrayElements(outScores, nullptr);
    brain->forward(idsVec.data(), scores);
    env->ReleaseFloatArrayElements(outScores, scores, JNI_COMMIT);
}

JNIEXPORT void JNICALL
Java_com_nucleo_ia_NativeBridge_nativeTrainStep(
    JNIEnv* env, jobject thiz, jlong handle, jintArray ids, jint label, jfloat lr) {
    auto* brain = reinterpret_cast<nucleo::Brain*>(handle);
    int len = env->GetArrayLength(ids);
    std::vector<int32_t> idsVec(len);
    env->GetIntArrayRegion(ids, 0, len, (int*)idsVec.data());
    brain->trainStep(idsVec.data(), label, (float)lr);
}

JNIEXPORT jbyteArray JNICALL
Java_com_nucleo_ia_NativeBridge_nativeSaveWeights(
    JNIEnv* env, jobject thiz, jlong handle) {
    auto* brain = reinterpret_cast<nucleo::Brain*>(handle);
    auto data = brain->saveWeights();
    jbyteArray arr = env->NewByteArray(data.size());
    env->SetByteArrayRegion(arr, 0, data.size(), (const jbyte*)data.data());
    return arr;
}

JNIEXPORT jboolean JNICALL
Java_com_nucleo_ia_NativeBridge_nativeLoadWeights(
    JNIEnv* env, jobject thiz, jlong handle, jbyteArray data) {
    auto* brain = reinterpret_cast<nucleo::Brain*>(handle);
    int len = env->GetArrayLength(data);
    jbyte* bytes = env->GetByteArrayElements(data, nullptr);
    bool ok = brain->loadWeights((const uint8_t*)bytes, len);
    env->ReleaseByteArrayElement(data, bytes, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}

}