#include <jni.h>

#include <cstdint>
#include <fstream>
#include <iterator>
#include <vector>

#include "brain.h"

using namespace nucleo;

namespace {

std::vector<uint8_t> readFile(const char* path) {
    std::ifstream file(path, std::ios::binary);
    if (!file) return {};
    return std::vector<uint8_t>(
        std::istreambuf_iterator<char>(file),
        std::istreambuf_iterator<char>());
}

bool writeFile(const char* path, const std::vector<uint8_t>& data) {
    std::ofstream file(path, std::ios::binary | std::ios::trunc);
    if (!file) return false;
    file.write(
        reinterpret_cast<const char*>(data.data()),
        static_cast<std::streamsize>(data.size()));
    return file.good();
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_nucleo_ia_core_NativeBridge_nativeCreateBrain(
        JNIEnv* /*env*/, jobject /*thiz*/,
        jint vocabSize, jint seqLen, jint embedDim,
        jint ffDim, jint hidden, jint numClasses) {
    BrainConfig config;
    config.vocabSize = vocabSize;
    config.seqLen = seqLen;
    config.embedDim = embedDim;
    config.ffDim = ffDim;
    config.hidden = hidden;
    config.numClasses = numClasses;
    return reinterpret_cast<jlong>(new Brain(config));
}

JNIEXPORT void JNICALL
Java_com_nucleo_ia_core_NativeBridge_nativeInfer(
        JNIEnv* env, jobject /*thiz*/,
        jlong handle, jintArray tokenIds, jfloatArray scores) {
    auto* brain = reinterpret_cast<Brain*>(handle);
    if (!brain || !tokenIds || !scores) return;

    jint* ids = env->GetIntArrayElements(tokenIds, nullptr);
    jfloat* output = env->GetFloatArrayElements(scores, nullptr);
    if (!ids || !output) {
        if (ids) env->ReleaseIntArrayElements(tokenIds, ids, JNI_ABORT);
        if (output) env->ReleaseFloatArrayElements(scores, output, 0);
        return;
    }

    brain->forward(
        reinterpret_cast<const int32_t*>(ids),
        reinterpret_cast<float*>(output));

    env->ReleaseIntArrayElements(tokenIds, ids, JNI_ABORT);
    env->ReleaseFloatArrayElements(scores, output, 0);
}

JNIEXPORT void JNICALL
Java_com_nucleo_ia_core_NativeBridge_nativeTrainStep(
        JNIEnv* env, jobject /*thiz*/,
        jlong handle, jintArray tokenIds, jint target, jfloat lr) {
    auto* brain = reinterpret_cast<Brain*>(handle);
    if (!brain || !tokenIds) return;

    jint* ids = env->GetIntArrayElements(tokenIds, nullptr);
    if (!ids) return;

    brain->trainStep(
        reinterpret_cast<const int32_t*>(ids),
        static_cast<int>(target),
        static_cast<float>(lr));

    env->ReleaseIntArrayElements(tokenIds, ids, JNI_ABORT);
}

JNIEXPORT void JNICALL
Java_com_nucleo_ia_core_NativeBridge_nativeDestroyBrain(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    delete reinterpret_cast<Brain*>(handle);
}

JNIEXPORT jboolean JNICALL
Java_com_nucleo_ia_core_NativeBridge_nativeSaveWeights(
        JNIEnv* env, jobject /*thiz*/, jlong handle, jstring path) {
    auto* brain = reinterpret_cast<Brain*>(handle);
    if (!brain || !path) return JNI_FALSE;

    const char* nativePath = env->GetStringUTFChars(path, nullptr);
    if (!nativePath) return JNI_FALSE;

    const std::vector<uint8_t> weights = brain->saveWeights();
    const bool ok = writeFile(nativePath, weights);

    env->ReleaseStringUTFChars(path, nativePath);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_nucleo_ia_core_NativeBridge_nativeLoadWeights(
        JNIEnv* env, jobject /*thiz*/, jlong handle, jstring path) {
    auto* brain = reinterpret_cast<Brain*>(handle);
    if (!brain || !path) return JNI_FALSE;

    const char* nativePath = env->GetStringUTFChars(path, nullptr);
    if (!nativePath) return JNI_FALSE;

    const std::vector<uint8_t> weights = readFile(nativePath);
    const bool ok = !weights.empty() &&
                    brain->loadWeights(weights.data(), weights.size());

    env->ReleaseStringUTFChars(path, nativePath);
    return ok ? JNI_TRUE : JNI_FALSE;
}

}  // extern "C"