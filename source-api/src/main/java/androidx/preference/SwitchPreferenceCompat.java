package androidx.preference;

import android.content.Context;
import android.util.AttributeSet;

public class SwitchPreferenceCompat extends TwoStatePreference {
	public SwitchPreferenceCompat(Context context) {
		super(context);
	}

	public SwitchPreferenceCompat(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
}
