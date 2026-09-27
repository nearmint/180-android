package fr.thermostat6.app180.data.network

import kotlinx.coroutines.delay

/**
 * Retry borné sur les lectures, miroir du `getData(_:authenticated:)` iOS
 * (`apple/180/APIService.swift:30-56`) et de la boucle de `HomeService`
 * (`apple/180/HomeService.swift:146-166`).
 *
 * **2 tentatives supplémentaires** (3 essais au total), à backoff croissant de
 * 1 s puis 3 s (`APIService.swift:32`), et uniquement sur erreur transitoire.
 *
 * Portée volontairement identique à celle de l'iOS, ni plus ni moins :
 * - **GET uniquement.** Les écritures (login, refresh, ajout/retrait de favori,
 *   sync) passent côté Swift par `send(_:)`, qui ne retente rien
 *   (`APIService.swift:58-65`) : rejouer une écriture non idempotente peut
 *   dupliquer son effet.
 * - **Erreurs transitoires uniquement** : hors-ligne, délai dépassé, 5xx.
 *   Un 4xx est un refus durable ([ApiError.isTransient]).
 * - **Jamais sur une redirection** : le garde-fou du LOT-01 lève une
 *   `IOException` classée [ApiError.ServerError], non transitoire — l'erreur
 *   remonte donc dès le premier essai, comme voulu.
 */
object RetryPolicy {

    /** Délais entre tentatives, en millisecondes (`APIService.swift:32`). */
    val BACKOFF_MS = longArrayOf(1_000L, 3_000L)

    /** Nombre maximum de tentatives supplémentaires. */
    val MAX_RETRIES: Int get() = BACKOFF_MS.size

    /**
     * Décide si une tentative doit être rejouée.
     *
     * @param isRead requête idempotente (GET) — seules celles-ci sont rejouées.
     * @param attempt index de la tentative qui vient d'échouer (0 = la première).
     * @param error erreur normalisée de cette tentative.
     */
    fun shouldRetry(isRead: Boolean, attempt: Int, error: ApiError): Boolean =
        isRead && attempt < MAX_RETRIES && error.isTransient

    /**
     * Exécute [block] avec le retry borné et renvoie son résultat, ou lève la
     * dernière [ApiError] rencontrée.
     *
     * @param isRead `false` pour toute écriture : le bloc n'est alors jamais rejoué.
     */
    suspend fun <T> withRetry(isRead: Boolean = true, block: suspend () -> T): T {
        var lastError: ApiError = ApiError.ServerError
        for (attempt in 0..MAX_RETRIES) {
            try {
                return block()
            } catch (throwable: Throwable) {
                val error = ApiError.from(throwable)
                lastError = error
                if (!shouldRetry(isRead, attempt, error)) throw error
                delay(BACKOFF_MS[attempt])
            }
        }
        throw lastError
    }
}
