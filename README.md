
<p align="center">
    <img width="400" img src="docs/images/RealCamMI.png" alt="RealCamMI logo">
</p>

# RealCamMI

**RealCamMI is a fork of [Open Camera](https://opencamera.org.uk/) by Mark Harman**, based on upstream revision [`v1.56.2` (commit `0dd4cb`)](https://sourceforge.net/p/opencamera/code/ci/v1.56.2/tree/). It is **not an independent application** — the overwhelming majority of the codebase, architecture, and UI originate from upstream Open Camera. This fork exists to fix multi-camera detection, colour/tone rendering, zoom, and image-quality issues observed on specific Xiaomi and Ulefone devices, and to add an optional OpenCV-based post-processing pipeline and an automatic HDR mode on top of the original application.

Licensed under **GPL v3.0 or later** — same licence as upstream, as required by the GPL for derivative works.

---

## Fork Notice

This project is a derivative work, distributed in compliance with the GNU GPLv3:

- Original project: **Open Camera**, © Mark Harman and contributors — [opencamera.org.uk](https://opencamera.org.uk/) / [SourceForge](https://sourceforge.net/projects/opencamera/)
- This fork: **RealCamMI**, © Persona78 (2026), maintained at [github.com/Persona78/RealCamMI](https://github.com/Persona78/RealCamMI)
- Base revision forked: `v1.56.2`, commit `0dd4cb`
- All fork-specific changes are marked with `[REALCAMMI FORK]` inline comments throughout the source — see [Code Convention](#code-convention)
- No upstream trademarks, branding, or the Open Camera name are used to represent this fork as the original application

---

## Why this fork exists

Stock Open Camera does not behave correctly on certain devices: camera detection misses or wrongly exposes virtual sensor IDs, zoom silently fails during still capture, and default colour/tone rendering diverges noticeably from the stock camera app. Every change in this fork was driven by a specific, observed, reproducible problem on real hardware — verified with side-by-side comparisons against each device's stock camera app — not by general-purpose feature requests.

**RealCamMI** stands for **Real**istic colour/tone output, originally driven by **M**ulti-**cam**era detection issues on **X**iaomi **(MI)** devices, with fixes that also benefit Ulefone hardware.

---

## Target Devices

| Device | Codename | Sensor | SoC |
|---|---|---|---|
| Xiaomi Redmi Note 13 Pro 5G / Poco X6 5G | garnet | ISOCELL HP3 200MP | Qualcomm SM7435 |
| Ulefone Armor 25T Pro | — | Samsung S5KGN1 | MediaTek Helio G99 |

Devices outside this list run the standard Open Camera code path unless explicitly fingerprinted. Samsung-specific handling (see [Attribution note](#a-note-on-samsung-specific-code) below) is inherited from upstream Open Camera, not developed for this fork's target devices.

---

## A note on Samsung-specific code

Upstream Open Camera already contains device-specific branching for Samsung hardware — most notably `EDGE_MODE_OFF` forced on Galaxy S7 / S7 Edge to avoid a glossy/"worm artefact" rendering bug, documented in upstream's own SourceForge discussion thread and a related StackOverflow report (both referenced directly in code comments). **This is original Open Camera behaviour, not something introduced by RealCamMI.**

RealCamMI's own contribution regarding Samsung is narrower and, in one case, more restrictive than a naive reading of the code might suggest:

- The `is_samsung_s7` fingerprint and its `EDGE_MODE_OFF` fix are **upstream-inherited**.
- RealCamMI's vendor HDR-extension logic (see below) **deliberately excludes Samsung devices**, including Galaxy S7 and the tested Galaxy S10e — the only Samsung device actually validated showed no benefit from the extension, so it was left out of the fork's trusted-device allowlist rather than assumed to work.

---

## What's different from upstream Open Camera

### 1. Multi-camera and physical-lens handling

**File:** `CameraControllerManager2.java`, `CameraController2.java`

Upstream calls `CameraManager.getCameraIdList()` directly wherever a camera ID is needed. On Xiaomi/Qualcomm devices this either omits real physical sensors hidden behind a logical multi-camera ID, or exposes virtual/depth/fusion camera IDs that cannot actually be opened — causing crashes or a misleading camera count.

This fork replaces direct calls with a pre-built, validated camera list and adds physical-lens awareness:

- `buildCameraIdList()` expands logical camera IDs to their physical sub-camera IDs, filters out virtual/depth/fusion IDs, and probes hidden physical cameras (IDs 2–9) specifically on Xiaomi and Ulefone devices
- `isRealCamera()` rejects sensors with no pixel array, no focal length, or missing the `BACKWARD_COMPATIBLE` capability
- `supportsVideoRecording()` rejects IDs that don't advertise usable `MediaRecorder` output sizes
- `isHiddenCameraId()`, `findLogicalParentForPhysicalId()`, and `switchLens()` add explicit support for switching between physical sub-cameras (e.g. ultrawide/telephoto) behind a logical multi-camera ID — not present upstream
- Lens type labelling (Macro detection via minimum focus distance) added on top of the Ultrawide/Telephoto thresholds reused from upstream's existing field-of-view logic

### 2. Zoom fix for Xiaomi Garnet

**File:** `CameraController2.java`, `Camera2Settings.java`

The Qualcomm CamX HAL on Garnet ignores `CONTROL_ZOOM_RATIO` / `SCALER_CROP_REGION` during still capture (`IQSetupTriggerData() Fail to get zoom ratio, pZoomRatioData is NULL`), delivering a full-resolution unzoomed image regardless of the zoom level set in the preview.

**Fix:** software JPEG crop in `onImageAvailable()`:

- The requested zoom ratio is tracked separately (`camera_settings.current_zoom_ratio`) as each zoom request is issued, independent of whether the HAL applies it
- The captured JPEG is decoded, cropped to the area corresponding to the active zoom ratio, and recompressed
- Zoom ratio used for the crop is read from `CaptureResult.CONTROL_ZOOM_RATIO` (updated every frame) instead of a cached value that can be stale during pinch-zoom gestures
- Crop centre offset fixed to correctly align with the image centre
- A dedicated `CONTROL_ZOOM_RATIO` capture-request branch was added for Android 11+ devices that support it, alongside the legacy `SCALER_CROP_REGION` path

### 3. Custom tonemap curve profiles

**Files:** `CameraController2.java`, `Camera2Settings.java`

Three custom tonemap profiles tuned through iterative pixel-level comparison against the stock camera app:

| Profile | Description |
|---|---|
| **JTVideo** | Natural rendering — clean deep shadows, smooth midtone transitions, controlled highlight rolloff. Default profile. |
| **JTLog** | Logarithmic profile — compressed shadows and highlights for maximum dynamic range, suitable for colour grading in post. |
| **S-Log3** | Sony S-Log3 implementation — maximum dynamic range for professional post-production (DaVinci Resolve, CapCut, etc.). Footage will look flat and desaturated — requires a LUT or manual grade. |

The 18-point JTVideo curve is maintained holistically: any edit is re-solved as a constrained least-squares problem (maximum |slope delta| ≤ 0.4 between consecutive segments, minimal deviation from the intended shape) rather than adjusting individual points, to avoid introducing visible tonal banding.

Also fixes the video log options being hidden unnecessarily: the `tonemap_log_max_curve_points_c` threshold was reduced from 128 to 17 (the actual number of points the custom curves use), so the Video Log and Profile Gamma options now correctly appear on Garnet.

### 4. Colour correction post-processing

**File:** `PostProcessing.java`

Corrects a systematic blue colour cast (B/R ratio ~1.12 vs ~1.00 in stock camera) using a `ColorMatrix` applied after capture.

This cannot be done via `COLOR_CORRECTION_TRANSFORM` because the Qualcomm CamX HAL silently ignores that field when AWB is in AUTO mode (a documented limitation of the Android Camera2 API on this hardware). Colour correction is applied in the post-processing pipeline instead, so it works regardless of AWB mode.

Activated via a dedicated toolbar button (tap to toggle; red = active). Visible in **Settings → GUI Icons → Show colour correction icon**.

### 5. OpenCV post-processing pipeline

**File:** `PostProcessing.java`

Four advanced post-processing features powered by OpenCV, each with a dedicated toolbar button:

| Feature | Method | Description |
|---|---|---|
| **Noise Reduction** | Bilateral Filter, ISO-adaptive strength | Smooths flat areas while preserving edges. Applied before sharpening. |
| **Sharpening** | Unsharp Mask | Enhances fine detail without colour fringing. Applied after noise reduction. |
| **Contrast Enhancement** | CLAHE (tile grid scaled to image resolution) | Improves local contrast on the L channel (Lab colour space), with brightness-weighted chroma compensation. Recovers shadow detail without blowing highlights. |
| **Blur Detection** | Laplacian variance | Analyses the final image and shows a warning toast if blur score is low. Does not modify the image. |

Processing order: Colour correction → NR → Sharpen → CLAHE → Blur Detection. Each feature is toggled independently via its own toolbar button. All buttons are visible/hidden via **Settings → GUI Icons**.

### 6. Device-specific image quality tuning

**File:** `Camera2Settings.java`, `CameraController2.java`

- **Edge mode**: `EDGE_MODE_OFF` forced for Samsung Galaxy S7 — **upstream Open Camera behaviour**, not fork-specific. `EDGE_MODE_HIGH_QUALITY` forced for Xiaomi (empirically confirmed to give better results than leaving the HAL's still-capture template default, which varies opaquely by CamX build). Default `EDGE_MODE_FAST` for all other devices.
- **Noise reduction**: `NOISE_REDUCTION_MODE_OFF` forced for Ulefone (prevents excessive blurring from the MediaTek ISP HAL-level NR) and Samsung Galaxy S7 (upstream-inherited condition). `NOISE_REDUCTION_MODE_MINIMAL` forced for Xiaomi — keeps hot-pixel correction (relevant on the 200MP HP3 sensor's binned high-ISO output) while delegating all luminance/chroma smoothing to the app's own tunable bilateral filter, instead of stacking with an opaque HAL default.
- **Burst noise-reduction tuning**: a dedicated `NOISE_REDUCTION_MODE_FAST` / `EDGE_MODE_HIGH_QUALITY` path is applied for Xiaomi and Ulefone devices during normal-mode bursts with noise reduction enabled — not present upstream, which applies no per-manufacturer branching to burst capture settings.

#### Why RealCamMI images sustain more zoom than the Ulefone stock camera

The Ulefone stock camera app is optimised by the manufacturer for immediate on-screen viewing, not for detail preservation at high zoom levels. Two decisions at factory level work against detail retention:

- The MediaTek ISP applies aggressive hardware-level Noise Reduction before the image ever reaches the JPEG encoder. This permanently erases high-frequency detail (fine textures, subtle edges) that cannot be recovered afterwards.
- The stock app uses a lower JPEG quality setting (typically 85–95%), introducing compression artefacts that become visible as soon as the image is enlarged.

RealCamMI counters both: `NOISE_REDUCTION_MODE_OFF` is forced on Ulefone to preserve the raw sensor detail the ISP would otherwise discard, and JPEG quality is set to 100% to eliminate compression loss. The trade-off is larger file sizes and slightly more visible noise in low-light conditions — but maximum detail that survives digital zoom.

### 7. Vendor camera-extension HDR: manual and automatic

**File:** `CameraController2.java`, `preview/Preview.java`

Upstream never automatically selects a vendor camera-extension session, and offers no automatic mode-switching based on scene brightness.

- **Manual (`X_HDR` photo mode)**: routes capture through the device's own vendor HDR extension pipeline (`CameraExtensionSession`, `EXTENSION_HDR`) instead of the standard capture session. Restricted to devices RealCamMI has actually vetted (`is_xiaomi || is_ulefone`) — **Samsung is deliberately excluded**: the only Samsung device tested (Galaxy S10e) showed no effect, and there's no positive evidence any Samsung device benefits.
- **Auto HDR** *(new)*: an opt-in setting that, in Standard photo mode only, reads the metered ISO/exposure time at the moment of capture. Below the low-light threshold (bright, high-dynamic-range scene) it transparently reopens the camera into the HDR extension session for that one shot, then reopens back to normal afterwards so flash and manual focus remain available for the next photo. Above the threshold, capture proceeds normally with flash available — the extension is never engaged in low light, since it disables flash regardless of scene and provides no proven benefit there.
- Extension sessions restrict flash (off/torch only), manual focus, and AE/AWB lock while active — an inherent Android Camera2 Extensions API limitation, not something this fork can work around.

### 8. Processing-time feedback

**File:** `preview/Preview.java`, `ImageSaver.java`

- Vendor extension captures (`X_HDR`, `X_Night`, etc.) show the real progress percentage reported by the device's own extension pipeline, where the vendor HAL supports it (not guaranteed on all devices).
- As a fallback covering every case where no native progress is reported — classic multi-frame HDR merge, Noise Reduction burst merge, Panorama stitching, and the Auto HDR camera-reopen cycle — a generic elapsed-time toast ("HDR... 3s") updates once a second for the duration of the operation, so slow processing never looks like a frozen app. Not shown for routine single-image saves, which are fast enough not to need it.

### 9. RAM-adaptive resolution ceiling

**File:** `MyApplicationInterface.java`

Modes that decompress and merge multiple full-resolution images in memory (Noise Reduction, classic HDR) risk running out of memory on lower-RAM devices. Upstream uses a single fixed resolution ceiling for these modes regardless of device.

RealCamMI reads the device's total RAM (`ActivityManager.MemoryInfo`) and adapts the ceiling: ≤4GB → 8MP, ≤8GB → 12MP, >8GB → 24MP — so higher-RAM devices aren't needlessly capped, and lower-RAM devices get a safer limit than a flat value would give them.

### 10. AndroidX migration

All legacy Android preference and fragment APIs migrated to AndroidX across all 80 Java files:

| Legacy | AndroidX |
|---|---|
| `android.preference.*` | `androidx.preference.*` |
| `android.app.Fragment/DialogFragment/FragmentManager` | `androidx.fragment.app.*` |
| `getFragmentManager()` | `getParentFragmentManager()` / `getSupportFragmentManager()` |
| `addPreferencesFromResource()` in `onCreate()` | `setPreferencesFromResource()` in `onCreatePreferences()` |
| Custom `DialogPreference` subclasses | AndroidX `PreferenceDialogFragmentCompat` inner classes |

### 11. Other improvements

- Default JPEG quality changed from 90% to 100%
- When any post-processing feature is active, the intermediate capture is forced to quality 100 to avoid double-compression loss
- Panorama JPEG quality now reads from user preference instead of hardcoded 90
- `setTargetFragment()` (deprecated API 31+) removed throughout
- All debug log tags unified to use the `TAG` constant (removed hardcoded manufacturer-specific strings)
- Device fingerprinting for `is_xiaomi` and `is_ulefone` introduced by this fork as the basis for the device-specific branching described above. `is_samsung` / `is_samsung_s7` fingerprints already existed upstream; this fork reuses them where relevant but deliberately keeps Samsung out of new fork-added behaviour (see [Samsung note](#a-note-on-samsung-specific-code))

---

## New features vs upstream at a glance

| Feature | Open Camera (v1.56.2) | RealCamMI |
|---|---|---|
| Physical camera discovery | `getCameraIdList()` only | Validated, filtered list + physical lens switching |
| Zoom in still capture (Garnet) | Broken (HAL bug) | Fixed via software crop |
| Tonemap profiles | Gamma / flat only | JTVideo, JTLog, S-Log3 |
| Vendor HDR extension session | Not auto-selected | Manual `X_HDR` + automatic ISO-based Auto HDR, both restricted to Xiaomi/Ulefone |
| Processing-time feedback | None | Real extension progress % where available, elapsed-time toast fallback everywhere else |
| Colour correction | — | Post-processing pipeline |
| OpenCV sharpening | — | Unsharp Mask |
| OpenCV noise reduction | — | ISO-adaptive Bilateral Filter |
| OpenCV contrast enhancement | — | CLAHE |
| Blur detection | — | Laplacian variance warning |
| Max resolution for NR/HDR modes | Fixed ceiling | RAM-adaptive ceiling (8/12/24MP) |
| Default JPEG quality | 90% | 100% |
| Xiaomi/Ulefone-specific tuning | None | Edge mode, noise reduction, burst NR, HDR extension |
| Samsung-specific tuning | S7 `EDGE_MODE_OFF` (upstream) | Unchanged from upstream; deliberately excluded from new HDR-extension logic |
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

- **Zoom crop resolution loss**: software zoom crop reduces effective resolution proportionally (~3MP at 2×, ~0.8MP at 4×). The Garnet sensor does not expose native 200MP resolution via the Camera2 API.
- **EXIF loss on zoom crop**: recompression of the zoomed JPEG discards the original EXIF metadata.
- **Video colour shift on Garnet**: a colour/exposure shift occurs the moment video recording starts. This is a device-level HAL/firmware issue, confirmed not to occur on Ulefone or other Xiaomi devices, and is not fixable from application code. Use the stock camera app for video on Garnet; photo mode is unaffected.
- **OpenCV post-processing latency**: bilateral filter and CLAHE require JPEG decode + recompression, adding save time proportional to image resolution.
- **Auto HDR capture latency**: on well-lit scenes where Auto HDR engages, the camera-reopen-and-merge cycle adds noticeable extra capture time (roughly one to a few seconds) compared to a normal shot. Low-light shots are unaffected.
- **Vendor extension behaviour is HAL-dependent and not yet validated end-to-end on real hardware**: whether the extension actually improves highlight retention on Garnet, and whether the device reports real-time progress percentage, both depend on the vendor's own (closed-source) implementation.

---

## Code Convention

All fork-specific changes are marked with `[REALCAMMI FORK]` in inline comments throughout the source, documenting the rationale for each deviation and making it straightforward to identify and re-apply changes when merging future upstream updates. Where a comment could otherwise be mistaken for a fork addition but is in fact inherited from upstream Open Camera, this is noted explicitly rather than left ambiguous.

---

## License

RealCamMI is licensed under the **GNU General Public License v3.0 or later**, the same licence as upstream Open Camera, as required for any distributed derivative work under the GPL. See `gpl-3.0.txt` for the full license text. No additional restrictions are imposed beyond those of the GPLv3.

---

## Credits

- **Mark Harman** — original author and maintainer of [Open Camera](https://opencamera.org.uk/)
- **Persona78** — creator of this fork ([XDA Forums](https://xdaforums.com/t/development-realcammi-a-open-camera-fork-update-28-06-2026.4793171/))
- Open Camera contributors — see `_docs/credits.html`
- Google Material Design icons — Apache License 2.0

## Download

![Download app](docs/images/download.png)

[RealCamMI](https://www.mediafire.com/file/urzj0hk2y5qh108/RealCamMI-release.apk/file)

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
