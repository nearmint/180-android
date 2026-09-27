package fr.thermostat6.app180.data.offline

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import fr.thermostat6.app180.data.model.Recipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Base64
import java.util.Locale

/**
 * Métadonnées d'une fiche téléchargée, tenues dans l'index.
 *
 * [modified] est le jeton de fraîcheur : la réconciliation compare la valeur
 * serveur à celle-ci pour décider d'un re-téléchargement. `null` (fiche écrite
 * par une version antérieure, ou serveur muet) ⇒ re-téléchargement au prochain
 * cycle, jamais de contenu périmé conservé par défaut.
 *
 * Miroir de `OfflineRecipeMeta` (`apple/180/OfflineStore.swift`).
 */
data class OfflineRecipeMeta(
    @SerializedName("id")           val id: Int,
    /** Horodatage local du téléchargement, en millisecondes depuis l'époque. */
    @SerializedName("downloadedAt") val downloadedAt: Long,
    @SerializedName("modified")     val modified: String? = null,
    @SerializedName("imageCount")   val imageCount: Int = 0
)

/**
 * Persistance des fiches consultables hors ligne.
 *
 * ## Emplacement
 *
 * `filesDir/offline_recipes/` — **jamais** `cacheDir`. Les deux caches existants
 * ([fr.thermostat6.app180.data.service.DiskCache] et le répertoire d'images de
 * Coil) vivent sous `cacheDir`, que le système évince librement sous pression
 * disque : parfait pour un cache, fatal pour un carnet hors ligne dont
 * l'utilisateur a explicitement demandé le téléchargement.
 *
 * L'exclusion de sauvegarde est **déclarative** côté Android, contrairement à
 * l'attribut d'inode iOS : voir `res/xml/data_extraction_rules.xml` et
 * `res/xml/backup_rules.xml`. Tout ici est re-téléchargeable.
 *
 * ```
 * offline_recipes/
 *   index.json                  ← métadonnées (id, date, modified, nb images)
 *   13400072/recipe.json        ← Recipe encodée
 *   13400072/images/<clé>.webp  ← octets d'origine, jamais recompressés
 * ```
 *
 * ## Images
 *
 * Les octets sont stockés **tels que servis** (webp/jpeg d'origine, aucune
 * recompression, aucun recadrage). Le nom de fichier dérive de l'URL par la même
 * transformation base64 url-safe que [fr.thermostat6.app180.data.service.DiskCache],
 * ce qui rend la lecture possible sans consulter l'index.
 *
 * ## Concurrence
 *
 * Les écritures sont sérialisées par un [Mutex] et les E/S renvoyées sur
 * [Dispatchers.IO]. L'index est doublé en mémoire dans une référence remplacée
 * atomiquement : [has] et [metadata] sont donc **synchrones et sans E/S**, ce
 * qu'exige le rendu d'une liste de cartes.
 *
 * Miroir de `OfflineStore` (`apple/180/OfflineStore.swift`).
 *
 * @param root racine d'accueil. Les tests y injectent un dossier temporaire.
 */
class OfflineStore(private val root: File) {

    private val gson = Gson()
    private val indexFile = File(root, INDEX_FILENAME)
    private val ioMutex = Mutex()

    /** Index en mémoire, reflet de `index.json`. Remplacé atomiquement. */
    @Volatile
    private var index: Map<Int, OfflineRecipeMeta> = emptyMap()

    init {
        createRootIfNeeded()
        index = readIndex()
    }

    // ── Arborescence ─────────────────────────────────────────────────────────

    private fun createRootIfNeeded() {
        runCatching { if (!root.exists()) root.mkdirs() }
    }

    private fun directoryFor(id: Int) = File(root, id.toString())

    private fun recipeFileFor(id: Int) = File(directoryFor(id), RECIPE_FILENAME)

    private fun imagesDirectoryFor(id: Int) = File(directoryFor(id), IMAGES_DIRECTORY)

    // ── Index ────────────────────────────────────────────────────────────────

    private fun readIndex(): Map<Int, OfflineRecipeMeta> = runCatching {
        if (!indexFile.exists()) return emptyMap()
        val type = object : TypeToken<List<OfflineRecipeMeta>>() {}.type
        val entries: List<OfflineRecipeMeta> = gson.fromJson(indexFile.readText(), type)
            ?: return emptyMap()
        entries.associateBy { it.id }
    }.getOrElse { emptyMap() }

    /** Réécrit `index.json` depuis l'index mémoire. À appeler sous [ioMutex]. */
    private fun persistIndex() {
        runCatching {
            createRootIfNeeded()
            indexFile.writeText(gson.toJson(index.values.sortedBy { it.id }))
        }
    }

    // ── Lecture synchrone (index mémoire, aucune E/S) ────────────────────────

    /** `true` si la fiche est disponible hors ligne. */
    fun has(id: Int): Boolean = index.containsKey(id)

    fun metadata(id: Int): OfflineRecipeMeta? = index[id]

    /** Toutes les fiches présentes localement (base de la réconciliation). */
    fun allMetadata(): List<OfflineRecipeMeta> = index.values.sortedBy { it.id }

    /** IDs présents localement. */
    fun storedIds(): Set<Int> = index.keys.toSet()

    // ── Écriture ─────────────────────────────────────────────────────────────

    /**
     * Écrit une fiche et ses visuels. Les octets d'image sont enregistrés tels
     * quels ; une image manquante n'empêche pas l'écriture de la fiche.
     *
     * L'entrée d'index n'est ajoutée qu'**après** l'écriture effective du JSON :
     * une interruption ne laisse jamais l'index annoncer une fiche absente.
     */
    suspend fun save(
        recipe: Recipe,
        images: Map<String, ByteArray> = emptyMap(),
        nowMillis: Long = System.currentTimeMillis()
    ): Unit = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val imagesDir = imagesDirectoryFor(recipe.id)
            val written = runCatching {
                createRootIfNeeded()
                imagesDir.mkdirs()
                recipeFileFor(recipe.id).writeText(gson.toJson(recipe))
            }.map {
                var count = 0
                images.forEach { (url, bytes) ->
                    if (bytes.isEmpty()) return@forEach
                    runCatching {
                        File(imagesDir, imageFilename(url)).writeBytes(bytes)
                        count++
                    }
                }
                count
            }.getOrElse { return@withLock }

            index = index + (recipe.id to OfflineRecipeMeta(
                id           = recipe.id,
                downloadedAt = nowMillis,
                modified     = recipe.modified,
                imageCount   = written
            ))
            persistIndex()
        }
    }

    // ── Lecture ──────────────────────────────────────────────────────────────

    /** Relit une fiche depuis le disque, `null` si absente ou illisible. */
    suspend fun load(id: Int): Recipe? = withContext(Dispatchers.IO) {
        runCatching {
            val file = recipeFileFor(id)
            if (!file.exists()) return@withContext null
            gson.fromJson(file.readText(), Recipe::class.java)
        }.getOrNull()
    }

    /**
     * Toutes les fiches disponibles hors ligne, **dans l'ordre demandé**. Les IDs
     * sans fiche locale sont simplement absents du résultat.
     */
    suspend fun load(ids: List<Int>): List<Recipe> = ids.mapNotNull { load(it) }

    /**
     * Octets d'une image téléchargée, quelle que soit la fiche qui la porte.
     *
     * La recherche est bornée aux fiches de l'index : une URL inconnue coûte un
     * parcours de dossiers, jamais une lecture réseau.
     */
    suspend fun imageData(url: String): ByteArray? = withContext(Dispatchers.IO) {
        val ids = storedIds()
        if (ids.isEmpty()) return@withContext null
        val filename = imageFilename(url)
        ids.forEach { id ->
            val candidate = File(imagesDirectoryFor(id), filename)
            val bytes = runCatching {
                if (candidate.exists()) candidate.readBytes() else null
            }.getOrNull()
            if (bytes != null && bytes.isNotEmpty()) return@withContext bytes
        }
        null
    }

    /**
     * Fichier local d'une image téléchargée, `null` si absente.
     *
     * Rendre le **chemin** plutôt que les octets permet au décodeur d'images de
     * sous-échantillonner en lisant le fichier, exactement comme il le fait pour
     * une entrée de cache disque. C'est ce qui garantit un rendu strictement
     * identique en ligne et hors ligne, avec le même pipeline de redimensionnement.
     *
     * Les contrôles d'existence sont bornés aux fiches de l'index : une URL
     * inconnue coûte un parcours de dossiers, jamais une lecture réseau.
     */
    fun imageFileOrNull(url: String): File? {
        val ids = storedIds()
        if (ids.isEmpty()) return null
        val filename = imageFilename(url)
        return ids.asSequence()
            .map { File(imagesDirectoryFor(it), filename) }
            .firstOrNull { candidate -> runCatching { candidate.isFile && candidate.length() > 0 }.getOrDefault(false) }
    }

    // ── Suppression ──────────────────────────────────────────────────────────

    /** Supprime une fiche et ses visuels. */
    suspend fun delete(id: Int): Unit = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            runCatching { directoryFor(id).deleteRecursively() }
            index = index - id
            persistIndex()
        }
    }

    /** Purge intégrale (toggle OFF, « Vider le cache », déconnexion). */
    suspend fun deleteAll(): Unit = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            runCatching { root.deleteRecursively() }
            index = emptyMap()
            createRootIfNeeded()
        }
    }

    // ── Taille occupée ───────────────────────────────────────────────────────

    /**
     * Poids total sur disque, index compris.
     *
     * **Divergence assumée avec l'iOS**, qui lit la taille *allouée* (blocs
     * réellement occupés). La JVM n'expose pas cette information de façon
     * portable : on somme les tailles logiques. L'écart est marginal et joue
     * toujours dans le sens de la sous-estimation, jamais de l'inverse.
     */
    suspend fun totalSizeBytes(): Long = withContext(Dispatchers.IO) {
        runCatching {
            root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }.getOrElse { 0L }
    }

    companion object {
        /** Nom du répertoire sous `filesDir`, repris dans les règles de backup. */
        const val DIRECTORY_NAME = "offline_recipes"
        const val INDEX_FILENAME = "index.json"
        const val RECIPE_FILENAME = "recipe.json"
        const val IMAGES_DIRECTORY = "images"

        /**
         * Nom de fichier sûr dérivé d'une URL d'image (base64 url-safe),
         * extension d'origine préservée pour la lisibilité du dossier.
         *
         * Même transformation que `DiskCache.fileFor` et que l'iOS
         * (`OfflineStore.swift`, `/`→`_`, `+`→`-`, `=` retiré) : l'encodeur
         * url-safe de la JVM produit directement `-` et `_`.
         */
        fun imageFilename(url: String): String {
            val safe = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(url.toByteArray())
            val extension = url.substringBefore('?').substringAfterLast('/', "")
                .substringAfterLast('.', "")
                .takeIf { it.isNotEmpty() && it.length <= 5 && it.all(Char::isLetterOrDigit) }
                ?: "img"
            return "$safe.$extension"
        }

        /** Taille lisible (« 12,4 Mo »), miroir de `OfflineStore.formatted`. */
        fun formatted(bytes: Long): String {
            if (bytes < 1_000) return "$bytes o"
            val units = listOf("ko", "Mo", "Go")
            var value = bytes.toDouble() / 1_000.0
            var unitIndex = 0
            while (value >= 1_000.0 && unitIndex < units.lastIndex) {
                value /= 1_000.0
                unitIndex++
            }
            return String.format(Locale.FRANCE, "%.1f %s", value, units[unitIndex])
        }

        @Volatile
        private var instance: OfflineStore? = null

        /** À appeler dans `Application.onCreate()` avec `filesDir`. */
        fun init(filesDirectory: File) {
            if (instance == null) {
                instance = OfflineStore(File(filesDirectory, DIRECTORY_NAME))
            }
        }

        /**
         * Instance applicative, `null` tant qu'[init] n'a pas été appelé.
         *
         * Volontairement nullable : la chaîne de chargement d'images peut être
         * sollicitée depuis un aperçu Compose ou un test d'UI où l'`Application`
         * n'est pas celle de production. Un visuel manquant vaut mieux qu'un
         * plantage.
         */
        val sharedOrNull: OfflineStore? get() = instance
    }
}
