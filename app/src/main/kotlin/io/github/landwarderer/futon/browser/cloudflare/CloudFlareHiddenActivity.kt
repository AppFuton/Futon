package io.github.landwarderer.futon.browser.cloudflare

import dagger.hilt.android.AndroidEntryPoint

/**
 * Automatic Cloudflare resolver that remains focused and rendered behind a translucent window.
 */
@AndroidEntryPoint
class CloudFlareHiddenActivity : CloudFlareActivity() {

	override val applyColorSchemeTheme: Boolean = false

	override val isHiddenAutoResolveActivity = true
}
