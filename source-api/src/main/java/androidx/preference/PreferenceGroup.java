package androidx.preference;

import android.content.Context;
import android.util.AttributeSet;

public class PreferenceGroup extends Preference {
	public PreferenceGroup(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public boolean addPreference(Preference preference) {
		return true;
	}

	public Preference findPreference(CharSequence key) {
		return null;
	}

	public int getPreferenceCount() {
		return 0;
	}

	public Preference getPreference(int index) {
		return null;
	}

	public boolean removePreference(Preference preference) {
		return true;
	}

	public void removeAll() {
	}
}
