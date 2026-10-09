package net.sourceforge.opencamera.realcammi;

import android.content.ContentValues;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import androidx.preference.PreferenceManager;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.Map;

/** Code for options for saving and restoring settings.
 */
public class SettingsManager {
    private static final String TAG = "SettingsManager";

    private final MainActivity main_activity;

    SettingsManager(MainActivity main_activity) {
        this.main_activity = main_activity;
    }

    private final static String doc_tag = "open_camera_prefs";
    private final static String boolean_tag = "boolean";
    private final static String float_tag = "float";
    private final static String int_tag = "int";
    private final static String long_tag = "long";
    private final static String string_tag = "string";
    // [REALCAMMI FIX 2026-09-16] Android SharedPreferences supports 6 value types; this file only
    // handled 5 (missing Set<String>, the type behind MultiSelectListPreference). No current
    // RealCamMI preference uses it, so this wasn't silently dropping anything from today's backups
    // - but any future multi-select preference would have hit the "unknown value type" branch
    // below in saveSettings() and been silently skipped, with nothing in the UI to say so. Added
    // for completeness/future-proofing, per the author's request that saved settings always match
    // the code, not the other way round.
    private final static String string_set_tag = "stringset";
    private final static String string_set_item_tag = "item";

    public boolean loadSettings(String file) {
        if( MyDebug.LOG )
            Log.d(TAG, "loadSettings: " + file);
        InputStream inputStream;
        try {
            inputStream = new FileInputStream(file);
        }
        catch(FileNotFoundException e) {
            MyDebug.logStackTrace(TAG, "failed to load: " + file, e);
            main_activity.getPreview().showToast(null, R.string.restore_settings_failed);
            return false;
        }
        return loadSettings(inputStream);
    }

    public boolean loadSettings(Uri uri) {
        if( MyDebug.LOG )
            Log.d(TAG, "loadSettings: " + uri);
        InputStream inputStream;
        try {
            inputStream = main_activity.getContentResolver().openInputStream(uri);
        }
        catch(FileNotFoundException e) {
            MyDebug.logStackTrace(TAG, "failed to load: " + uri, e);
            main_activity.getPreview().showToast(null, R.string.restore_settings_failed);
            return false;
        }
        return loadSettings(inputStream);
    }

    /** Loads all settings from the supplied inputStream. If successful, RealCamMI will restart.
     *  The supplied inputStream will be closed.
     * @return Whether the operation was succesful.
     */
    private boolean loadSettings(InputStream inputStream) {
        if( MyDebug.LOG )
            Log.d(TAG, "loadSettings: " + inputStream);
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(inputStream, null);
            parser.nextTag();

            parser.require(XmlPullParser.START_TAG, null, doc_tag);
            /*if( true )
            	throw new IOException(); // test*/

            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.clear();

            while( parser.next() != XmlPullParser.END_TAG ) {
                if( parser.getEventType() != XmlPullParser.START_TAG)  {
                    continue;
                }
                String name = parser.getName();
                String key = parser.getAttributeValue(null, "key");
                if( MyDebug.LOG ) {
                    Log.d(TAG, "name: " + name);
                    Log.d(TAG, "    key: " + key);
                    Log.d(TAG, "    value: " + parser.getAttributeValue(null, "value"));
                }

                switch( name ) {
                    case boolean_tag:
                        editor.putBoolean(key, Boolean.parseBoolean(parser.getAttributeValue(null, "value")));
                        break;
                    case float_tag:
                        editor.putFloat(key, Float.parseFloat(parser.getAttributeValue(null, "value")));
                        break;
                    case int_tag:
                        editor.putInt(key, Integer.parseInt(parser.getAttributeValue(null, "value")));
                        break;
                    case long_tag:
                        editor.putLong(key, Long.parseLong(parser.getAttributeValue(null, "value")));
                        break;
                    case string_tag:
                        editor.putString(key, parser.getAttributeValue(null, "value"));
                        break;
                    case string_set_tag: {
                        // [REALCAMMI FIX 2026-09-16] A <stringset key="..."> holds its values as
                        // nested <item value="..."/> children (unlike the other tag types above,
                        // which are self-closing with a single "value" attribute), since a
                        // Set<String> can hold any number of entries. Walk the nested items
                        // ourselves and consume through to our own matching end tag, then
                        // "continue" below to skip the shared skipXml() call at the bottom of the
                        // loop - it isn't needed here since we've already fully consumed this
                        // element, and calling it again would incorrectly try to skip the
                        // following sibling tag instead.
                        java.util.HashSet<String> string_set = new java.util.HashSet<>();
                        int depth = 1;
                        while( depth != 0 ) {
                            int event = parser.next();
                            if( event == XmlPullParser.START_TAG ) {
                                if( string_set_item_tag.equals(parser.getName()) ) {
                                    String item_value = parser.getAttributeValue(null, "value");
                                    if( item_value != null )
                                        string_set.add(item_value);
                                }
                                depth++;
                            }
                            else if( event == XmlPullParser.END_TAG ) {
                                depth--;
                            }
                        }
                        editor.putStringSet(key, string_set);
                        continue;
                    }
                    default:
                        break;
                }

                skipXml(parser);
            }

            // even though we're restoring from settings, we don't want the first time or what's new dialog showing up again!
            // important to do this after reading from xml, so that the keys aren't overwritten
            editor.putBoolean(PreferenceKeys.FirstTimePreferenceKey, true);
            try {
                PackageInfo pInfo = main_activity.getPackageManager().getPackageInfo(main_activity.getPackageName(), 0);
                int version_code = pInfo.versionCode;
                editor.putInt(PreferenceKeys.LatestVersionPreferenceKey, version_code);
            }
            catch(PackageManager.NameNotFoundException e) {
                MyDebug.logStackTrace(TAG, "NameNotFoundException exception trying to get version number", e);
            }

            editor.apply();
            if( !main_activity.is_test ) {
                // restarting seems to cause problems for test code (e.g., see testSettingsSaveLoad - even if that test is fine, it risks affecting subsequent tests)
                main_activity.restartOpenCamera();
            }
            return true;
        }
        catch(Exception e) {
            MyDebug.logStackTrace(TAG, "failed to restore settings", e);
            main_activity.getPreview().showToast(null, R.string.restore_settings_failed);
            return false;
        }
        finally {
            try {
                inputStream.close();
            }
            catch(IOException e) {
                MyDebug.logStackTrace(TAG, "failed to close inputStream", e);
            }
        }
    }

    private static void skipXml(XmlPullParser parser) throws XmlPullParserException, IOException {
        if( parser.getEventType() != XmlPullParser.START_TAG ) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    break;
            }
        }
    }

    public void saveSettings(String filename) {
        if( MyDebug.LOG )
            Log.d(TAG, "saveSettings: " + filename);

        // [REALCAMMI FIX 2026-09-20] Was written to StorageUtils.getSettingsFolder()
        // (context.getExternalFilesDir(null)+"/backups") - an app-private folder that Android
        // deletes on uninstall AND on "Clear storage" from Android's own per-app Settings screen
        // (independent of, and in addition to, this app's own in-app "Reset" button, which only
        // clears SharedPreferences and never touched this folder either way). A backup that lives
        // inside the app's own private storage can't survive the exact events - uninstall, clear
        // storage - that a backup exists to protect against, which defeats the point of it.
        //
        // Now written via MediaStore to the public Download/RealCamMI folder instead - the same
        // approach GCam uses (a dedicated subfolder under Download) - which survives app
        // uninstalls, "Clear storage", and reinstalls, since it isn't part of the app's storage at
        // all. Every device this fork targets is minSdkVersion 30, where MediaStore.Downloads is
        // always available, so this is expected to succeed essentially always; the old app-private
        // path is kept only as a defensive fallback if the MediaStore write itself fails for some
        // unexpected reason (some OEM storage restriction, for instance), so a save attempt still
        // produces *something* rather than failing outright - just with a toast that says so.
        StorageUtils storageUtils = main_activity.getStorageUtils();
        Uri media_store_uri = null;
        OutputStream outputStream = null;
        try {
            ContentValues content_values = new ContentValues();
            content_values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
            content_values.put(MediaStore.MediaColumns.MIME_TYPE, "text/xml");
            content_values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + File.separator + "RealCamMI");
            media_store_uri = main_activity.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, content_values);
            if( media_store_uri != null )
                outputStream = main_activity.getContentResolver().openOutputStream(media_store_uri);
        }
        catch(Exception e) {
            // Deliberately catching Exception, not just IOException: ContentResolver.insert()/
            // openOutputStream() can throw other unchecked exceptions on some OEM ROMs for
            // storage-permission-related failures, and any of them should fall back below rather
            // than propagate.
            MyDebug.logStackTrace(TAG, "failed to create settings file via MediaStore", e);
            media_store_uri = null;
            outputStream = null;
        }

        boolean used_media_store = (outputStream != null);
        File fallback_file = null;
        if( !used_media_store ) {
            try {
                File settings_folder = storageUtils.getSettingsFolder();
                // in theory the folder should have been created when choosing a name, but just in case...
                storageUtils.createFolderIfRequired(settings_folder);
                fallback_file = new File(settings_folder.getPath() + File.separator + filename);
                main_activity.test_save_settings_file = fallback_file.getAbsolutePath();
                outputStream = new FileOutputStream(fallback_file);
            }
            catch(IOException e) {
                MyDebug.logStackTrace(TAG, "failed to create fallback settings file", e);
                main_activity.getPreview().showToast(null, R.string.save_settings_failed);
                return;
            }
        }

        try {
            XmlSerializer xmlSerializer = Xml.newSerializer();

            StringWriter writer = new StringWriter();
            xmlSerializer.setOutput(writer);
            xmlSerializer.startDocument("UTF-8", true);
            xmlSerializer.startTag(null, doc_tag);

            // [REALCAMMI FORK 2026-08-31] Extra context for anyone inspecting a saved settings
            // file by hand (e.g. figuring out which device/app-version a backup came from before
            // restoring it). Written as a plain, unrecognised child tag (not an XML comment -
            // XmlSerializer.comment() is not guaranteed supported by every implementation, which
            // could throw IllegalStateException and break Save Settings entirely). loadSettings()'s
            // parser only acts on the 5 recognised tag names; anything else (like this one) is
            // safely skipped generically by depth, regardless of its name.
            try {
                PackageInfo pInfo = main_activity.getPackageManager().getPackageInfo(main_activity.getPackageName(), 0);
                xmlSerializer.startTag(null, "meta");
                xmlSerializer.attribute(null, "app_version", "RealCamMI " + pInfo.versionName + " (build " + pInfo.versionCode + ")");
                xmlSerializer.attribute(null, "device", Build.MANUFACTURER + " " + Build.MODEL);
                xmlSerializer.attribute(null, "android_version", Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
                xmlSerializer.attribute(null, "saved_at", new Date().toString());
                xmlSerializer.endTag(null, "meta");
            }
            catch(PackageManager.NameNotFoundException e) {
                MyDebug.logStackTrace(TAG, "failed to get version info for settings file meta tag", e);
            }

            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
            Map<String, ?> map = sharedPreferences.getAll();
            for( Map.Entry<String, ?> entry : map.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if( key != null ) {
                    String tag_type = null;
                    if( value instanceof Boolean ) {
                        tag_type = boolean_tag;
                    }
                    else if( value instanceof Float ) {
                        tag_type = float_tag;
                    }
                    else if( value instanceof Integer ) {
                        tag_type = int_tag;
                    }
                    else if( value instanceof Long ) {
                        tag_type = long_tag;
                    }
                    else if( value instanceof String ) {
                        tag_type = string_tag;
                    }
                    else if( value instanceof java.util.Set ) {
                        // [REALCAMMI FIX 2026-09-16] Handles Set<String> (e.g. a future
                        // MultiSelectListPreference) - see string_set_tag's comment above. Written
                        // as a parent tag with one <item value="..."/> child per set entry, since
                        // (unlike every other type here) it can hold any number of values under a
                        // single key, not just one.
                        xmlSerializer.startTag(null, string_set_tag);
                        xmlSerializer.attribute(null, "key", key);
                        for( Object item : (java.util.Set<?>)value ) {
                            xmlSerializer.startTag(null, string_set_item_tag);
                            xmlSerializer.attribute(null, "value", item == null ? "" : item.toString());
                            xmlSerializer.endTag(null, string_set_item_tag);
                        }
                        xmlSerializer.endTag(null, string_set_tag);
                        tag_type = null; // already fully written above, skip the generic single-tag path below
                    }
                    else {
                        Log.e(TAG, "unknown value type: " + value);
                    }

                    if( tag_type != null ) {
                        xmlSerializer.startTag(null, tag_type);
                        xmlSerializer.attribute(null, "key", key);
                        xmlSerializer.attribute(null, "value", value.toString());
                        xmlSerializer.endTag(null, tag_type);
                    }
                }
            }
            xmlSerializer.endTag(null, doc_tag);
            xmlSerializer.endDocument();
            xmlSerializer.flush();
            String dataWrite = writer.toString();
            outputStream.write(dataWrite.getBytes(Charset.forName("UTF-8")));

            if( used_media_store ) {
                main_activity.getPreview().showToast(null, main_activity.getResources().getString(R.string.saved_settings_downloads, filename));
                // MediaStore-inserted entries in the Downloads collection are visible immediately
                // once the stream is closed below - no separate broadcast/rescan needed the way
                // broadcastFile() below provides for the legacy File-based fallback path.
            }
            else {
                main_activity.getPreview().showToast(null, R.string.saved_settings);
                storageUtils.broadcastFile(fallback_file, false, false, false, false, null);
            }
        }
        catch(IOException e) {
            MyDebug.logStackTrace(TAG, "failed to save settings", e);
            main_activity.getPreview().showToast(null, R.string.save_settings_failed);
            // Clean up a half-written MediaStore entry rather than leaving an empty/corrupt file
            // behind in the user's Downloads folder.
            if( media_store_uri != null ) {
                try {
                    main_activity.getContentResolver().delete(media_store_uri, null, null);
                }
                catch(Exception e2) {
                    MyDebug.logStackTrace(TAG, "failed to clean up incomplete MediaStore entry", e2);
                }
            }
        }
        finally {
            if( outputStream != null ) {
                try {
                    outputStream.close();
                }
                catch(IOException e) {
                    MyDebug.logStackTrace(TAG, "failed to close outputStream", e);
                }
            }
        }
    }
}
