#include <jni.h>
#include <atomic>
#include <string>
#include "jni_common.h"
#include "org_futo_voiceinput_Parakeet.h"

namespace {

struct ParakeetState {
    // Cancellation flag, may be read from the inference thread and written from a cancel call.
    std::atomic<bool> cancelled{false};

    // These fields will be used when the real Parakeet backend is wired up and needs to emit partial results.
    // For now they are set up but not used.
    JNIEnv* env = nullptr;
    jobject partial_result_instance = nullptr;
    jmethodID partial_result_method = nullptr;

    // Future fields: model context pointer, decoding parameters, etc.
    // For now the stub does not hold a real model.
};

static void emit_partial_result(ParakeetState* state, const std::string& text) {
    if (!state || !state->env || !state->partial_result_instance || !state->partial_result_method) {
        return;
    }

    JNIEnv* env = state->env;
    jstring jtext = env->NewStringUTF(text.c_str());
    env->CallVoidMethod(state->partial_result_instance, state->partial_result_method, jtext);
    env->DeleteLocalRef(jtext);
}

jlong nativeOpenFromBuffer(JNIEnv* env, jclass /*clazz*/, jobject /*buffer*/) {
    // TODO: map buffer and construct Parakeet model
    auto* state = new ParakeetState();
    return reinterpret_cast<jlong>(state);
}

// Cancellation protocol expected by Kotlin (mirrors WhisperGGML):
//
// - Normal completion: return the final transcript text, with no "<>CANCELLED<>" marker.
// - Bail on forbidden language: return "<>CANCELLED<> lang=<langId>" and Kotlin will throw BailLanguageException.
// - User or system cancel: return "<>CANCELLED<> flag" and Kotlin will throw InferenceCancelledException.
//
// The exact substrings "<>CANCELLED<> flag" and "<>CANCELLED<> lang=" must be preserved.
// Parakeet's real backend must follow the same contract when it is implemented.
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

    if (state->cancelled.load(std::memory_order_relaxed)) {
        const char* cancelled = "<>CANCELLED<> flag";
        return env->NewStringUTF(cancelled);
    }

    // TODO: implement real inference
    const char* dummy = "";
    return env->NewStringUTF(dummy);
}

void nativeCancel(JNIEnv* /*env*/, jclass /*clazz*/, jlong handle) {
    auto* state = reinterpret_cast<ParakeetState*>(handle);
    if (!state) return;
    state->cancelled.store(true, std::memory_order_relaxed);
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
