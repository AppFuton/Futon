package io.github.landwarderer.futon.core.prefs

import android.content.Context
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import io.github.landwarderer.futon.core.util.ext.getEnumValue
import io.github.landwarderer.futon.core.util.ext.putEnumValue
import io.github.landwarderer.futon.core.util.ext.sanitizeHeaderValue
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.ifNullOrEmpty
import org.koitharu.kotatsu.parsers.util.nullIfEmpty
import io.github.landwarderer.futon.settings.utils.validation.DomainValidator
import java.io.File

class SourceSettings(context: Context, source: MangaSource) : MangaSourceConfig {

	private val appContext = context.applicationContext

    private val prefs = context.getSharedPreferences(
        source.name.replace(File.separatorChar, '$'),
        Context.MODE_PRIVATE,
    )

	var defaultSortOrder: SortOrder?
		get() = prefs.getEnumValue(KEY_SORT_ORDER, SortOrder::class.java)
		set(value) = prefs.edit { putEnumValue(KEY_SORT_ORDER, value) }

	val isSlowdownEnabled: Boolean
		get() = prefs.getBoolean(KEY_SLOWDOWN, false)

	val isCaptchaNotificationsDisabled: Boolean
		get() = prefs.getBoolean(KEY_NO_CAPTCHA, false)

	/**
	 * Automatic solving is experimental and opt-in: it stays off unless it is enabled globally,
	 * and can still be turned off for a single source.
	 */
	val isCaptchaAutoResolveDisabled: Boolean
		get() = !PreferenceManager.getDefaultSharedPreferences(appContext)
			.getBoolean(AppSettings.KEY_CAPTCHA_AUTO_SOLVE, false) ||
			prefs.getBoolean(KEY_NO_AUTO_CAPTCHA, false)

	/**
	 * The User-Agent that earned this source's current `cf_clearance` cookie.
	 *
	 * Cloudflare only honours a clearance for the exact agent it was issued to, and the challenge can
	 * only be solved by a WebView presenting an agent consistent with its own engine. Parsers that pin
	 * an inconsistent (e.g. hard-coded desktop) agent in `getRequestHeaders()` would otherwise stay
	 * blocked no matter how many times the challenge is solved. Set by the solver, applied by
	 * `CommonHeadersInterceptor`; null means the parser's own agent is fine.
	 *
	 * Recorded together with the user's own User-Agent setting at that moment: once the user changes
	 * that setting, their choice wins again and the stored agent is ignored until the next solve.
	 */
	var cloudFlareUserAgent: String?
		get() {
			val value = prefs.getString(KEY_CF_USER_AGENT, null)?.nullIfEmpty() ?: return null
			val base = prefs.getString(KEY_CF_USER_AGENT_BASE, null).orEmpty()
			return value.takeIf { base == prefs.getString(KEY_USER_AGENT, null).orEmpty() }
		}
		set(value) = prefs.edit {
			putString(KEY_CF_USER_AGENT, value?.sanitizeHeaderValue())
			putString(KEY_CF_USER_AGENT_BASE, prefs.getString(KEY_USER_AGENT, null).orEmpty())
		}

	@Suppress("UNCHECKED_CAST")
	override fun <T> get(key: ConfigKey<T>): T {
		return when (key) {
			is ConfigKey.UserAgent -> prefs.getString(key.key, key.defaultValue)
				.ifNullOrEmpty { key.defaultValue }
				.sanitizeHeaderValue()

			is ConfigKey.Domain -> prefs.getString(key.key, key.defaultValue)
				?.trim()
				?.takeIf { DomainValidator.isValidDomain(it) }
				?: key.defaultValue

			is ConfigKey.ShowSuspiciousContent -> prefs.getBoolean(key.key, key.defaultValue)
			is ConfigKey.SplitByTranslations -> prefs.getBoolean(key.key, key.defaultValue)
			is ConfigKey.PreferredImageServer -> prefs.getString(key.key, key.defaultValue)?.nullIfEmpty()
			is ConfigKey.DisableUpdateChecking -> prefs.getBoolean(key.key, key.defaultValue)
			is ConfigKey.InterceptCloudflare -> prefs.getBoolean(key.key, key.defaultValue)
		} as T
	}

	operator fun <T> set(key: ConfigKey<T>, value: T) = prefs.edit {
		when (key) {
			is ConfigKey.Domain -> putString(key.key, value as String?)
			is ConfigKey.ShowSuspiciousContent -> putBoolean(key.key, value as Boolean)
			is ConfigKey.UserAgent -> putString(key.key, (value as String?)?.sanitizeHeaderValue())
			is ConfigKey.SplitByTranslations -> putBoolean(key.key, value as Boolean)
			is ConfigKey.PreferredImageServer -> putString(key.key, value as String? ?: "")
			is ConfigKey.InterceptCloudflare -> putBoolean(key.key, value as Boolean)
			is ConfigKey.DisableUpdateChecking -> {
				// Read-only parser flag; keep it parser-controlled.
			}
		}
	}

	fun subscribe(listener: OnSharedPreferenceChangeListener) {
		prefs.registerOnSharedPreferenceChangeListener(listener)
	}

	fun unsubscribe(listener: OnSharedPreferenceChangeListener) {
		prefs.unregisterOnSharedPreferenceChangeListener(listener)
	}

	companion object {

		const val KEY_DOMAIN = "domain"
		const val KEY_NO_CAPTCHA = "no_captcha"
		const val KEY_NO_AUTO_CAPTCHA = "no_auto_captcha"
		const val KEY_SLOWDOWN = "slowdown"
		const val KEY_SORT_ORDER = "sort_order"
		const val KEY_CF_USER_AGENT = "cf_user_agent"
		const val KEY_CF_USER_AGENT_BASE = "cf_user_agent_base"
		private const val KEY_USER_AGENT = "user_agent" // ConfigKey.UserAgent
	}
}
