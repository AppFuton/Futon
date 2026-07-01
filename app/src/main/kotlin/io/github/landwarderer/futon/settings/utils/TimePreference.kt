package io.github.landwarderer.futon.settings.utils

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import androidx.fragment.app.FragmentActivity
import androidx.preference.Preference
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import io.github.landwarderer.futon.core.util.ext.findActivity
import java.util.Locale

class TimePreference @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
	defStyleRes: Int = 0,
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

	private var hour: Int = 0
	private var minute: Int = 0

	init {
		isPersistent = true
	}

	override fun onGetDefaultValue(a: TypedArray, index: Int): Any? {
		return a.getString(index)
	}

	override fun onSetInitialValue(defaultValue: Any?) {
		val value = getPersistedString(defaultValue as? String ?: "00:00")
		decodeTime(value)
		updateSummary()
	}

	private fun decodeTime(value: String?) {
		if (value == null) return
		val parts = value.split(":")
		if (parts.size == 2) {
			hour = parts[0].toIntOrNull() ?: 0
			minute = parts[1].toIntOrNull() ?: 0
		}
	}

	private fun updateSummary() {
		summary = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
	}

	override fun onClick() {
		val activity = context.findActivity() as? FragmentActivity ?: return
		showPicker(activity)
	}

	private fun showPicker(activity: FragmentActivity) {
		val picker = MaterialTimePicker.Builder()
			.setTimeFormat(TimeFormat.CLOCK_24H)
			.setHour(hour)
			.setMinute(minute)
			.setTitleText(title)
			.build()

		picker.addOnPositiveButtonClickListener {
			hour = picker.hour
			minute = picker.minute
			val value = String.format(Locale.US, "%02d:%02d", hour, minute)
			if (callChangeListener(value)) {
				persistString(value)
				updateSummary()
			}
		}

		picker.show(activity.supportFragmentManager, "time_picker")
	}
}
