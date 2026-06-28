<img src="docs/images/RealCamMI.png" width="400"/>

# RealCamMI — Fork Documentation

**Fork of:** Open Camera (commit `0dd4cb`, master branch)  
**Upstream:** https://sourceforge.net/p/opencamera/code/ci/master/tree/  
**Fork repository:** https://github.com/Persona78/RealCamMI  
**License:** GPLv3 (inherited from Open Camera)  
**Last updated:** 27 June 2026

---

## Overview

RealCamMI is a GPLv3 fork of Open Camera, targeting the **Xiaomi Redmi Note 13 Pro 5G** ("garnet", ISOCELL HP3 200MP sensor) and **Ulefone Armor 25T Pro** (Samsung S5KGN1, MediaTek Helio G99). It extends Open Camera with device-specific fixes, advanced image quality tuning, and additional post-processing features.

---

## Build Configuration Changes

| Property | Open Camera (upstream) | RealCamMI |
|---|---|---|
| `applicationId` | `net.sourceforge.opencamera` | `net.sourceforge.opencamera.realcammi` |
| `versionName` | (upstream) | `1.1-RealCamMI` |
| `versionCode` | (upstream) | `1` |
| `minSdkVersion` | 15 | 30 |
| `targetSdkVersion` | 24 | 37 |
| `compileSdk` | 24 | 37 |

All Java source packages have been refactored from `net.sourceforge.opencamera` to `net.sourceforge.opencamera.realcammi`.

---

## New Dependencies (build.gradle)

The following dependencies were added over upstream:

```gradle
// ML Kit barcode scanning
implementation 'com.google.mlkit:barcode-scanning:17.3.0'

// TensorFlow Lite
implementation 'org.tensorflow:tensorflow-lite:2.16.1'
implementation 'org.tensorflow:tensorflow-lite-support:0.4.4'

// OpenCV
implementation 'com.quickbirdstudios:opencv:4.5.3.0'
```

---

## New Files (not present in upstream)

### Camera Controller (`cameracontroller/`)

| File | Description |
|---|---|
| `Camera2Settings.java` | Extracted Camera2 capture request configuration. Handles tonemap curves (JTVideo/JTLog/S-Log3), ColorSpaceTransform, edge mode, noise reduction, zoom ratio, and crop region for still capture. Centralises all Camera2-specific settings previously scattered across `CameraController2`. |
| `RawImage.java` | Encapsulates a RAW (DNG) image with its associated metadata and byte data. |

### Preview (`preview/`)

| File | Description |
|---|---|
| `BasicApplicationInterface.java` | Default stub implementation of `ApplicationInterface`. Subclasses only need to override methods they care about. |
| `VideoProfile.java` | Encapsulates a video recording profile (resolution, frame rate, bitrate, codec). |

### Remote Control (`remotecontrol/`)

All files in this package are new — not present in the upstream version used as base:

| File | Description |
|---|---|
| `BluetoothLeService.java` | Android BLE service for Bluetooth LE remote control communication. |
| `BluetoothRemoteControl.java` | High-level BLE remote control handler; maps BLE button events to camera actions. |
| `DeviceScanner.java` | Scans for BLE remote control devices. |
| `KrakenGattAttributes.java` | GATT attribute UUIDs for the Kraken BLE remote shutter. |

### UI (`ui/`)

| File | Description |
|---|---|
| `ArraySeekBarPreference.java` | Custom `DialogPreference` (AndroidX `PreferenceFragmentCompat` compatible) that shows a seekbar for selecting array values. Includes inner `ArraySeekBarPreferenceDialog extends PreferenceDialogFragmentCompat`. |
| `ManualSeekbars.java` | On-screen manual control seekbars for ISO, shutter speed, focus, white balance, and exposure. |
| `MyEditTextPreference.java` | Custom `DialogPreference` (AndroidX compatible) for text input. Fixes emoji support on Android 10 and earlier. Includes inner `MyEditTextPreferenceDialog extends PreferenceDialogFragmentCompat`. |
| `OnScreenIcons.java` | Manages all on-screen toolbar icon buttons — visibility, state updates, user interaction, and persistence. Handles: flash, focus peaking, auto-level, color correction, OpenCV sharpen/NR/CLAHE/blur-detect, face detection, store location, stamp, text stamp, cycle lock orientation, RAW, exposure lock, white balance lock, and others. |

### Root Package

| File | Description |
|---|---|
| `ExifHandler.java` | Reads, writes, and copies EXIF/XMP metadata for JPEG and DNG files. |
| `ImageUtils.java` | Bitmap utility methods: loading with EXIF rotation, scaling, and format conversion. |
| `JavaImageFunctionsHDR.java` | Java-side HDR image processing functions (tone mapping, exposure merging). |
| `JavaImageFunctionsPanorama.java` | Java-side panorama stitching functions. |
| `JavaImageFunctionsPreview.java` | Java-side preview processing functions (histogram, zebra stripes computation). |
| `JavaImageProcessing.java` | Core Java image processing utilities shared across HDR, panorama, and preview. |
| `KeyguardUtils.java` | Helpers for dismissing the keyguard to allow camera launch from the lock screen. |
| `MagneticSensor.java` | Reads the device's magnetic/compass sensor for photo direction metadata. |
| `MultiCamHandler.java` | Manages multi-camera sessions, including logical/physical camera switching. |
| `MyAudioTriggerListenerCallback.java` | Callback interface for audio-triggered capture (sound-activated shutter). |
| `OpenCameraApplication.java` | Custom `Application` subclass. Initialises OpenCV via `OpenCVLoader.initLocal()` at app start. Includes crash workaround for app-replacing state. |
| `PanoramaProcessor.java` | Orchestrates panorama capture and stitching using `JavaImageFunctionsPanorama`. |
| `PanoramaProcessorException.java` | Exception type for panorama processing failures. |
| `PermissionHandler.java` | Centralises runtime permission requests (camera, storage, location, microphone). |
| `PostProcessing.java` | Post-capture image processing pipeline. See dedicated section below. |
| `PreferenceSubCameraControlsMore.java` | Settings sub-screen: additional camera controls (focus distance, exposure bracketing, etc.). |
| `PreferenceSubGUI.java` | Settings sub-screen: GUI icon visibility toggles. |
| `PreferenceSubLicences.java` | Settings sub-screen: open source licences. |
| `PreferenceSubLocation.java` | Settings sub-screen: GPS/location settings. |
| `PreferenceSubPhoto.java` | Settings sub-screen: photo capture settings. |
| `PreferenceSubPreview.java` | Settings sub-screen: preview display settings. |
| `PreferenceSubProcessing.java` | Settings sub-screen: image processing settings. |
| `PreferenceSubRemoteCtrl.java` | Settings sub-screen: BLE remote control settings. |
| `PreferenceSubScreen.java` | Base class for all settings sub-screens. Extends `PreferenceFragmentCompat`. Implements `onDisplayPreferenceDialog()` so all sub-screens correctly handle `ArraySeekBarPreference` and `MyEditTextPreference` dialogs. |
| `PreferenceSubSettingsManager.java` | Settings sub-screen: settings manager (import/export/reset). |
| `PreferenceSubVideo.java` | Settings sub-screen: video recording settings. |
| `Preshots.java` | Manages "save preview shots" — captures a short video clip of frames just before each still capture. |
| `SaveLocationHandler.java` | Resolves and validates the save location for photos and videos, handling both scoped storage and SAF. |
| `SettingsManager.java` | Import, export, and reset of all app preferences to/from JSON files. |
| `SoundPoolManager.java` | Manages the shutter sound pool for camera capture sounds. |

---

## Modified Files — Key Changes

### Package Rename (all 80 files)

All files migrated from `net.sourceforge.opencamera` to `net.sourceforge.opencamera.realcammi`. This is the most pervasive change, affecting every file.

### AndroidX Migration (all 80 files)

All legacy Android APIs migrated to AndroidX:

| Legacy | AndroidX replacement |
|---|---|
| `android.preference.PreferenceFragment` | `androidx.preference.PreferenceFragmentCompat` |
| `android.preference.*` (all classes) | `androidx.preference.*` |
| `android.app.Fragment` | `androidx.fragment.app.Fragment` |
| `android.app.FragmentManager` | `androidx.fragment.app.FragmentManager` |
| `android.app.DialogFragment` | `androidx.fragment.app.DialogFragment` |
| `getFragmentManager()` | `getParentFragmentManager()` / `getSupportFragmentManager()` |
| `addPreferencesFromResource()` in `onCreate()` | `setPreferencesFromResource()` in `onCreatePreferences()` |
| `PreferenceFragment.OnPreferenceStartFragmentCallback` | `PreferenceFragmentCompat.OnPreferenceStartFragmentCallback` |

### `CameraController2.java`

**Tonemap curves (new in fork):**
- `jtvideo_values_base` — calibrated JTVideo tonemap curve, tuned via pixel-level Sample against the Xiaomi Garnet stock camera app. Adjusts shadow rendering, midtone transitions, and highlight rolloff.
- `jtlog_values_base` — JTLog logarithmic profile.
- `jtlog2_values_base` — JTLog2 alternative logarithmic profile.
- `TonemapProfile` enum extended with `TONEMAPPROFILE_JTVIDEO`, `TONEMAPPROFILE_JTLOG`, `TONEMAPPROFILE_JTLOG2`.

**Zoom fix for Xiaomi Garnet (`[REALCAMMI FORK]`):**
- Diagnosed: Qualcomm CamX HAL ignores `CONTROL_ZOOM_RATIO` / `SCALER_CROP_REGION` during still capture on Garnet (`IQSetupTriggerData() Fail to get zoom ratio, pZoomRatioData is NULL`).
- Fix: Software crop in `onImageAvailable()`. The captured JPEG is decoded, cropped to the area corresponding to the active zoom ratio, and recompressed.
- Zoom ratio source: `capture_result_zoom_ratio` (read from `CaptureResult.CONTROL_ZOOM_RATIO` at each frame) is used instead of `camera_settings.current_zoom_ratio`, which can be stale during pinch-zoom gestures.
- Crop centre: fixed from `/2` to `/0.5` to correctly offset the crop to the image centre.
- JPEG quality: uses `ApplicationInterface.getImageQualityPref()` instead of hardcoded 90.

**Capture result zoom reading (`[REALCAMMI FORK]`):**
- New field `capture_result_zoom_ratio` updated in `updateCachedCaptureResult()` via `CaptureResult.CONTROL_ZOOM_RATIO` (API 30+).

### `CameraControllerManager2.java`

**Device fingerprinting (`[REALCAMMI FORK]`):**
- New `is_xiaomi` flag: detected via `Build.MANUFACTURER`, `Build.BRAND`, and `Build.DEVICE` (includes `garnet` and a comprehensive list of Redmi Note codenames).
- New `is_ulefone` flag: detected via `Build.MANUFACTURER`.
- `buildCameraIdList()`: filters virtual/depth camera IDs (IDs 4–6 on Garnet), and probes hidden physical sensors (IDs 2–9) to expose them to the app.
- `isRealCamera()`: rejects `INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY` camera IDs.
- `supportsVideoRecording()`: additional guard for non-video-capable sensors.

### `Camera2Settings.java` (new file, extracted from `CameraController2`)

**Zoom and crop (`[REALCAMMI FORK]`):**
- `setControlZoomRatio()`: applies `CONTROL_ZOOM_RATIO` (Android 11+) for still capture.
- `setCropRegion()`: applies `SCALER_CROP_REGION` as fallback for devices that support it.

**Tonemap profiles (`[REALCAMMI FORK]`):**
- `setTonemapProfile()`: applies JTVideo, JTLog, JTLog2, or Sony S-Log3 tonemap curves.
- `slog3()`: implements the official Sony S-Log3 formula.
- `ColorSpaceTransform` applied in WB manual mode for colour science tuning.

**Edge mode and noise reduction (`[REALCAMMI FORK]`):**
- Garnet: `EDGE_MODE_HIGH_QUALITY` + `NOISE_REDUCTION_HIGH_QUALITY` for still capture (prevents the soft-photo issue caused by `EDGE_MODE_OFF` being applied to Xiaomi devices in some configurations).
- Ulefone / Samsung S7: retain `EDGE_MODE_OFF` + `NOISE_REDUCTION_OFF` to avoid glow and worm artefacts.

### `MyApplicationInterface.java`

**Getters for new fork features (`[REALCAMMI FORK]`):**
- `getColorCorrectionPref()` — colour correction post-processing toggle.
- `getOpenCVSharpenPref()` — OpenCV sharpening toggle.
- `getOpenCVNRPref()` — OpenCV noise reduction toggle.
- `getOpenCVCLAHEPref()` — OpenCV CLAHE contrast enhancement toggle.
- `getOpenCVBlurDetectPref()` — OpenCV blur detection toggle.

**Image quality (`[REALCAMMI FORK]`):**
- `getImageQualityPref()` returns 100 when any post-processing feature (colour correction, OpenCV) is active, ensuring the intermediate JPEG used for reprocessing is lossless.

### `PreferenceKeys.java`

**New preference keys (`[REALCAMMI FORK]`):**

| Key | Purpose |
|---|---|
| `ColorCorrectionPreferenceKey` | Toggle for colour correction post-processing |
| `ShowColorCorrectionPreferenceKey` | Show/hide colour correction toolbar icon |
| `OpenCVSharpenPreferenceKey` | Toggle for OpenCV Unsharp Mask sharpening |
| `OpenCVNRPreferenceKey` | Toggle for OpenCV Bilateral Filter noise reduction |
| `OpenCVCLAHEPreferenceKey` | Toggle for OpenCV CLAHE contrast enhancement |
| `OpenCVBlurDetectPreferenceKey` | Toggle for OpenCV blur detection warning |
| `ShowOpenCVSharpenPreferenceKey` | Show/hide sharpen toolbar icon |
| `ShowOpenCVNRPreferenceKey` | Show/hide NR toolbar icon |
| `ShowOpenCVCLAHEPreferenceKey` | Show/hide CLAHE toolbar icon |
| `ShowOpenCVBlurDetectPreferenceKey` | Show/hide blur detect toolbar icon |

### `PostProcessing.java` (new file)

Post-capture image processing pipeline, invoked from `ImageSaver` after stamping. All processing is applied to the decoded bitmap before final JPEG recompression. Processing order:

1. **Colour correction** (`applyColorCorrection`) — `[REALCAMMI FORK]`  
   Corrects a systematic blue colour cast (B/R ratio ~1.12 vs ~1.00 in stock camera) using `ColorMatrix`: `R×1.01`, `G×1.00`, `B×0.92`. Cannot be done via `COLOR_CORRECTION_TRANSFORM` because the Qualcomm CamX HAL ignores that field when AWB is in AUTO mode.

2. **OpenCV Noise Reduction** (`applyOpenCVNR`) — `[REALCAMMI FORK]`  
   Bilateral filter (`d=9, sigmaColor=75, sigmaSpace=75`). Preserves edges while smoothing flat areas. Applied before sharpening.

3. **OpenCV Sharpening** (`applyOpenCVSharpen`) — `[REALCAMMI FORK]`  
   Unsharp Mask via `GaussianBlur` + `addWeighted` (`radius=1.5, amount=1.2`). Applied after noise reduction.

4. **OpenCV CLAHE** (`applyOpenCVCLAHE`) — `[REALCAMMI FORK]`  
   Contrast Limited Adaptive Histogram Equalisation on the L channel (Lab colour space), `clipLimit=2.0, tileSize=8×8`. Improves local contrast without blowing highlights.

5. **OpenCV Blur Detection** (`detectAndWarnBlur`) — `[REALCAMMI FORK]`  
   Laplacian variance analysis. Shows a toast warning if the blur score falls below 100. Does not modify the image.

### `OpenCameraApplication.java` (new file)

Extends `Application`. Adds:
- `initOpenCV()`: calls `OpenCVLoader.initLocal()` to initialise the bundled OpenCV native libraries at app start. No external OpenCV Manager required.

### `MainActivity.java`

- Version code comment aligned to upstream v1.2-RealCamMI (1).
- `onPreferenceStartFragment` migrated to full AndroidX implementation.
- `getSupportFragmentManager()` used consistently throughout.
- New `clickedXxx` methods for all fork toolbar buttons: `clickedColorCorrection`, `clickedOpenCVSharpen`, `clickedOpenCVNR`, `clickedOpenCVCLAHE`, `clickedOpenCVBlurDetect`.

### `MyPreferenceFragment.java`

- Migrated to `PreferenceFragmentCompat`.
- `onDisplayPreferenceDialog()` added to route `ArraySeekBarPreference` and `MyEditTextPreference` clicks to their `PreferenceDialogFragmentCompat` inner classes.

---

## Target Devices

| Device | Codename | Sensor | SoC | Notes |
|---|---|---|---|---|
| Xiaomi Redmi Note 13 Pro 5G | garnet | ISOCELL HP3 200MP | Qualcomm SM7435 | Camera2 Level 3. Known CamX HAL zoom bug in still capture — fixed via software crop. |
| Ulefone Armor 25T Pro | — | Samsung S5KGN1 | MediaTek Helio G99 | Edge mode and noise reduction disabled to prevent worm artefacts. |

---

## Known Limitations

- **Zoom crop resolution loss**: software zoom crop reduces effective resolution proportionally (e.g. ~3MP at 2×, ~0.8MP at 4×). This is a HAL limitation on Garnet — the sensor does not expose native 200MP resolution via Camera2 API (`SCALER_STREAM_CONFIGURATION_MAP` tops out at 4080×3060).
- **EXIF loss on zoom crop**: recompression of the zoomed JPEG discards the original EXIF data.
- **Colour correction only in AWB Auto**: the `COLOR_CORRECTION_TRANSFORM` field is silently ignored by Qualcomm CamX when AWB is in AUTO mode (Android Camera2 API specification). Colour correction for AWB Auto is handled via `PostProcessing.applyColorCorrection()` instead.
- **OpenCV post-processing increases save time**: bilateral filter and CLAHE require JPEG decode + recompression, which adds latency proportional to image resolution.

---

## Code Comment Convention

All changes specific to the RealCamMI fork are marked with the tag `[REALCAMMI FORK]` in inline comments throughout the modified source files, making it straightforward to identify fork-specific code when merging upstream updates.

---

## Upstream Sample Summary

| Metric | Value |
|---|---|
| Common files modified | 38 of 38 |
| New files added | 42 |
| Total Java files | 80 (vs 38 upstream) |
| Package | `net.sourceforge.opencamera.realcammi` |
| Min SDK | 30 (Android 11) — upstream was 15 |
| Target SDK | 37 (Android 14) — upstream was 24 |


### 1. Multi-camera detection fix
**File:** `CameraControllerManager2.java`

Stock Open Camera calls `CameraManager.getCameraIdList()` directly, which on several devices (particularly Xiaomi/Qualcomm) either omits real physical cameras hidden behind a "logical" multi-camera ID, or exposes virtual/depth/fusion camera IDs that can't actually be opened or used for preview/capture/recording — causing crashes or a misleading camera count.

This fork replaces that with a custom `buildCameraIdList()` that:
- Walks all logical camera IDs reported by the system
- Expands each logical camera's physical sub-camera IDs (multi-camera modules)
- Filters out known-bad and virtual camera IDs
- Validates every candidate camera with two new safety checks:
  - `isRealCamera()` — rejects sensors with no valid active pixel array, no focal length data, or that don't report the `BACKWARD_COMPATIBLE` capability
  - `supportsVideoRecording()` — rejects camera IDs that report characteristics but don't actually advertise any usable `MediaRecorder` output size (these would otherwise cause `MediaRecorder.stop()` failures with 0 recorded frames)
- Runs an additional probe (IDs 2–9) specifically on Xiaomi, and Ulefone devices to surface hidden real cameras the standard API call misses

`getNumberOfCameras()`, `isFrontFacing()`, and `getDescription()` were all updated to use this filtered, validated camera list instead of the raw system list.

### 2. Device detection
**Files:** `CameraControllerManager2.java`, `Camera2Settings.java`

Added manufacturer/model fingerprinting (`is_xiaomi`, `is_ulefone`) used as the basis for the device-specific fixes below. Xiaomi detection covers a wide range of specific model codes across Redmi and POCO sub-brands.

### 3. Custom tone curves
**Files:** `Camera2Settings.java`, `CameraController.java`

Added tuned tonemap curve profiles (`jtvideo`, `jtlog`, `jtlog2`) designed for more realistic results than the device defaults:
- Deep, clean shadows without the "milky"/lifted look common on stock tone curves
- Smooth, natural midtone transitions with a touch of extra contrast for perceived sharpness/definition
- Controlled highlight roll-off to avoid blown-out whites

These curves were iteratively tuned and tested against real side-by-side Samples with each device's stock camera app, across bright midday light, golden hour, and indoor low light.

Also added `TONEMAPPROFILE_SLOG3`, an implementation of Sony's S-Log3 logarithmic profile for users who want maximum dynamic range for color grading in post-production (DaVinci Resolve, CapCut, etc.). Note: footage shot with this profile will look intentionally flat and desaturated straight out of the camera — that's expected, and it requires a LUT or manual grade afterward.

### 4. Color correction matrix
**File:** `Camera2Settings.java`

Added a `COLOR_CORRECTION_TRANSFORM` matrix to boost saturation slightly beyond the device default, tuned to better match (and in several tests, exceed) the color richness of each device's stock camera app, without introducing a visible color cast on neutral whites/grays.

### 5. Device-specific noise reduction override
**File:** `Camera2Settings.java`

Forces `NOISE_REDUCTION_MODE_OFF` on Ulefone Armor devices, where the default noise reduction mode caused excessive blurring/smearing of fine detail.

### 6. Minor adjustments
- `N_IMAGES_NR_DARK` raised from 8 to 10 (multi-frame night mode)
- Added 3200 and 6400 to the manual ISO preset list

## Why these changes exist

Every change here was driven by a real, observed problem on real hardware (Ulefone Armor 25T, a Xiaomi device, and a Redmi Note 13 Pro 5G), verified with side-by-side photo/video Samples against each device's stock camera app, rather than applied speculatively. Where a problem turned out to be a hardware/driver-level limitation rather than something fixable in app code (see the video color-shift note below), no workaround was forced in — it's left as a known limitation instead.

## Known limitations

On the **Redmi Note 13 Pro 5G** specifically, video recording exhibits a color/exposure shift the moment recording starts, which persists for the full recording. This has been confirmed to **not** occur on Ulefone Armor or other tested Xiaomi devices, and is consistent with independently documented exposure/color-rendering instability on this specific device's camera hardware (see DXOMARK's and GSMArena's reviews of this model). This is believed to be a device-level HAL/firmware quirk in how the camera pipeline diverges between the preview stream and the video encoder stream, not something fixable from application code. Recommended workaround: use the stock camera app for video on this specific device; this fork's photo mode is unaffected and tests favorably against stock.

## License

RealCamMI remains licensed under the **GNU General Public License v3.0 or later**, the same as upstream Open Camera. See `gpl-3.0.txt` for the full license text. Original copyright: Mark Harman, 2013–present.

## Credits

- **Mark Harman** — original author and maintainer of Open Camera.
- **Persona78** - Creator of this fork.
- Open Camera's other contributors (see `_docs/credits.html`).
- Google Material Design icons, Apache License 2.0.

## Photo Sample

.

