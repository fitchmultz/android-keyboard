package org.futo.inputmethod.latin.uix

import android.content.ContextWrapper
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * Small helpers to provide a minimal Context and DataStore for JVM tests.
 * These are intentionally narrow and only cover the API surface used by ResourceHelper.
 */
object TestContextUtils {
    class FakeContext(private val baseDir: File) : ContextWrapper(ApplicationProvider.getApplicationContext()) {
        override fun getExternalFilesDir(type: String?): File = baseDir
        override fun getFilesDir(): File = baseDir
    }

    /**
     * Injects an in-memory DataStore into the Settings.kt static state so Context.getSetting/setSetting
     * operate on predictable test data.
     */
    fun installTestDataStore(context: Context, file: File = File.createTempFile("prefs", ".pb")): DataStore<Preferences> {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }

        val settingsClass = Class.forName("org.futo.inputmethod.latin.uix.SettingsKt")
        val fieldNames = settingsClass.declaredFields.map { it.name }
        val unlocked = try {
            settingsClass.getDeclaredField("unlockedDataStore")
        } catch (e: NoSuchFieldException) {
            throw IllegalStateException("unlockedDataStore not found; fields=$fieldNames", e)
        }
        unlocked.isAccessible = true
        unlocked.set(null, dataStore)

        DataStoreHelper.init(context)
        syncPreferences(context)
        return dataStore
    }

    fun syncPreferences(context: Context) {
        val prefs = runBlocking { context.dataStore.data.first() }
        val currentPrefsField = DataStoreHelper::class.java.getDeclaredField("currentPreferences")
        currentPrefsField.isAccessible = true
        currentPrefsField.set(null, prefs)
    }
}
