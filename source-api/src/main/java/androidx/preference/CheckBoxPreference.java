package androidx.preference;

import android.content.Context;
import android.util.AttributeSet;

public class CheckBoxPreference extends TwoStatePreference {
	public CheckBoxPreference(Context context) {
		super(context);
	}

	public CheckBoxPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
}
