package org.futo.voiceinput.shared.whisper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.futo.voiceinput.shared.ggml.BailLanguageException
import org.futo.voiceinput.shared.ggml.DecodingMode
import org.futo.voiceinput.shared.ggml.InferenceCancelledException
import org.futo.voiceinput.shared.types.InferenceState
import org.futo.voiceinput.shared.types.Language
import org.futo.voiceinput.shared.types.ModelInferenceCallback
import org.futo.voiceinput.shared.types.ModelLoader
import org.futo.voiceinput.shared.types.getLanguageFromEngineString
import org.futo.voiceinput.shared.types.toEngineString


data class MultiModelRunConfiguration(
    val primaryModel: ModelLoader,
    val languageSpecificModels: Map<Language, ModelLoader>
)

data class DecodingConfiguration(
    val glossary: List<String>,
    val languages: Set<Language>,
    val suppressSymbols: Boolean
)

class MultiModelRunner(
    private val modelManager: ModelManager
) {
    companion object {
        private const val LOG_TAG = "VoiceInput"
    }

    suspend fun preload(runConfiguration: MultiModelRunConfiguration) = coroutineScope {
        val jobs = mutableListOf<Job>()

        jobs.add(launch(Dispatchers.Default) {
            modelManager.obtainModel(runConfiguration.primaryModel)
        })

        if (runConfiguration.languageSpecificModels.count() < 2) {
            runConfiguration.languageSpecificModels.forEach {
                jobs.add(launch(Dispatchers.Default) {
                    modelManager.obtainModel(it.value)
                })
            }
        }

        jobs.forEach { it.join() }
    }

    @Throws(InferenceCancelledException::class)
    suspend fun run(
        samples: FloatArray,
        runConfiguration: MultiModelRunConfiguration,
        decodingConfiguration: DecodingConfiguration,
        callback: ModelInferenceCallback
    ): String = coroutineScope {
        callback.updateStatus(InferenceState.LoadingModel)
        val primaryLoader = runConfiguration.primaryModel
        val engineKind = primaryLoader.engineKind

        require(runConfiguration.languageSpecificModels.values.all { it.engineKind == engineKind }) {
            "MultiModelRunner only supports a single engine kind per runConfiguration"
        }

        val primaryEngine = modelManager.obtainModel(primaryLoader)
        val modelKey = primaryLoader.key(modelManager.context).toString()

        val allowedLanguages = decodingConfiguration.languages
            .map { it.toEngineString(engineKind) }
            .toTypedArray()

        val bailLanguages = runConfiguration.languageSpecificModels
            .filter { it.value != primaryLoader }
            .keys
            .map { it.toEngineString(engineKind) }
            .toTypedArray()
        Log.d(
            LOG_TAG,
            "ASR start engine=$engineKind modelKey=$modelKey languages=${allowedLanguages.joinToString()} bail=${bailLanguages.joinToString()}"
        )

        val glossary = if(decodingConfiguration.glossary.isNotEmpty()) {
            "(Glossary: " + decodingConfiguration.glossary.joinToString(separator = ", ") + ")"
        } else {
            ""
        }

        val result = try {
            callback.updateStatus(InferenceState.Encoding)
            primaryEngine.infer(
                samples = samples,
                prompt = glossary,
                languages = allowedLanguages,
                bailLanguages = bailLanguages,
                decodingMode = DecodingMode.BeamSearch5,
                suppressNonSpeechTokens = decodingConfiguration.suppressSymbols,
                partialResultCallback = {
                    callback.partialResult(it)
                }
            )
        } catch(e: BailLanguageException) {
            callback.updateStatus(InferenceState.SwitchingModel)
            val language = getLanguageFromEngineString(engineKind, e.language)

            val specificModelLoader = runConfiguration.languageSpecificModels[language]!!
            val specificEngine = modelManager.obtainModel(specificModelLoader)
            Log.d(
                LOG_TAG,
                "ASR bail engine=$engineKind fromModel=$modelKey toModel=${specificModelLoader.key(modelManager.context)} language=${e.language}"
            )

            specificEngine.infer(
                samples = samples,
                prompt = glossary,
                languages = arrayOf(e.language),
                bailLanguages = emptyArray(),
                decodingMode = DecodingMode.BeamSearch5,
                suppressNonSpeechTokens = decodingConfiguration.suppressSymbols,
                partialResultCallback = {
                    callback.partialResult(it)
                }
            )
        }

        Log.d(LOG_TAG, "ASR completed engine=$engineKind modelKey=$modelKey status=success")
        return@coroutineScope result
    }

    fun cancelAll() {
        modelManager.cancelAll()
    }
}
