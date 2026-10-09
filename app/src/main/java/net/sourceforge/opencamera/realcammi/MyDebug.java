package net.sourceforge.opencamera.realcammi;

import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Helper class for logging with dynamic tagging support.
 */
public class MyDebug {
    public static final boolean LOG = false;
    // Default fallback tag
    public static final String DEFAULT_TAG = "OpenCameraDebug";

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    /** Standard debug message using a custom tag.
     */
    public static void d(String tag, String msg) {
        if( LOG ) {
            Log.d(tag, getMetaInfo() + msg);
        }
    }

    /** Standard debug message using the default tag.
     */
    public static void d(String msg) {
        if( LOG ) {
            Log.d(DEFAULT_TAG, getMetaInfo() + msg);
        }
    }

    /** Standard error message using a custom tag.
     */
    public static void e(String tag, String msg) {
        if( LOG ) {
            Log.e(tag, getMetaInfo() + msg);
        }
    }

    /** Wrapper to print exceptions with custom tag.
     */
    public static void logStackTrace(String tag, String msg, Throwable tr) {
        if( LOG ) {
            Log.e(tag, getMetaInfo() + msg, tr);
        }
    }

    /** Logs the current execution path using a custom tag.
     */
    public static void logStackTrace(String tag) {
        if( LOG ) {
            StackTraceElement[] elements = Thread.currentThread().getStackTrace();
            String meta = getMetaInfo();
            Log.d(tag, meta + "--- Execution Path Trace Start ---");
            for (int i = 2; i < elements.length; i++) {
                Log.d(tag, meta + "   at " + elements[i].toString());
            }
            Log.d(tag, meta + "--- Execution Path Trace End ---");
        }
    }

    private static String getMetaInfo() {
        Thread currentThread = Thread.currentThread();
        String threadInfo = "[" + currentThread.getName() + ":" + currentThread.getId() + "] ";
        String timeInfo = "[" + dateFormat.format(new Date()) + "] ";

        StackTraceElement[] stackTrace = currentThread.getStackTrace();
        if (stackTrace.length > 4) {
            StackTraceElement caller = stackTrace[4];
            String fullClassName = caller.getClassName();
            String simpleClassName = fullClassName.substring(fullClassName.lastIndexOf('.') + 1);
            return timeInfo + threadInfo + "[" + simpleClassName + "." + caller.getMethodName() + "():" + caller.getLineNumber() + "] ";
        }
        return timeInfo + threadInfo;
    }
}
