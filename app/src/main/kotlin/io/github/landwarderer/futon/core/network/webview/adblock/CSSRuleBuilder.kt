package io.github.landwarderer.futon.core.network.webview.adblock

import androidx.collection.ArraySet

class CSSRuleBuilder {

	private val selectors = ArraySet<String>()

	fun add(selector: String) {
		selectors.add(selector)
	}

	fun build() = buildString {
		append("<style> {")
		for (selector in selectors) {
			append(selector)
			append(";")
		}
		append("}!important</style>")
	}
}
