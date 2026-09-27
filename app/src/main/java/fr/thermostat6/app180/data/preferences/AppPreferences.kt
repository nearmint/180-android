package fr.thermostat6.app180.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appPrefsDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "app_prefs")

/**
 * Préférences globales de l'app : onboarding, apparence, historique de recherche.
 * Initialiser via [init] dans Application.onCreate().
 */
object AppPreferences {

    private val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
    private val APPEARANCE_MODE          = intPreferencesKey("appearance_mode")
    private val SEARCH_HISTORY_JSON      = stringPreferencesKey("search_history_json")
    private val NEWSLETTER_SUBSCRIBED    = booleanPreferencesKey("newsletter_subscribed")
    private val OFFLINE_RECIPES_ENABLED  = booleanPreferencesKey("offline_recipes_enabled")

    private lateinit var dataStore: DataStore<Preferences>

    fun init(context: Context) {
        dataStore = context.applicationContext.appPrefsDataStore
    }

    // ── Onboarding ────────────────────────────────────────────────────────────

    val hasCompletedOnboarding: Flow<Boolean>
        get() = dataStore.data.map { it[HAS_COMPLETED_ONBOARDING] ?: false }

    suspend fun setOnboardingCompleted() {
        dataStore.edit { it[HAS_COMPLETED_ONBOARDING] = true }
    }

    // ── Apparence (0 = auto, 1 = clair, 2 = sombre) ──────────────────────────

    val appearanceMode: Flow<Int>
        get() = dataStore.data.map { it[APPEARANCE_MODE] ?: 0 }

    suspend fun setAppearanceMode(mode: Int) {
        dataStore.edit { it[APPEARANCE_MODE] = mode }
    }

    // ── Newsletter ────────────────────────────────────────────────────────────
    // Le proxy WP n'expose que l'inscription : on mémorise localement si
    // l'utilisateur s'est inscrit depuis l'app, faute de pouvoir interroger Mailchimp.

    val newsletterSubscribed: Flow<Boolean>
        get() = dataStore.data.map { it[NEWSLETTER_SUBSCRIBED] ?: false }

    suspend fun setNewsletterSubscribed(subscribed: Boolean) {
        dataStore.edit { it[NEWSLETTER_SUBSCRIBED] = subscribed }
    }

    // ── Recettes hors ligne ───────────────────────────────────────────────────
    // Toggle de l'écran compte. Remis à `false` à la déconnexion et à la perte
    // d'abonnement — jamais sur une simple absence de réseau.
    // Miroir de la clé `offline_recipes_enabled` (`apple/180/OfflineSyncService.swift`).

    val offlineRecipesEnabled: Flow<Boolean>
        get() = dataStore.data.map { it[OFFLINE_RECIPES_ENABLED] ?: false }

    suspend fun setOfflineRecipesEnabled(enabled: Boolean) {
        dataStore.edit { it[OFFLINE_RECIPES_ENABLED] = enabled }
    }

    // ── Historique de recherche (max 5 items) ─────────────────────────────────

    val searchHistory: Flow<List<String>>
        get() = dataStore.data.map { prefs ->
            parseHistoryJson(prefs[SEARCH_HISTORY_JSON] ?: "[]")
        }

    suspend fun addSearchQuery(query: String) {
        dataStore.edit { prefs ->
            val current = parseHistoryJson(prefs[SEARCH_HISTORY_JSON] ?: "[]").toMutableList()
            current.remove(query)         // évite les doublons
            current.add(0, query)         // insertion en tête
            prefs[SEARCH_HISTORY_JSON] = serializeHistory(current.take(5))
        }
    }

    suspend fun removeSearchQuery(query: String) {
        dataStore.edit { prefs ->
            val current = parseHistoryJson(prefs[SEARCH_HISTORY_JSON] ?: "[]").toMutableList()
            current.remove(query)
            prefs[SEARCH_HISTORY_JSON] = serializeHistory(current)
        }
    }

    suspend fun clearSearchHistory() {
        dataStore.edit { it[SEARCH_HISTORY_JSON] = "[]" }
    }

    // ── Sérialisation JSON minimaliste (sans dépendance externe) ─────────────

    private fun parseHistoryJson(json: String): List<String> = try {
        val inner = json.trim().removePrefix("[").removeSuffix("]")
        if (inner.isBlank()) emptyList()
        else inner.split(",").map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() }
    } catch (_: Exception) {
        emptyList()
    }

    private fun serializeHistory(items: List<String>): String =
        "[${items.joinToString(",") { "\"${it.replace("\\", "\\\\").replace("\"", "\\\"")}\"" }}]"
}
