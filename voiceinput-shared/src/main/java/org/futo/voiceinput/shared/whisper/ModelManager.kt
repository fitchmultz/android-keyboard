package org.futo.voiceinput.shared.whisper

import android.content.Context
import org.futo.voiceinput.shared.types.ASREngine
import org.futo.voiceinput.shared.types.ModelLoader


class ModelManager(
    val context: Context
) {
    private val loadedModels: HashMap<Any, ASREngine> = hashMapOf()

    fun obtainModel(model: ModelLoader): ASREngine {
        val key = model.key(context)
        val existing = loadedModels[key]
        if (existing != null) {
            return existing
        }

        val engine = model.loadEngine(context)
        loadedModels[key] = engine
        return engine
    }

    fun cancelAll() {
        loadedModels.values.forEach { it.cancel() }
    }

    suspend fun cleanUp() {
        for (engine in loadedModels.values) {
            engine.cancel()
            engine.close()
        }

        loadedModels.clear()
    }
}
