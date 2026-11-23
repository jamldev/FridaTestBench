#include <jni.h>
#include <android/log.h>

JNIEXPORT jint JNI_OnLoad(JavaVM *, void *) {
    __android_log_print(ANDROID_LOG_INFO, "FridaTestBench", "Hello world from System.loadLibrary");
    return JNI_VERSION_1_6;
}
