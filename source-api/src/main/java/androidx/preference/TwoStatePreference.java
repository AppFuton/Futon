package androidx.preference;

import android.content.Context;
import android.util.AttributeSet;

public abstract class TwoStatePreference extends Preference {
	private boolean checked;

	public TwoStatePreference(Context context) {
		super(context);
	}

	public TwoStatePreference(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public void setChecked(boolean checked) {
		this.checked = checked;
	}

	public boolean isChecked() {
		return checked;
	}
}
