package org.futo.inputmethod.latin.uix

import kotlinx.coroutines.runBlocking
import org.futo.inputmethod.latin.uix.FileKind
import org.futo.inputmethod.latin.uix.TestContextUtils.FakeContext
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.setSetting
import org.futo.inputmethod.latin.uix.preferenceKeyFor
import org.junit.runner.RunWith
import org.futo.voiceinput.shared.BUILTIN_ENGLISH_MODEL
import org.futo.voiceinput.shared.ENGLISH_MODELS
import org.futo.voiceinput.shared.types.ModelFileFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class ResourceHelperVoiceInputTest {
    private val tempDirs = mutableListOf<File>()

    private fun newContext(): FakeContext {
        val dir = createTempDir(prefix = "voiceinput-test")
        tempDirs += dir
        val context = FakeContext(dir)
        TestContextUtils.installTestDataStore(context, File(dir, "prefs.preferences_pb"))
        return context
    }

    private fun installExternalVoiceModel(context: FakeContext, locale: Locale, fileName: String = "external_model.bin"): File {
        val externalFile = File(context.getExternalFilesDir(null), fileName)
        externalFile.writeText("dummy")
        runBlocking {
            context.setSetting(FileKind.VoiceInput.preferenceKeyFor(locale.toString()), fileName)
        }
        TestContextUtils.syncPreferences(context)
        return externalFile
    }

    private fun ensureBaseEnglishExists(context: FakeContext) {
        // Base English is a ModelDownloadable; mark it as "downloaded" by creating the file.
        val baseFileName = "base_en_acft_q8_0.bin"
        File(context.filesDir, baseFileName).writeText("dummy")
    }

    @After
    fun tearDown() {
        tempDirs.forEach { it.deleteRecursively() }
    }

    @Test
    fun defaultBuiltInIsTinyWhisperAndNotCustom() {
        val context = newContext()
        val locale = Locale("en")

        val preferred = ResourceHelper.getPreferredBuiltInVoiceInputModel(context, locale)
        val chosen = ResourceHelper.tryFindingVoiceInputModelForLocale(context, locale)
        val nonDefault = ResourceHelper.hasNonDefaultBuiltInVoiceModel(context, locale)

        assertSame(BUILTIN_ENGLISH_MODEL, preferred)
        assertSame(BUILTIN_ENGLISH_MODEL, chosen)
        assertFalse(nonDefault)
    }

    @Test
    fun selectingNonDefaultBuiltInMarksAsCustomBuiltIn() {
        val context = newContext()
        val locale = Locale("en")
        ensureBaseEnglishExists(context)
        val baseModel = ENGLISH_MODELS[1] // base_en ModelDownloadable

        ResourceHelper.selectBuiltInVoiceInputModel(context, locale, baseModel)
        TestContextUtils.syncPreferences(context)

        val preferred = ResourceHelper.getPreferredBuiltInVoiceInputModel(context, locale)
        val chosen = ResourceHelper.tryFindingVoiceInputModelForLocale(context, locale)

        assertSame(baseModel, preferred)
        assertSame(baseModel, chosen)
        assertTrue(ResourceHelper.hasNonDefaultBuiltInVoiceModel(context, locale))
    }

    @Test
    fun externalVoiceModelOverridesBuiltInsButDoesNotCountAsNonDefaultBuiltIn() {
        val context = newContext()
        val locale = Locale("en")
        installExternalVoiceModel(context, locale)

        val chosen = ResourceHelper.tryFindingVoiceInputModelForLocale(context, locale)
        assertTrue(chosen is ModelFileFile)
        assertFalse(ResourceHelper.hasNonDefaultBuiltInVoiceModel(context, locale))
    }

    @Test
    fun selectingBuiltInClearsExternalFileAndSetsPreference() {
        val context = newContext()
        val locale = Locale("en")
        installExternalVoiceModel(context, locale)
        ensureBaseEnglishExists(context)
        val baseModel = ENGLISH_MODELS[1]

        ResourceHelper.selectBuiltInVoiceInputModel(context, locale, baseModel)
        TestContextUtils.syncPreferences(context)

        val externalPref = context.getSetting(FileKind.VoiceInput.preferenceKeyFor(locale.toString()), "")
        val chosen = ResourceHelper.tryFindingVoiceInputModelForLocale(context, locale)

        assertEquals("", externalPref)
        assertSame(baseModel, chosen)
        assertTrue(ResourceHelper.hasNonDefaultBuiltInVoiceModel(context, locale))
    }

    @Test
    fun deleteResourceForLanguageClearsExternalAndBuiltInPreference() {
        val context = newContext()
        val locale = Locale("en")
        installExternalVoiceModel(context, locale)
        ensureBaseEnglishExists(context)
        val baseModel = ENGLISH_MODELS[1]
        ResourceHelper.setPreferredBuiltInVoiceInputModel(context, locale, baseModel)
        TestContextUtils.syncPreferences(context)

        ResourceHelper.deleteResourceForLanguage(context, FileKind.VoiceInput, locale)
        TestContextUtils.syncPreferences(context)

        val externalPref = context.getSetting(FileKind.VoiceInput.preferenceKeyFor(locale.toString()), "")
        val preferred = ResourceHelper.getPreferredBuiltInVoiceInputModel(context, locale)

        assertEquals("", externalPref)
        assertSame(BUILTIN_ENGLISH_MODEL, preferred)
        assertFalse(ResourceHelper.hasNonDefaultBuiltInVoiceModel(context, locale))
    }
}
