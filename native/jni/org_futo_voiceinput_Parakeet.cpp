#include <jni.h>
#include <string>
#include "jni_common.h"
#include "org_futo_voiceinput_Parakeet.h"

namespace {

struct ParakeetState {
    // Add fields for Parakeet model context when you implement it
    bool cancelled = false;
};

jlong nativeOpenFromBuffer(JNIEnv* env, jclass /*clazz*/, jobject /*buffer*/) {
    // TODO: map buffer and construct Parakeet model
    auto* state = new ParakeetState();
    return reinterpret_cast<jlong>(state);
}

jstring nativeInfer(
        JNIEnv* env,
        jobject /*thiz*/,
        jlong handle,
        jfloatArray /*samplesArray*/,
        jstring /*prompt*/,
        jobjectArray /*languages*/,
        jobjectArray /*bailLanguages*/,
        jint /*decodingMode*/,
        jboolean /*suppressNonSpeechTokens*/) {

    auto* state = reinterpret_cast<ParakeetState*>(handle);
    if (!state) {
        return env->NewStringUTF("");
    }

    // TODO: implement real inference
    const char* dummy = "";
    return env->NewStringUTF(dummy);
}

void nativeCancel(JNIEnv* /*env*/, jclass /*clazz*/, jlong handle) {
    auto* state = reinterpret_cast<ParakeetState*>(handle);
    if (!state) return;
    state->cancelled = true;
}

void nativeClose(JNIEnv* /*env*/, jclass /*clazz*/, jlong handle) {
    auto* state = reinterpret_cast<ParakeetState*>(handle);
    if (!state) return;
    delete state;
}

const JNINativeMethod gMethods[] = {
    {
        const_cast<char*>("openFromBufferNative"),
        const_cast<char*>("(Ljava/nio/Buffer;)J"),
        reinterpret_cast<void*>(nativeOpenFromBuffer)
    },
    {
        const_cast<char*>("inferNative"),
        const_cast<char*>("(J[FLjava/lang/String;[Ljava/lang/String;[Ljava/lang/String;IZ)Ljava/lang/String;"),
        reinterpret_cast<void*>(nativeInfer)
    },
    {
        const_cast<char*>("cancelNative"),
        const_cast<char*>("(J)V"),
        reinterpret_cast<void*>(nativeCancel)
    },
    {
        const_cast<char*>("closeNative"),
        const_cast<char*>("(J)V"),
        reinterpret_cast<void*>(nativeClose)
    },
};

} // anonymous namespace

namespace voiceinput {

int register_Parakeet(JNIEnv* env) {
    const char* const kClassPathName = "org/futo/voiceinput/shared/parakeet/ParakeetEngine";
    return latinime::registerNativeMethods(env, kClassPathName, gMethods, sizeof(gMethods) / sizeof(gMethods[0]));
}

} // namespace voiceinput
