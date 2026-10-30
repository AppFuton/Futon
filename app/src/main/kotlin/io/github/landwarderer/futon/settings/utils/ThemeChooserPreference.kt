package io.github.landwarderer.futon.settings.utils

import android.content.Context
import android.content.res.TypedArray
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver
import android.widget.HorizontalScrollView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.isEmpty
import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
import androidx.customview.view.AbsSavedState
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.prefs.ColorScheme
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.databinding.ItemColorSchemeBinding
import io.github.landwarderer.futon.databinding.PreferenceThemeBinding
import java.lang.ref.WeakReference
import com.google.android.material.R as materialR

class ThemeChooserPreference @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = R.attr.themeChooserPreferenceStyle,
	defStyleRes: Int = R.style.Preference_ThemeChooser,
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

	private val settings = AppSettings(context)
	private val entries: List<ColorScheme>
		get() = ColorScheme.getAvailableList(includeCustom = settings.importedThemes.isNotEmpty())
	private var currentValue: ColorScheme = ColorScheme.default
	private val lastScrollPosition = intArrayOf(-1)
	private val itemClickListener = View.OnClickListener {
		val tag = it.tag as? ColorScheme ?: return@OnClickListener
		setValueInternal(tag.name, true)
	}
	private var scrollPersistListener: ScrollPersistListener? = null

	var value: String
		get() = currentValue.name
		set(value) = setValueInternal(value, notifyChanged = true)

	override fun onBindViewHolder(holder: PreferenceViewHolder) {
		super.onBindViewHolder(holder)
		val binding = PreferenceThemeBinding.bind(holder.itemView)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			binding.scrollView.suppressLayout(true)
			binding.linear.suppressLayout(true)
		}
		binding.linear.removeAllViews()
		for (theme in entries) {
			val customTheme = settings.selectedCustomTheme.takeIf { theme == ColorScheme.CUSTOM }
			val styleResId = when {
				theme == ColorScheme.CUSTOM && customTheme?.isDark == true -> R.style.ThemeOverlay_Futon_Custom_Dark
				else -> theme.styleResId
			}
			val themedContext = ContextThemeWrapper(context, styleResId)
			val item = ItemColorSchemeBinding.inflate(LayoutInflater.from(themedContext), binding.linear, false)
			if (binding.linear.isEmpty()) {
				item.root.updatePaddingRelative(start = 0)
			}
			val isSelected = theme == currentValue
			item.card.isChecked = isSelected
			item.card.strokeWidth = if (isSelected) themedContext.resources.getDimensionPixelSize(
				materialR.dimen.m3_comp_outlined_card_outline_width,
			) else 0
			item.textViewTitle.text = customTheme?.displayName ?: themedContext.getString(theme.titleResId)
			if (customTheme != null) {
				// Tint the preview shapes with the user's seed color instead of flattening
				// the card background (which would erase shape_1/shape_2 visual indicators).
				// Leave textViewTitle using its default ?android:attr/textColorPrimary so the
				// label stays legible regardless of light/dark mode.
				item.shape1.backgroundTintList = android.content.res.ColorStateList.valueOf(customTheme.primaryColor)
				item.shape2.backgroundTintList = android.content.res.ColorStateList.valueOf(customTheme.primaryColor)
			}
			item.root.tag = theme
			item.card.tag = theme
			item.imageViewCheck.isVisible = theme == currentValue
			item.root.setOnClickListener(itemClickListener)
			item.card.setOnClickListener(itemClickListener)
			binding.linear.addView(item.root)
			if (isSelected) {
				item.root.requestFocus()
			}
		}
		if (lastScrollPosition[0] >= 0) {
			val scroller = Scroller(binding.scrollView, lastScrollPosition[0])
			scroller.run()
			binding.scrollView.post(scroller)
		}
		binding.scrollView.viewTreeObserver.run {
			scrollPersistListener?.let { removeOnScrollChangedListener(it) }
			scrollPersistListener =
				ScrollPersistListener(WeakReference(binding.scrollView), lastScrollPosition)
			addOnScrollChangedListener(scrollPersistListener)
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			binding.linear.suppressLayout(false)
			binding.scrollView.suppressLayout(false)
		}
	}

	override fun onSetInitialValue(defaultValue: Any?) {
		value = getPersistedString(
			when (defaultValue) {
				is String -> ColorScheme.safeValueOf(defaultValue) ?: ColorScheme.default
				is ColorScheme -> defaultValue
				else -> ColorScheme.default
			}.name,
		)
	}

	override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
		return a.getInt(index, 0)
	}

	override fun onSaveInstanceState(): Parcelable? {
		val superState = super.onSaveInstanceState() ?: return null
		return SavedState(
			superState = superState,
			scrollPosition = lastScrollPosition[0],
		)
	}

	override fun onRestoreInstanceState(state: Parcelable?) {
		if (state !is SavedState) {
			super.onRestoreInstanceState(state)
			return
		}
		super.onRestoreInstanceState(state.superState)
		lastScrollPosition[0] = state.scrollPosition
	}

	/** Externally refresh this preference's view when custom-theme data has changed. */
	fun refresh() = notifyChanged()

	private fun setValueInternal(enumName: String, notifyChanged: Boolean) {
		val newValue = ColorScheme.safeValueOf(enumName) ?: return
		if (newValue != currentValue) {
			currentValue = newValue
			persistString(newValue.name)
			if (notifyChanged) {
				notifyChanged()
			}
		}
	}

	private class SavedState : AbsSavedState {

		val scrollPosition: Int

		constructor(
			superState: Parcelable,
			scrollPosition: Int,
		) : super(superState) {
			this.scrollPosition = scrollPosition
		}

		constructor(source: Parcel, classLoader: ClassLoader?) : super(source, classLoader) {
			scrollPosition = source.readInt()
		}

		override fun writeToParcel(out: Parcel, flags: Int) {
			super.writeToParcel(out, flags)
			out.writeInt(scrollPosition)
		}

		companion object {
			@Suppress("unused")
			@JvmField
			val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
				override fun createFromParcel(`in`: Parcel) =
					SavedState(`in`, SavedState::class.java.classLoader)

				override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
			}
		}
	}

	private class ScrollPersistListener(
		private val scrollViewRef: WeakReference<HorizontalScrollView>,
		private val lastScrollPosition: IntArray,
	) : ViewTreeObserver.OnScrollChangedListener {

		override fun onScrollChanged() {
			val scrollView = scrollViewRef.get() ?: return
			lastScrollPosition[0] = scrollView.scrollX
		}
	}

	private class Scroller(
		private val scrollView: HorizontalScrollView,
		private val position: Int,
	) : Runnable {

		override fun run() {
			scrollView.scrollTo(position, 0)
		}
	}
}
