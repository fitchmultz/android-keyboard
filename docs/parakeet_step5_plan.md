## Parakeet integration – step 5

Scope: UX clarity for voice-input model origin (built-in vs imported), UI-level debug logging, and a Parakeet backend contract doc.

Changes:
- VoiceInputAction logs engine/model/locales at session start.
- Languages screen appends `(Imported)` to external voice-input models while leaving built-ins unchanged.
- Added `parakeet_backend_contract.md` describing the native Parakeet expectations.

References:
- docs/parakeet_step2_plan.md
- docs/parakeet_step3_plan.md
- docs/parakeet_step4_plan.md
- docs/parakeet_backend_contract.md
