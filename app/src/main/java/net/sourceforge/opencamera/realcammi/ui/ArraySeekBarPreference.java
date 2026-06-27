package net.sourceforge.opencamera.realcammi.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.preference.DialogPreference;
import androidx.preference.PreferenceDialogFragmentCompat;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import net.sourceforge.opencamera.realcammi.R;

/** Custom preference that displays a seekbar in place of a ListPreference.
 *  In AndroidX, the dialog logic is split into this preference class (data/state)
 *  and ArraySeekBarPreferenceDialog (the actual dialog fragment).
 */
public class ArraySeekBarPreference extends DialogPreference {

    private CharSequence[] entries; // user-readable strings
    private CharSequence[] values;  // values corresponding to each string

    private final String default_value;
    private String value; // current saved value
    private boolean value_set;

    public ArraySeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        String namespace = "http://schemas.android.com/apk/res/android";
        this.default_value = attrs.getAttributeValue(namespace, "defaultValue");

        int entries_id = attrs.getAttributeResourceValue(namespace, "entries", 0);
        if( entries_id > 0 )
            this.setEntries(entries_id);
        int values_id = attrs.getAttributeResourceValue(namespace, "entryValues", 0);
        if( values_id > 0 )
            this.setEntryValues(values_id);

        setDialogLayoutResource(R.layout.arrayseekbarpreference);
    }

    public void setEntries(CharSequence[] entries) {
        this.entries = entries;
    }

    private void setEntries(int entries) {
        setEntries(getContext().getResources().getTextArray(entries));
    }

    public void setEntryValues(CharSequence[] values) {
        this.values = values;
    }

    private void setEntryValues(int values) {
        setEntryValues(getContext().getResources().getTextArray(values));
    }

    public CharSequence[] getEntries() {
        return entries;
    }

    public CharSequence[] getEntryValues() {
        return values;
    }

    public String getDefaultValue() {
        return default_value;
    }

    public String getValue() {
        return value;
    }

    /** Called by the dialog fragment when the user confirms a new value. */
    public void onValueSelected(String newValue) {
        if( callChangeListener(newValue) ) {
            setValue(newValue);
        }
    }

    @Override
    public CharSequence getSummary() {
        CharSequence summary = super.getSummary();
        if( summary != null ) {
            CharSequence entry = getEntry();
            return String.format(summary.toString(), entry == null ? "" : entry);
        }
        else
            return null;
    }

    /** Returns the index of the current value in the values array, or -1 if not found. */
    public int getValueIndex() {
        if( value != null && values != null ) {
            for(int i = values.length - 1; i >= 0; i--) {
                if( values[i].equals(value) ) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** Returns the human-readable string of the current value. */
    private CharSequence getEntry() {
        int index = getValueIndex();
        return index >= 0 && entries != null ? entries[index] : null;
    }

    private void setValue(String value) {
        final boolean changed = !TextUtils.equals(this.value, value);
        if( changed || !value_set ) {
            this.value = value;
            value_set = true;
            persistString(value);
            if( changed ) {
                notifyChanged();
            }
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        setValue(restoreValue ? getPersistedString(value) : (String) defaultValue);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if( isPersistent() ) {
            return superState;
        }
        final SavedState state = new SavedState(superState);
        state.value = value;
        return state;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if( state == null || !state.getClass().equals(SavedState.class) ) {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState myState = (SavedState)state;
        super.onRestoreInstanceState(myState.getSuperState());
        setValue(myState.value);
    }

    private static class SavedState extends BaseSavedState {
        String value;

        SavedState(Parcel source) {
            super(source);
            value = source.readString();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(value);
        }

        SavedState(Parcelable superState) {
            super(superState);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<>() {
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }
                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }

    // =========================================================================
    // Inner dialog fragment (AndroidX replacement for onBindDialogView/onDialogClosed)
    // =========================================================================

    /** Dialog fragment for ArraySeekBarPreference.
     *  AndroidX requires the dialog logic to live in a PreferenceDialogFragmentCompat subclass.
     *  Show this from MyPreferenceFragment.onDisplayPreferenceDialog().
     */
    public static class ArraySeekBarPreferenceDialog extends PreferenceDialogFragmentCompat {

        private SeekBar seekbar;
        private TextView textView;

        public static ArraySeekBarPreferenceDialog newInstance(String key) {
            ArraySeekBarPreferenceDialog fragment = new ArraySeekBarPreferenceDialog();
            Bundle args = new Bundle();
            args.putString(ARG_KEY, key);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        protected void onBindDialogView(View view) {
            super.onBindDialogView(view);

            ArraySeekBarPreference pref = (ArraySeekBarPreference) getPreference();
            CharSequence[] entries = pref.getEntries();
            CharSequence[] values = pref.getEntryValues();

            if( entries == null || values == null ) {
                throw new IllegalStateException("ArraySeekBarPreference requires entries and entryValues arrays");
            }
            else if( entries.length != values.length ) {
                throw new IllegalStateException("ArraySeekBarPreference requires entries and entryValues arrays of same length");
            }

            this.seekbar = view.findViewById(R.id.arrayseekbarpreference_seekbar);
            this.textView = view.findViewById(R.id.arrayseekbarpreference_value);

            seekbar.setMax(entries.length - 1);

            int index = pref.getValueIndex();
            if( index == -1 ) {
                // Stored value not in array — fall back to default
                String default_value = pref.getDefaultValue();
                if( default_value != null ) {
                    for(int i = values.length - 1; i >= 0; i--) {
                        if( values[i].equals(default_value) ) {
                            index = i;
                            break;
                        }
                    }
                }
            }
            if( index >= 0 )
                seekbar.setProgress(index);

            seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                private long last_haptic_time;

                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    textView.setText(entries[progress].toString());
                    if( fromUser ) {
                        last_haptic_time = MainUI.performHapticFeedback(seekBar, last_haptic_time);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            textView.setText(entries[seekbar.getProgress()].toString());
        }

        @Override
        public void onDialogClosed(boolean positiveResult) {
            if( positiveResult ) {
                ArraySeekBarPreference pref = (ArraySeekBarPreference) getPreference();
                CharSequence[] values = pref.getEntryValues();
                if( values != null ) {
                    String newValue = values[seekbar.getProgress()].toString();
                    pref.onValueSelected(newValue);
                }
            }
        }
    }
}
