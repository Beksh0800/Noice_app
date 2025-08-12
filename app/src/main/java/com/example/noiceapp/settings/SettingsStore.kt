package com.example.noiceapp.settings

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private val KEY_OFFSET_DB = doublePreferencesKey("offset_db")
    private val KEY_STUDENT_ID = stringPreferencesKey("student_id")
    private val KEY_UPLOAD_URL = stringPreferencesKey("upload_url")
    private val KEY_API_KEY = stringPreferencesKey("api_key")

    val offsetDbFlow: Flow<Double> = context.dataStore.data.map { prefs ->
        prefs[KEY_OFFSET_DB] ?: 90.0
    }

    suspend fun setOffsetDb(value: Double) {
        context.dataStore.edit { it[KEY_OFFSET_DB] = value }
    }

    val studentIdFlow: Flow<String> = context.dataStore.data.map { it[KEY_STUDENT_ID] ?: "" }
    val uploadUrlFlow: Flow<String> = context.dataStore.data.map { it[KEY_UPLOAD_URL] ?: "" }
    val apiKeyFlow: Flow<String> = context.dataStore.data.map { it[KEY_API_KEY] ?: "SCHOOL_SHARED_KEY" }

    suspend fun setStudentId(value: String) { context.dataStore.edit { it[KEY_STUDENT_ID] = value } }
    suspend fun setUploadUrl(value: String) { context.dataStore.edit { it[KEY_UPLOAD_URL] = value } }
    suspend fun setApiKey(value: String) { context.dataStore.edit { it[KEY_API_KEY] = value } }
}


