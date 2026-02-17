# Futon - Agent Development Guide

**Project:** Futon - Free and open-source manga reader for Android  
**Fork of:** [Kotatsu](https://github.com/KotatsuApp/Kotatsu)  
**Language:** Kotlin  
**Build System:** Gradle with Kotlin DSL  
**Minimum SDK:** 23 (Android 6.0+)  
**Target SDK:** 36

---

## Build Commands

### Standard Builds

```bash
# Debug build (recommended for development)
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Release build (requires signing setup)
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk

# Nightly build (auto-generates version from date)
./gradlew assembleNightly
# Output: app/build/outputs/apk/nightly/app-nightly.apk
```

### Lint & Verification

```bash
# Run all checks (lint + tests)
./gradlew check

# Lint only
./gradlew lint

# Lint specific variant
./gradlew lintDebug
```

### Testing

```bash
# Run ALL unit tests (fast, JVM-only)
./gradlew test

# Run tests for specific variant
./gradlew testDebugUnitTest

# Run instrumented tests (requires device/emulator)
./gradlew connectedDebugAndroidTest

# Run a specific test class
./gradlew test --tests "io.github.landwarderer.futon.core.github.VersionIdTest"

# Run a specific test method
./gradlew test --tests "io.github.landwarderer.futon.core.github.VersionIdTest.testVersionIdParse"

# Run instrumented test class
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.landwarderer.futon.core.db.MangaDatabaseTest

# Run specific instrumented test method
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.landwarderer.futon.core.db.MangaDatabaseTest#migrateAll
```

### Clean & Rebuild

```bash
# Clean build artifacts
./gradlew clean

# Full rebuild
./gradlew clean assembleDebug
```

---

## Code Style Guidelines

### Import Organization

Imports follow a strict order:
1. Android/AndroidX imports (grouped by component)
2. Third-party libraries (Hilt, Kotlin coroutines, etc.)
3. Internal project imports (`io.github.landwarderer.futon.*`)
4. `javax.inject` imports (always last)

```kotlin
import android.content.Intent
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.ui.BaseActivity
import javax.inject.Inject
```

**Import Aliases:** Use for Material components:
```kotlin
import com.google.android.material.R as materialR
import androidx.appcompat.R as appcompatR
```

### Formatting & Indentation

- **Indentation:** Tabs (converted to 4 spaces per `.editorconfig`)
- **Line length:** Max 120 characters (soft limit)
- **Trailing commas:** Use in multiline parameter lists and collections
- **Expression bodies:** Prefer for single-expression functions
- **Charset:** UTF-8
- **End of line:** LF (Unix style)
- **Final newline:** Required

**EditorConfig Rules:**
```
indent_style = space
indent_size = 4
max_line_length = 120
insert_final_newline = true
disabled_rules = no-wildcard-imports, no-unused-imports
```

### Naming Conventions

#### Classes & Interfaces (PascalCase)
Use suffixes to indicate purpose:
- `*Activity`, `*Fragment`, `*Service` — Android components
- `*ViewModel` — ViewModels
- `*Repository` — Repositories
- `*UseCase` — Domain use cases
- `*Dao` — Room DAOs
- `*Entity` — Database entities
- `*AD` — Adapter Delegates (e.g., `BookmarkLargeAD`)
- `*Helper` — Helper classes
- `*Worker` — WorkManager workers

#### Variables & Functions (camelCase)
```kotlin
val isResumeEnabled        // Boolean: is/has/should prefix
val onOpenReader           // Event flow
private val viewModel by viewModels<MainViewModel>()
fun adjustFabVisibility()  // Descriptive verbs
```

**Constants:**
```kotlin
private const val MAX_PARALLELISM = 4
private const val NO_ID = 0L

companion object {
    private const val TAGS_LIMIT = 12
    const val TAG_ONESHOT = "oneshot"  // Public constants
}
```

### Type Annotations

**Use explicit types when:**
- Clarity improves understanding
- Type inference is ambiguous
- Public API contracts

**Omit when:**
- Type is obvious from right-hand side
- Local variables with clear initialization

```kotlin
// Explicit type for clarity
val settings: AppSettings = ...

// Type inference acceptable
val count = list.size
```

---

## Error Handling

### Exception Patterns

#### runCatchingCancellable
**Always use** instead of standard `runCatching` to preserve `CancellationException`:

```kotlin
runCatchingCancellable {
    LocalMangaParser(localManga.url.toUri()).getMangaInfo()
}.onFailure {
    it.printStackTraceDebug()
}.getOrNull()
```

#### Debug Stack Traces
Use `printStackTraceDebug()` extension — prints only in debug builds:

```kotlin
} catch (e: IllegalStateException) {
    e.printStackTraceDebug()
}
```

#### CancellationException Handling
**Never swallow** `CancellationException` — always re-throw:

```kotlin
try {
    doWork()
} catch (e: CancellationException) {
    throw e  // REQUIRED - do not catch or ignore
} catch (e: Throwable) {
    e.printStackTraceDebug()
    Result.failure()
}
```

#### ViewModel Error Handling
Use base error flow pattern:

```kotlin
val onError = MutableEventFlow<Throwable>()

// Extension function for flow error handling
repository.observeData()
    .withErrorHandling()
    .stateIn(viewModelScope, SharingStarted.Lazily, defaultValue)
```

### Null Safety

```kotlin
checkNotNull(value) { "Descriptive error message" }  // With message
requireNotNull(value)
value?.let { /* safe access */ }
value ?: defaultValue  // Elvis operator
```

---

## Dependency Injection (Hilt)

### Component Annotations

```kotlin
@AndroidEntryPoint      // Activities, Fragments, Services, Views
@HiltViewModel          // ViewModels
@HiltWorker             // WorkManager Workers
@HiltAndroidTest        // Test classes
```

### Scope Annotations

```kotlin
@Singleton              // Application-wide single instance
@Reusable               // May be pooled, not guaranteed singleton
@ViewModelScoped        // Scoped to ViewModel lifecycle
```

### Injection Patterns

#### Constructor Injection (Preferred)
```kotlin
@Singleton
class LocalMangaRepository @Inject constructor(
    private val storageManager: LocalStorageManager,
    private val localMangaIndex: LocalMangaIndex,
    @LocalStorageChanges private val localStorageChanges: MutableSharedFlow<LocalManga?>,
    private val settings: AppSettings,
) : MangaRepository
```

#### Field Injection (Android Components Only)
```kotlin
@AndroidEntryPoint
class MainActivity : BaseActivity<ActivityMainBinding>() {
    @Inject
    lateinit var settings: AppSettings
    
    private val viewModel by viewModels<MainViewModel>()
}
```

#### Assisted Injection (Workers)
```kotlin
@HiltWorker
class TrackWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val captchaHandler: CaptchaHandler,
) : CoroutineWorker(context, workerParams)
```

### Custom Qualifiers
Define in `Qualifiers.kt`:

```kotlin
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LocalStorageChanges

// Usage:
@LocalStorageChanges 
private val localStorageChanges: MutableSharedFlow<LocalManga?>
```

---

## Coroutines & Flow

### Dispatcher Usage

**Always specify dispatchers explicitly:**

```kotlin
launchJob(Dispatchers.Default) {
    // CPU-intensive work
}

withContext(Dispatchers.IO) {
    // I/O operations
}

viewModelScope + Dispatchers.IO  // Combined scope
```

### Flow Patterns

#### StateFlow for UI State
```kotlin
val isRunning = scheduler.observeIsRunning()
    .stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Lazily, false)
```

#### SharedFlow for Events
```kotlin
val onOpenReader = MutableEventFlow<Manga>()  // Custom event flow

// Emit event
onOpenReader.call(manga)
```

#### Flow Transformations
```kotlin
combine(
    observeHeader(),
    quickFilter.appliedOptions,
    repository.observeTrackingLog(limit, filters)
) { header, filters, list ->
    // Transform
}.catch { e ->
    emit(listOf(e.toErrorState(canRetry = false)))
}.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, listOf(LoadingState))
```

### Concurrency Control

#### ChannelFlow for Parallel Processing
```kotlin
channelFlow {
    for (file in files) {
        launch(dispatcher) {
            runCatchingCancellable {
                val result = processFile(file)
                send(result)
            }
        }
    }
}
```

#### Semaphore for Limited Parallelism
```kotlin
private val semaphore = Semaphore(MAX_PARALLELISM)

semaphore.withPermit {
    // Limited concurrent execution
}
```

---

## Project Structure

### Feature-Based Architecture

```
app/src/main/kotlin/io/github/landwarderer/futon/
├── <feature>/
│   ├── data/           # Repositories, DAOs, Entities
│   ├── domain/         # Use cases, Business logic
│   └── ui/             # Activities, Fragments, ViewModels
│       ├── adapter/    # RecyclerView adapters
│       └── model/      # UI models
├── core/               # Shared utilities
│   ├── db/            # Database
│   ├── network/       # OkHttp, interceptors
│   ├── prefs/         # Settings
│   ├── ui/            # Base UI classes
│   └── util/          # Extensions
```

### Main Features
- `tracker/` — Manga tracking
- `reader/` — Manga reader
- `favourites/` — Favorites management
- `history/` — Reading history
- `search/` — Search functionality
- `download/` — Download manager
- `local/` — Local manga storage
- `bookmarks/` — Bookmarks
- `scrobbling/` — External service integration
- `settings/` — App configuration

---

## Testing

### Test Organization

```
app/src/
├── test/                    # Unit tests (JVM-only, fast)
│   └── kotlin/io/github/landwarderer/futon/
│       └── *Test.kt
└── androidTest/             # Instrumented tests (requires device)
    ├── assets/              # Test data (JSON, backups)
    └── kotlin/io/github/landwarderer/futon/
        ├── HiltTestRunner.kt     # Custom runner
        ├── SampleData.kt         # Test data loader
        └── *Test.kt
```

### Unit Test Pattern

```kotlin
class VersionIdTest {
    @Test
    fun testVersionIdParse() {
        val version = VersionId("2.0")
        assertEquals(2, version.major)
        assertEquals(0, version.minor)
    }
    
    @Test
    fun testCoroutines() = runTest {  // kotlinx-coroutines-test
        val result = suspendingFunction()
        assertNotNull(result)
    }
}
```

### Instrumented Test Pattern

```kotlin
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppShortcutManagerTest {
    @get:Rule
    var hiltRule = HiltAndroidRule(this)
    
    @Inject
    lateinit var repository: HistoryRepository
    
    @Inject
    lateinit var database: MangaDatabase
    
    @Before
    fun setUp() {
        hiltRule.inject()
        database.clearAllTables()
    }
    
    @Test
    fun testFeature() = runTest {
        // Setup
        repository.addOrUpdate(/* ... */)
        
        // Wait for async operations
        InstrumentationRegistry.getInstrumentation().awaitForIdle()
        
        // Assert
        assertEquals(expected, actual)
    }
}
```

### Test Data Loading

```kotlin
// Use centralized SampleData
val manga = SampleData.manga
val categories = SampleData.categories

// Load custom assets
context.assets.open("futon_test.bak").use { input ->
    // Process test file
}
```

---

## Performance Guidelines

**From CONTRIBUTING.md:**
> Performance matters. In the case of choosing between source code beauty and performance, performance should be a priority.

### Do's
- Profile before optimizing
- Use `Dispatchers.Default` for CPU-intensive work
- Use `Dispatchers.IO` for I/O operations
- Limit parallelism with `Semaphore`
- Use `StateFlow` with `SharingStarted.Lazily` when appropriate
- Avoid blocking the main thread

### Don'ts
- Add unnecessary dependencies (APK size matters)
- Create unnecessary object allocations in hot paths
- Use nested flows when flat transformations suffice
- Ignore memory leaks (LeakCanary is enabled in debug)

---

## Contribution Rules

**From CONTRIBUTING.md:**
1. **Assign issues** to yourself before working on them
2. **Open discussion** for new features before implementation
3. **Translations** go through [Weblate](https://hosted.weblate.org/engage/kotatsu/)
4. **Manga sources** go in [futon-parsers](https://github.com/AppFuton/futon-parsers)
5. **Do not modify** README or info files (except typos)
6. **Avoid new dependencies** unless required

---

## Common Patterns

### ViewModel Setup
```kotlin
@HiltViewModel
class MyViewModel @Inject constructor(
    private val repository: MyRepository,
    private val settings: AppSettings,
) : BaseViewModel() {
    
    val onError = MutableEventFlow<Throwable>()
    
    val data = repository.observeData()
        .withErrorHandling()
        .stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Lazily, emptyList())
}
```

### Repository Pattern
```kotlin
@Singleton
class MyRepository @Inject constructor(
    private val dao: MyDao,
) {
    suspend fun getData(): List<Data> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            dao.getAll()
        }.getOrThrow()
    }
    
    fun observeData(): Flow<List<Data>> = dao.observeAll()
}
```

### Worker Pattern
```kotlin
@HiltWorker
class MyWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: MyRepository,
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result = try {
        repository.doWork()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        e.printStackTraceDebug()
        Result.failure()
    }
}
```

---

## Resources

- **Discord:** https://discord.gg/9sqBHXhwzz
- **Parsers Repo:** https://github.com/AppFuton/futon-parsers
- **Original Kotatsu:** https://github.com/KotatsuApp/Kotatsu
- **CI/CD Setup:** See [CI.md](./CI.md)
- **Contributing:** See [CONTRIBUTING.md](./CONTRIBUTING.md)

---

**Generated for AI coding agents operating in this repository.**
