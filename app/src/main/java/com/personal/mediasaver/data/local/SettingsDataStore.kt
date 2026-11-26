package com.personal.mediasaver.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.personal.mediasaver.domain.model.AppearanceSettings
import com.personal.mediasaver.domain.model.ProxySettings
import com.personal.mediasaver.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "media_saver_settings"
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val PROXY_ENABLED = booleanPreferencesKey("proxy_enabled")
        val PROXY_URL = stringPreferencesKey("proxy_url")
        val PROXY_ONLY_OVERSEAS = booleanPreferencesKey("proxy_only_overseas")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }

    val proxySettingsFlow: Flow<ProxySettings> = context.settingsDataStore.data.map { prefs ->
        ProxySettings(
            enabled = prefs[Keys.PROXY_ENABLED] ?: false,
            proxyUrl = prefs[Keys.PROXY_URL] ?: "",
            onlyOverseas = prefs[Keys.PROXY_ONLY_OVERSEAS] ?: true,
        )
    }

    val appearanceSettingsFlow: Flow<AppearanceSettings> = context.settingsDataStore.data.map { prefs ->
        AppearanceSettings(
            themeMode = ThemeMode.fromKey(prefs[Keys.THEME_MODE]),
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
        )
    }

    suspend fun getProxySettings(): ProxySettings = proxySettingsFlow.first()

    suspend fun updateProxySettings(
        enabled: Boolean? = null,
        proxyUrl: String? = null,
        onlyOverseas: Boolean? = null,
    ) {
        context.settingsDataStore.edit { prefs ->
            enabled?.let { prefs[Keys.PROXY_ENABLED] = it }
            proxyUrl?.let { prefs[Keys.PROXY_URL] = it.trim() }
            onlyOverseas?.let { prefs[Keys.PROXY_ONLY_OVERSEAS] = it }
        }
    }

    suspend fun updateAppearance(
        themeMode: ThemeMode? = null,
        dynamicColor: Boolean? = null,
    ) {
        context.settingsDataStore.edit { prefs ->
            themeMode?.let { prefs[Keys.THEME_MODE] = it.key }
            dynamicColor?.let { prefs[Keys.DYNAMIC_COLOR] = it }
        }
    }
}
