# 180-android

![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?logo=android&logoColor=white) ![Kotlin 2.2](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white) ![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white) [![License: MIT](https://img.shields.io/badge/License-MIT-green)](LICENSE)

Native Android app of [180°C](https://www.180c.fr), an independent French food magazine. Subscribers browse the recipe library, keep favorites offline and receive new recipes by push notification. Content and accounts come from the 180°C WordPress site through its REST API. The app follows the iOS app feature for feature.

## Highlights

- **Recipe library**: editorial home, search, and filters by course, season and publication.
- **Offline favorites**: saved recipes and their images stay readable without a network.
- **Account**: JWT sign-in against WordPress, tokens stored in encrypted preferences. No in-app purchase and no subscription link, per Google Play policy.
- **Push notifications**: OneSignal with recipe deep links.
- **Analytics**: Umami and Firebase Analytics.

## Stack

| Layer | Technology |
|---|---|
| Platform | Android 8.0+ (API 26), target API 36 |
| UI | Jetpack Compose, Material 3, Navigation Compose |
| Networking | Retrofit, OkHttp, Coil |
| Storage | DataStore, AndroidX Security Crypto |
| Integrations | OneSignal, Firebase Analytics |
| Tests | JUnit 4, MockWebServer |
| CI/CD | GitHub Actions, Google Play Internal testing |

## Getting started

Requirements: Android Studio, JDK 17.

```bash
git clone https://github.com/nearmint/180-android.git
cd 180-android
cp app/google-services.sample.json app/google-services.json   # or the real Firebase file
./gradlew assembleDebug
```

`app/google-services.json` is gitignored. The backend origin is set by `BuildConfig.API_ORIGIN`.

## Development

```bash
./gradlew lint                # Android lint
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleDebug       # debug APK
```

- UI conventions mirror the iOS app: see `.claude/skills/ui-parite-ios/` and `docs/design/`.
- `versionName` is set by hand and shared with iOS; `versionCode` is never edited.
- Commits follow [Conventional Commits](https://www.conventionalcommits.org/); `main` is merged with `--no-ff` only.

## Deployment

- **CI** (`ci.yml`): lint, unit tests and debug build on `feat/**`, `fix/**`, `chore/**` and pull requests to `main`.
- **Release** (`release.yml`): every push to `main` builds a signed `.aab` and uploads it to the Internal testing track. Promotion to production stays manual in the Play Console.
- **Promote** (`promote.yml`, manual dispatch): moves an existing `versionCode` from one track to another (e.g. `internal` → `beta`) without sending it for review — review and publishing stay in the Play Console.

Signing, secrets and upload-key rotation are documented in [`docs/release.md`](docs/release.md).

## Project layout

```
app/src/main/java/fr/thermostat6/app180/
  data/         Networking, auth, offline store, push, analytics
  navigation/   Navigation graph and tab routing
  ui/           Screens, components, view models, theme
app/src/test/   Unit tests and JSON fixtures
docs/           Release process, design tokens and wording
```

## License

[MIT](LICENSE)
