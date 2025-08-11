package com.example.noiceapp.settings

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private val KEY_OFFSET_DB = doublePreferencesKey("offset_db")

    val offsetDbFlow: Flow<Double> = context.dataStore.data.map { prefs ->
        prefs[KEY_OFFSET_DB] ?: 90.0
    }

    suspend fun setOffsetDb(value: Double) {
        context.dataStore.edit { it[KEY_OFFSET_DB] = value }
    }
}


