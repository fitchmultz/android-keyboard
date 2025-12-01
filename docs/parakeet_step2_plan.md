## 0. Scope and current state

From the files you pasted:

* Whisper is still the only engine actually used at runtime.
* There is now:

  * `ASREngine` interface.
  * `EngineKind` enum (`Whisper`, `Parakeet`).
  * `ModelLoader` generalized to return `ASREngine`.
  * `ModelManager` and `MultiModelRunner` working with `ASREngine` and enforcing a single `EngineKind` per run.
* `ParakeetEngine` exists with JNI stubs:

  * `inferNative` returns an empty string.
  * Cancellation / bail semantics are not wired.
* `PARAKEET_ENGLISH_MODEL` is defined but not used anywhere.
* `parakeet_en_name` is only defined in `voiceinput-shared/src/main/res/values/strings-voiceinput.xml` and missing in `translations/voiceinput/...`.

Goal of this step:

1. Keep Whisper as the default and currently used engine.
2. Make Parakeet semantics mirror Whisper at the interface level (handle guard, cancellation, bail).
3. Expose Parakeet English as an extra selectable English voice model in the UI.
4. Fix the missing translations entry.
5. Do not implement real Parakeet inference yet; keep the native side as a stub with correct cancellation behavior.

All tasks below are written for an AI coding agent with full repo context.

---

## 1. Build sanity check for the current state

**Task 1.1 - Build check**

1. From the project root, run:

   ```bash
   ./gradlew assembleUnstableDebug
   ```

2. If there are compile errors, fix only what is necessary to get a clean build, keeping behavior identical to what you already implemented.

3. Once the build passes, continue to the next sections.

---

## 2. Make ParakeetEngine mirror Whisper semantics

We want `ParakeetEngine` to behave like `WhisperGGML` from the Kotlin side:

* Guard against using a closed handle.
* Use the same `< >CANCELLED< >` sentinel convention for cancel and language bail.
* Keep cancellation path symmetric.

### 2.1 - Mirror WhisperGGML.infer in ParakeetEngine

**File:** `voiceinput-shared/src/main/java/org/futo/voiceinput/shared/parakeet/ParakeetEngine.kt`

**Task 2.1.1 - Replace infer with Whisper-like logic**

Currently `infer` just calls `inferNative` and returns the trimmed result.

Replace the body of `infer` with this implementation (keep the signature as-is):

```kotlin
@Throws(BailLanguageException::class, InferenceCancelledException::class)
override suspend fun infer(
    samples: FloatArray,
    prompt: String,
    languages: Array<String>,
    bailLanguages: Array<String>,
    decodingMode: DecodingMode,
    suppressNonSpeechTokens: Boolean,
    partialResultCallback: (String) -> Unit
): String = withContext(inferenceContext) {
    if (handle == 0L) {
        throw IllegalStateException("ParakeetEngine has already been closed, cannot infer")
    }

    this@ParakeetEngine.partialResultCallback = partialResultCallback

    val result = inferNative(
        handle,
        samples,
        prompt,
        languages,
        bailLanguages,
        decodingMode.value,
        suppressNonSpeechTokens
    ).trim()

    if (result.contains("<>CANCELLED<>")) {
        when {
            result.contains("flag") -> {
                throw InferenceCancelledException()
            }
            result.contains("lang=") -> {
                val language = result.split("lang=")[1]
                throw BailLanguageException(language)
            }
            else -> {
                throw IllegalStateException("Cancelled for unknown reason")
            }
        }
    } else {
        return@withContext result
    }
}
```

Notes:

* This is intentionally almost identical to `WhisperGGML.infer`.
* We are not adding any new behavior on the native side yet; we just pre-wire decoding of the sentinel when it appears.

`cancel()` and `close()` are already structurally fine and can be left unchanged.

### 2.2 - Make the native Parakeet stub honor cancellation

Right now the native stub tracks a `cancelled` flag but ignores it in `nativeInfer`. We want it to short-circuit with the same sentinel string Whisper uses so Kotlin can map it to `InferenceCancelledException`.

**File:** `native/jni/org_futo_voiceinput_Parakeet.cpp`

**Task 2.2.1 - Add cancelled check in nativeInfer**

Update `nativeInfer` to check `state->cancelled` and return a cancellation sentinel string:

```cpp
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

    if (state->cancelled) {
        // Match the Whisper convention so Kotlin can throw InferenceCancelledException
        return env->NewStringUTF("<>CANCELLED<>flag");
    }

    // TODO: implement real Parakeet inference here

    const char* dummy = "";
    return env->NewStringUTF(dummy);
}
```

**Task 2.2.2 - Leave reset logic for a later "real" implementation**

Do not add extra logic to reset `state->cancelled` in this step. When Parakeet gets a real implementation, the inference loop should clear or reuse this flag appropriately.

The only requirement now is that after `cancelNative` sets `cancelled = true`, a subsequent call into `nativeInfer` will return the cancellation sentinel without doing work.

---

## 3. Wire Parakeet English into the English model list

`PARAKEET_ENGLISH_MODEL` is currently defined but unused. We want:

* English settings to show "Parakeet English" as an extra English voice model.
* Whisper Tiny English to remain the default and built in fallback.

### 3.1 - Add Parakeet English to ENGLISH_MODELS

**File:** `voiceinput-shared/src/main/java/org/futo/voiceinput/shared/Models.kt`

You already have:

```kotlin
val BUILTIN_ENGLISH_MODEL: ModelLoader = ModelBuiltInAsset(
    name = R.string.tiny_en_name,
    ggmlFile = "tiny_en_acft_q8_0.bin.not.tflite"
)
```

and:

```kotlin
val PARAKEET_ENGLISH_MODEL: ModelLoader = object : ModelLoader {
    override val name: Int = R.string.parakeet_en_name

    override val engineKind: EngineKind
        get() = EngineKind.Parakeet

    override fun exists(context: Context): Boolean = true

    override fun getRequiredDownloadList(context: Context): List<String> = emptyList()

    override fun loadEngine(context: Context): ASREngine {
        val buffer = loadMappedFile(context, "parakeet_en.bin")
        return ParakeetEngine(buffer)
    }

    override fun key(context: Context): Any = "BuiltInParakeetEn"
}
```

And the English model list:

```kotlin
val ENGLISH_MODELS: List<ModelLoader> = listOf(
    ModelBuiltInAsset(
        name = R.string.tiny_en_name,
        ggmlFile = "tiny_en_acft_q8_0.bin.not.tflite"
    ),

    ModelDownloadable(
        name = R.string.base_en_name,
        ggmlFile = "base_en_acft_q8_0.bin",
        checksum = "e9b4b7b81b8a28769e8aa9962aa39bb9f21b622cf6a63982e93f065ed5caf1c8"
    ),
    ModelDownloadable(
        name = R.string.small_en_name,
        ggmlFile = "small_en_acft_q8_0.bin",
        checksum = "58fbe949992dafed917590d58bc12ca577b08b9957f0b3e0d7ee71b64bed3aa8"
    ),
)
```

**Task 3.1.1 - Add Parakeet to ENGLISH_MODELS without changing the default**

Update `ENGLISH_MODELS` to:

```kotlin
val ENGLISH_MODELS: List<ModelLoader> = listOf(
    ModelBuiltInAsset(
        name = R.string.tiny_en_name,
        ggmlFile = "tiny_en_acft_q8_0.bin.not.tflite"
    ),
    ModelDownloadable(
        name = R.string.base_en_name,
        ggmlFile = "base_en_acft_q8_0.bin",
        checksum = "e9b4b7b81b8a28769e8aa9962aa39bb9f21b622cf6a63982e93f065ed5caf1c8"
    ),
    ModelDownloadable(
        name = R.string.small_en_name,
        ggmlFile = "small_en_acft_q8_0.bin",
        checksum = "58fbe949992dafed917590d58bc12ca577b08b9957f0b3e0d7ee71b64bed3aa8"
    ),
    PARAKEET_ENGLISH_MODEL,
)
```

Important:

* Do not change `BUILTIN_ENGLISH_MODEL`. It must remain the tiny Whisper model so Whisper remains the default and built in fallback.
* Assume that `parakeet_en.bin` will be provided manually as an asset. Do not create dummy or placeholder binary files.

### 3.2 - Ensure settings UI uses ENGLISH_MODELS

**File:** `java/src/org/futo/inputmethod/latin/uix/settings/pages/Languages.kt`

You did not include this file; the agent will need to inspect it.

**Task 3.2.1 - Confirm English voice model picker is driven by ENGLISH_MODELS**

1. Search for `ENGLISH_MODELS` in the project and find its usage in `Languages.kt` (or related settings code).
2. Confirm that the English voice model picker (the UI where the user selects the voice model) uses this list.

If it already uses `ENGLISH_MODELS` directly, no changes beyond Task 3.1.1 are needed.

If it does not, then:

3. Refactor that part of `Languages.kt` so that:

   * The English voice model options are built from the `ENGLISH_MODELS` list rather than being hardcoded.
   * The entry labels come from each `ModelLoader.name` string resource.
   * Adding or removing entries from `ENGLISH_MODELS` is enough to update the UI.

Constraints:

* Keep the existing default behavior:

  * The default choice must remain the tiny Whisper English model.
* Do not modify behavior for non English languages.

---

## 4. Fix missing translation entry for parakeet_en_name

We want the translations bundle to define the same key so resource resolution is consistent.

### 4.1 - Add parakeet_en_name to translations/voiceinput base values

**File:** `translations/voiceinput/values/strings-voiceinput.xml`

You pasted this file; it currently has `tiny_en_name`, `base_en_name`, `small_en_name`, but no `parakeet_en_name`.

**Task 4.1.1 - Add a base entry**

Insert the following snippet in the appropriate place near the other English model names:

```xml
<!-- Model name for the tiny english model -->
<string name="tiny_en_name">English-39 (default)</string>
<!-- Model name for the Parakeet english model -->
<string name="parakeet_en_name">Parakeet English</string>
<!-- Model name for the base english model -->
<string name="base_en_name">English-74 (slower, more accurate)</string>
<!-- Model name for the small english model -->
<string name="small_en_name">English-244 (slow)</string>
```

Notes:

* Use the same English text as in `voiceinput-shared/src/main/res/values/strings-voiceinput.xml`.
* Do not add translated variants in `translations/voiceinput/values-*/strings-voiceinput.xml` in this step. Those can be handled separately if needed.

---

## 5. Rebuild and quick expectations

### 5.1 - Rebuild

**Task 5.1.1 - Build again**

Run:

```bash
./gradlew assembleUnstableDebug
```

and ensure it completes successfully.

If anything fails, fix only what is needed to restore a clean build while preserving the behavior specified above.

### 5.2 - Behavioral expectations for later manual testing

For this iteration, automated tests are not required, but the following should hold when you do manual runs:

* English voice input:

  * If no external voice model is configured for English:

    * Whisper Tiny English remains the default and still works as before.
    * In the English voice model settings, "Parakeet English" appears as an additional option.
* Parakeet English model:

  * When selected and with a valid `parakeet_en.bin` asset present:

    * Model loading goes through `PARAKEET_ENGLISH_MODEL.loadEngine` and returns a `ParakeetEngine`.
    * Inference currently returns an empty string because the native implementation is stubbed.
  * When cancellation is triggered:

    * After a future real implementation starts using `< >CANCELLED< >flag`, Kotlin will map it to `InferenceCancelledException` just like Whisper. The stub already handles the sentinel decoding on the Kotlin side.
* Whisper models:

  * Behavior is unchanged; cancellation and bail continue to work as they did before introducing Parakeet.

Do not add or commit any Parakeet model binaries (`parakeet_en.bin`). Those are external assets that should be provided manually.

---

## 6. Future work placeholder (not to implement now)

The following is intentionally out of scope for this step and should not be implemented by the agent yet:

* Real Parakeet inference in `native/jni/org_futo_voiceinput_Parakeet.cpp`:

  * Use the mapped buffer from `openFromBufferNative` to construct a Parakeet model.
  * Implement decoding, language detection, and periodically call back into `ParakeetEngine.invokePartialResult`.
  * Respect `state->cancelled` inside the inference loop, returning `< >CANCELLED< >flag` or `< >CANCELLED< >lang=xx` as needed.
* Extending `Language.toEngineString` and `getLanguageFromEngineString` if Parakeet uses codes that differ from Whisper.
* Wiring Parakeet as the default or preferred English model.

Those can be specified in a later document once you are ready to actually run Parakeet on device.
