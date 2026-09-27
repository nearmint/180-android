package fr.thermostat6.app180.data.network

/**
 * Identifiants Play Store. **Source unique** de la fiche de l'application.
 *
 * Miroir d'`AppStoreInfo` (`apple/180/APIConfig.swift:103-122`) : tant que
 * l'application n'est pas publiée, les fonctions qui en dépendent — invitation
 * à mettre à jour, « Partager l'app », « Notez l'app »
 * (`ui/screen/AccountScreen.kt`, TODO) — sont **masquées**, jamais
 * pointées vers un lien factice.
 *
 * 👉 Renseigner [PACKAGE_NAME] à la publication.
 */
object PlayStoreInfo {

    /**
     * Nom de paquet de la fiche Play Store publiée.
     *
     * `null` tant que la fiche n'existe pas : l'URL construite mènerait à une
     * page d'erreur, ce qui est particulièrement dommageable sur un écran
     * bloquant (`ui/screen/ForceUpdateScreen.kt`).
     */
    val PACKAGE_NAME: String? = null

    /** `true` lorsqu'une fiche réelle est configurée. */
    val isConfigured: Boolean get() = PACKAGE_NAME != null

    /** Fiche Play Store (mise à jour / partage). `null` si non configurée. */
    val storeUrl: String?
        get() = PACKAGE_NAME?.let { "https://play.google.com/store/apps/details?id=$it" }
}
