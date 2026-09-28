plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

// Paramètres de signature de release. Ils viennent de l'environnement, jamais
// du dépôt : en CI ils sont posés par .github/workflows/release.yml à partir des
// secrets GitHub, en local ils sont simplement absents. `keystorePath` nul est
// donc un cas nominal, pas une erreur — voir `signingConfigs` plus bas.
val keystorePath: String? = providers.environmentVariable("KEYSTORE_PATH").orNull

android {
    namespace = "fr.thermostat6.app180"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "fr.thermostat6.app180"
        minSdk = 26
        targetSdk = 36
        // Politique de version : `versionName` est la version marketing, commune
        // aux deux plateformes — l'iOS est à `MARKETING_VERSION = 1.0.4`
        // (`apple/180.xcodeproj/project.pbxproj:455`). Elle reste écrite ici, à
        // la main, à chaque jalon produit.
        //
        // `versionCode` est l'entier strictement croissant exigé par Play. Il
        // n'est plus tenu à la main : le workflow Release injecte
        // `VERSION_CODE = github.run_number + 100`, qui ne recule jamais. En local
        // (variable absente) on retombe sur 1 — suffisant pour un debug ou un
        // bundle d'essai, jamais pour une soumission.
        versionCode = providers.environmentVariable("VERSION_CODE").orNull?.toInt() ?: 1
        versionName = "1.0.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Origine canonique du backend. TOUJOURS `www` : l'apex `180c.fr` répond
        // en 301 vers `www`, et un 301 dégrade un POST en GET (corps perdu).
        // Surchargeable par variante de build le jour où un env local existera.
        buildConfigField("String", "API_ORIGIN", "\"https://www.180c.fr\"")

        // Adresses ouvertes par l'écran Compte (mailto « rédaction » / « support »).
        // Valeurs publiques par défaut ; surcharge par variable d'environnement
        // (en CI : secrets de dépôt GitHub, cf. .github/workflows/release.yml).
        // Une surcharge change le BuildConfig : Gradle recompile en conséquence.
        val editorialEmail = providers.environmentVariable("CONTACT_EDITORIAL_EMAIL").orNull ?: "redaction@180c.fr"
        val supportEmail = providers.environmentVariable("CONTACT_SUPPORT_EMAIL").orNull ?: "contact@180c.fr"
        buildConfigField("String", "CONTACT_EDITORIAL_EMAIL", "\"$editorialEmail\"")
        buildConfigField("String", "CONTACT_SUPPORT_EMAIL", "\"$supportEmail\"")
    }

    signingConfigs {
        // Créée seulement si l'environnement fournit un keystore. Sans elle,
        // `bundleRelease` produit un .aab non signé : le build local reste
        // possible, et une release non signée échoue à l'upload Play plutôt que
        // de partir avec une signature de debug.
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = providers.environmentVariable("KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            // `findByName` et non `getByName` : null quand l'environnement de
            // signature est absent, ce qui laisse le variant non signé.
            signingConfig = signingConfigs.findByName("release")

            // R8 : suppression du code et des ressources inutilisés, obfuscation.
            // Les règles de conservation vivent dans `proguard-rules.pro` — tout
            // ce qui est résolu par réflexion (Gson, Retrofit, Tink, OneSignal)
            // y est explicitement préservé.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // Bascule téléphone / tablette : seule source des seuils de largeur.
    implementation(libs.androidx.compose.material3.window.size)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // DataStore Preferences
    implementation(libs.androidx.datastore.preferences)

    // Image loading (Coil 3)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Navigation Compose
    implementation(libs.androidx.navigation.compose)

    // ViewModel Compose + collectAsStateWithLifecycle
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Material Icons (inclut core + extended)
    implementation(libs.androidx.compose.material.icons.extended)

    // Analytics Firebase — le catalogue d'événements est miroité de l'iOS
    // (apple/180/AnalyticsService.swift). Config par app/google-services.json,
    // gitignoré : voir google-services.sample.json.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)

    // Push OneSignal — la config FCM vit dans le dashboard, pas dans l'app
    // (aucun google-services.json, aucun plugin Gradle OneSignal).
    implementation(libs.onesignal)

    // Custom Tabs — navigateur intégré pour les liens publics du site
    implementation(libs.androidx.browser)

    // Stockage sécurisé JWT (EncryptedSharedPreferences)
    implementation(libs.androidx.security.crypto)

    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}