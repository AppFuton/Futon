package androidx.preference;

import android.content.Context;
import android.util.AttributeSet;

public class Preference {
	private CharSequence title;
	private CharSequence summary;
	private Object defaultValue;
	private String key;
	private boolean enabled = true;
	private boolean visible = true;

	public Preference(Context context) {
		this(context, null);
	}

	public Preference(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public Preference(Context context, AttributeSet attrs, int defStyleAttr) {
	}

	public Context getContext() {
		return null;
	}

	public void setTitle(CharSequence title) {
		this.title = title;
	}

	public void setTitle(int titleResId) {
	}

	public CharSequence getTitle() {
		return title;
	}

	public void setSummary(CharSequence summary) {
		this.summary = summary;
	}

	public void setSummary(int summaryResId) {
	}

	public CharSequence getSummary() {
		return summary;
	}

	public void setDefaultValue(Object defaultValue) {
		this.defaultValue = defaultValue;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getKey() {
		return key;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	public boolean isVisible() {
		return visible;
	}

	public void setOnPreferenceChangeListener(OnPreferenceChangeListener listener) {
	}

	public void setOnPreferenceClickListener(OnPreferenceClickListener listener) {
	}

	public interface OnPreferenceChangeListener {
		boolean onPreferenceChange(Preference preference, Object newValue);
	}

	public interface OnPreferenceClickListener {
		boolean onPreferenceClick(Preference preference);
	}
}
