package fr.thermostat6.app180.data.offline

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path.Companion.toOkioPath

/**
 * Cran « store hors ligne » de la chaîne de chargement d'images.
 *
 * Enregistré dans l'`ImageLoader` applicatif, il s'intercale ainsi :
 *
 * ```
 * cache mémoire  →  STORE HORS LIGNE  →  cache disque  →  réseau
 * ```
 *
 * L'ordre n'est pas un détail. Le cache mémoire est consulté avant tout fetcher
 * par le moteur lui-même. Ce fetcher est enregistré **avant** celui du réseau,
 * lequel porte le cache disque : un visuel téléchargé explicitement par
 * l'utilisateur prime donc sur une copie opportuniste qui, elle, vit sous
 * `cacheDir` et peut avoir été évincée.
 *
 * On rend le **chemin** du fichier et non ses octets : le décodeur
 * sous-échantillonne alors en lisant le fichier, exactement comme pour une
 * entrée de cache disque. Le rendu hors ligne emprunte ainsi le même pipeline de
 * redimensionnement proportionnel que le rendu en ligne — aucun composant
 * parallèle, aucun recadrage introduit.
 *
 * Rendre `null` depuis [fetch] délègue au fetcher suivant : une image absente du
 * store retombe naturellement sur le cache disque puis le réseau.
 *
 * Miroir de l'étape 2 de `loadImage()` (`apple/180/CachedAsyncImage.swift:57-79`).
 */
class OfflineImageFetcher(
    private val uri: Uri,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val file = OfflineStore.sharedOrNull?.imageFileOrNull(uri.toString())
            ?: return@withContext null

        SourceFetchResult(
            source     = ImageSource(
                file       = file.toOkioPath(),
                fileSystem = options.fileSystem
            ),
            mimeType   = null,
            dataSource = DataSource.DISK
        )
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            // Seules les URL distantes ont pu être téléchargées ; tout le reste
            // (ressources, fichiers, `data:`) part directement au fetcher suivant.
            val scheme = data.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return null
            return OfflineImageFetcher(data, options)
        }
    }
}
