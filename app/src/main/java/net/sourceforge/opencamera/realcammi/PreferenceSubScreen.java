package net.sourceforge.opencamera.realcammi;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.preference.DialogPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import android.util.Log;
import android.view.View;

import java.util.HashSet;

/** Must be used as the parent class for all sub-screens.
 */
public class PreferenceSubScreen extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener, DialogPreference.TargetFragment {
    private static final String TAG = "PreferenceSubScreen";

    private boolean edge_to_edge_mode = false;

    // see note for dialogs in MyPreferenceFragment
    protected final HashSet<AlertDialog> dialogs = new HashSet<>();

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // sub-screens load their own preferences in their own onCreatePreferences
    }

    /** Required by AndroidX PreferenceFragmentCompat to handle custom DialogPreference subclasses.
     * Defined here in the base class so all sub-screens inherit it automatically.
     */
    @Override
    public void onDisplayPreferenceDialog (androidx.preference.Preference preference) {
        // [REALCAMMI FORK BUGFIX 2026-07-21] A previous "fix" removed the two
        // dialog.setTargetFragment(this, 0) calls that used to be here, reasoning that
        // setTargetFragment() is deprecated (API 31+) and unnecessary since the dialogs resolve
        // their Preference via ARG_KEY, not via the target fragment. That's true for RESOLVING
        // the preference - but PreferenceDialogFragmentCompat.onCreate() separately requires a
        // callback implementing DialogPreference.TargetFragment (checked via getParentFragment(),
        // then getTargetFragment(), then the hosting Context, in that order) - with none of the
        // three satisfied, it throws "Target fragment must implement TargetFragment interface"
        // on every open, confirmed via logcat. Restored the calls (now paired with this class
        // implementing DialogPreference.TargetFragment above, so the interface check passes even
        // if a future androidx version stops honouring plain setTargetFragment). Every screen
        // that extends this class (Photo, GUI, Licences, Location, Preview, Processing, Remote
        // Control, Settings Manager, Video) was affected - any ArraySeekBarPreference/
        // MyEditTextPreference dialog on any of those screens crashed the app when opened.
        if (preference instanceof net.sourceforge.opencamera.realcammi.ui.ArraySeekBarPreference) {
            net.sourceforge.opencamera.realcammi.ui.ArraySeekBarPreference.ArraySeekBarPreferenceDialog dialog =
                    net.sourceforge.opencamera.realcammi.ui.ArraySeekBarPreference.ArraySeekBarPreferenceDialog.newInstance(preference.getKey());
            dialog.setTargetFragment(this, 0);
            dialog.show(getParentFragmentManager(), "ArraySeekBarPreferenceDialog");
        }
        else if (preference instanceof net.sourceforge.opencamera.realcammi.ui.MyEditTextPreference) {
            net.sourceforge.opencamera.realcammi.ui.MyEditTextPreference.MyEditTextPreferenceDialog dialog =
                    net.sourceforge.opencamera.realcammi.ui.MyEditTextPreference.MyEditTextPreferenceDialog.newInstance(preference.getKey());
            dialog.setTargetFragment(this, 0);
            dialog.show(getParentFragmentManager(), "MyEditTextPreferenceDialog");
        }
        else {
            super.onDisplayPreferenceDialog(preference);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        final Bundle bundle = getArguments();
        this.edge_to_edge_mode = bundle.getBoolean("edge_to_edge_mode");

        if( MyDebug.LOG )
            Log.d(TAG, "onCreate done");
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if( edge_to_edge_mode ) {
            MyPreferenceFragment.handleEdgeToEdge(view);
        }
    }

    @Override
    public void onDestroy() {
        if( MyDebug.LOG )
            Log.d(TAG, "onDestroy");
        super.onDestroy();

        MyPreferenceFragment.dismissDialogs(getParentFragmentManager(), dialogs);
    }

    public void onResume() {
        super.onResume();

        MyPreferenceFragment.setBackground(this);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getActivity());
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    /* See comment for MyPreferenceFragment.onSharedPreferenceChanged().
     */
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if( MyDebug.LOG )
            Log.d(TAG, "onSharedPreferenceChanged: " + key);

        if( key == null ) {
            // On Android 11+, when targetting Android 11+, this method is called with key==null
            // if preferences are cleared. Unclear if this happens here in practice, but return
            // just in case.
            return;
        }

        Preference pref = findPreference(key);
        MyPreferenceFragment.handleOnSharedPreferenceChanged(prefs, key, pref);
    }
}
