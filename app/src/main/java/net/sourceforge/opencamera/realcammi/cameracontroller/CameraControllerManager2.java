package net.sourceforge.opencamera.realcammi.cameracontroller;

import android.content.Context;
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;
import android.util.Size;
import android.util.SizeF;

import androidx.annotation.RequiresApi;

import net.sourceforge.opencamera.realcammi.MyDebug;
import net.sourceforge.opencamera.realcammi.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CameraControllerManager2 extends CameraControllerManager {
    private static final String TAG = "CControllerManager2";
    private final Context context;
    private final List<String> extractedCameraIds = new ArrayList<>();

    //************************************************************************
    // [REALCAMMI FORK] Device fingerprinting and custom camera ID validation.
    // buildCameraIdList(), isRealCamera(), supportsVideoRecording() below are
    // entirely fork-specific — not present in upstream Open Camera.
    private final boolean is_xiaomi;
    private final boolean is_ulefone;

    //************************************************************************
    // [REALCAMMI FORK] Samsung fingerprinting, used to disable Edge Mode on Galaxy S series
    private final boolean is_samsung;
    private final boolean is_samsung_galaxy_s;
    private final boolean is_samsung_galaxy_f;


    public CameraControllerManager2(Context context) {
        this.context = context;

        String build_model = Build.MODEL.toLowerCase(Locale.US);

        //this.is_oneplus = Build.MANUFACTURER.toLowerCase(Locale.US).contains("oneplus");
        this.is_samsung = Build.MANUFACTURER.toLowerCase(Locale.US).contains("samsung");
        this.is_samsung_galaxy_s = is_samsung && ( build_model.contains("sm-g") || build_model.contains("sm-s") );
        this.is_samsung_galaxy_f = is_samsung && build_model.contains("sm-f");

        // [REALCAMMI FORK]
        this.is_ulefone =
                Build.MANUFACTURER.toLowerCase(Locale.US).contains("ulefone") ||
                        Build.BRAND.toLowerCase(Locale.US).contains("ulefone") ||
                        Build.MODEL.contains("Armor 25T Pro") ||
                        Build.DEVICE.contains("gq5007af2_eea") ||
                        Build.MODEL.contains("Armor 25T") ||
                        Build.DEVICE.contains("gq5007tf1");

        this.is_xiaomi =
                Build.MANUFACTURER.toLowerCase(Locale.US).contains("xiaomi") ||
                        Build.BRAND.toLowerCase(Locale.US).contains("redmi") ||
                        // Redmi Note 14 series
                        Build.MODEL.contains("24116racc") ||  // Note 14 Pro
                        Build.MODEL.contains("24090ra29") ||  // Note 14 Pro 5G
                        Build.MODEL.contains("24115ra8e") ||  // Note 14 Pro+ 5G
                        Build.MODEL.contains("24117rn76") ||  // Note 14 5G
                        Build.MODEL.contains("24094raD4") ||  // Note 14 5G India
                        // Redmi Note 15 series
                        Build.MODEL.contains("kunzite") ||    // Note 15 5G
                        Build.MODEL.contains("lapis") ||      // Note 15 Pro 5G
                        Build.MODEL.contains("charoite") ||   // Note 15 Pro
                        Build.MODEL.contains("flourite") ||   // Note 15 Pro+ 5G
                        // Redmi Note 13
                        Build.DEVICE.contains("sapphire") ||
                        Build.MODEL.contains("23129raa4g") ||
                        Build.MODEL.contains("23129ra5fl") ||
                        // Redmi Note 13 4g nfc
                        Build.DEVICE.contains("sapphiren") ||
                        Build.MODEL.contains("23124ra7eo") ||
                        // Redmi 13C
                        Build.DEVICE.contains("gust") ||
                        Build.MODEL.contains("23108rn04y") ||
                        // poco x6 5g
                        Build.DEVICE.contains("garnet") ||
                        Build.MODEL.contains("23122pcd1g") ||
                        Build.MODEL.contains("23122pcd1i") ||
                        // Redmi Note 13 Pro
                        Build.MODEL.contains("2312dra50c") ||
                        Build.MODEL.contains("2312dra50g") ||
                        Build.MODEL.contains("2312dra50i") ||
                        Build.MODEL.contains("2312crad3c");

        if( MyDebug.LOG ) {
            Log.d(TAG, "is_xiaomi: " + is_xiaomi);
            Log.d(TAG, "is_ulefone: " + is_ulefone);
            Log.d(TAG, "is_samsung: " + is_samsung);
            Log.d(TAG, "is_samsung_galaxy_s: " + is_samsung_galaxy_s);
            Log.d(TAG, "is_samsung_galaxy_f: " + is_samsung_galaxy_f);
        }

        buildCameraIdList();
    }

    /**
     * Returns true if the camera is a real usable camera.
     * Filters out virtual/depth/fusion cameras that Xiaomi exposes in getCameraCharacteristics()
     * but cannot be opened or used for preview/capture.
     */
    private boolean isRealCamera(CameraCharacteristics chars, String id) {
        Rect activeArray = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        if( activeArray == null || activeArray.width() == 0 || activeArray.height() == 0 ) {
            if( MyDebug.LOG )
                Log.d(TAG, "Manager: " + id + " rejected - no active array");
            return false;
        }

        float[] focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
        if( focalLengths == null || focalLengths.length == 0 ) {
            if( MyDebug.LOG )
                Log.d(TAG, "Manager: " + id + " rejected - no focal lengths");
            return false;
        }

        int[] capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        if( capabilities != null ) {
            for( int cap : capabilities ) {
                if( cap == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE ) {
                    return true;
                }
            }
        }
        if( MyDebug.LOG )
            Log.d(TAG, "Manager: " + id + " rejected - no BACKWARD_COMPATIBLE capability");
        return false;
    }

    /**
     * Video recorder safety check.
     *
     * Some Xiaomi/Qualcomm physical or hidden camera IDs expose characteristics
     * and may even work for preview, but they do not deliver frames to a
     * MediaRecorder surface. In logcat this appears as:
     *
     *   MPEG4Writer: The number of recorded samples is 0
     *   MediaRecorder: stop failed: -1007
     *
     * Only expose these extra IDs as normal RealCamMI cameras if Android
     * advertises at least one MediaRecorder output size.
     */
    private boolean supportsVideoRecording(CameraCharacteristics chars, String id) {
        StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if( map == null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "Manager: " + id + " rejected - no stream configuration map");
            return false;
        }

        Size[] videoSizes = map.getOutputSizes(MediaRecorder.class);
        if( videoSizes == null || videoSizes.length == 0 ) {
            if( MyDebug.LOG )
                Log.d(TAG, "Manager: " + id + " rejected - no MediaRecorder output sizes");
            return false;
        }

        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    private void buildCameraIdList() {
        extractedCameraIds.clear();
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

        try {
            String[] logicalIds = manager.getCameraIdList();
            for (String id : logicalIds) {
                // Skip known-bad/virtual camera IDs ("4", "5", "6" are not usable on affected devices)
                if (id.equals("4") || id.equals("5") || id.equals("6") || id.toLowerCase().contains("virtual")) {
                    continue;
                }
                if (!extractedCameraIds.contains(id)) {
                    extractedCameraIds.add(id);
                    if (MyDebug.LOG)
                        Log.d(TAG, "Added logical camera ID: " + id);
                }
            }

            for (String logicalId : logicalIds) {
                if (logicalId.equals("4") || logicalId.equals("5") || logicalId.equals("6")) continue;
                try {
                    CameraCharacteristics chars = manager.getCameraCharacteristics(logicalId);
                    Set<String> physicalIds = chars.getPhysicalCameraIds();
                    if (physicalIds != null && !physicalIds.isEmpty()) {
                        for (String physicalId : physicalIds) {
                            // Skip known-bad/virtual physical camera IDs (same filtering as above)
                            if (physicalId.equals("4") || physicalId.equals("5") || physicalId.equals("6") || physicalId.toLowerCase().contains("virtual")) {
                                continue;
                            }
                            if (!extractedCameraIds.contains(physicalId)) {
                                CameraCharacteristics physicalChars = manager.getCameraCharacteristics(physicalId);
                                if (isRealCamera(physicalChars, physicalId)
                                        && supportsVideoRecording(physicalChars, physicalId)) {
                                    extractedCameraIds.add(physicalId);
                                    if (MyDebug.LOG)
                                        Log.d(TAG, "Added physical camera ID: " + physicalId);
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }

            if (is_samsung || is_samsung_galaxy_s || is_samsung_galaxy_f || is_xiaomi || is_ulefone) {
                if (MyDebug.LOG)
                    Log.d(TAG, "Running hidden camera probe...");
                for (int i = 2; i <= 9; i++) {
                    // Skip known-bad IDs ("4", "5", "6") during the aggressive hidden-camera probe
                    if (i == 4 || i == 5 || i == 6) continue;
                    String testId = String.valueOf(i);
                    if (!extractedCameraIds.contains(testId)) {
                        try {
                            CameraCharacteristics chars = manager.getCameraCharacteristics(testId);
                            if (chars != null) {
                                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                                if (facing != null && isRealCamera(chars, testId)
                                        && supportsVideoRecording(chars, testId)) {
                                    extractedCameraIds.add(testId);
                                    if( MyDebug.LOG )
                                        Log.d(TAG, "Manager: added hidden real camera ID: " + testId + " facing: " + facing);
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }

        } catch (Throwable e) {
            MyDebug.logStackTrace(TAG, "Exception building camera ID list", e);
        }

        // Final safety pass: remove known-bad camera IDs from the output list,
        // in case any slipped through the earlier filtering stages above.
        extractedCameraIds.remove("4");
        extractedCameraIds.remove("5");
        extractedCameraIds.remove("6");

        if( MyDebug.LOG )
            Log.d(TAG, "Manager: total cameras after filtering: "
                    + extractedCameraIds.size() + " -> " + extractedCameraIds);
    }

    // [REALCAMMI FORK] Resolves position index to filtered camera ID string — no upstream equivalent
    public String getCameraIdString(int cameraId) {
        if( cameraId >= 0 && cameraId < extractedCameraIds.size() ) {
            return extractedCameraIds.get(cameraId);
        }
        return null;
    }

    // [REALCAMMI FORK] Returns count from filtered list; upstream calls getCameraIdList().length directly with its own try/catch
    @Override
    public int getNumberOfCameras() {
        return extractedCameraIds.size();
    }

    @Override
    public CameraController.Facing getFacing(int cameraId) {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            if( cameraId < 0 || cameraId >= extractedCameraIds.size() )
                return CameraController.Facing.FACING_UNKNOWN;
            String id = extractedCameraIds.get(cameraId);
            CameraCharacteristics chars = manager.getCameraCharacteristics(id);
            Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
            if( facing == null ) return CameraController.Facing.FACING_UNKNOWN;
            switch( facing ) {
                case CameraMetadata.LENS_FACING_FRONT:    return CameraController.Facing.FACING_FRONT;
                case CameraMetadata.LENS_FACING_BACK:     return CameraController.Facing.FACING_BACK;
                case CameraMetadata.LENS_FACING_EXTERNAL: return CameraController.Facing.FACING_EXTERNAL;
                default: return CameraController.Facing.FACING_UNKNOWN;
            }
        } catch( Throwable e ) {
            MyDebug.logStackTrace(TAG, "exception getting facing for camera " + cameraId, e);
        }
        return CameraController.Facing.FACING_UNKNOWN;
    }

    @Override
    public String getDescription(Context context, int cameraId) {
        try {
            if( cameraId >= 0 && cameraId < extractedCameraIds.size() ) {
                return getDescription(null, context, extractedCameraIds.get(cameraId), true, false);
            }
        } catch( Throwable e ) {
            MyDebug.logStackTrace(TAG, "exception getting description for camera " + cameraId, e);
        }
        return null;
    }

    @Override
    public String getDescription(CameraInfo info, Context context, String cameraIdS,
                                 boolean include_type, boolean include_angles) {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        StringBuilder description = new StringBuilder();
        try {
            CameraCharacteristics chars = manager.getCameraCharacteristics(cameraIdS);

            if( include_type ) {
                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                if( facing != null ) {
                    switch( facing ) {
                        case CameraMetadata.LENS_FACING_FRONT:
                            description.append(context.getResources().getString(R.string.front_camera));
                            break;
                        case CameraMetadata.LENS_FACING_BACK:
                            description.append(context.getResources().getString(R.string.back_camera));
                            break;
                        case CameraMetadata.LENS_FACING_EXTERNAL:
                            description.append(context.getResources().getString(R.string.external_camera));
                            break;
                    }
                }
            }

            SizeF viewAngle = computeViewAngles(chars);
            if( info != null ) info.view_angle = viewAngle;

            String lensType = getLensTypeLabel(context, chars, viewAngle);
            if( lensType != null ) {
                description.append(", ").append(lensType);
            }

            if( MyDebug.LOG ) {
                description.append(" [ID:").append(cameraIdS).append("]");
            }

        } catch( Throwable e ) {
            MyDebug.logStackTrace(TAG, "exception building description for camera " + cameraIdS, e);
        }
        return description.toString();
    }

    // [REALCAMMI FORK] Adds Macro detection (not in upstream); Ultrawide/Telephoto thresholds reused from upstream's getDescription() logic
    private String getLensTypeLabel(Context context, CameraCharacteristics chars, SizeF viewAngle) {
        Float minFocusDist = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        if( minFocusDist != null && minFocusDist > 25.0f ) {
            return "Macro";
        }

        float fov = viewAngle.getWidth();
        if( fov <= 0f ) return null;

        if( fov >= 100.0f ) return "Ultrawide";
        if( fov < 35.0f ) return "Telephoto";

        return null;
    }

    // [REALCAMMI FORK] Simplified vs upstream — omits active/pixel array fraction correction and the 55.0f/43.0f default fallback (returns 0,0 instead)
    static SizeF computeViewAngles(CameraCharacteristics characteristics) {
        Rect activeSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        SizeF physicalSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
        float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);

        if( activeSize == null || physicalSize == null
                || focalLengths == null || focalLengths.length == 0 ) {
            return new SizeF(0, 0);
        }

        float focalLength = focalLengths[0];
        float angleX = (float) Math.toDegrees(2 * Math.atan(physicalSize.getWidth()  / (2 * focalLength)));
        float angleY = (float) Math.toDegrees(2 * Math.atan(physicalSize.getHeight() / (2 * focalLength)));
        return new SizeF(angleX, angleY);
    }

    // [REALCAMMI FORK] Always allows Camera2, bypasses upstream's LIMITED-hardware-level gate (see isHardwareLevelSupported below)
    public boolean allowCamera2Support(int cameraId) {
        return true;
    }

    // [REALCAMMI FORK] Public (upstream is package-private); rewritten as switch with explicit LEVEL_3 handling, same intent as upstream
    public static boolean isHardwareLevelSupported(CameraCharacteristics characteristics,
                                                   int requiredLevel) {
        Integer currentLevel = characteristics.get(
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        if( currentLevel == null ) return false;
        if( currentLevel == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY ) {
            return requiredLevel == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY;
        }
        switch( requiredLevel ) {
            case CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY:  return true;
            case CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED:
                return currentLevel != CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY;
            case CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL:
                return currentLevel == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL
                        || currentLevel == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3;
            case CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3:
                return currentLevel == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3;
            default:
                return currentLevel == requiredLevel;
        }
    }
}