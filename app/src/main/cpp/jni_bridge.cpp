#include "brain.h"
#include <jni.h>
#include <android/log.h>
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,"NucleoBrain",__VA_ARGS__)
static nucleo::Brain* g_brain=nullptr;
extern "C" {
JNIEXPORT jlong JNICALL Java_com_nucleo_ia_core_NativeBridge_nativeCreate(JNIEnv*,jclass){
    if(g_brain) delete g_brain;
    g_brain=new nucleo::Brain();
    LOGI("Brain criado: %zu bytes", g_brain->weightBytes());
    return (jlong)g_brain;
}
JNIEXPORT void JNICALL Java_com_nucleo_ia_core_NativeBridge_nativeDestroy(JNIEnv*,jclass,jlong h){
    delete (nucleo::Brain*)h; if(h==(jlong)g_brain) g_brain=nullptr;
}
JNIEXPORT jboolean JNICALL Java_com_nucleo_ia_core_NativeBridge_nativeLoadWeights(
        JNIEnv*env,jclass,jlong h,jbyteArray arr){
    auto*b=(nucleo::Brain*)h; if(!b)return JNI_FALSE;
    jsize n=env->GetArrayLength(arr);
    jbyte*d=env->GetByteArrayElements(arr,nullptr);
    bool ok=b->loadWeights((uint8_t*)d,n);
    env->ReleaseByteArrayElements(arr,d,JNI_ABORT);
    return ok?JNI_TRUE:JNI_FALSE;
}
JNIEXPORT jbyteArray JNICALL Java_com_nucleo_ia_core_NativeBridge_nativeSaveWeights(
        JNIEnv*env,jclass,jlong h){
    auto*b=(nucleo::Brain*)h; if(!b)return nullptr;
    auto v=b->saveWeights();
    jbyteArray a=env->NewByteArray((jsize)v.size());
    env->SetByteArrayRegion(a,0,(jsize)v.size(),(const jbyte*)v.data());
    return a;
}
JNIEXPORT jfloatArray JNICALL Java_com_nucleo_ia_core_NativeBridge_nativeForward(
        JNIEnv*env,jclass,jlong h,jintArray ids){
    auto*b=(nucleo::Brain*)h; if(!b)return nullptr;
    jint*d=env->GetIntArrayElements(ids,nullptr);
    float out[nucleo::NUM_CLASSES];
    b->forward(d,out);
    env->ReleaseIntArrayElements(ids,d,JNI_ABORT);
    jfloatArray r=env->NewFloatArray(nucleo::NUM_CLASSES);
    env->SetFloatArrayRegion(r,0,nucleo::NUM_CLASSES,out);
    return r;
}
JNIEXPORT void JNICALL Java_com_nucleo_ia_core_NativeBridge_nativeTrainStep(
        JNIEnv*env,jclass,jlong h,jintArray ids,jint label,jfloat lr){
    auto*b=(nucleo::Brain*)h; if(!b)return;
    jint*d=env->GetIntArrayElements(ids,nullptr);
    b->trainStep(d,label,lr);
    env->ReleaseIntArrayElements(ids,d,JNI_ABORT);
}
JNIEXPORT jlong JNICALL Java_com_nucleo_ia_core_NativeBridge_nativeWeightBytes(
        JNIEnv*,jclass,jlong h){ auto*b=(nucleo::Brain*)h; return b?(jlong)b->weightBytes():0; }
}