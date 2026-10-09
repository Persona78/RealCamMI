package net.sourceforge.opencamera.realcammi;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.os.Build;
import android.util.Log;

import net.sourceforge.opencamera.realcammi.cameracontroller.CameraControllerManager;
import net.sourceforge.opencamera.realcammi.cameracontroller.CameraControllerManager2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** [REALCAMMI FORK 2026-09-12] Read-only Camera2 diagnostic, added specifically to answer one
 *  question before any stereo-depth / cross-lens work is attempted: can this device's HAL stream
 *  two camera devices (e.g. the main 200MP sensor and the hidden ultrawide) AT THE SAME TIME?
 *
 *  This does NOT open a camera or start a capture session - it only queries CameraManager /
 *  CameraCharacteristics, both of which are safe to call regardless of whether a camera is
 *  currently in use elsewhere in the app. Every call is wrapped so a failure on one device/ID
 *  never breaks the rest of the report - the whole point of this class is to be trustworthy
 *  when things are unsupported/absent, not just when everything works.
 *
 *  Background for why this exists (see project notes, 2026-09): a Camera2Probe dump of this
 *  fork's target Xiaomi Garnet device showed the back camera's available_capabilities list does
 *  NOT include LOGICAL_MULTI_CAMERA, meaning the official Android Multi-Camera API
 *  (OutputConfiguration#setPhysicalCameraId, single capture session, potential hardware-level
 *  frame sync) is not available on that device for main+ultrawide+macro - those extra lenses are
 *  only reachable via the undocumented hidden-numeric-ID probing this fork's
 *  CameraControllerManager2.buildCameraIdList() already does. The remaining, weaker possibility
 *  is CameraManager#getConcurrentCameraIds() (API 30+, no hardware sync guarantee at all) - this
 *  class checks that directly, since a static capabilities dump can't answer it (concurrency
 *  support is a runtime query, not a fixed characteristic).
 */
public class CameraDiagnostics {
    private static final String TAG = "CameraDiagnostics";

    /** Builds the full human-readable report. Never throws - any failure is captured as a line
     *  in the report itself, so the caller can always show/log something useful. */
    public static String buildReport(Context context, MainActivity main_activity) {
        StringBuilder sb = new StringBuilder();
        sb.append("RealCamMI camera concurrency diagnostic\n");
        sb.append("Android ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append("----------------------------------------\n");

        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if( manager == null ) {
            sb.append("ERROR: could not obtain CameraManager from system service.\n");
            return sb.toString();
        }

        // ---- Section 1: official getCameraIdList() + per-ID capabilities ----
        sb.append("\n[1] Official camera IDs (CameraManager.getCameraIdList()):\n");
        String[] official_ids;
        try {
            official_ids = manager.getCameraIdList();
        }
        catch(CameraAccessException e) {
            sb.append("  ERROR calling getCameraIdList(): ").append(e.getMessage()).append('\n');
            official_ids = new String[0];
        }
        if( official_ids.length == 0 ) {
            sb.append("  (none returned)\n");
        }
        for(String id : official_ids) {
            appendCameraSummary(sb, manager, id, "  ");
        }

        // ---- Section 2: this fork's own extended ID list (official + hidden-probed) ----
        // Reuses CameraControllerManager2's own already-validated list instead of re-probing
        // independently, so this report reflects exactly what the app itself would try to use.
        sb.append("\n[2] RealCamMI's own extended camera list (includes hidden-probed IDs,\n");
        sb.append("    see CameraControllerManager2.buildCameraIdList()):\n");
        List<String> fork_ids = new ArrayList<>();
        try {
            CameraControllerManager base_manager = main_activity.getPreview().getCameraControllerManager();
            if( base_manager instanceof CameraControllerManager2 ) {
                CameraControllerManager2 manager2 = (CameraControllerManager2) base_manager;
                int n = manager2.getNumberOfCameras();
                for(int i = 0; i < n; i++) {
                    String id = manager2.getCameraIdString(i);
                    if( id != null ) {
                        fork_ids.add(id);
                        boolean is_hidden = !containsId(official_ids, id);
                        sb.append("  index ").append(i).append(" -> id \"").append(id).append('"');
                        if( is_hidden )
                            sb.append("  [HIDDEN - not in official getCameraIdList()]");
                        sb.append('\n');
                        if( is_hidden ) {
                            // [REALCAMMI FORK 2026-09-12] These are exactly the fields needed to
                            // do stereo calibration using the SYSTEM's own numbers instead of a
                            // manual chessboard shoot - only worth checking for hidden ids, since
                            // section [1] already told us LOGICAL_MULTI_CAMERA=false for the
                            // official ids, which is normally a precondition for these being
                            // populated at all. Checking anyway rather than assuming, since a
                            // manufacturer could technically fill them in without declaring the
                            // logical-multi-camera capability.
                            appendCalibrationFields(sb, manager, id, "    ");
                        }
                    }
                }
            }
            else {
                sb.append("  (CameraControllerManager is not a CameraControllerManager2 instance -\n");
                sb.append("   unexpected on this fork's minSdk 30 build; skipping this section)\n");
            }
        }
        catch(Throwable e) {
            sb.append("  ERROR reading fork's camera list: ").append(e.getMessage()).append('\n');
        }

        // ---- Section 3: CameraManager.getConcurrentCameraIds() (API 30+) ----
        sb.append("\n[3] CameraManager.getConcurrentCameraIds() - combinations of camera IDs the\n");
        sb.append("    HAL will let one app stream from AT THE SAME TIME (no sync guarantee -\n");
        sb.append("    see note below):\n");
        Set<Set<String>> concurrent_sets = null;
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ) {
            try {
                concurrent_sets = manager.getConcurrentCameraIds();
            }
            catch(Throwable e) {
                sb.append("  ERROR calling getConcurrentCameraIds(): ").append(e.getMessage()).append('\n');
            }
            if( concurrent_sets != null ) {
                if( concurrent_sets.isEmpty() ) {
                    sb.append("  (empty - this device reports NO camera IDs can be streamed\n");
                    sb.append("   concurrently by a single app)\n");
                }
                else {
                    for(Set<String> combo : concurrent_sets) {
                        sb.append("  combo: ").append(combo).append('\n');
                    }
                }
            }
        }
        else {
            sb.append("  (unavailable - requires Android 11 / API 30, this device/build reports SDK ")
                    .append(Build.VERSION.SDK_INT).append(")\n");
        }

        // ---- Section 4: verdict, cross-checking hidden IDs against the concurrent sets ----
        sb.append("\n[4] Verdict for hidden IDs specifically (ultrawide/macro):\n");
        boolean any_hidden = false;
        for(String id : fork_ids) {
            if( !containsId(official_ids, id) ) {
                any_hidden = true;
                boolean in_some_combo = false;
                if( concurrent_sets != null ) {
                    for(Set<String> combo : concurrent_sets) {
                        if( combo.contains(id) ) {
                            in_some_combo = true;
                            break;
                        }
                    }
                }
                sb.append("  id \"").append(id).append("\": ")
                        .append(in_some_combo ? "APPEARS in a concurrent combo" : "does NOT appear in any concurrent combo")
                        .append('\n');
            }
        }
        if( !any_hidden ) {
            sb.append("  (no hidden IDs found in section [2] to check)\n");
        }
        sb.append("\nNote: per Android's own documentation for getConcurrentCameraIds(), the\n");
        sb.append("returned combinations never include physical IDs that only exist as part of a\n");
        sb.append("logical multi-camera device - so a hidden ID not appearing above doesn't\n");
        sb.append("necessarily mean concurrent capture is impossible, only that it isn't exposed\n");
        sb.append("through this particular API on this device. A conclusive answer requires\n");
        sb.append("actually attempting to open two CameraDevice instances at once and observing\n");
        sb.append("whether the second open() succeeds or the first gets disconnected - this\n");
        sb.append("report intentionally does not attempt that (it would interrupt any camera\n");
        sb.append("session already in progress), but it's the logical next test if this section\n");
        sb.append("looks promising.\n");

        return sb.toString();
    }

    private static boolean containsId(String[] ids, String id) {
        for(String s : ids) {
            if( s.equals(id) )
                return true;
        }
        return false;
    }

    private static void appendCalibrationFields(StringBuilder sb, CameraManager manager, String id, String indent) {
        try {
            CameraCharacteristics chars = manager.getCameraCharacteristics(id);

            float[] pose_translation = chars.get(CameraCharacteristics.LENS_POSE_TRANSLATION);
            sb.append(indent).append("LENS_POSE_TRANSLATION (metres, x/y/z from a common reference): ")
                    .append(pose_translation == null ? "not reported" : Arrays.toString(pose_translation)).append('\n');

            float[] pose_rotation = chars.get(CameraCharacteristics.LENS_POSE_ROTATION);
            sb.append(indent).append("LENS_POSE_ROTATION (quaternion): ")
                    .append(pose_rotation == null ? "not reported" : Arrays.toString(pose_rotation)).append('\n');

            float[] intrinsic = chars.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION);
            sb.append(indent).append("LENS_INTRINSIC_CALIBRATION (fx, fy, cx, cy, skew): ")
                    .append(intrinsic == null ? "not reported" : Arrays.toString(intrinsic)).append('\n');

            float[] distortion = chars.get(CameraCharacteristics.LENS_DISTORTION);
            sb.append(indent).append("LENS_DISTORTION: ")
                    .append(distortion == null ? "not reported" : Arrays.toString(distortion)).append('\n');

            if( pose_translation == null && pose_rotation == null && intrinsic == null ) {
                sb.append(indent).append("-> none of the system stereo-calibration fields are populated for this id.\n");
                sb.append(indent).append("   Manual calibration (chessboard/checkerboard pattern) will be needed.\n");
            }
        }
        catch(Throwable e) {
            sb.append(indent).append("ERROR reading calibration fields: ").append(e.getMessage()).append('\n');
        }
    }

    private static void appendCameraSummary(StringBuilder sb, CameraManager manager, String id, String indent) {
        try {
            CameraCharacteristics chars = manager.getCameraCharacteristics(id);
            Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
            String facing_str;
            if( facing == null ) facing_str = "unknown";
            else switch( facing ) {
                case CameraMetadata.LENS_FACING_FRONT: facing_str = "FRONT"; break;
                case CameraMetadata.LENS_FACING_BACK: facing_str = "BACK"; break;
                case CameraMetadata.LENS_FACING_EXTERNAL: facing_str = "EXTERNAL"; break;
                default: facing_str = "unknown(" + facing + ")";
            }

            int[] capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            boolean is_logical_multi_camera = false;
            if( capabilities != null ) {
                for(int cap : capabilities) {
                    if( cap == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA ) {
                        is_logical_multi_camera = true;
                        break;
                    }
                }
            }

            sb.append(indent).append("id \"").append(id).append("\": facing=").append(facing_str)
                    .append(", LOGICAL_MULTI_CAMERA=").append(is_logical_multi_camera).append('\n');

            if( is_logical_multi_camera ) {
                Set<String> physical_ids = chars.getPhysicalCameraIds();
                sb.append(indent).append("  physical sub-IDs: ").append(physical_ids).append('\n');
            }
        }
        catch(Throwable e) {
            sb.append(indent).append("id \"").append(id).append("\": ERROR reading characteristics: ")
                    .append(e.getMessage()).append('\n');
        }
    }

    /** Splits the report into Logcat-safe chunks (Logcat truncates long single lines/messages
     *  on some devices) and logs it line by line, so the full report is always recoverable via
     *  `adb logcat` even if the user doesn't copy the on-screen dialog text. */
    public static void logReport(String report) {
        for(String line : report.split("\n")) {
            Log.i(TAG, line);
        }
    }
}
