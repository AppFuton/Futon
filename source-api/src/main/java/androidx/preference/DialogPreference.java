package androidx.preference;

import android.content.Context;
import android.util.AttributeSet;

public abstract class DialogPreference extends Preference {
	public DialogPreference(Context context) {
		super(context);
	}

	public DialogPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public void setDialogTitle(CharSequence dialogTitle) {
	}

	public void setDialogMessage(CharSequence dialogMessage) {
	}

	public void setPositiveButtonText(CharSequence positiveButtonText) {
	}

	public void setNegativeButtonText(CharSequence negativeButtonText) {
	}
}
