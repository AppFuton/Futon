package androidx.preference;

import android.content.Context;
import android.util.AttributeSet;

public class ListPreference extends DialogPreference {
	private CharSequence[] entries;
	private CharSequence[] entryValues;
	private String value;

	public ListPreference(Context context) {
		super(context);
	}

	public ListPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public void setEntries(CharSequence[] entries) {
		this.entries = entries;
	}

	public CharSequence[] getEntries() {
		return entries;
	}

	public void setEntryValues(CharSequence[] entryValues) {
		this.entryValues = entryValues;
	}

	public CharSequence[] getEntryValues() {
		return entryValues;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}

	public CharSequence getEntry() {
		return null;
	}
}
