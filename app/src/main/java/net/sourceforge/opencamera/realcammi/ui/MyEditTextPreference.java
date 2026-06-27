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
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import net.sourceforge.opencamera.realcammi.R;

/** Custom EditTextPreference that properly supports emoji on Android 10 and earlier.
 *  Android's built-in EditTextPreference allocates the EditText programmatically, so AppCompat
 *  cannot update it to support emoji. This implementation inflates the EditText from XML,
 *  allowing AppCompat to handle it correctly.
 *
 *  In AndroidX, the dialog logic is split into this preference class (data/state) and
 *  MyEditTextPreferenceDialog (the actual dialog fragment).
 */
public class MyEditTextPreference extends DialogPreference {

    private String dialogMessage = "";
    private final int inputType;

    private String value; // current saved value
    private boolean value_set;

    public MyEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        String namespace = "http://schemas.android.com/apk/res/android";

        int id = attrs.getAttributeResourceValue(namespace, "dialogMessage", 0);
        if( id > 0 )
            this.dialogMessage = context.getString(id);

        this.inputType = attrs.getAttributeIntValue(namespace, "inputType", EditorInfo.TYPE_NULL);

        setDialogLayoutResource(R.layout.myedittextpreference);
    }

    public String getDialogMessage() {
        return dialogMessage;
    }

    public int getInputType() {
        return inputType;
    }

    public String getText() {
        return value;
    }

    /** Called by the dialog fragment when the user confirms a new value. */
    public void onValueSelected(String newValue) {
        if( callChangeListener(newValue) ) {
            setValue(newValue);
        }
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

    /** Dialog fragment for MyEditTextPreference.
     *  AndroidX requires the dialog logic to live in a PreferenceDialogFragmentCompat subclass.
     *  Show this from MyPreferenceFragment.onDisplayPreferenceDialog().
     */
    public static class MyEditTextPreferenceDialog extends PreferenceDialogFragmentCompat {

        private EditText edittext;

        public static MyEditTextPreferenceDialog newInstance(String key) {
            MyEditTextPreferenceDialog fragment = new MyEditTextPreferenceDialog();
            Bundle args = new Bundle();
            args.putString(ARG_KEY, key);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        protected void onBindDialogView(View view) {
            super.onBindDialogView(view);

            MyEditTextPreference pref = (MyEditTextPreference) getPreference();

            this.edittext = view.findViewById(R.id.myedittextpreference_edittext);
            this.edittext.setInputType(pref.getInputType());

            TextView textView = view.findViewById(R.id.myedittextpreference_summary);
            textView.setText(pref.getDialogMessage());

            if( pref.getText() != null ) {
                this.edittext.setText(pref.getText());
            }
        }

        @Override
        public void onDialogClosed(boolean positiveResult) {
            if( positiveResult ) {
                MyEditTextPreference pref = (MyEditTextPreference) getPreference();
                String newValue = edittext.getText().toString();
                pref.onValueSelected(newValue);
            }
        }
    }
}
