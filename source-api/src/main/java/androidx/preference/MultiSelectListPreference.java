package androidx.preference;

import android.content.Context;
import android.util.AttributeSet;

import java.util.HashSet;
import java.util.Set;

public class MultiSelectListPreference extends DialogPreference {
	private CharSequence[] entries;
	private CharSequence[] entryValues;
	private Set<String> values;

	public MultiSelectListPreference(Context context) {
		super(context);
	}

	public MultiSelectListPreference(Context context, AttributeSet attrs) {
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

	public void setValues(Set<String> values) {
		this.values = values;
	}

	public Set<String> getValues() {
		return values != null ? values : new HashSet<>();
	}
}
