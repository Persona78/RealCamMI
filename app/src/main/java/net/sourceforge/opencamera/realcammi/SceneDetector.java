package net.sourceforge.opencamera.realcammi;

import android.media.Image;
import android.util.Log;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;

/** [REALCAMMI FORK] AI scene detection — Piece 2 of the "adjust before capture, not after"
 *  feature (see CameraController2's analysisImageReader, added in Piece 1).
 *
 *  Classifies each throttled preview frame into one of a minimal 3-category ruleset, combining
 *  three different signals, each used for what it's actually good at:
 *
 *   - EXTREME_BACKLIT: cheap synchronous luminance-histogram check on the frame's own Y plane.
 *     No MLKit involved — MLKit's generic Image Labeling model has no "backlit" label, this is
 *     plain histogram math (significant population in both the dark and near-clipped bins).
 *   - LOW_LIGHT: reuses HDRProcessor.sceneIsLowLight(iso, exposure_time), the same
 *     already-tested heuristic the Auto HDR feature relies on — not re-derived from the
 *     analysis frame's own pixel brightness, which would be normalised by preview AE gain
 *     and less reliable than the actual metered ISO/exposure time.
 *   - INDOOR: the one category MLKit's default Image Labeling model is actually suited for —
 *     matches generic content labels (furniture, room, wall, ceiling, etc.) against a curated
 *     keyword list.
 *
 *  Priority order when multiple signals fire on the same frame: backlit > low-light > indoor >
 *  standard (default/no strong signal). Backlit and low-light are checked first and, if
 *  positive, skip the MLKit call entirely for that frame — saves battery/inference time on
 *  frames where we already have a confident answer without needing content classification.
 *
 *  Hysteresis: a category change is only reported to the listener after HYSTERESIS_COUNT
 *  consecutive classifications agree, to avoid flip-flopping camera settings on a borderline/
 *  ambiguous scene (e.g. someone slowly panning across a room with a bright window in view).
 */
public class SceneDetector {
    private static final String TAG = "SceneDetector";

    public enum SceneCategory {
        STANDARD,        // default - no strong signal for any of the categories below
        EXTREME_BACKLIT,
        LOW_LIGHT,
        INDOOR
    }

    public interface Listener {
        void onSceneCategoryChanged(SceneCategory category);
    }

    // Luminance histogram thresholds (Y plane is 0..255)
    private static final int DARK_BIN_THRESHOLD = 40;    // sample counts as "dark" below this
    private static final int BRIGHT_BIN_THRESHOLD = 220; // sample counts as "near-clipped" above this
    private static final float BACKLIT_DARK_FRACTION = 0.25f;   // >=25% of sampled pixels dark
    private static final float BACKLIT_BRIGHT_FRACTION = 0.15f; // >=15% of sampled pixels near-clipped
    private static final int HISTOGRAM_SAMPLE_STEP = 4; // sample every 4th pixel in each dimension

    // MLKit generic Image Labeling content labels associated with indoor scenes.
    // Not exhaustive by design - false negatives (missing an indoor scene) just mean we fall
    // back to STANDARD, which is a safe default, not a wrong/harmful one.
    private static final String[] INDOOR_LABELS = {
            "furniture", "room", "chair", "table", "wall", "ceiling", "floor",
            "shelf", "door", "window", "interior design", "home appliance",
            "kitchen appliance", "bed", "couch", "curtain", "cabinetry", "desk"
    };
    private static final float INDOOR_LABEL_MIN_CONFIDENCE = 0.6f;

    private static final int HYSTERESIS_COUNT = 3;

    private final ImageLabeler labeler;
    private final Listener listener;

    // [REALCAMMI FORK] Thread-safe synchronization layers for background pipelines
    private volatile SceneCategory current_category = SceneCategory.STANDARD;
    private SceneCategory pending_category = SceneCategory.STANDARD;
    private int pending_count = 0;
    private volatile boolean mlkit_busy = false;

    public SceneDetector(Listener listener) {
        this.listener = listener;
        this.labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS);
    }

    public void close() {
        labeler.close();
    }

    public SceneCategory getCurrentCategory() {
        return current_category;
    }

    /**
     * Orchestrates synchronous baseline math checks and schedules asynchronous MLKit
     * inference sessions without blocking the native hardware camera preview pipeline.
     */
    public void classifyFrame(Image image, int rotation_degrees, int iso, long exposure_time_ns, boolean has_metering) {
        SceneCategory histogram_result = classifyByLuminanceHistogram(image);
        if( histogram_result == SceneCategory.EXTREME_BACKLIT ) {
            reportCandidate(SceneCategory.EXTREME_BACKLIT);
            image.close();
            return;
        }

        if( has_metering && HDRProcessor.sceneIsLowLight(iso, exposure_time_ns) ) {
            reportCandidate(SceneCategory.LOW_LIGHT);
            image.close();
            return;
        }

        if( mlkit_busy ) {
            image.close();
            return;
        }
        mlkit_busy = true;

        try {
            InputImage input = InputImage.fromMediaImage(image, rotation_degrees);
            labeler.process(input)
                    .addOnSuccessListener(labels ->
                            reportCandidate(isIndoorLabelSet(labels) ? SceneCategory.INDOOR : SceneCategory.STANDARD))
                    .addOnFailureListener(e -> {
                        if( MyDebug.LOG )
                            Log.e(TAG, "MLKit image labeling failed: " + e.getMessage());
                    })
                    .addOnCompleteListener(task -> {
                        // [REALCAMMI FORK] Safely releases frame resources inside the callback thread
                        mlkit_busy = false;
                        image.close();
                    });
        } catch (Exception e) {
            if( MyDebug.LOG ) Log.e(TAG, "Failed to instantiate InputImage from media layer", e);
            mlkit_busy = false;
            image.close();
        }
    }

    /**
     * Executes a cheap synchronous luminance-histogram calculation on the frame's Y plane.
     * Evaluates boundary population profiles to identify extreme backlit anomalies.
     */
    private SceneCategory classifyByLuminanceHistogram(Image image) {
        if (image == null || image.getPlanes() == null || image.getPlanes().length == 0) {
            return SceneCategory.STANDARD;
        }

        Image.Plane y_plane = image.getPlanes()[0]; // Y channel is always plane 0 in YUV_420_888
        ByteBuffer buffer = y_plane.getBuffer();
        int row_stride = y_plane.getRowStride();
        int pixel_stride = y_plane.getPixelStride();
        int width = image.getWidth();
        int height = image.getHeight();

        int dark_count = 0;
        int bright_count = 0;
        int total = 0;

        // Microscopic scan: sample pixels based on step intervals to save memory bandwidth
        for( int y = 0; y < height; y += HISTOGRAM_SAMPLE_STEP ) {
            int row_start = y * row_stride;
            for( int x = 0; x < width; x += HISTOGRAM_SAMPLE_STEP ) {
                int value = buffer.get(row_start + x * pixel_stride) & 0xFF;
                if( value < DARK_BIN_THRESHOLD )
                    dark_count++;
                else if( value > BRIGHT_BIN_THRESHOLD )
                    bright_count++;
                total++;
            }
        }

        if (total == 0) return SceneCategory.STANDARD;

        float dark_fraction = (float) dark_count / total;
        float bright_fraction = (float) bright_count / total;

        // Extreme backlit scenario: large shadowed region juxtaposed against near-clipped highlights
        if (dark_fraction >= BACKLIT_DARK_FRACTION && bright_fraction >= BACKLIT_BRIGHT_FRACTION) {
            return SceneCategory.EXTREME_BACKLIT;
        }

        return SceneCategory.STANDARD;
    }

    /**
     * Iterates over MLKit labels and performs a professional-tier keyword verification
     * against the indoor lookup dictionary to isolate room/furniture metadata.
     */
    private boolean isIndoorLabelSet(List<ImageLabel> labels) {
        if (labels == null || labels.isEmpty()) {
            return false;
        }

        for (ImageLabel label : labels) {
            if (label.getConfidence() >= INDOOR_LABEL_MIN_CONFIDENCE) {
                String labelText = label.getText().toLowerCase(Locale.ROOT).trim();
                for (String indoorLabel : INDOOR_LABELS) {
                    if (labelText.equals(indoorLabel)) {
                        if (MyDebug.LOG) {
                            Log.d(TAG, "Matched Indoor Content: " + labelText + " (Conf: " + label.getConfidence() + ")");
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Evaluates incoming frame candidates against a strict hysteresis counter threshold.
     * Smooths out transition fluctuations to avoid rapid state bouncing in active capture requests.
     */
    private void reportCandidate(SceneCategory candidate) {
        if (candidate == pending_category) {
            pending_count++;
            if (pending_count >= HYSTERESIS_COUNT) {
                if (current_category != pending_category) {
                    current_category = pending_category;
                    if (listener != null) {
                        listener.onSceneCategoryChanged(current_category);
                    }
                    if (MyDebug.LOG) {
                        Log.i(TAG, "Scene stabilized. Transmitted new profile to HAL3: " + current_category.name());
                    }
                }
            }
        } else {
            pending_category = candidate;
            pending_count = 1;
        }
    }
}
