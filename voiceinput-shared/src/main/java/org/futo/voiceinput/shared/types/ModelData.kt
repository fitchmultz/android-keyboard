package org.futo.voiceinput.shared.types

import android.content.Context
import androidx.annotation.StringRes
import org.futo.voiceinput.shared.ggml.WhisperGGML
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel


// Taken from https://github.com/tensorflow/tflite-support/blob/483c45d002cbed57d219fae1676a4d62b28fba73/tensorflow_lite_support/java/src/java/org/tensorflow/lite/support/common/FileUtil.java#L158
/**
 * Loads a file from the asset folder through memory mapping.
 *
 * @param context Application context to access assets.
 * @param filePath Asset path of the file.
 * @return the loaded memory mapped file.
 * @throws IOException if an I/O error occurs when loading the file model.
 */
enum class EngineKind {
    Whisper,
    Parakeet,
    // Future engines can be added here
}

@Throws(IOException::class)
fun loadMappedFile(context: Context, filePath: String): MappedByteBuffer {
    context.assets.openFd(filePath).use { fileDescriptor ->
        FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        }
    }
}

// Maybe add `val languages: Set<Language>`
interface ModelLoader {
    @get:StringRes
    val name: Int

    /** Which ASR engine this model uses. Defaults to Whisper for existing models. */
    val engineKind: EngineKind
        get() = EngineKind.Whisper

    fun exists(context: Context): Boolean
    fun getRequiredDownloadList(context: Context): List<String>

    /** Load and construct the engine instance for this model. */
    fun loadEngine(context: Context): ASREngine

    /** Key used by ModelManager to cache this engine instance. */
    fun key(context: Context): Any
}

internal class ModelBuiltInAsset(
    override val name: Int,
    val ggmlFile: String
) : ModelLoader {
    override val engineKind: EngineKind
        get() = EngineKind.Whisper

    override fun exists(context: Context): Boolean {
        return true
    }

    override fun getRequiredDownloadList(context: Context): List<String> {
        return listOf()
    }

    override fun loadEngine(context: Context): ASREngine {
        val file = loadMappedFile(context, ggmlFile)
        return WhisperGGML(file)
    }

    override fun key(context: Context): Any {
        return "BuiltIn$ggmlFile"
    }
}

@Throws(IOException::class)
private fun tryOpenDownloadedModel(file: File): MappedByteBuffer {
    val fis = file.inputStream()
    val channel = fis.channel

    return channel.map(
        FileChannel.MapMode.READ_ONLY,
        0, channel.size()
    ).load()
}

@Throws(IOException::class)
private fun Context.tryOpenDownloadedModel(pathStr: String): MappedByteBuffer {
    return tryOpenDownloadedModel(File(filesDir, pathStr))
}

internal class ModelDownloadable(
    override val name: Int,
    val ggmlFile: String,
    val checksum: String
) : ModelLoader {
    override val engineKind: EngineKind
        get() = EngineKind.Whisper

    override fun exists(context: Context): Boolean {
        return getRequiredDownloadList(context).isEmpty()
    }

    override fun getRequiredDownloadList(context: Context): List<String> {
        return listOf(ggmlFile).filter {
            !File(context.filesDir, it).exists()
        }
    }

    override fun loadEngine(context: Context): ASREngine {
        val file = context.tryOpenDownloadedModel(ggmlFile)
        return WhisperGGML(file)
    }

    override fun key(context: Context): Any {
        return "Downloadable$ggmlFile$checksum"
    }
}

public class ModelFileFile(
    override val name: Int,
    val file: File,
) : ModelLoader {
    override val engineKind: EngineKind
        get() = EngineKind.Whisper

    override fun exists(context: Context): Boolean {
        return file.exists()
    }

    override fun getRequiredDownloadList(context: Context): List<String> {
        return listOf()
    }

    override fun loadEngine(context: Context): ASREngine {
        val file = tryOpenDownloadedModel(file)
        return WhisperGGML(file)
    }

    override fun key(context: Context): Any {
        return "File${file.absolutePath}"
    }
}
