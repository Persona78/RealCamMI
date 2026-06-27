package net.sourceforge.opencamera.realcammi;

import android.os.Bundle;
import android.os.Build;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import android.util.Log;

public class PreferenceSubRemoteCtrl extends PreferenceSubScreen {
    private static final String TAG = "PreferenceSubRemoteCtrl";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_sub_remote_ctrl, rootKey);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.N ) {
            PreferenceGroup preference_screen = getPreferenceScreen();
            Preference remote_video_mode_pref = findPreference(PreferenceKeys.RemoteVideoMode);
            if( preference_screen != null && remote_video_mode_pref != null ) {
                preference_screen.removePreference(remote_video_mode_pref);
            }
        }
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate done");
    }
}
