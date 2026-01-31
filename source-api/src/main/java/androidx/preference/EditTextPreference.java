package androidx.preference;

import android.content.Context;
import android.util.AttributeSet;

public class EditTextPreference extends DialogPreference {
	private String text;

	public EditTextPreference(Context context) {
		super(context);
	}

	public EditTextPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public void setText(String text) {
		this.text = text;
	}

	public String getText() {
		return text;
	}
}
