# Parakeet Voice Input – Integration Overview

## Scope

Parakeet is wired into FUTO Keyboard as an ASR engine alongside Whisper. The current native backend is a stub that accepts a mapped model buffer, supports cancellation via the `<>CANCELLED<> flag` marker, and otherwise returns an empty string. The Kotlin side (ParakeetEngine) already handles these markers and guards reuse after close. The scaffolding is in place so a real backend can be dropped in without changing app-level code.

## Architecture

- **VoiceInputAction / VoiceInputActionWindow**
  - Builds `RecognizerViewSettings` from user/language settings.
  - Selects a `ModelLoader` (Whisper or Parakeet) via `ResourceHelper.tryFindingVoiceInputModelForLocale`.
- **ModelLoader / Models.kt**
  - `ENGLISH_MODELS` order: tiny Whisper (default), base Whisper, small Whisper, Parakeet.
  - `ModelLoader.loadEngine(context)` constructs engines: Whisper loaders return WhisperGGML engines; `PARAKEET_ENGLISH_MODEL` returns `ParakeetEngine` over the JNI bridge.
- **ModelManager / MultiModelRunner / AudioRecognizer**
  - `ModelManager` creates and caches engines by `loader.key(context)`.
  - `MultiModelRunner` runs `ASREngine` instances and handles bail/cancel semantics.
  - `AudioRecognizer` orchestrates runs and logs engine/model keys and lifecycle events.
- **ParakeetEngine / JNI stub**
  - `ParakeetEngine` implements `ASREngine` for `EngineKind.Parakeet`.
  - JNI stub (`native/jni/org_futo_voiceinput_Parakeet.cpp`) owns native state and defines `openFromBufferNative`, `inferNative`, `cancelNative`, `closeNative`.
  - Cancellation uses the `<>CANCELLED<> …` markers described in `docs/parakeet_backend_contract.md`.

## Model Selection and UI Semantics

- **Built-in vs external**
  - `ResourceHelper.tryFindingVoiceInputModelForLocale` picks an external voice model if present; otherwise it uses built-ins.
  - Tiny Whisper is the default English built-in. Parakeet appears as an additional built-in when `parakeet_en.bin` is packaged.
- **Languages screen**
  - Users can select built-in voice models (including Parakeet) or import external files.
  - Semantics:
    - Default built-in (tiny Whisper): treated as “unset”.
    - Non-default built-in (e.g., Parakeet): treated as “set” but not marked `(Imported)`.
    - External voice model: treated as “set” and labeled `(Imported)`.

## Logging and Tests

- **Logging**
  - UI boundary: `VoiceInputActionWindow` logs engine, model, and locales at session start (no user text/audio).
  - Pipeline: `MultiModelRunner` and `AudioRecognizer` log engine kind, model key, allowed/bail languages, and bail/cancel/complete events; they never log transcripts.
- **Tests (unitTests/src)**
  - `ResourceHelperVoiceInputTest`: verifies built-in vs external selection semantics, including Parakeet.
  - `LanguageVoiceInputUiSemanticsTest`: verifies UI semantics for default vs non-default vs external models.
  - Test helpers (`TestContextUtils`) provide a `FakeContext` and test DataStore to exercise `ResourceHelper` logic.

## Backend Contract and Step Docs

- `docs/parakeet_backend_contract.md`: JNI contract for a real Parakeet backend (markers, parameters, lifecycle).
- Step docs: `docs/parakeet_step2_plan.md`, `docs/parakeet_step3_plan.md`, `docs/parakeet_step4_plan.md`, `docs/parakeet_step5_plan.md` capture the incremental integration path.

Future Parakeet backend work should update this overview and the backend contract rather than scattering new notes elsewhere.