## Parakeet backend contract

### 1. Purpose
This document describes the expectations for a real Parakeet backend behind `native/jni/org_futo_voiceinput_Parakeet.cpp`, consumed by `ParakeetEngine.kt` through the `ASREngine` pipeline.

### 2. Entry points and lifecycle
JNI entry points:
- `openFromBufferNative(Buffer)`
- `inferNative(handle, samples, prompt, languages, bailLanguages, decodingMode, suppressNonSpeechTokens)`
- `cancelNative(handle)`
- `closeNative(handle)`

Expectations:
- **openFromBufferNative**: map the direct Buffer containing the model file into a backend model context; return a non-zero handle on success, `0` on failure (treated as fatal by ParakeetEngine).
- **closeNative**: free all resources for the handle; safe to call once (no double-free/use-after-free).
- **cancelNative**: set a thread-safe flag that makes `inferNative` exit promptly when possible.

### 3. Inference parameters
- `samples`: mono float PCM at 16 kHz (match Whisper), normalized ~[-1.0, 1.0].
- `prompt`: optional text prompt; backend may bias decoding or ignore it.
- `languages`: allowed languages. Empty = auto-detect. One = fixed. Multiple = restrict detection to that set.
- `bailLanguages`: languages that should cause early bail. If detected, return a bail marker (see below) instead of a transcript.
- `decodingMode`: mirrors Whisper: `0` = greedy; `>0` = beam search with that width (backend may interpret reasonably while preserving greedy vs beam idea).
- `suppressNonSpeechTokens`: hint to avoid non-speech/special tokens where possible, similar to Whisper.

### 4. Cancellation and bail markers (must match Kotlin behavior)
- User/system cancel: return `<>CANCELLED<> flag` → ParakeetEngine throws `InferenceCancelledException`.
- Language bail: return `<>CANCELLED<> lang=<code>` where `<code>` is compatible with `getLanguageFromEngineString` for `EngineKind.Parakeet` → ParakeetEngine throws `BailLanguageException(language)`.
- Any other string containing `<>CANCELLED<>` is treated as an error and should not be used.

Partial results:
- Should be surfaced via the `invokePartialResult` JNI callback on `ParakeetEngine` (similar to Whisper partial_text_callback).
- Stub currently emits none; future backend should call the callback with decoded fragments as available.
- Partial strings must NOT include the `<>CANCELLED<>` marker.

### 5. Error handling expectations
- Model load failures: return `0` from `openFromBufferNative`; ParakeetEngine treats as fatal.
- Inference-time errors: log natively; if needed, return a non-empty, non-`<>CANCELLED<>` string indicating backend failure. Reserve `<>CANCELLED<>` exclusively for cancel/bail.

TODO for a real backend (future): VAD/streaming behavior, resource reuse, and alignment of language codes with `EngineKind.Parakeet` if they differ from Whisper.
