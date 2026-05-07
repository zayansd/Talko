package com.talko.app.core.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "talko_session")

/**
 * Persistent key-value store for user session preferences.
 * Uses Jetpack DataStore (Preferences) — no sensitive secrets are stored here;
 * Firebase handles auth tokens internally in its own encrypted storage.
 */
@Singleton
class SessionDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val DARK_MODE_ENABLED      = booleanPreferencesKey("dark_mode_enabled")
        val LAST_ACTIVE_CHAT_ID    = stringPreferencesKey("last_active_chat_id")
        val ONBOARDING_DONE        = booleanPreferencesKey("onboarding_done")
    }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.NOTIFICATIONS_ENABLED] ?: true }

    val darkModeEnabled: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.DARK_MODE_ENABLED] ?: false }

    val lastActiveChatId: Flow<String?> = context.dataStore.data
        .map { prefs -> prefs[Keys.LAST_ACTIVE_CHAT_ID] }

    val onboardingDone: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.ONBOARDING_DONE] ?: false }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setDarkModeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DARK_MODE_ENABLED] = enabled }
    }

    suspend fun setLastActiveChatId(chatId: String) {
        context.dataStore.edit { it[Keys.LAST_ACTIVE_CHAT_ID] = chatId }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    suspend fun clearSession() {
        context.dataStore.edit { it.clear() }
    }
}
