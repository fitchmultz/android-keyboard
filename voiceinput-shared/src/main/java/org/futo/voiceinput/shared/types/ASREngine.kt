package org.futo.voiceinput.shared.types

import org.futo.voiceinput.shared.ggml.BailLanguageException
import org.futo.voiceinput.shared.ggml.DecodingMode
import org.futo.voiceinput.shared.ggml.InferenceCancelledException

/**
 * Generic ASR engine interface used by ModelManager and MultiModelRunner.
 * Whisper and Parakeet engines both implement this.
 */
interface ASREngine {
    @Throws(BailLanguageException::class, InferenceCancelledException::class)
    suspend fun infer(
        samples: FloatArray,
        prompt: String,
        languages: Array<String>,
        bailLanguages: Array<String>,
        decodingMode: DecodingMode,
        suppressNonSpeechTokens: Boolean,
        partialResultCallback: (String) -> Unit
    ): String

    /** Request cancellation of an in flight inference. */
    fun cancel()

    /** Release resources. Called from ModelManager.cleanUp(). */
    suspend fun close()
}
