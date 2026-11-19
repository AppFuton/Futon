package io.github.landwarderer.futon.settings.reader

import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.preference.EditTextPreference
import androidx.preference.EditTextPreferenceDialogFragmentCompat
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.prefs.AppSettings

/** Whole-number input for the smart resume threshold and update timeout preferences. */
class SmartResumeThresholdDialog : EditTextPreferenceDialogFragmentCompat() {
    override fun onStart() {
        super.onStart()
        val alert = dialog as? AlertDialog ?: return
        val editor = alert.findViewById<EditText>(android.R.id.edit) ?: return
        val pref = preference as EditTextPreference
        editor.inputType = InputType.TYPE_CLASS_NUMBER
        alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val number = editor.text.toString().toIntOrNull()
            val (valid, error) = when (pref.key) {
                AppSettings.KEY_SMART_RESUME_PERCENTAGE ->
                    (number != null && number in 1..100) to R.string.smart_resume_percentage_error
                AppSettings.KEY_SMART_RESUME_TIMEOUT ->
                    (number != null && number in AppSettings.SMART_RESUME_TIMEOUT_RANGE) to
                        R.string.smart_resume_timeout_error
                else -> (number != null && number >= 0) to R.string.smart_resume_pages_error
            }
            if (!valid || number == null) {
                editor.error = getString(error)
            } else if (pref.callChangeListener(number.toString())) {
                pref.text = number.toString()
                dismiss()
            }
        }
    }

    // The positive button validates and persists explicitly; cancel/back leave the value untouched.
    override fun onDialogClosed(positiveResult: Boolean) = Unit
}
