## Parakeet integration – step 4

Goal: make the voice input runtime pipeline engine-agnostic so Parakeet engines flow end-to-end via `ASREngine`, `ModelLoader`, `ModelManager`, and `RecognizerView`, while keeping Whisper tiny as the default English model.

What changed:
- `ModelManager` now centralizes engine construction via `ModelLoader.loadEngine`, so both Whisper and Parakeet engines are obtained through the same path.
- `RecognizerView`/`AudioRecognizer` run through `MultiModelRunner` using `ASREngine` only; cancellation and bail exceptions are handled generically for any engine.
- Added debug logging in the ASR path to report engine kind, model key, and configured languages, plus completion/cancel/bail events (no user text or audio logged).

Related docs: see also `docs/parakeet_step2_plan.md` and `docs/parakeet_step3_plan.md`.
