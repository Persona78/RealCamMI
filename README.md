
<p align="center">
    <img width="400" img src="docs/images/RealCamMI.png" alt="RealCamMI logo">
</p>

# RealCamMI

**RealCamMI is a fork of [Open Camera](https://opencamera.org.uk/) by Mark Harman**, based on upstream revision [`v1.56.2` (commit `0dd4cb`)](https://sourceforge.net/p/opencamera/code/ci/v1.56.2/tree/). It is **not an independent application** — the overwhelming majority of the codebase, architecture, and UI originate from upstream Open Camera. This fork exists to fix multi-camera detection, colour/tone rendering, zoom, and image-quality issues observed on specific Xiaomi and Ulefone devices, and to add an optional OpenCV-based post-processing pipeline, an on-device scene classifier that drives automatic HDR / night / exposure behaviour, a depth-based background blur and a photo-look matching tool on top of the original application.

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

RealCamMI's own contribution regarding Samsung is small:

- The `is_samsung_s7` fingerprint and its `EDGE_MODE_OFF` fix are **upstream-inherited**.
- RealCamMI's vendor HDR-extension logic (see below) is **not gated by device**: `Preview.supportsCameraExtension()` only requires the Camera2 API and that the camera reports `EXTENSION_HDR` support. The only Samsung device tested (Galaxy S10e) showed no benefit from the extension and there is no positive evidence any Samsung device benefits, but no code excludes Samsung. (Earlier versions of this README and of the code comment claimed an `is_xiaomi || is_ulefone` allowlist; the code never had one, and the documentation was corrected to match it on 2026-10-08.)

---

## What's different from upstream Open Camera

### 1. Multi-camera and physical-lens handling

**File:** `CameraControllerManager2.java`, `CameraController2.java`

Upstream calls `CameraManager.getCameraIdList()` directly wherever a camera ID is needed. On Xiaomi/Qualcomm devices this either omits real physical sensors hidden behind a logical multi-camera ID, or exposes virtual/depth/fusion camera IDs that cannot actually be opened — causing crashes or a misleading camera count.

This fork replaces direct calls with a pre-built, validated camera list and adds physical-lens awareness:

- `buildCameraIdList()` expands logical camera IDs to their physical sub-camera IDs, filters out virtual/depth/fusion IDs, and probes hidden physical cameras (IDs 2–9) specifically on Xiaomi and Ulefone devices
- `isRealCamera()` rejects sensors with no pixel array, no focal length, or missing the `BACKWARD_COMPATIBLE` capability
- `supportsVideoRecording()` rejects IDs that don't advertise usable `MediaRecorder` output sizes
- Physical sub-cameras (e.g. ultrawide/telephoto) behind a logical multi-camera ID are exposed as separate entries of that validated list, so the normal camera-switch control reaches them. `CameraController2` also keeps a `selectedPhysicalCameraId` hook used when building the capture session outputs; the in-app lens-switch method that used to set it had no callers and was removed (2026-10-07), so the hook is currently inactive
- Lens type labelling (Macro detection via minimum focus distance) added on top of the Ultrawide/Telephoto thresholds reused from upstream's existing field-of-view logic

### 2. Zoom handling for Xiaomi Garnet

**File:** `CameraController2.java`, `Camera2Settings.java`

Originally, the Qualcomm CamX HAL on Garnet ignored `CONTROL_ZOOM_RATIO` / `SCALER_CROP_REGION` during still capture (`IQSetupTriggerData() Fail to get zoom ratio, pZoomRatioData is NULL`), delivering a full-resolution unzoomed image regardless of the zoom level set in the preview. The first version of this fork worked around that with a software JPEG crop in `onImageAvailable()`.

**Current state (2026-10):** a side-by-side preview-vs-photo comparison at 2.0× on the Redmi Note 13 Pro 5G showed that the device's current HAL/firmware now applies zoom correctly for still capture, so the software crop was doubling the zoom (2.0× requested → ~4× effective). The crop was disabled on 2026-08-01 and has now been **removed from the code**, together with the cropped-bitmap cache that depended on it (the rule that forces JPEG quality 100 when zoom > 1× is still in the code, on all devices — see section 11).

What remains of the zoom work is the dedicated `CONTROL_ZOOM_RATIO` capture-request branch for Android 11+ devices (alongside the legacy `SCALER_CROP_REGION` path), and the use of `TEMPLATE_STILL_CAPTURE` on Xiaomi when ISO is set manually.

> **Ulefone note:** Ulefone has *not* been re-tested since the crop was removed. If a "zoom doesn't crop" report ever appears there, the old software crop (see the 1.2 source) may need to return behind an `is_ulefone` check, after repeating the same preview-vs-photo test.

### 3. Custom tonemap curve profiles

**Files:** `CameraController2.java`, `Camera2Settings.java`, `PostProcessing.java`

Ten tonemap/"Image Profile" options, tuned through iterative pixel-level comparison against the stock camera app:

| Profile | Description |
|---|---|
| **Standard** | Native camera rendering, untouched. Default. |
| **JTVideo** | Natural rendering — clean deep shadows, smooth midtone transitions, controlled highlight rolloff. |
| **JTLog** | Logarithmic profile — compressed shadows and highlights for maximum dynamic range, suitable for colour grading in post. |
| **JTLog2** | A second, more aggressive logarithmic variant. |
| **Rec709** | Standard ITU-R BT.709 OETF. |
| **sRGB** | Standard sRGB transfer function. |
| **Log** | Adjustable-strength logarithmic curve. |
| **Gamma** | Adjustable power-law gamma curve. |
| **S-Log3** | Sony S-Log3 implementation — maximum dynamic range for professional post-production (DaVinci Resolve, CapCut, etc.). Footage will look flat and desaturated — requires a LUT or manual grade. |
| **S-Log3C** | A second S-Log3-style curve variant (`TONEMAPPROFILE_SLOG3C`), added alongside S-Log3. |

The 18-point JTVideo curve is maintained holistically: any edit is re-solved as a constrained least-squares problem (maximum |slope delta| ≤ 0.4 between consecutive segments, minimal deviation from the intended shape) rather than adjusting individual points, to avoid introducing visible tonal banding.

Also fixes the video log options being hidden unnecessarily: the `tonemap_log_max_curve_points_c` threshold was reduced from upstream's 128 to 64 (the custom curves use 17–18 points each), so the Video Log and Profile Gamma options now correctly appear on Garnet.

**Photo vs video handling differs, deliberately:** in video mode, the chosen profile is applied live via `TONEMAP_MODE` exactly as before. In photo mode, a profile is **never** applied live to the capture request — confirmed on-device (2026-07-18) that doing so caused the Xiaomi garnet AE algorithm to drift the preview progressively darker over several seconds, reproduced with every non-Standard profile. Instead, for photos, the camera always captures in the native/Standard tonemap (stable, matches Standard's proven behaviour), and the chosen profile's curve is applied afterwards to the captured bitmap in `PostProcessing.applyImageProfileMat()` — including a decode-back-to-linear-light step, since the curve is an OETF designed for linear input, not for an already gamma-encoded JPEG. One consequence: the live photo preview always shows the Standard look while framing; only the saved photo shows the selected profile's rendering.

**Image Profile Strength** (Settings, 0–100, default 10): the profile's LUT is blended with an identity mapping, so 100 applies the full curve and 0 is treated exactly like "no profile selected" (the curve isn't even resolved). Photo profiles are skipped for HDR captures and for video snapshots.

### 3a. AI Scene Detection

**File:** `SceneDetector.java`, integrated via `CameraController2.java`, `Preview.java`, `PostProcessing.java`

A lightweight on-device scene classifier, active whenever AI Scene Detection or one of the automatic modes below is enabled. Reads a dedicated low-resolution (320×240) analysis frame in the background and classifies the scene into one of **five** categories, using 3-consecutive-frame hysteresis (thread-safe) to avoid flickering between categories:

| Category | Detection method | Effect |
|---|---|---|
| **EXTREME_BACKLIT** | Luminance histogram (large dark + large bright fraction at once) | Mild -2 EV exposure bias in `Camera2Settings.setupBuilder()`, but only when Auto HDR is unavailable/off for that shot, ISO/exposure aren't under manual control, and no extension session is active — a safety net against blown-out skies/highlights. |
| **OVEREXPOSED** | Luminance histogram (almost entirely near-clipped, essentially no shadow detail — beach/snow/sky-filling shots). Mutually exclusive with backlit by construction. | With **Auto Exposure Correction** enabled: -2 EV compensation, applied only while the user's own EV compensation is 0 (never fights a deliberate choice) and released when the scene changes. |
| **LOW_LIGHT** | Metered ISO/exposure time (`HDRProcessor.sceneIsLowLight`) | Noise-reduction strength STRONG for the photo; can trigger **Auto Night Mode** (section 7). |
| **INDOOR** | MLKit Image Labeling | Raises sharpening amount to 0.65 (from the 0.5 default) |
| **STANDARD** | None of the above | No adjustment |

Priority when several signals fire: backlit/overexposed > low-light > indoor > standard. Backlit, overexposed and low-light are decided without calling MLKit at all for that frame.

Noise reduction strength while AI Scene Detection is on is chosen by the scene (LOW_LIGHT → STRONG, otherwise LOWER_LIGHT) and the manual NR strength preset (LOWER_LIGHT / LIGHT / MEDIUM / STRONG) is ignored; with it off, the manual preset applies.

Apart from the optional ±2 EV corrections above, scene detection does not alter the live preview's brightness or colour.

### 4. Colour post-processing: contrast boost, Color Boost and Match Photo's Look

**File:** `PostProcessing.java`, `PhotoLookMatcher.java`, `PreferenceKeys.java`

**Colour contrast boost.** The toolbar button that was originally a blue-cast correction (B/R ratio ~1.12 vs ~1.00 in stock camera) is now a neutral **+20% contrast boost** (`PRO_COLOR_MATRIX`, a `ColorMatrix` applied after capture, no channel bias and no desaturation). It is applied in post-processing rather than via `COLOR_CORRECTION_TRANSFORM`/`COLOR_CORRECTION_GAINS` because the Camera2 specification ignores those fields whenever `CONTROL_AWB_MODE != OFF` — universal documented platform behaviour, not a vendor quirk. Activated via a dedicated toolbar button (tap to toggle; red = active), visible in **Settings → GUI Icons**.

**Color Boost** *(new)*. Six user-editable numeric fields (GCam-style dialogs, not sliders): red / green / blue scale, offset, saturation and hue. Scale values are stored as a delta (default `0.000`, effective multiplier = 1 + value). Defaults are a genuine no-op and the step is skipped entirely when nothing was changed. All values are clamped to safe ranges, so a bad entry can never crush a channel to black or white. It runs in the single Mat pipeline right after the Image Profile.

**Match Photo's Look** *(new)*. Inspired by Oppo's "One-Tap Recall": the user picks 1–3 reference photos (image chooser in `MainActivity`) and `PhotoLookMatcher` suggests values for the Color Boost fields from the whole-image average colour tint and HSV saturation. The tint and saturation suggestions are deliberately damped (`TINT_SENSITIVITY` 2.2, `TINT_DAMPING` 0.5, `BASELINE_SATURATION` 0.35, `SATURATION_DAMPING` 0.5 — hand-tuned on-device against real captures). It does **not** reproduce a tonal curve and is not subject-aware: treat the result as a starting point to fine-tune by hand.

**Bugfix (2026-07-17):** the manual-white-balance code path in `Camera2Settings.setWhiteBalance()` had its condition inverted — it checked `CONTROL_AWB_MODE_AUTO` instead of `CONTROL_AWB_MODE_OFF`, meaning the manual colour-correction-matrix/gains override ran every time AWB was in its default AUTO state instead of only when the user picked a manual white-balance temperature. This was invisible on Garnet (where those fields are ignored under AWB AUTO regardless), but caused a visible green/negative colour cast on Ulefone Armor 25T under AWB AUTO in low light, since its HAL does not ignore them. Fixed by correcting the condition.

**Ongoing tuning note:** the contrast boost and Color Boost stack with an active Image Profile's own curve; values are retuned from time to time rather than treated as a final calibration.

### 5. OpenCV post-processing pipeline

**File:** `PostProcessing.java`

Four user-toggleable post-processing features powered by OpenCV, each with a dedicated toolbar button, plus always-on internal steps that aren't separately toggleable. Since 2026-09-29 the colour/NR/sharpen steps run as one **fused `Mat` pipeline**: the JPEG is decoded once, converted once to BGR, processed step by step on `Mat`s (noise reduction in float precision) and encoded once, instead of a decode/encode round-trip per step.

| Feature | Method | Description |
|---|---|---|
| **Noise Reduction** | Bilateral filter, strength chosen by scene/preset (see section 3a) | Smooths flat areas while preserving edges. Applied before sharpening. When the toggle is off, the mildest NR level (`NR_STRENGTH_LOWER_LIGHT`) still runs as an always-on floor. |
| **Sharpening** | Unsharp mask, amount 0.5 (0.65 indoors) scaled by **Sharpness Strength** (0–100, default 10) | Optional extra sharpening layered on top of the always-on baseline pass below. The amount is computed per photo and passed as a parameter (it used to be a shared field, which could race between concurrent saves). |
| **Contrast Enhancement** | CLAHE (tile grid scaled to image resolution) | Improves local contrast on the L channel (Lab colour space). Recovers shadow detail without blowing highlights. |
| **Blur Detection** | Laplacian variance | Analyses the final image and shows a warning toast if blur score is low. Does not modify the image. |

Always-on, not separately toggleable:

- **Baseline sharpen**: a permanent, subtle unsharp-mask pass applied to every photo regardless of the Sharpening toggle — closer to what a stock camera ISP already does as standard. Tuning note: on Ulefone, indoor/low-light with Noise Reduction off, the default amount was found to amplify visible sensor noise and was lowered accordingly (2026-07-30) — see the constant's doc comment in `PostProcessing.java` for the current value.
- **Tonemap desaturation compensation**: corrects the saturation loss caused by the Camera2 `TonemapCurve` being applied identically to R/G/B (that mechanism has no luma-only mode, so highlights lose saturation as a side effect). It runs in video, and in photos whenever an Image Profile is selected with strength > 0, independent of the Contrast Enhancement toggle. Note: the condition re-resolves the selected profile rather than checking the curve actually applied, so it also runs on HDR photos, where the profile curve itself is skipped.

Processing order: Colour contrast boost → Image Profile curve (photos only, see section 3) → Color Boost → Noise Reduction (float) → Baseline Sharpen → Sharpening (optional) → Tonemap Desaturation Compensation (when a profile was applied) → Contrast Enhancement → Blur Detection → Depth Effect / Bokeh (section 3b — deliberately last, so it never triggers a false blur warning). Each user-facing feature is toggled independently via its own toolbar button; all buttons are visible/hidden via **Settings → GUI Icons**.

### 3b. Depth Effect (Bokeh)

**File:** `DepthEffect.java`, integrated via `PostProcessing.java`

An optional portrait-style background blur applied to photos only (not video), toggled via **Settings → Processing → Depth Blur**. Estimates a depth map on-device using a bundled MiDaS v2.1 (small) TFLite model, refines it with a fast guided filter (plain box-filter based, since the project's OpenCV build doesn't include the `ximgproc` module needed for a ready-made joint bilateral filter), and blends a sharp foreground with a Gaussian-blurred background using a saturating falloff curve around an estimated focus depth. The blur amount is a continuous function of per-pixel depth (never a binary mask). A **strength preset** (subtle / natural / strong / very strong) is chosen in Settings, and the large downscales use libyuv (`LibYuvScaler`). The TFLite interpreter is lazily created once and reused for the lifetime of the app's `PostProcessing` instance (not recreated per photo), released on app shutdown. Runs last in the post-processing pipeline (see section 5) so it never interferes with blur detection. Not currently recommended together with the colour contrast boost — the two haven't been fully validated together yet.

### 6. Device-specific image quality tuning

**File:** `Camera2Settings.java`, `CameraController2.java`

- **Edge mode**: `EDGE_MODE_OFF` forced for Samsung Galaxy S7 — **upstream Open Camera behaviour**, not fork-specific. `EDGE_MODE_HIGH_QUALITY` forced for Xiaomi (empirically confirmed to give better results than leaving the HAL's still-capture template default, which varies opaquely by CamX build). Default `EDGE_MODE_FAST` for all other devices.
- **Noise reduction**: `NOISE_REDUCTION_MODE_OFF` forced for Ulefone (prevents excessive blurring from the MediaTek ISP HAL-level NR) and Samsung Galaxy S7 (upstream-inherited condition). `NOISE_REDUCTION_MODE_MINIMAL` forced for Xiaomi — keeps hot-pixel correction (relevant on the 200MP HP3 sensor's binned high-ISO output) while delegating all luminance/chroma smoothing to the app's own tunable bilateral filter, instead of stacking with an opaque HAL default.
- **Burst noise-reduction tuning**: a dedicated `NOISE_REDUCTION_MODE_FAST` / `EDGE_MODE_HIGH_QUALITY` path is applied for Xiaomi and Ulefone devices during normal-mode bursts with noise reduction enabled — not present upstream, which applies no per-manufacturer branching to burst capture settings.

#### Why RealCamMI images sustain more zoom than the Ulefone stock camera

The Ulefone stock camera app is optimised by the manufacturer for immediate on-screen viewing, not for detail preservation at high zoom levels. Two decisions at factory level work against detail retention:

- The MediaTek ISP applies aggressive hardware-level Noise Reduction before the image ever reaches the JPEG encoder. This permanently erases high-frequency detail (fine textures, subtle edges) that cannot be recovered afterwards.
- The stock app uses a lower JPEG quality setting (typically 85–95%), introducing compression artefacts that become visible as soon as the image is enlarged.

RealCamMI counters both: `NOISE_REDUCTION_MODE_OFF` is forced on Ulefone to preserve the raw sensor detail the ISP would otherwise discard, and the default JPEG quality is 100% to eliminate compression loss. The trade-off is larger file sizes and slightly more visible noise in low-light conditions — but maximum detail that survives digital zoom.

### 7. Vendor camera extensions and automatic modes

**File:** `CameraController2.java`, `preview/Preview.java`, `HDRProcessor.java`

Upstream never automatically selects a vendor camera-extension session, and offers no automatic mode-switching based on scene brightness.

- **Manual (`X_HDR` photo mode)**: routes capture through the device's own vendor HDR extension pipeline (`CameraExtensionSession`, `EXTENSION_HDR`) instead of the standard capture session. Offered whenever the Camera2 API is in use and the device reports `EXTENSION_HDR` support; there is **no** Xiaomi/Ulefone check in the code (earlier documentation said otherwise — corrected 2026-10-08). Only one Samsung device was tested (Galaxy S10e) and it showed no effect.
- **Auto HDR**: opt-in setting that, in Standard photo mode only (not video), reads the metered ISO at the moment of capture. At or below the ISO threshold (800 — a bright, high-dynamic-range scene) it transparently reopens the camera into the HDR extension for that one shot, then reopens back to normal afterwards so flash and manual focus remain available. Since 2026-08-12 it is **no longer gated on the scene category**. If the extension isn't available it falls back to the classic software HDR (3-exposure bracket). In low light it never engages, since the extension disables flash and has no proven benefit there.
- **Auto Night Mode** *(new)*: opt-in; when AI Scene Detection reports LOW_LIGHT, it uses the vendor `EXTENSION_NIGHT` session (`X_Night`) for that shot and, where that extension is unavailable, falls back to the software Noise Reduction burst. A short settle delay after reopening applies to Night mode only.
- **Best Shot** *(new, experimental)*: opt-in, Standard photo mode only (never video snapshots or capture-intent requests). Takes a small fixed burst (3 frames) and keeps the sharpest one, using the same sharpness test as the NR/HDR "lucky imaging" reference-frame selection (`HDRProcessor`).
- **Auto Exposure Correction**: see section 3a (OVEREXPOSED → -2 EV).
- If the app is closed or crashes while a temporary automatic mode (Auto HDR / Night) has changed the active photo mode, the original mode is recovered on the next start (`onResume`), so the camera can't stay stuck in the temporary one.
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

All legacy Android preference and fragment APIs migrated to AndroidX across the project's 88 Java files:

| Legacy | AndroidX |
|---|---|
| `android.preference.*` | `androidx.preference.*` |
| `android.app.Fragment/DialogFragment/FragmentManager` | `androidx.fragment.app.*` |
| `getFragmentManager()` | `getParentFragmentManager()` / `getSupportFragmentManager()` |
| `addPreferencesFromResource()` in `onCreate()` | `setPreferencesFromResource()` in `onCreatePreferences()` |
| Custom `DialogPreference` subclasses | AndroidX `PreferenceDialogFragmentCompat` inner classes |

### 11. Other improvements

- Default JPEG quality changed from 90% to 100% (code default and the settings-screen default are now consistent)
- Quality 100 is still used for the multi-frame fusion modes (HDR / DRO / Noise Reduction), for non-standard output formats, and whenever zoom > 1× (on all devices; this rule dates from the removed software crop but was kept). Single-frame cosmetic post-processing (colour, NR, sharpen, CLAHE, depth blur) deliberately does **not** force it any more (2026-07-28: the extra codec work was costly on lower-end SoCs for a small gain)
- Manual-ISO **extra exposure compensation** setting, a persisted **metering mode** selector (Matrix / Center / Spot), and a live histogram while ISO is manual
- Highlight-clipping warning, and the white-balance menu's Auto option now resets ISO / exposure / EV
- EXIF now stores the 35 mm-equivalent focal length, and the compass cardinal direction can be added to the photo stamp
- Noise-reduction burst frame counts: `N_IMAGES_NR_DARK` = 10 (upstream 8) and `N_IMAGES_NR_DARK_LOW_LIGHT` = 15 (the two constants had been swapped and were fixed on 2026-09-18). The burst merge picks the sharper of the first frames as reference ("lucky imaging")
- Settings backup/restore handles set-type preferences and no longer relies only on the app-private folder
- Garnet video uses the Android 15+ low-light AE mode where available
- The "processing" toast no longer gets stuck after an auto-mode cycle
- Panorama JPEG quality now reads from user preference instead of hardcoded 90
- `setTargetFragment()` (deprecated API 31+) removed throughout
- All debug log tags unified to use the `TAG` constant (removed hardcoded manufacturer-specific strings)
- Device fingerprinting for `is_xiaomi` and `is_ulefone` introduced by this fork as the basis for the device-specific branching described above. `is_samsung` / `is_samsung_s7` fingerprints already existed upstream; the fork-added code has no Samsung-specific branches (see [Samsung note](#a-note-on-samsung-specific-code))
- Code audit (2026-10-07/08): removed dead code (the unused camera diagnostics/dual-camera test classes, the disabled zoom-crop block and its cache, unused lens-switch helpers, unused imports), made the sharpen amount a per-photo parameter and the scene-category hysteresis thread-safe (both behaviour-preserving), aligned the settings-screen JPEG-quality default with the code default (100), and corrected stale comments and README statements to match what the code actually does

---

## New features vs upstream at a glance

| Feature | Open Camera (v1.56.2) | RealCamMI |
|---|---|---|
| Physical camera discovery | `getCameraIdList()` only | Validated, filtered list + physical lens switching |
| Zoom in still capture (Garnet) | Broken (HAL bug, at the time) | `CONTROL_ZOOM_RATIO` capture branch; HAL now handles zoom, software crop removed |
| Tonemap profiles | Gamma / flat only | Standard, JTVideo, JTLog, JTLog2, Rec709, sRGB, Log, Gamma, S-Log3, S-Log3C — applied live in video, post-capture in photos (adjustable strength) |
| AI Scene Detection | — | On-device classifier (backlit / overexposed / low-light / indoor / standard) adjusts NR strength, sharpening and exposure bias automatically |
| Depth Effect (Bokeh) | — | On-device MiDaS depth estimation + guided filter, portrait-style background blur, photos only |
| Vendor HDR / Night extension | Not auto-selected | Manual `X_HDR`, automatic ISO-based Auto HDR and scene-based Auto Night Mode (with software fallbacks); extensions available wherever the device reports support (Camera2 only) |
| Best Shot | — | Experimental 3-frame burst keeping the sharpest frame |
| Match Photo's Look | — | Suggests Color Boost values from 1–3 reference photos |
| Processing-time feedback | None | Real extension progress % where available, elapsed-time toast fallback everywhere else |
| Colour contrast boost / Color Boost | — | Post-processing (+20% contrast, plus six editable colour fields) |
| OpenCV sharpening | — | Unsharp Mask |
| OpenCV noise reduction | — | Scene-adaptive Bilateral Filter (float pipeline) |
| OpenCV contrast enhancement | — | CLAHE |
| Blur detection | — | Laplacian variance warning |
| Max resolution for NR/HDR modes | Fixed ceiling | RAM-adaptive ceiling (8/12/24MP) |
| Default JPEG quality | 90% | 100% |
| Xiaomi/Ulefone-specific tuning | None | Edge mode, noise reduction, burst NR |
| Samsung-specific tuning | S7 `EDGE_MODE_OFF` (upstream) | Unchanged from upstream; no Samsung-specific logic added by the fork |
| AndroidX migration | Legacy | Full AndroidX |

---

## Build Configuration

| Property | Value |
|---|---|
| `applicationId` | `net.sourceforge.opencamera.realcammi` |
| `versionName` | `1.2-RealCamMI` |
| `versionCode` | `2` |
| `minSdkVersion` | `30` (Android 11) |
| `targetSdkVersion` | `37` (Android 17) |
| `compileSdk` | `37` |
| ABI filters | `arm64-v8a`, `armeabi-v7a` |

### Dependencies (additions over upstream)

```gradle
implementation 'com.google.mlkit:image-labeling:17.0.9'
implementation 'org.tensorflow:tensorflow-lite:2.16.1'
implementation 'org.tensorflow:tensorflow-lite-support:0.4.4'
implementation 'com.quickbirdstudios:opencv:4.5.3.0'
implementation 'io.github.crow-misia.libyuv:libyuv-android:0.44.0'
```

The build file also pins `commons-lang3`, `tomcat-embed-core` and `aspectjrt` to recent versions. None of them is imported by the app's own source (verified 2026-10-07), so they appear to be version overrides for transitive dependencies; they were left untouched.

`build.gradle.txt` in the project root is the source of truth for the module build configuration; `app/build.gradle` is generated from it.

---

## Known Limitations

- **Video colour shift on Garnet**: a colour/exposure shift occurs the moment video recording starts. This is a device-level HAL/firmware issue, confirmed not to occur on Ulefone or other Xiaomi devices, and is not fixable from application code. Use the stock camera app for video on Garnet; photo mode is unaffected.
- **OpenCV post-processing latency**: bilateral filter and CLAHE require JPEG decode + recompression, adding save time proportional to image resolution. The fused Mat pipeline reduces, but does not remove, this cost.
- **Zoom on Ulefone not re-tested**: see section 2.
- **Auto Exposure Correction, Best Shot and Auto Night Mode are young features**: their thresholds (e.g. the -2 EV step) are initial estimates that have not been validated across many devices or scenes.
- **Toasts for automatic modes are English-only**: "Auto HDR" / "Auto Night Mode" processing toasts are not yet in the translatable string resources.
- **Auto HDR capture latency**: on well-lit scenes where Auto HDR engages, the camera-reopen-and-merge cycle adds noticeable extra capture time (roughly one to a few seconds) compared to a normal shot. Low-light shots are unaffected.
- **Image Profile preview doesn't match the saved photo**: in photo mode, the live preview always shows the Standard/native look regardless of the selected Image Profile (see section 3) — the profile's rendering only appears in the saved photo, applied afterwards in post-processing. This is a deliberate trade-off to avoid an AE exposure-drift bug confirmed when profiles were applied live to photo capture; showing the profile live in the preview as well would need a separate real-time preview-only rendering path, not yet implemented.
- **Vendor extension behaviour is HAL-dependent and not yet validated end-to-end on real hardware**: whether the extensions actually improve highlight retention / low-light results on Garnet, and whether the device reports real-time progress percentage, both depend on the vendor's own (closed-source) implementation.

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

[RealCamMI](https://www.mediafire.com/file/5mdq2pgrwbeuj8w/RealCamMI-release.apk/file)

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
