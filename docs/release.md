# Release — Android

## Le pipeline en cinq lignes

1. **Push** sur `feat/**`, `fix/**`, `chore/**` ou **PR vers `main`** → workflow **CI** : `lint`, `testDebugUnitTest`, `assembleDebug`, APK debug en artifact (7 jours).
2. **Push sur `main`** → workflow **Release** : keystore et `google-services.json` décodés depuis les secrets, `bundleRelease` signé avec `VERSION_CODE = github.run_number + 100`.
3. Le `.aab` est archivé en artifact (30 jours) **avant** l'appel à Play : un échec d'upload n'oblige ni à rebuilder ni à brûler un `versionCode`.
4. `r0adkll/upload-google-play` dépose le bundle sur la piste **Internal testing** en `status: completed` ; **Play App Signing** le resigne avec la clé d'application détenue par Google.
5. Toute promotion **Internal → Ouvert → Production** reste **manuelle**, dans la Play Console : c'est une décision produit, pas une étape de build.

Les deux workflows tournent sur `ubuntu-latest`, JDK 17 temurin, cache Gradle via
`gradle/actions/setup-gradle`. `Release` est sérialisé (`concurrency: release`,
sans annulation en vol).

## Politique de version

`versionName` est la version marketing, tenue à la main dans
`app/build.gradle.kts`, commune à l'iOS. `versionCode` n'est **jamais** édité :
il vaut le numéro de run GitHub **plus 100**, strictement croissant. L'offset
couvre les `versionCode` déjà publiés avant la remise à zéro du compteur de
runs ; il est calculé en shell dans le workflow (les expressions GitHub ne font
pas d'arithmétique). En local, variable absente, il retombe sur `1` — bon pour
un essai, jamais pour une soumission.

## Secrets GitHub

Six secrets, dans **Settings → Secrets and variables → Actions** du dépôt.

| Secret | Contenu | Utilisé par |
|---|---|---|
| `ANDROID_KEYSTORE_BASE64` | le `.jks` d'upload encodé en base64, sur une seule ligne | Release |
| `ANDROID_KEYSTORE_PASSWORD` | mot de passe du keystore | Release |
| `ANDROID_KEY_ALIAS` | `upload` | Release |
| `ANDROID_KEY_PASSWORD` | mot de passe de la clé — **identique** au précédent (PKCS12 n'accepte pas deux mots de passe distincts) | Release |
| `PLAY_SERVICE_ACCOUNT_JSON` | JSON d'un compte de service Google Cloud, collé tel quel, avec rôle *Release manager* dans la Play Console | Release |
| `GOOGLE_SERVICES_JSON_BASE64` | `app/google-services.json` encodé en base64 | CI **et** Release |

Deux **variables** de dépôt (non secrètes), facultatives : `CONTACT_EDITORIAL_EMAIL`
et `CONTACT_SUPPORT_EMAIL`, adresses ouvertes par l'écran Compte. Absentes, le
build retombe sur les adresses publiques du site.

Le keystore, ses mots de passe et les commandes d'encodage vivent hors du dépôt,
dans un dossier privé `android-secrets/`. **Aucun de ces éléments n'entre
jamais dans un commit** : `.gitignore` bloque `*.jks`, `*.keystore`,
`android-secrets/` et `google-services.json`.

## Rotation du keystore d'upload

Play App Signing sépare deux clés : la **clé d'application**, détenue par Google
et jamais remplacée, et la **clé d'upload**, la nôtre, qui ne sert qu'à prouver
l'origine d'un bundle. La perdre ou la compromettre n'empêche donc pas de
continuer à publier — elle se remplace.

1. Générer la nouvelle clé, hors du dépôt :

   ```sh
   export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
   "$JAVA_HOME/bin/keytool" -genkeypair \
     -keystore android-secrets/upload-keystore-NOUVEAU.jks \
     -storetype PKCS12 -alias upload -keyalg RSA -keysize 2048 -validity 10000 \
     -dname "CN=180C Upload Key, OU=Mobile, O=180C, L=Paris, C=FR"
   ```

2. En exporter le certificat public :

   ```sh
   "$JAVA_HOME/bin/keytool" -export -rfc \
     -keystore android-secrets/upload-keystore-NOUVEAU.jks \
     -alias upload -file android-secrets/upload-cert-NOUVEAU.pem
   ```

3. Play Console → **Test et versions → Intégrité de l'app → Signature d'application**
   → *Demander une réinitialisation de la clé d'upload*, joindre le `.pem`.
   Google traite la demande sous ~48 h ; l'ancienne clé reste acceptée pendant
   la transition.

4. Une fois la réinitialisation confirmée, mettre à jour les secrets
   `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD` et
   `ANDROID_KEY_PASSWORD`, puis relancer le workflow Release.

5. Détruire l'ancien `.jks` et mettre à jour les notes privées du dossier `android-secrets/`.

En cas de **compromission** et non de simple rotation : faire la demande de
réinitialisation *avant* toute autre action, et vérifier dans la Play Console
qu'aucune version inconnue n'a été déposée sur les pistes.
