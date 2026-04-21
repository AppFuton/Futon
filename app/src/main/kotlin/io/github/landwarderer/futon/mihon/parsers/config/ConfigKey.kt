package io.github.landwarderer.futon.mihon.parsers.config

sealed class ConfigKey<T>(
    @JvmField val key: String,
) {

    abstract val defaultValue: T

    class Domain(
        @JvmField @JvmSuppressWildcards vararg val presetValues: String,
    ) : ConfigKey<String>("domain") {

        init {
            require(presetValues.isNotEmpty()) { "You must provide at least one domain" }
        }

        override val defaultValue: String
            get() = presetValues.first()
    }

    class Text(
        key: String,
        @JvmField val title: String,
        override val defaultValue: String = "",
    ) : ConfigKey<String>(key)

    class ShowSuspiciousContent(
        override val defaultValue: Boolean,
    ) : ConfigKey<Boolean>("show_suspicious")

    class UserAgent(
        override val defaultValue: String,
    ) : ConfigKey<String>("user_agent")

    class SplitByTranslations(
        override val defaultValue: Boolean,
    ) : ConfigKey<Boolean>("split_translations")

    class PreferredImageServer(
        val presetValues: Map<String?, String?>,
        override val defaultValue: String?,
    ) : ConfigKey<String?>("img_server")

    class Toggle(
        key: String,
        @JvmField val title: String,
        override val defaultValue: Boolean = false,
    ) : ConfigKey<Boolean>(key)

    class PreferredLanguage(
        @JvmField val title: String,
        @JvmField val presetValues: Map<String, String>,
        override val defaultValue: String = "all",
    ) : ConfigKey<String>("preferred_language")
}
