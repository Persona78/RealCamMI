package net.sourceforge.opencamera.realcammi;

import android.app.Application;
import android.os.Process;
import android.util.Log;

// [REALCAMMI FORK] OpenCV initialisation
import org.opencv.android.OpenCVLoader;

/** We override the Application class to implement the workaround at
 *  https://issuetracker.google.com/issues/36972466#comment14 for Google bug crash. It seems ugly,
 *  but Google consider this a low priority despite calling these "bad behaviours" in applications!
 */
public class OpenCameraApplication extends Application {
    private static final String TAG = "realcammiApplication";

    @Override
    public void onCreate() {
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate");
        super.onCreate();
        checkAppReplacingState();
        initOpenCV();
    }

    /** Initialise OpenCV native libraries.
     *  Uses OpenCVLoader.initLocal() which loads the bundled OpenCV native libs
     *  directly from the app's own APK — no external OpenCV Manager app required.
     *  Called once at app start; safe to call even if OpenCV features are not enabled.
     */
    private void initOpenCV() {
        if( OpenCVLoader.initDebug() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "OpenCV initialised successfully, version: " + OpenCVLoader.OPENCV_VERSION);
        }
        else {
            Log.e(TAG, "OpenCV initialisation failed — OpenCV post-processing features will be unavailable");
        }
    }

    private void checkAppReplacingState() {
        if( MyDebug.LOG )
            Log.d(TAG, "checkAppReplacingState");
        if( getResources() == null ) {
            Log.e(TAG, "app is replacing, kill");
            Process.killProcess(Process.myPid());
        }
    }
}
