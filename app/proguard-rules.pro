-optimizationpasses 8
-dontobfuscate
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
	public static void checkExpressionValueIsNotNull(...);
	public static void checkNotNullExpressionValue(...);
	public static void checkReturnedValueIsNotNull(...);
	public static void checkFieldIsNotNull(...);
	public static void checkParameterIsNotNull(...);
	public static void checkNotNullParameter(...);
}

-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn coil3.PlatformContext

-keep class io.github.landwarderer.futon.settings.NotificationSettingsLegacyFragment
-keep class io.github.landwarderer.futon.settings.about.changelog.ChangelogFragment

-keep class io.github.landwarderer.futon.core.exceptions.* { *; }
-keep class io.github.landwarderer.futon.core.prefs.ScreenshotsPolicy { *; }
-keep class io.github.landwarderer.futon.backups.ui.periodical.PeriodicalBackupSettingsFragment { *; }
-keep class org.jsoup.parser.Tag
-keep class org.jsoup.internal.StringUtil

# Mihon/Tachiyomi extension rules
# Disable shrinking and optimization for the core bridge to ensure ClassLoaders and Injekt work perfectly.

-keep class eu.kanade.tachiyomi.** { *; }
-keep interface eu.kanade.tachiyomi.** { *; }
-keeppackagenames eu.kanade.tachiyomi.**

-keep class uy.kohesive.injekt.** { *; }
-keep interface uy.kohesive.injekt.** { *; }
-keeppackagenames uy.kohesive.injekt.**
-keepclassmembers class uy.kohesive.injekt.** { *; }

-keep class io.github.landwarderer.futon.mihon.** { *; }
-keeppackagenames io.github.landwarderer.futon.mihon.**

# Keep everything related to dynamic loading
-keep class io.github.landwarderer.futon.mihon.ChildFirstPathClassLoader { *; }
-keep public class * extends dalvik.system.PathClassLoader { *; }
-keep public class * extends dalvik.system.BaseDexClassLoader { *; }

# Critical attributes for Kotlin reflection and Injekt
-keepattributes Signature, InnerClasses, EnclosingMethod, AnnotationDefault, *Annotation*, kotlin.Metadata, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Keep Kotlin Metadata class itself
-keep class kotlin.Metadata { *; }

# Prevent stripping of extension entry points in the app
-keep public class * implements eu.kanade.tachiyomi.source.Source
-keep public class * implements eu.kanade.tachiyomi.source.SourceFactory

# Keep common libraries used by extensions
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class org.jsoup.** { *; }
-keep class rx.** { *; }
-keep class kotlinx.serialization.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keeppackagenames okhttp3.**, okio.**, org.jsoup.**, rx.**, kotlinx.serialization.**, kotlin.**, kotlinx.coroutines.**

# Keep Kotlin standard library facades and internal classes often used by extensions
-keep class kotlin.LazyKt** { *; }
-keep class kotlin.collections.CollectionsKt** { *; }
-keep class kotlin.sequences.SequencesKt** { *; }
-keep class kotlin.text.StringsKt** { *; }
-keep class kotlin.comparisons.ComparisonsKt** { *; }
-keep class kotlin.io.FilesKt** { *; }
-keep class kotlin.jvm.internal.** { *; }
-keep class kotlin.jvm.functions.** { *; }

# Suppress warnings
-dontwarn uy.kohesive.injekt.**
-dontwarn eu.kanade.tachiyomi.**
-dontwarn kotlinx.serialization.**
-dontwarn kotlin.reflect.**
