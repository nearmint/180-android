# ══════════════════════════════════════════════════════════════════════════════
# Règles R8 — app 180°C
#
# Principe retenu : en cas de doute, conserver. Une classe sérialisée obfusquée
# ne provoque pas d'erreur de compilation — elle produit un champ `null` ou un
# plantage au premier appel réseau, en production. Le gain de quelques kilo-
# octets ne vaut pas ce risque.
# ══════════════════════════════════════════════════════════════════════════════


# ── Attributs indispensables à la réflexion ───────────────────────────────────
# `Signature` porte les types génériques : sans lui, Gson lit `List<Recipe>`
# comme un `List` brut et rend des `LinkedTreeMap` au lieu de recettes.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepattributes Exceptions

# Traces d'incident lisibles : le mapping reste dans `build/outputs/mapping/`.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile


# ── Modèles sérialisés (Gson) ─────────────────────────────────────────────────
# Les noms de champs sont la clé JSON dès qu'il n'y a pas de `@SerializedName` :
# les obfusquer casse silencieusement le décodage.
-keep class fr.thermostat6.app180.data.model.** { *; }

# Écrits sur disque par `DiskCache` sans annotation : mêmes contraintes.
# `Envelope` est imbriquée dans l'objet `DiskCache` — d'où le `$`. Sans cette
# règle, ses champs `storedAt`/`value` deviennent `a`/`b`, et un instantané écrit
# par une version de l'app devient illisible par la suivante, dont R8 aura pu
# choisir d'autres noms.
-keep class fr.thermostat6.app180.data.service.HomeSnapshot { *; }
-keep class fr.thermostat6.app180.data.service.HomeContent { *; }
-keep class fr.thermostat6.app180.data.service.DiskCache$Envelope { *; }

# Adaptateurs déclarés à la main (`ApiClient.kt:207`).
-keep class * implements com.google.gson.JsonDeserializer { *; }
-keep class * extends com.google.gson.TypeAdapter { *; }

# `TypeToken` anonymes (`object : TypeToken<List<AppNotification>>() {}`).
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# Les énumérations sérialisées sont résolues par réflexion sur `valueOf`.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}


# ── Retrofit ──────────────────────────────────────────────────────────────────
# Les interfaces d'API sont implémentées à l'exécution à partir de leurs
# annotations : elles doivent survivre avec leurs méthodes.
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn retrofit2.**


# ── OkHttp / Okio ─────────────────────────────────────────────────────────────
# Fournisseurs TLS optionnels, absents à la compilation : avertissements seuls.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**


# ── Coil 3 ────────────────────────────────────────────────────────────────────
-dontwarn coil3.**


# ── Stockage chiffré (androidx.security → Tink) ───────────────────────────────
# Tink instancie ses primitives par réflexion depuis un registre de noms.
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**


# ── Firebase Analytics ────────────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**


# ── OneSignal ─────────────────────────────────────────────────────────────────
# Le SDK résout des composants par nom de classe depuis le manifeste fusionné.
-keep class com.onesignal.** { *; }
-dontwarn com.onesignal.**


# ── Room (via WorkManager, dépendance transitive de OneSignal) ────────────────
# Room instancie sa base en dérivant le nom de l'implémentation générée du **nom
# canonique** de la classe abstraite : `Class.forName(canonicalName + "_Impl")`,
# puis constructeur sans argument. Obfusquer l'un ou l'autre casse cette
# résolution — et le fait au *démarrage*, dans l'initialiseur `androidx.startup`
# de WorkManager, donc avant même `Application.onCreate()` :
#
#   RuntimeException: Failed to create an instance of class
#                     androidx.work.impl.WorkDatabase.canonicalName
#
# Invisible en debug, où R8 ne tourne pas. `-keepnames` interdit le renommage,
# `-keep … { <init>(); }` conserve la classe et le constructeur appelé par
# réflexion ; `extends` est transitif, donc `WorkDatabase` **et** son
# `WorkDatabase_Impl` sont couverts.
-keepnames class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomDatabase { <init>(); }
