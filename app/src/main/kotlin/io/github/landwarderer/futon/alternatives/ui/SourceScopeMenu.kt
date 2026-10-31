package io.github.landwarderer.futon.alternatives.ui

import android.content.Context
import android.view.Menu
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.alternatives.domain.AlternativeSourceScope
import io.github.landwarderer.futon.alternatives.domain.AlternativeSourceScopeOptions

/**
 * The source-scope choices shared by the alternatives screen and source replacement: every usable
 * preset, then enabled and disabled sources. Kept in one place so both screens always offer the same
 * list in the same order.
 */
internal object SourceScopeMenu {

	private const val ID_ENABLED = Menu.FIRST + 3000
	private const val ID_DISABLED = Menu.FIRST + 3001
	private const val ID_PRESET_FIRST = Menu.FIRST + 3100

	/** Adds one checkable item per available scope to [menu], in [group]. */
	fun populate(
		menu: Menu,
		group: Int,
		options: AlternativeSourceScopeOptions?,
		selected: AlternativeSourceScope,
	) {
		options?.presets?.forEachIndexed { index, preset ->
			menu.add(group, ID_PRESET_FIRST + index, Menu.NONE, preset.title).isChecked =
				selected == AlternativeSourceScope.Preset(preset.id)
		}
		menu.add(group, ID_ENABLED, Menu.NONE, R.string.enabled_sources).isChecked =
			selected == AlternativeSourceScope.Enabled
		if (options?.hasDisabledSources == true) {
			menu.add(group, ID_DISABLED, Menu.NONE, R.string.disabled_sources).isChecked =
				selected == AlternativeSourceScope.Disabled
		}
		menu.setGroupCheckable(group, true, true)
	}

	/** The scope a tapped item stands for, or null when the id is not one of ours. */
	fun scopeFor(itemId: Int, options: AlternativeSourceScopeOptions?): AlternativeSourceScope? = when (itemId) {
		ID_ENABLED -> AlternativeSourceScope.Enabled
		ID_DISABLED -> AlternativeSourceScope.Disabled
		else -> options?.presets?.getOrNull(itemId - ID_PRESET_FIRST)?.let { AlternativeSourceScope.Preset(it.id) }
	}

	fun title(context: Context, scope: AlternativeSourceScope, options: AlternativeSourceScopeOptions?): String =
		when (scope) {
			AlternativeSourceScope.Enabled -> context.getString(R.string.enabled_sources)
			AlternativeSourceScope.Disabled -> context.getString(R.string.disabled_sources)
			is AlternativeSourceScope.Preset -> options?.presetTitle(scope.id)
				?: context.getString(R.string.source_presets)
		}
}
