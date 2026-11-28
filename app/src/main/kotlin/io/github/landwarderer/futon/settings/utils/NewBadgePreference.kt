package io.github.landwarderer.futon.settings.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import android.util.AttributeSet
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceViewHolder
import io.github.landwarderer.futon.R

/**
 * A reusable preference that displays an accent 'new' indicator dot.
 *
 * Supports:
 * - [BadgePosition.INLINE_TITLE] (Option B, default): Dot attached directly to the title text.
 * - [BadgePosition.END_WIDGET] (Option A): Dot in the end widget frame.
 * - [BadgePosition.BOTH]: Both end widget and inline title.
 * - [isDismissible]: When true, tapping the preference marks it as seen and hides the dot.
 */
class NewBadgePreference @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
) : Preference(context, attrs, defStyleAttr) {

	enum class BadgePosition {
		END_WIDGET,
		INLINE_TITLE,
		BOTH,
	}

	var badgePosition: BadgePosition = BadgePosition.INLINE_TITLE
		set(value) {
			if (field != value) {
				field = value
				notifyChanged()
			}
		}

	var isDismissible: Boolean = true

	var dependentKeys: String? = null
		set(value) {
			if (field != value) {
				field = value
				notifyChanged()
			}
		}

	private val seenKey: String
		get() = getSeenKey(key ?: "unknown")

	var isNew: Boolean
		get() {
			val deps = dependentKeys
			if (!deps.isNullOrEmpty()) {
				return deps.split(',').any { key -> !isSeen(context, key.trim()) }
			}
			if (!isDismissible) return true
			val prefs = sharedPreferences ?: PreferenceManager.getDefaultSharedPreferences(context)
			return !prefs.getBoolean(seenKey, false)
		}
		set(value) {
			if (isDismissible) {
				val prefs = sharedPreferences ?: PreferenceManager.getDefaultSharedPreferences(context)
				prefs.edit { putBoolean(seenKey, !value) }
			}
			notifyChanged()
		}

	init {
		if (attrs != null) {
			val a = context.obtainStyledAttributes(attrs, R.styleable.NewBadgePreference)
			val posOrdinal = a.getInt(R.styleable.NewBadgePreference_badgePosition, BadgePosition.INLINE_TITLE.ordinal)
			badgePosition = BadgePosition.entries.getOrElse(posOrdinal) { BadgePosition.INLINE_TITLE }
			isDismissible = a.getBoolean(R.styleable.NewBadgePreference_dismissible, true)
			dependentKeys = a.getString(R.styleable.NewBadgePreference_dependentKeys)
			a.recycle()
		}
		if (badgePosition == BadgePosition.END_WIDGET || badgePosition == BadgePosition.BOTH) {
			widgetLayoutResource = R.layout.preference_widget_new_dot
		}
	}

	fun refresh() {
		notifyChanged()
	}

	override fun performClick() {
		if (isDismissible && isNew) {
			isNew = false
		}
		super.performClick()
	}

	override fun onBindViewHolder(holder: PreferenceViewHolder) {
		super.onBindViewHolder(holder)
		val shouldShow = isNew

		// Option A: End widget dot
		val dotView = holder.findViewById(R.id.new_dot)
		dotView?.isVisible = shouldShow && (badgePosition == BadgePosition.END_WIDGET || badgePosition == BadgePosition.BOTH)

		// Option B: Inline title dot
		val titleView = holder.findViewById(android.R.id.title) as? TextView
		if (titleView != null) {
			if (shouldShow && (badgePosition == BadgePosition.INLINE_TITLE || badgePosition == BadgePosition.BOTH)) {
				val originalTitle = title ?: ""
				val dotDrawable = ContextCompat.getDrawable(context, R.drawable.bg_new_feature_dot)?.mutate()
				if (dotDrawable != null) {
					val dotSize = (6.5f * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
					dotDrawable.setBounds(0, 0, dotSize, dotSize)
					val ssb = SpannableStringBuilder(originalTitle).append(" ​")
					val span = CenteredImageSpan(dotDrawable)
					ssb.setSpan(span, ssb.length - 1, ssb.length, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE)
					titleView.text = ssb
				}
			} else {
				titleView.text = title
			}
		}
	}

	private class CenteredImageSpan(drawable: Drawable) : ImageSpan(drawable) {

		override fun draw(
			canvas: Canvas,
			text: CharSequence,
			start: Int,
			end: Int,
			x: Float,
			top: Int,
			y: Int,
			bottom: Int,
			paint: Paint,
		) {
			val b = drawable
			canvas.save()
			val transY = top + (bottom - top) / 2f - b.bounds.height() / 2f
			canvas.translate(x, transY)
			b.draw(canvas)
			canvas.restore()
		}
	}

	companion object {

		fun getSeenKey(key: String): String = "pref_seen_$key"

		fun markSeen(context: Context, key: String) {
			PreferenceManager.getDefaultSharedPreferences(context).edit {
				putBoolean(getSeenKey(key), true)
			}
		}

		fun isSeen(context: Context, key: String): Boolean {
			return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(getSeenKey(key), false)
		}

		fun resetSeen(context: Context, key: String) {
			PreferenceManager.getDefaultSharedPreferences(context).edit {
				remove(getSeenKey(key))
			}
		}
	}
}
