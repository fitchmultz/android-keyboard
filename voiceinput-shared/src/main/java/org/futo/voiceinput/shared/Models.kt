package org.futo.voiceinput.shared

import android.content.Context
import org.futo.voiceinput.shared.parakeet.ParakeetEngine
import org.futo.voiceinput.shared.types.ASREngine
import org.futo.voiceinput.shared.types.EngineKind
import org.futo.voiceinput.shared.types.ModelBuiltInAsset
import org.futo.voiceinput.shared.types.ModelDownloadable
import org.futo.voiceinput.shared.types.ModelLoader
import org.futo.voiceinput.shared.types.loadMappedFile

val BUILTIN_ENGLISH_MODEL: ModelLoader = ModelBuiltInAsset(
    name = R.string.tiny_en_name,
    ggmlFile = "tiny_en_acft_q8_0.bin.not.tflite"
)

val PARAKEET_ENGLISH_MODEL: ModelLoader = object : ModelLoader {
    override val name: Int = R.string.parakeet_en_name

    override val engineKind: EngineKind
        get() = EngineKind.Parakeet

    /**
     * Treat Parakeet as a built-in asset model, but only report it as existing
     * if the asset is actually present in the APK.
     */
    override fun exists(context: Context): Boolean {
        return try {
            context.assets.openFd("parakeet_en.bin").close()
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun getRequiredDownloadList(context: Context): List<String> = emptyList()

    override fun loadEngine(context: Context): ASREngine {
        val buffer = loadMappedFile(context, "parakeet_en.bin")
        return ParakeetEngine(buffer)
    }

    override fun key(context: Context): Any = "BuiltInParakeetEn"
}

/**
 * Order here matters if any code treats the first element as the default.
 * We keep the existing tiny Whisper model as the first entry to avoid changing
 * the default behavior, and add Parakeet as an additional option.
 */
val ENGLISH_MODELS: List<ModelLoader> = listOf(
    BUILTIN_ENGLISH_MODEL,
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

val MULTILINGUAL_MODELS: List<ModelLoader> = listOf(
    ModelDownloadable(
        name = R.string.tiny_name,
        ggmlFile = "tiny_acft_q8_0.bin",
        checksum = "07aa4d514144deacf5ffec5cacb36c93dee272fda9e64ac33a801f8cd5cbd953"
    ),
    ModelDownloadable(
        name = R.string.base_name,
        ggmlFile = "base_acft_q8_0.bin",
        checksum = "e44f352c9aa2c3609dece20c733c4ad4a75c28cd9ab07d005383df55fa96efc4"
    ),
    ModelDownloadable(
        name = R.string.small_name,
        ggmlFile = "small_acft_q8_0.bin",
        checksum = "15ef255465a6dc582ecf1ec651a4618c7ee2c18c05570bbe46493d248d465ac4"
    ),
)
