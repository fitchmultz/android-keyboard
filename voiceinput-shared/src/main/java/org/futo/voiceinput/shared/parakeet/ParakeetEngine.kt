package org.futo.voiceinput.shared.parakeet

import androidx.annotation.Keep
import kotlinx.coroutines.withContext
import org.futo.voiceinput.shared.ggml.BailLanguageException
import org.futo.voiceinput.shared.ggml.DecodingMode
import org.futo.voiceinput.shared.ggml.InferenceCancelledException
import org.futo.voiceinput.shared.ggml.inferenceContext
import org.futo.voiceinput.shared.types.ASREngine
import java.nio.Buffer

@Keep
class ParakeetEngine(
    modelBuffer: Buffer
) : ASREngine {

    private var handle: Long = 0L
    private var partialResultCallback: (String) -> Unit = {}

    init {
        handle = openFromBufferNative(modelBuffer)
        if (handle == 0L) {
            throw RuntimeException("Parakeet model could not be loaded")
        }
    }

    @Keep
    private fun invokePartialResult(text: String) {
        partialResultCallback(text.trim())
    }

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

        return@withContext result
    }

    override fun cancel() {
        if (handle != 0L) {
            cancelNative(handle)
        }
    }

    override suspend fun close() = withContext(inferenceContext) {
        if (handle != 0L) {
            closeNative(handle)
            handle = 0L
        }
    }

    private external fun openFromBufferNative(buffer: Buffer): Long

    private external fun inferNative(
        handle: Long,
        samples: FloatArray,
        prompt: String,
        languages: Array<String>,
        bailLanguages: Array<String>,
        decodingMode: Int,
        suppressNonSpeechTokens: Boolean
    ): String

    private external fun cancelNative(handle: Long)
    private external fun closeNative(handle: Long)
}
