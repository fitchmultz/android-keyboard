package org.futo.inputmethod.latin.uix.settings.pages

import kotlinx.coroutines.runBlocking
import org.futo.inputmethod.latin.uix.FileKind
import org.futo.inputmethod.latin.uix.ResourceHelper
import org.futo.inputmethod.latin.uix.TestContextUtils
import org.futo.inputmethod.latin.uix.setSetting
import org.futo.inputmethod.latin.uix.preferenceKeyFor
import org.futo.voiceinput.shared.BUILTIN_ENGLISH_MODEL
import org.futo.voiceinput.shared.ENGLISH_MODELS
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class LanguageVoiceInputUiSemanticsTest {
    private val tempDirs = mutableListOf<File>()
    private val importedIndicator = "(Imported)"

    private fun newContext(): TestContextUtils.FakeContext {
        val dir = createTempDir(prefix = "voiceinput-ui-test")
        tempDirs += dir
        val context = TestContextUtils.FakeContext(dir)
        TestContextUtils.installTestDataStore(context, File(dir, "prefs.preferences_pb"))
        return context
    }

    private fun installExternalVoiceModel(context: TestContextUtils.FakeContext, locale: Locale, fileName: String = "external_model.bin") {
        File(context.getExternalFilesDir(null), fileName).writeText("dummy")
        runBlocking {
            context.setSetting(FileKind.VoiceInput.preferenceKeyFor(locale.toString()), fileName)
        }
        TestContextUtils.syncPreferences(context)
    }

    private fun ensureBaseEnglishExists(context: TestContextUtils.FakeContext) {
        val baseFileName = "base_en_acft_q8_0.bin"
        File(context.filesDir, baseFileName).writeText("dummy")
    }

    private fun isCurrentlySet(context: TestContextUtils.FakeContext, locale: Locale): Boolean {
        val hasExternalFile = runBlocking {
            ResourceHelper.findFileForKind(context, locale, FileKind.VoiceInput)?.exists() == true
        }
        return hasExternalFile || ResourceHelper.hasNonDefaultBuiltInVoiceModel(context, locale)
    }

    private fun formatVoiceInputLabel(baseName: String, hasExternal: Boolean): String {
        return if (hasExternal) "$baseName $importedIndicator" else baseName
    }

    @After
    fun tearDown() {
        tempDirs.forEach { it.deleteRecursively() }
    }

    @Test
    fun defaultTinyWhisperShowsUnsetAndNoImportedTag() {
        val context = newContext()
        val locale = Locale("en")

        val hasExternal = false
        val label = formatVoiceInputLabel("Tiny Whisper", hasExternal)

        assertFalse(isCurrentlySet(context, locale))
        assertFalse(label.contains(importedIndicator))
        val preferred = ResourceHelper.getPreferredBuiltInVoiceInputModel(context, locale)
        assertTrue(preferred === BUILTIN_ENGLISH_MODEL)
    }

    @Test
    fun nonDefaultBuiltInIsSetButNotImported() {
        val context = newContext()
        val locale = Locale("en")
        ensureBaseEnglishExists(context)
        val baseModel = ENGLISH_MODELS[1]

        ResourceHelper.selectBuiltInVoiceInputModel(context, locale, baseModel)
        TestContextUtils.syncPreferences(context)

        val hasExternal = runBlocking {
            ResourceHelper.findFileForKind(context, locale, FileKind.VoiceInput)?.exists() == true
        }
        val label = formatVoiceInputLabel("Base English", hasExternal)

        assertTrue(isCurrentlySet(context, locale))
        assertFalse(label.contains(importedIndicator))
    }

    @Test
    fun externalVoiceModelIsSetAndShowsImportedTag() {
        val context = newContext()
        val locale = Locale("en")
        installExternalVoiceModel(context, locale)

        val hasExternal = runBlocking {
            ResourceHelper.findFileForKind(context, locale, FileKind.VoiceInput)?.exists() == true
        }
        val label = formatVoiceInputLabel("External Model", hasExternal)

        assertTrue(isCurrentlySet(context, locale))
        assertTrue(label.contains(importedIndicator))
    }
}
