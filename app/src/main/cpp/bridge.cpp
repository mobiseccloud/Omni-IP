#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_mobisec_omniip_core_NativeEngine_executeNmapScan(
        JNIEnv* env,
        jobject /* this */,
        jstring target) {
    std::string hello = "Nmap Engine Linked";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mobisec_omniip_core_NativeEngine_executeRawPing(
        JNIEnv* env,
        jobject /* this */,
        jstring target) {
    std::string hello = "Raw Ping Engine Linked";
    return env->NewStringUTF(hello.c_str());
}
