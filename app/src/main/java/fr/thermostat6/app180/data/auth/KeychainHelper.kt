package fr.thermostat6.app180.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stockage sécurisé du JWT — équivalent Android du Keychain iOS.
 * Utilise EncryptedSharedPreferences (AES256-GCM) via AndroidX Security.
 */
class KeychainHelper(context: Context) {

    companion object {
        private const val ENCRYPTED_PREFS_FILE = "app180_keychain"
        const val KEY_JWT = "jwt_token"

        /** Ancien fichier SharedPreferences non chiffré (migration). */
        private const val LEGACY_PREFS_FILE = "app180_auth_legacy"
    }

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        ENCRYPTED_PREFS_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /** Référence aux prefs non chiffrées pour la migration. */
    private val legacyPrefs: SharedPreferences =
        context.getSharedPreferences(LEGACY_PREFS_FILE, Context.MODE_PRIVATE)

    // ── API publique ──────────────────────────────────────────────────────────

    fun saveToken(token: String) {
        encryptedPrefs.edit().putString(KEY_JWT, token).apply()
    }

    fun getToken(): String? = encryptedPrefs.getString(KEY_JWT, null)

    fun deleteToken() {
        encryptedPrefs.edit().remove(KEY_JWT).apply()
    }

    /**
     * Migration depuis l'ancien stockage non chiffré.
     * Si un token JWT est présent dans legacyPrefs, il est transféré dans
     * EncryptedSharedPreferences puis supprimé de l'ancienne source.
     * Retourne true si une migration a eu lieu.
     */
    fun migrateFromLegacyIfNeeded(): Boolean {
        val legacyToken = legacyPrefs.getString(KEY_JWT, null) ?: return false
        saveToken(legacyToken)
        legacyPrefs.edit().remove(KEY_JWT).apply()
        return true
    }
}
