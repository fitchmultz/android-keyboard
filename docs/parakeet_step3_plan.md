## Parakeet integration – step 3

- Parakeet JNI now uses `std::atomic<bool>` for cancellation and returns exactly `<>CANCELLED<> flag` when cancelled, matching WhisperGGML.
- Added placeholder fields and an `emit_partial_result` helper in `ParakeetState` to support future partial result callbacks; the stub still returns an empty transcript for normal inference.
- Documented the cancellation marker protocol on the native side and clarified how Kotlin interprets it; no functional changes to defaults or model ordering (Whisper tiny remains the default English model).
