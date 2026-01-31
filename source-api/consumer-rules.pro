# Keep all Tachiyomi extension API classes for runtime loading
-keep class eu.kanade.tachiyomi.source.** { *; }
-keep interface eu.kanade.tachiyomi.source.** { *; }

# Keep androidx.preference classes that extensions might reference
# Even though the app uses these, R8 might strip classes not directly referenced by app code
# Extensions need these classes to be available via parent ClassLoader
-keep class androidx.preference.Preference { *; }
-keep class androidx.preference.PreferenceScreen { *; }
-keep class androidx.preference.PreferenceGroup { *; }
-keep class androidx.preference.EditTextPreference { *; }
-keep class androidx.preference.ListPreference { *; }
-keep class androidx.preference.MultiSelectListPreference { *; }
-keep class androidx.preference.CheckBoxPreference { *; }
-keep class androidx.preference.SwitchPreferenceCompat { *; }
-keep class androidx.preference.DialogPreference { *; }
-keep class androidx.preference.TwoStatePreference { *; }
