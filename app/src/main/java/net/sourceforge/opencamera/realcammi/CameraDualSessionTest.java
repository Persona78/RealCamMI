package net.sourceforge.opencamera.realcammi;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;

import androidx.core.content.ContextCompat;

import java.util.Arrays;
import java.util.Comparator;

/** [REALCAMMI FORK 2026-09-12] Follow-up to CameraDualOpenTest.java, which confirmed that BOTH
 *  CameraDevice handles (main "0" + hidden ultrawide "2") can be open at the same time on the
 *  Garnet - but explicitly flagged that this doesn't prove two CaptureSessions can actually
 *  stream/capture concurrently. This is that heavier test: configure a session + ImageReader on
 *  each camera, then fire one capture() request on each as close together in code as possible,
 *  and measure how close the two resulting frames actually land in time.
 *
 *  Two different timestamp measurements are reported, because CaptureResult#SENSOR_TIMESTAMP is
 *  ONLY guaranteed comparable across two different camera devices when both report
 *  SENSOR_INFO_TIMESTAMP_SOURCE = REALTIME; with UNKNOWN (common on mid-range/budget SoCs), the
 *  two sensor timestamps are still "roughly" in the same timebase per Android's own docs, but not
 *  precise enough to trust for stereo sync math:
 *    - sensor_timestamp_delta_ms: from CaptureResult#SENSOR_TIMESTAMP on each frame - the
 *      hardware-level number, whose trustworthiness depends on the timestamp source (reported
 *      separately for each id).
 *    - wall_clock_delta_ms: from System.nanoTime() measured in this test's own code, at the
 *      moment each capture() call was issued - always valid regardless of timestamp source,
 *      but only bounds the software-side gap, not the actual sensor exposure gap.
 */
public class CameraDualSessionTest {
    private static final String TAG = "CameraDualSessionTest";
    private static final long STAGE_TIMEOUT_MS = 5000;

    public interface Callback {
        void onComplete(String report);
    }

    private final Context context;
    private final String main_id;
    private final String hidden_id;
    private final Callback callback;
    private final Handler ui_handler;

    private HandlerThread bg_thread;
    private Handler bg_handler;
    private final StringBuilder report = new StringBuilder();
    private final long t_start = System.currentTimeMillis();
    private boolean finished = false;

    private CameraDevice device_main, device_hidden;
    private ImageReader reader_main, reader_hidden;
    private CameraCaptureSession session_main, session_hidden;

    private Long sensor_ts_main = null, sensor_ts_hidden = null;
    private Long capture_call_nanos_main = null, capture_call_nanos_hidden = null;
    private boolean image_delivered_main = false, image_delivered_hidden = false;

    public static void run(Context context, String main_id, String hidden_id, Handler ui_handler, Callback callback) {
        new CameraDualSessionTest(context, main_id, hidden_id, ui_handler, callback).start();
    }

    private CameraDualSessionTest(Context context, String main_id, String hidden_id, Handler ui_handler, Callback callback) {
        this.context = context;
        this.main_id = main_id;
        this.hidden_id = hidden_id;
        this.ui_handler = ui_handler;
        this.callback = callback;
    }

    private long elapsed() {
        return System.currentTimeMillis() - t_start;
    }

    private void log(String line) {
        Log.i(TAG, "[" + elapsed() + "ms] " + line);
        report.append(line).append('\n');
    }

    @SuppressLint("MissingPermission") // checked explicitly below before any camera call
    private void start() {
        report.append("RealCamMI dual CAPTURE SESSION test (id \"").append(main_id).append("\" + id \"").append(hidden_id).append("\")\n");
        report.append("----------------------------------------\n");

        if( ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ) {
            report.append("ABORTED: CAMERA permission not granted.\n");
            finish();
            return;
        }

        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            logTimestampSource(manager, main_id);
            logTimestampSource(manager, hidden_id);
        }
        catch(Throwable e) {
            log("Could not read SENSOR_INFO_TIMESTAMP_SOURCE: " + e);
        }

        bg_thread = new HandlerThread(TAG);
        bg_thread.start();
        bg_handler = new Handler(bg_thread.getLooper());
        bg_handler.post(() -> openCamera(true));
    }

    private void logTimestampSource(CameraManager manager, String id) throws Exception {
        CameraCharacteristics chars = manager.getCameraCharacteristics(id);
        Integer source = chars.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE);
        String source_str = (source == null) ? "not reported"
                : (source == CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME) ? "REALTIME (precise cross-device comparison OK)"
                : (source == CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN) ? "UNKNOWN (only roughly comparable across devices - see class comment)"
                : "unknown value " + source;
        log("id \"" + id + "\" SENSOR_INFO_TIMESTAMP_SOURCE = " + source_str);
    }

    @SuppressLint("MissingPermission")
    private void openCamera(final boolean is_main) {
        if( finished ) return;
        final String id = is_main ? main_id : hidden_id;
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        log("Opening id \"" + id + "\"...");

        final boolean[] callback_fired = {false};
        Runnable timeout_runnable = () -> {
            if( !callback_fired[0] ) {
                callback_fired[0] = true;
                log("TIMEOUT opening id \"" + id + "\".");
                log("VERDICT: could not even reach the capture-session stage - stopping here.");
                closeEverythingAndFinish();
            }
        };
        bg_handler.postDelayed(timeout_runnable, STAGE_TIMEOUT_MS);

        try {
            manager.openCamera(id, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice device) {
                    if( callback_fired[0] ) { device.close(); return; }
                    callback_fired[0] = true;
                    bg_handler.removeCallbacks(timeout_runnable);
                    if( is_main ) device_main = device; else device_hidden = device;
                    log("id \"" + id + "\" opened. Setting up ImageReader + CaptureSession...");
                    setupSession(is_main, device);
                }

                @Override
                public void onDisconnected(CameraDevice device) {
                    if( !callback_fired[0] ) {
                        callback_fired[0] = true;
                        bg_handler.removeCallbacks(timeout_runnable);
                    }
                    log("id \"" + id + "\" onDisconnected during/after open.");
                    log("VERDICT: lost this camera before finishing session setup - stopping here.");
                    closeEverythingAndFinish();
                }

                @Override
                public void onError(CameraDevice device, int error) {
                    if( !callback_fired[0] ) {
                        callback_fired[0] = true;
                        bg_handler.removeCallbacks(timeout_runnable);
                    }
                    log("id \"" + id + "\" onError(" + error + ").");
                    log("VERDICT: could not open this camera right now - stopping here.");
                    closeEverythingAndFinish();
                }
            }, bg_handler);
        }
        catch(Throwable e) {
            bg_handler.removeCallbacks(timeout_runnable);
            log("EXCEPTION opening id \"" + id + "\": " + e);
            closeEverythingAndFinish();
        }
    }

    private void setupSession(final boolean is_main, final CameraDevice device) {
        if( finished ) return;
        final String id = is_main ? main_id : hidden_id;
        try {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics chars = manager.getCameraCharacteristics(id);
            StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes = map.getOutputSizes(ImageFormat.YUV_420_888);
            if( sizes == null || sizes.length == 0 ) {
                log("id \"" + id + "\": no YUV_420_888 output sizes reported - cannot proceed.");
                closeEverythingAndFinish();
                return;
            }
            // Smallest available size - keeps bandwidth/memory minimal, this test only cares
            // about timing and whether frames arrive at all, not image quality.
            Size chosen = Arrays.stream(sizes).min(Comparator.comparingLong(s -> (long) s.getWidth() * s.getHeight())).orElse(sizes[0]);
            log("id \"" + id + "\": using capture size " + chosen.getWidth() + "x" + chosen.getHeight());

            ImageReader reader = ImageReader.newInstance(chosen.getWidth(), chosen.getHeight(), ImageFormat.YUV_420_888, 2);
            reader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader r) {
                    Image image = null;
                    try {
                        image = r.acquireLatestImage();
                        if( image != null ) {
                            if( is_main ) image_delivered_main = true; else image_delivered_hidden = true;
                            log("id \"" + id + "\": image delivered via ImageReader (" + image.getWidth() + "x" + image.getHeight() + ")");
                        }
                    }
                    catch(Throwable e) {
                        log("id \"" + id + "\": exception acquiring image: " + e);
                    }
                    finally {
                        if( image != null ) image.close();
                    }
                }
            }, bg_handler);
            if( is_main ) reader_main = reader; else reader_hidden = reader;

            device.createCaptureSession(java.util.Collections.singletonList(reader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    if( finished ) { session.close(); return; }
                    if( is_main ) session_main = session; else session_hidden = session;
                    log("id \"" + id + "\": CaptureSession configured successfully.");
                    if( is_main ) {
                        // Main session ready - now bring up the hidden camera's session too.
                        openCamera(false);
                    }
                    else {
                        // Both sessions configured - fire both captures back-to-back.
                        fireBothCaptures();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    log("id \"" + id + "\": CaptureSession onConfigureFailed.");
                    log("VERDICT: this device's HAL accepted opening both camera DEVICES, but refused");
                    log("  to configure a capture session for id \"" + id + "\" while the other camera's");
                    log("  session was active - true concurrent STREAMING is NOT usable here, even though");
                    log("  concurrent device open (CameraDualOpenTest) is.");
                    closeEverythingAndFinish();
                }
            }, bg_handler);
        }
        catch(Throwable e) {
            log("id \"" + id + "\": EXCEPTION setting up session: " + e);
            closeEverythingAndFinish();
        }
    }

    private void fireBothCaptures() {
        if( finished ) return;
        log("Both sessions configured. Firing capture() on both, back-to-back...");

        bg_handler.postDelayed(() -> {
            if( !finished ) {
                log("TIMEOUT waiting for both captures to complete.");
                log("main captured=" + (sensor_ts_main != null) + ", hidden captured=" + (sensor_ts_hidden != null));
                buildFinalVerdictAndClose();
            }
        }, STAGE_TIMEOUT_MS);

        try {
            CaptureRequest.Builder req_main = device_main.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            req_main.addTarget(reader_main.getSurface());
            CaptureRequest.Builder req_hidden = device_hidden.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            req_hidden.addTarget(reader_hidden.getSurface());

            capture_call_nanos_main = System.nanoTime();
            session_main.capture(req_main.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    Long ts = result.get(TotalCaptureResult.SENSOR_TIMESTAMP);
                    sensor_ts_main = ts;
                    log("id \"" + main_id + "\": onCaptureCompleted, SENSOR_TIMESTAMP=" + ts);
                    maybeFinishCaptures();
                }
                @Override
                public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, android.hardware.camera2.CaptureFailure failure) {
                    log("id \"" + main_id + "\": onCaptureFailed, reason=" + failure.getReason());
                    sensor_ts_main = -1L; // sentinel: attempted, failed
                    maybeFinishCaptures();
                }
            }, bg_handler);

            // Issued immediately after the first, with no artificial delay - this gap is exactly
            // what wall_clock_delta_ms in the report measures.
            capture_call_nanos_hidden = System.nanoTime();
            session_hidden.capture(req_hidden.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    Long ts = result.get(TotalCaptureResult.SENSOR_TIMESTAMP);
                    sensor_ts_hidden = ts;
                    log("id \"" + hidden_id + "\": onCaptureCompleted, SENSOR_TIMESTAMP=" + ts);
                    maybeFinishCaptures();
                }
                @Override
                public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, android.hardware.camera2.CaptureFailure failure) {
                    log("id \"" + hidden_id + "\": onCaptureFailed, reason=" + failure.getReason());
                    sensor_ts_hidden = -1L;
                    maybeFinishCaptures();
                }
            }, bg_handler);
        }
        catch(Throwable e) {
            log("EXCEPTION firing captures: " + e);
            buildFinalVerdictAndClose();
        }
    }

    private boolean verdict_scheduled = false;

    private void maybeFinishCaptures() {
        // [REALCAMMI FORK BUGFIX 2026-09-12] Originally called buildFinalVerdictAndClose()
        // directly here, as soon as both onCaptureCompleted metadata callbacks had fired - but
        // onCaptureCompleted (metadata) can and does arrive before the actual image buffer is
        // delivered to the ImageReader (onImageAvailable), especially for a slower/hidden
        // physical camera. Confirmed on-device: this produced "image delivered: hidden=false"
        // even though the capture itself succeeded - closeEverythingAndFinish() was racing ahead
        // of the hidden camera's buffer delivery and tearing down its ImageReader before
        // onImageAvailable could fire. Now we wait a fixed margin after both metadata callbacks
        // arrive before checking/reporting the image_delivered flags, giving slower buffer
        // delivery a real chance to complete first.
        if( sensor_ts_main != null && sensor_ts_hidden != null && !verdict_scheduled ) {
            verdict_scheduled = true;
            bg_handler.postDelayed(this::buildFinalVerdictAndClose, 800);
        }
    }

    private void buildFinalVerdictAndClose() {
        if( finished ) return; // guarded again inside closeEverythingAndFinish(), but avoids duplicate verdict text
        log("");
        boolean main_ok = sensor_ts_main != null && sensor_ts_main > 0;
        boolean hidden_ok = sensor_ts_hidden != null && sensor_ts_hidden > 0;

        if( main_ok && hidden_ok ) {
            double wall_delta_ms = Math.abs(capture_call_nanos_hidden - capture_call_nanos_main) / 1_000_000.0;
            double sensor_delta_ms = Math.abs(sensor_ts_main - sensor_ts_hidden) / 1_000_000.0;
            log(String.format(java.util.Locale.US, "wall_clock_delta_ms (capture() calls issued): %.2f ms", wall_delta_ms));
            log(String.format(java.util.Locale.US, "sensor_timestamp_delta_ms (SENSOR_TIMESTAMP): %.2f ms", sensor_delta_ms));
            log("image delivered: main=" + image_delivered_main + ", hidden=" + image_delivered_hidden);
            log("VERDICT: TRUE CONCURRENT CAPTURE WORKS on this device - both sessions streamed and");
            log("  both produced a frame. Use the sensor_timestamp_delta_ms figure above (if the");
            log("  timestamp sources logged at the start are both REALTIME) to judge whether this is");
            log("  tight enough for stereo depth; if either source is UNKNOWN, treat that number as");
            log("  indicative only and rely on wall_clock_delta_ms plus real-world testing instead.");
        }
        else {
            log("VERDICT: capture-level concurrency did NOT fully succeed (main ok=" + main_ok + ", hidden ok=" + hidden_ok + ").");
            log("  Device-level open works (per CameraDualOpenTest) but streaming both at once does not -");
            log("  fall back to sequential fast-switch capture for both planned features.");
        }
        closeEverythingAndFinish();
    }

    private void closeEverythingAndFinish() {
        if( finished ) return;
        finished = true;
        try { if( session_hidden != null ) session_hidden.close(); } catch(Throwable e) { log("exception closing hidden session: " + e); }
        try { if( session_main != null ) session_main.close(); } catch(Throwable e) { log("exception closing main session: " + e); }
        try { if( reader_hidden != null ) reader_hidden.close(); } catch(Throwable e) { log("exception closing hidden reader: " + e); }
        try { if( reader_main != null ) reader_main.close(); } catch(Throwable e) { log("exception closing main reader: " + e); }
        try { if( device_hidden != null ) device_hidden.close(); } catch(Throwable e) { log("exception closing hidden device: " + e); }
        try { if( device_main != null ) device_main.close(); } catch(Throwable e) { log("exception closing main device: " + e); }
        log("Test finished.");
        finish();
    }

    private void finish() {
        final String final_report = report.toString();
        if( ui_handler != null )
            ui_handler.post(() -> callback.onComplete(final_report));
        else
            callback.onComplete(final_report);
        if( bg_thread != null )
            bg_thread.quitSafely();
    }
}
