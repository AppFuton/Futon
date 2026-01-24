# AI Coding Agent Instructions (Futon / Kotatsu fork)

These notes help AI agents work effectively in this Android/Kotlin codebase by capturing architecture, workflows, and project-specific patterns.

## Overview
- App: Android manga reader; Kotlin, Gradle, Java 17, minSdk 23, target/compileSdk 36.
- DI: Hilt (`@HiltAndroidApp` in `BaseApp`), bindings/providers in `AppModule`.
- Data: Room `MangaDatabase` with `InvalidationTracker` observers; WorkManager used for background tasks.
- Networking & images: OkHttp client with custom interceptors; Coil v3 configured with SVG/GIF/AVIF decoders, CBZ fetcher, cache sizes.
- External sources: Manga parsers via `com.github.AppFuton:futon-parsers:$parsersVersion` (JitPack).

## Build & Variants
- Variants: `debug`, `release`, `nightly` (inherits `release`). Nightly auto-sets version to `NyyyyMMdd` and date-based `versionCode`.
- Commands:
  - `./gradlew assembleDebug` → debug APK.
  - `./gradlew assembleRelease` → signed release APK (requires keystore env vars).
  - `./gradlew assembleNightly` → signed nightly APK.
- Tests:
  - Unit: `./gradlew test` (Android resources enabled).
  - Instrumented: `./gradlew connectedAndroidTest` (runner `io.github.landwarderer.futon.HiltTestRunner`).
- Lint: strict; certain rules disabled (e.g., `MissingTranslation`, `PrivateResource`).

## CI/CD & Signing
- GitHub Actions:
  - Tag `v*` → release build to GitHub Releases.
  - Weekly `nightly` with smart skip when no new commits.
  - PRs build `debug` and attach APK artifacts.
- Required secrets: `KEYSTORE_FILE` (base64), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- Local dev: If env vars missing, Gradle prompts interactively; `local.properties` may supply values like `tg_backup_bot_token`.
- Signature check: `AppValidator` verifies keystore SHA-256; release builds must match the configured fingerprint.

## Key Modules & Responsibilities
- `BaseApp`: app init, theme, TLS provider (Conscrypt on < Android 10), lifecycle callbacks registration, database observers, WorkManager config.
- `AppModule`: DI bindings for loader context, HTML image getter, OkHttp/Coil/Image components, caches (`PageCache`, `FaviconCache`), `NetworkState`, `WorkManager`, database observers and lifecycle callbacks.
- Package layout under `io/github/landwarderer/futon/`:
  - `core/`: shared infra (`db/`, `network/`, `parser/`, `prefs/`, `ui/`, `util/`, `os/`).
  - Feature packages: `reader/`, `details/`, `main/`, `search/`, `download/`, `local/`, `favourites/`, `history/`, `bookmarks/`, `scrobbling/`, `sync/`, `widget/`, etc.

## Parsers Dependency (Project-Specific)
- Uses JitPack `futon-parsers` with version from `libs.versions.toml`.
- Override for testing:
  - `./gradlew assembleDebug -DparsersVersionOverride=<short-sha>`
  - Example: `curl -s https://api.github.com/repos/appfuton/futon-parsers/commits/master -H "Accept: application/vnd.github.sha" | cut -c -10`.
- Interceptors add parser headers; captcha handling wired to Coil event listener.

## Conventions
- Performance over cosmetic refactors; avoid adding dependencies unless necessary (APK size matters).
- Translations managed via Weblate; do not edit string resources manually outside that flow.
- Material You design; viewBinding enabled; prefer adapter-delegates patterns in lists.
- Debug-only tooling: LeakCanary and WorkInspector active in `debug`/`nightly`.

## Integration & Deep Links
- Manifest defines many deep links (`DetailsActivity`, `ReaderActivity`, etc.) and app-specific schemes (`futon://…`); validate flows when changing intents.
- Network security config and locale config present; keep them consistent when adding features requiring webviews or language-specific behavior.

## Examples & Tips
- Adding a DB table: create `@Entity`, `@Dao`, wire via `MangaDatabase`; register invalidation observers if UI/Widgets depend on it.
- New feature module: follow package-per-feature structure; inject services via Hilt; use `viewBinding` and adapter-delegates for lists.
- Image pipelines: prefer Coil components configured in `AppModule`; avoid custom caches unless justified.

—
If any of the above seems off or incomplete (e.g., missing build lanes or undocumented modules), please leave notes and we’ll refine these instructions.
