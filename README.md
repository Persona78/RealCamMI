
<p align="center">
    <img width="400" img src="docs/images/RealCamMI.png" alt="RealCamMI logo">
</p>

# RealCamMI

A focused fork of [Open Camera](https://opencamera.org.uk/) by Mark Harman, engineered to fix multi-camera detection, colour/tone rendering, and image quality on Xiaomi and Ulefone devices — with advanced post-processing features powered by OpenCV.

Licensed under **GPL v3.0 or later** — same as upstream Open Camera.

---

## What is RealCamMI?

**RealCamMI** stands for **Real**istic colour/tone output, originally driven by **M**ulti-**cam**era detection issues on **X**iaomi **(MI)** devices — with fixes that also benefit Ulefone hardware.

This fork started from a specific, practical problem: certain devices (Xiaomi/Redmi/POCO, Ulefone Armor series) don't behave correctly with stock Open Camera's camera detection and default colour/tone rendering. Every change here was driven by a real, observed problem on real hardware, verified with side-by-side comparisons against each device's stock camera app.

All credit for the original application, architecture, and the vast majority of the codebase belongs to **Mark Harman** and Open Camera's contributors.

---

## Target Devices

| Device | Codename | Sensor | SoC |
|---|---|---|---|
| Xiaomi Redmi Note 13 Pro 5G | garnet | ISOCELL HP3 200MP | Qualcomm SM7435 |
| Ulefone Armor 25T Pro | — | Samsung S5KGN1 | MediaTek Helio G99 |

---

## What's different from upstream Open Camera

### 1. Multi-camera detection fix

**File:** `CameraControllerManager2.java`

Stock Open Camera calls `CameraManager.getCameraIdList()` directly, which on Xiaomi/Qualcomm devices either omits real physical cameras hidden behind a logical multi-camera ID, or exposes virtual/depth/fusion camera IDs that can't actually be used — causing crashes or a misleading camera count.

This fork replaces that with a custom `buildCameraIdList()` that:
- Expands logical camera IDs to their physical sub-camera IDs
- Filters out virtual, depth, and fusion camera IDs
- Validates every candidate with `isRealCamera()` (rejects sensors with no pixel array, no focal length, or missing `BACKWARD_COMPATIBLE` capability) and `supportsVideoRecording()` (rejects IDs that don't advertise usable `MediaRecorder` output sizes)
- Probes hidden physical cameras (IDs 2–9) specifically on Xiaomi and Ulefone devices

### 2. Zoom fix for Xiaomi Garnet

**File:** `CameraController2.java`

The Qualcomm CamX HAL on Garnet ignores `CONTROL_ZOOM_RATIO` / `SCALER_CROP_REGION` during still capture (`IQSetupTriggerData() Fail to get zoom ratio, pZoomRatioData is NULL`), delivering a full-resolution unzoomed image regardless of the zoom level set in the preview.

**Fix:** Software JPEG crop in `onImageAvailable()`:
- The captured JPEG is decoded, cropped to the area corresponding to the active zoom ratio, and recompressed
- Zoom ratio is read from `CaptureResult.CONTROL_ZOOM_RATIO` (updated every frame) instead of a cached value that can be stale during pinch-zoom gestures
- Crop centre offset fixed to correctly align with the image centre

### 3. Custom tonemap curve profiles

**Files:** `CameraController2.java`, `Camera2Settings.java`

Three custom tonemap profiles tuned through iterative pixel-level comparison against the stock camera app:

| Profile | Description |
|---|---|
| **JTVideo** | Natural rendering — clean deep shadows, smooth midtone transitions, controlled highlight rolloff. Default profile. |
| **JTLog** | Logarithmic profile — compressed shadows and highlights for maximum dynamic range, suitable for colour grading in post. |
| **S-Log3** | Sony S-Log3 implementation — maximum dynamic range for professional post-production (DaVinci Resolve, CapCut, etc.). Footage will look flat and desaturated — requires a LUT or manual grade. |

Also fixes the video log options being hidden unnecessarily: the `tonemap_log_max_curve_points_c` threshold was reduced from 128 to 17 (the actual number of points our curves use), so the Video Log and Profile Gamma options now correctly appear on Garnet.

### 4. Colour correction post-processing

**File:** `PostProcessing.java`

Corrects a systematic blue colour cast (B/R ratio ~1.12 vs ~1.00 in stock camera) using a `ColorMatrix` applied after capture: `R×1.01`, `G×1.00`, `B×0.92`.

Cannot be done via `COLOR_CORRECTION_TRANSFORM` because the Qualcomm CamX HAL silently ignores that field when AWB is in AUTO mode (Android Camera2 API specification). Colour correction is applied in the post-processing pipeline instead, so it works regardless of the AWB mode.

Activated via a dedicated toolbar button (tap to toggle; red = active). Visible in **Settings → GUI Icons → Show colour correction icon**.

### 5. OpenCV post-processing pipeline

**File:** `PostProcessing.java`

Four advanced post-processing features powered by OpenCV, each with a dedicated toolbar button:

| Feature | Method | Description |
|---|---|---|
| **Noise Reduction** | Bilateral Filter (`d=9, σ=75`) | Smooths flat areas while preserving edges. Applied before sharpening. |
| **Sharpening** | Unsharp Mask (`radius=1.5, amount=1.2`) | Enhances fine detail without colour fringing. Applied after noise reduction. |
| **Contrast Enhancement** | CLAHE (`clipLimit=2.0, tile=8×8`) | Improves local contrast on the L channel (Lab colour space). Recovers shadow detail without blowing highlights. |
| **Blur Detection** | Laplacian variance | Analyses the final image and shows a warning toast if blur score < 100. Does not modify the image. |

Processing order: Colour correction → NR → Sharpen → CLAHE → Blur Detection.

Each feature is toggled independently via its own toolbar button. All buttons are visible/hidden via **Settings → GUI Icons**.

### 6. Device-specific image quality tuning

**File:** `Camera2Settings.java`

- **Edge mode**: `EDGE_MODE_OFF` forced for Ulefone and Samsung S7 (prevents glow/worm artefacts). Default `EDGE_MODE_FAST` for all other devices including Garnet.
- **Noise reduction**: `NOISE_REDUCTION_MODE_OFF` forced for Ulefone (prevents excessive blurring from the MediaTek ISP HAL-level NR). Log/flat profiles request `NOISE_REDUCTION_MINIMAL` + `EDGE_MODE_OFF` on all devices.

### 7. AndroidX migration

All legacy Android preference and fragment APIs migrated to AndroidX across all 80 Java files:

| Legacy | AndroidX |
|---|---|
| `android.preference.*` | `androidx.preference.*` |
| `android.app.Fragment/DialogFragment/FragmentManager` | `androidx.fragment.app.*` |
| `getFragmentManager()` | `getParentFragmentManager()` / `getSupportFragmentManager()` |
| `addPreferencesFromResource()` in `onCreate()` | `setPreferencesFromResource()` in `onCreatePreferences()` |
| Custom `DialogPreference` subclasses | AndroidX `PreferenceDialogFragmentCompat` inner classes |

### 8. Other improvements

- Default JPEG quality changed from 90% to 100%
- When any post-processing feature is active, the intermediate capture is forced to quality 100 to avoid double-compression loss
- Panorama JPEG quality now reads from user preference instead of hardcoded 90
- `setTargetFragment()` (deprecated API 31+) removed throughout
- All debug log tags unified to use the `TAG` constant (removed hardcoded `"XIAOMI_CAM"` strings)

---

## New features vs upstream at a glance

| Feature | Open Camera | RealCamMI |
|---|---|---|
| Physical camera discovery | `getCameraIdList()` only | Custom probe + validation |
| Zoom in still capture (Garnet) | Broken (HAL bug) | Fixed via software crop |
| Tonemap profiles | Gamma / flat only | JTVideo, JTLog, S-Log3 |
| Colour correction | — | Post-processing pipeline |
| OpenCV sharpening | — | Unsharp Mask |
| OpenCV noise reduction | — | Bilateral Filter |
| OpenCV contrast enhancement | — | CLAHE |
| Blur detection | — | Laplacian variance warning |
| Default JPEG quality | 90% | 100% |
| AndroidX migration | Legacy | Full AndroidX |

---

## Build Configuration

| Property | Value |
|---|---|
| `applicationId` | `net.sourceforge.opencamera.realcammi` |
| `versionName` | `1.2-RealCamMI` |
| `versionCode` | `2` |
| `minSdkVersion` | `30` (Android 11) |
| `targetSdkVersion` | `37` (Android 15) |
| `compileSdk` | `37` |

### Dependencies (additions over upstream)

```gradle
implementation 'com.google.mlkit:barcode-scanning:17.3.0'
implementation 'org.tensorflow:tensorflow-lite:2.16.1'
implementation 'org.tensorflow:tensorflow-lite-support:0.4.4'
implementation 'com.quickbirdstudios:opencv:4.5.3.0'
```

---

## Known Limitations

- **Zoom crop resolution loss**: software zoom crop reduces effective resolution proportionally (~3MP at 2×, ~0.8MP at 4×). The Garnet sensor does not expose native 200MP resolution via Camera2 API.
- **EXIF loss on zoom crop**: recompression of the zoomed JPEG discards the original EXIF metadata.
- **Video colour shift on Garnet**: a colour/exposure shift occurs the moment video recording starts. This is a device-level HAL/firmware issue, confirmed not to occur on Ulefone or other Xiaomi devices, and is not fixable from application code. Use the stock camera app for video on Garnet; the photo mode is unaffected.
- **OpenCV post-processing latency**: bilateral filter and CLAHE require JPEG decode + recompression, adding save time proportional to image resolution.

---

## Code Convention

All fork-specific changes are marked with `[REALCAMMI FORK]` in inline comments throughout the source, making it straightforward to identify changes when merging upstream updates.

---

## License

RealCamMI is licensed under the **GNU General Public License v3.0 or later**, the same as upstream Open Camera. See `gpl-3.0.txt` for the full license text.

---

## Credits

- **Mark Harman** — original author and maintainer of [Open Camera](https://opencamera.org.uk/)
- **Persona78** — creator of this fork ([XDA Forums](https://xdaforums.com/t/development-realcammi-a-open-camera-fork-update-28-06-2026.4793171/))
- Open Camera contributors — see `_docs/credits.html`
- Google Material Design icons — Apache License 2.0

## Download

![Download app](docs/images/download.png)

## Disclaimer

- Use at Your Own Risk!
- No Professional Advice!
- Results Not Typical!
- No Affiliate Disclaimer!

## Photo Sample

![Photo Sample](docs/images/img(1).jpg)
![Photo Sample](docs/images/img(2).jpg)
![Photo Sample](docs/images/img(3).jpg)
![Photo Sample](docs/images/img(4).jpg)
![Photo Sample](docs/images/img(5).jpg)
![Photo Sample](docs/images/img(6).jpg)
![Photo Sample](docs/images/img(7).jpg)
![Photo Sample](docs/images/img(8).jpg)
![Photo Sample](docs/images/img(9).jpg)
![Photo Sample](docs/images/img(10).jpg)

.

