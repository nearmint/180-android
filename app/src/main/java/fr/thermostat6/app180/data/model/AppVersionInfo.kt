package fr.thermostat6.app180.data.model

import com.google.gson.annotations.SerializedName

/**
 * Réponse de `GET 180c/v1/app-version` — `{ ios: {min,current}, android: {min,current} }`.
 *
 * Miroir d'`AppVersionResponse` (`apple/180/AppVersionService.swift:3-11`), qui
 * ne décode que sa propre plateforme : Android lit `android` et ignore `ios`.
 * Champs prouvés par la fixture `app_version.json` (capture anonyme réelle).
 *
 * Nullabilité prudente : une réponse tronquée donne une plateforme absente, que
 * le service traite en **fail-open** — jamais de blocage sur une donnée douteuse.
 */
data class AppVersionInfo(
    @SerializedName("android") val android: PlatformVersion? = null
)

/** Versions d'une plateforme (`AppVersionService.swift:8-11`). */
data class PlatformVersion(
    @SerializedName("min")     val min: String? = null,
    @SerializedName("current") val current: String? = null
)
