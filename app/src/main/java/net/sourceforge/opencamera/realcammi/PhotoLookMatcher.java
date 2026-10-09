package net.sourceforge.opencamera.realcammi;

import android.graphics.Bitmap;   // source image type this class analyzes
import android.graphics.Color;    // used only for Color.RGBToHSV()
import android.util.Log;          // debug logging, gated by MyDebug.LOG
import java.util.List;            // used by analyzeMultiple() to accept 1-3 reference bitmaps

/** [REALCAMMI FORK 2026-09-18] "Match Photo's Look" - inspired by Oppo's "One-Tap Recall" feature
 *  (extracts filter/saturation/tonal parameters from a photo the user picked and applies them to
 *  the camera). This is a software-only analog built entirely on infrastructure this fork already
 *  has: the six Color Boost fields (PostProcessing.applyColorBoostMat(), see PreferenceKeys.java)
 *  are exactly the "knobs" a look-matching tool needs to adjust, and they were already made safe
 *  to set programmatically by the 2026-09-15 clamping fix (COLOR_BOOST_SCALE_MIN/MAX etc. in
 *  PostProcessing.java) - a suggestion from this class can never crash a channel to black/white
 *  even if the analysis is a rough estimate.
 *
 *  What this does NOT do, to be upfront about the scope: it does not reproduce a tonal curve
 *  (Oppo's tool extracts one; Color Boost has no per-tone curve control, only flat scale/offset/
 *  saturation/hue), and it does not attempt any subject/scene-aware analysis - it is a simple,
 *  whole-image average colour/saturation estimate. Treat the result as a reasonable starting
 *  point to fine-tune by hand via the Color Boost sliders, not a precise match.
 */
public class PhotoLookMatcher {
    private static final String TAG = "PhotoLookMatcher"; // logcat tag for this class only

    // [REALCAMMI FIX 2026-09-18] Was 3.0 - recalibrated together with the damping factor below
    // after a real-world test (author's own reference photo, a fairly neutral interior/storage
    // room scene) produced overly strong suggestions. Lowered to 2.0 and paired with
    // TINT_DAMPING so a single reference photo can't push a suggestion to its full raw strength.
    // [REALCAMMI FIX 2026-09-19] Raised again to 2.8, hand-tuned by the author against further
    // real captures on-device until the Color Boost result visually matched the reference photo
    // as closely as possible - 2.0 undershot in practice. Kept paired with TINT_DAMPING (now
    // 0.6) below, so the combined strength (2.8 * 0.6 = 1.68) is still well short of the original
    // uncalibrated 3.0 * 1.0.
    // NOTE FOR FUTURE ME: multiplies the raw (channel - luminance) tint deviation before damping.
    // Higher = stronger colour-cast correction suggested per unit of measured deviation.
    // [REALCAMMI FIX 2026-10-07] Comment audit: the 2026-09-19 note above (2.8, paired with 0.6)
    // records an intermediate tuning state. CURRENT values after later hand-tuning:
    // TINT_SENSITIVITY = 2.2 and TINT_DAMPING = 0.5 (combined strength 2.2 * 0.5 = 1.1).
    private static final float TINT_SENSITIVITY = 2.2f; // previously 2.0, originally 3.0

    // [REALCAMMI FIX 2026-09-18] Further softens the tint suggestion after TINT_SENSITIVITY is
    // applied: suggestion = raw * TINT_DAMPING. 1.0 = no extra damping; lower = gentler. Exists
    // for the same reason as SATURATION_DAMPING below - a single whole-image average is a rough
    // estimate, and a reference photo with unusual lighting (e.g. one dominant coloured light
    // source) can otherwise map onto an overly strong scale value.
    // [REALCAMMI FIX 2026-09-19] Lowered from 0.7 to 0.6 alongside TINT_SENSITIVITY's increase
    // above - hand-tuned together against real captures, not independently.
    // NOTE FOR FUTURE ME: multiplicative safety margin applied AFTER TINT_SENSITIVITY.
    // Tune TINT_SENSITIVITY and TINT_DAMPING together, not in isolation (see combined-strength math above).
    // [REALCAMMI FIX 2026-10-07] Current value is 0.5 (the 0.6 mentioned above was intermediate).
    private static final float TINT_DAMPING = 0.5f; // previously 0.7

    // [REALCAMMI FIX 2026-09-18] Was 0.45 - too high. Traced from a real-world failure report: a
    // reference photo of a fairly neutral interior/storage scene (white walls, beige floor, a few
    // coloured objects) measured an average HSV saturation of only ~0.27, but with the old 0.45
    // baseline that produced a suggested multiplier of 0.608 - a drastic ~40% desaturation - which
    // washed out the scene's actual coloured objects along with its neutral surfaces. The core
    // problem: average saturation reflects how colourful the *subject matter* is at least as much
    // as any deliberate colour grading, and plenty of ordinary photos (interiors, overcast days,
    // neutral-toned subjects) are legitimately well below what the old baseline assumed as
    // "typical". Lowered to 0.30 (closer to what this specific measured case implies is closer to
    // "ordinary"), and paired with SATURATION_DAMPING below so future edge cases can't swing to
    // the extremes either, even if 0.30 still isn't the right baseline for some other photo.
    // [REALCAMMI FIX 2026-09-19] Lowered further, from 0.30 to 0.2, hand-tuned by the author
    // against further real captures until the suggested saturation visually matched the
    // reference photo as closely as possible - 0.30 still undersaturated the result in practice.
    // Direction to remember when re-tuning this constant: this is a divisor (raw_ratio =
    // avg_saturation / BASELINE_SATURATION), so a HIGHER value here means a LOWER ratio for the
    // same measured photo, which means the tool suggests LESS colour (more desaturation) - and a
    // lower value, as applied here, means MORE colour is suggested for the same reference photo.
    // >>> PRIMARY SATURATION CALIBRATION POINT #1 <<<
    // Raise this value -> tool suggests LESS saturation for the same photo.
    // Lower this value -> tool suggests MORE saturation for the same photo.
    // [REALCAMMI FIX 2026-10-07] Comment audit: 0.2 above was an intermediate value. CURRENT value
    // is 0.35 (originally 0.45) after later hand-tuning.
    private static final float BASELINE_SATURATION = 0.35f; // originally 0.45; higher = suggests less saturation

    // [REALCAMMI FIX 2026-09-18] Blends the raw saturation ratio partway back towards 1.0
    // (unchanged) rather than applying it in full: final = 1.0 + (raw_ratio - 1.0) * DAMPING.
    // 1.0 = no damping (old behaviour); lower = gentler, safer suggestions. A deliberate safety
    // margin given how easily whole-image average saturation is skewed by subject matter rather
    // than actual grading, as the 2026-09-18 real-world test above demonstrated.
    // [REALCAMMI FIX 2026-09-19] Raised from 0.6 to 0.75 alongside BASELINE_SATURATION's decrease
    // above - hand-tuned together against real captures, not independently, so less of the raw
    // swing gets damped away now that BASELINE_SATURATION itself is better calibrated.
    // >>> PRIMARY SATURATION CALIBRATION POINT #2 <<<
    // Closer to 1.0 -> follows the raw measured ratio more closely (stronger suggestions).
    // Closer to 0.0 -> suggestion stays closer to "no change" (gentler suggestions).
    // Tune together with BASELINE_SATURATION above, not independently.
    // [REALCAMMI FIX 2026-10-07] Comment audit: 0.75 above was intermediate. CURRENT value is 0.5.
    private static final float SATURATION_DAMPING = 0.5f;

    // Same safe bounds as PostProcessing.java's COLOR_BOOST_SCALE_MIN/MAX and
    // COLOR_BOOST_SATURATION_MIN/MAX (duplicated here rather than made cross-file-visible, since
    // these are just the documented safe ranges for the preference values themselves, not
    // internals of the post-processing pipeline).
    // Safety bounds for the R/G/B "scale" suggestions - keeps a channel from ever being clamped to black/white.
    private static final float SCALE_MIN = -0.9f;
    private static final float SCALE_MAX = 2.0f;

    // Safety bounds for the "saturation" suggestion - 0.0 = fully desaturated, 3.0 = strongly boosted.
    private static final float SATURATION_MIN = 0.0f;
    private static final float SATURATION_MAX = 3.0f;

    // Simple immutable result struct: the 4 Color Boost values this class suggests.
    public static class SuggestedLook {
        public final float red_scale;
        public final float green_scale;
        public final float blue_scale;
        public final float saturation;

        // Package-private constructor (no access modifier) - only analyze() below is meant to build one.
        SuggestedLook(float red_scale, float green_scale, float blue_scale, float saturation) {
            this.red_scale = red_scale;
            this.green_scale = green_scale;
            this.blue_scale = blue_scale;
            this.saturation = saturation;
        }
    }

    // Classic clamp: first cap at max, then floor at min.
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Analyses a reference bitmap and suggests Color Boost values that would nudge future photos
     *  towards its average colour balance and saturation. Uses a sparse sample grid (same
     *  lightweight-sampling style used elsewhere in this fork - see
     *  HDRProcessor.computeMedianLuminance()/computeSharpness() - so this stays fast even on a
     *  large gallery image) rather than scanning every pixel.
     */
    public static SuggestedLook analyze(Bitmap bitmap) {
        if( MyDebug.LOG )
            Log.d(TAG, "PhotoLookMatcher.analyze"); // debug trace, only compiled/executed in debug builds

        final int n_samples_c = 400; // denser than the 100-point grids elsewhere, since colour
        // averages benefit from more samples than a luminance
        // histogram or sharpness estimate does
        final int n_w_samples = (int)Math.sqrt(n_samples_c); // ~20 columns
        final int n_h_samples = n_samples_c / n_w_samples;   // ~20 rows (20*20 = 400)
        int width = bitmap.getWidth();   // reference photo width in pixels
        int height = bitmap.getHeight(); // reference photo height in pixels

        double sum_r = 0.0, sum_g = 0.0, sum_b = 0.0, sum_saturation = 0.0; // running totals across all sampled pixels
        int count = 0;              // how many pixels were actually sampled
        float [] hsv = new float[3]; // reusable buffer filled by Color.RGBToHSV() each iteration
        for(int y=0;y<n_h_samples;y++) {
            // Spread sample rows evenly across the image height, avoiding the exact top/bottom edge
            // (y+1 over n_h_samples+1, not y/n_h_samples, keeps samples inset from the border).
            double alpha = ((double) y + 1.0) / ((double) n_h_samples + 1.0);
            int y_coord = (int) (alpha * height);
            if( y_coord >= height )     // rounding safety net - should be rare/never with the formula above
                y_coord = height - 1;
            for(int x=0;x<n_w_samples;x++) {
                // Same even-spacing idea as above, but across the image width.
                double beta = ((double) x + 1.0) / ((double) n_w_samples + 1.0);
                int x_coord = (int) (beta * width);
                if( x_coord >= width )  // same rounding safety net for the x axis
                    x_coord = width - 1;

                int color = bitmap.getPixel(x_coord, y_coord); // packed ARGB int for this sample point
                int r = (color & 0xFF0000) >> 16; // extract red channel (bits 16-23)
                int g = (color & 0xFF00) >> 8;    // extract green channel (bits 8-15)
                int b = (color & 0xFF);           // extract blue channel (bits 0-7)
                sum_r += r; // accumulate for the average red
                sum_g += g; // accumulate for the average green
                sum_b += b; // accumulate for the average blue
                Color.RGBToHSV(r, g, b, hsv); // convert this sample to HSV, only hsv[1] (saturation) is used
                sum_saturation += hsv[1]; // accumulate saturation (0.0-1.0 range)
                count++; // one more sample counted
            }
        }

        if( count == 0 ) {
            // Shouldn't happen for any real bitmap (width/height > 0 implies count > 0), but avoids
            // a divide-by-zero below and returns a fully neutral ("no change") suggestion instead.
            return new SuggestedLook(0.0f, 0.0f, 0.0f, 1.0f);
        }

        float avg_r = (float)(sum_r / count); // average red across all samples
        float avg_g = (float)(sum_g / count); // average green across all samples
        float avg_b = (float)(sum_b / count); // average blue across all samples
        float avg_luminance = (avg_r + avg_g + avg_b) / 3.0f; // simple (unweighted) average of the 3 channels, used as the neutral reference point
        float avg_saturation = (float)(sum_saturation / count); // average HSV saturation across all samples

        // For each channel: how far it sits from the neutral average (normalised to 0-1 by /255),
        // scaled by sensitivity, softened by damping, then clamped to the safe range.
        float red_scale = clamp( ((avg_r - avg_luminance) / 255.0f) * TINT_SENSITIVITY * TINT_DAMPING, SCALE_MIN, SCALE_MAX );
        float green_scale = clamp( ((avg_g - avg_luminance) / 255.0f) * TINT_SENSITIVITY * TINT_DAMPING, SCALE_MIN, SCALE_MAX );
        float blue_scale = clamp( ((avg_b - avg_luminance) / 255.0f) * TINT_SENSITIVITY * TINT_DAMPING, SCALE_MIN, SCALE_MAX );

        // Saturation calibration happens here - see the two ">>> PRIMARY SATURATION CALIBRATION
        // POINT <<<" constants above if this suggestion feels too strong/weak in future.
        float raw_saturation_ratio = avg_saturation / BASELINE_SATURATION; // >1.0 = more saturated than baseline, <1.0 = less
        float saturation = clamp( 1.0f + (raw_saturation_ratio - 1.0f) * SATURATION_DAMPING, SATURATION_MIN, SATURATION_MAX );

        if( MyDebug.LOG ) { // debug-only logging of the measured averages and the final suggestion
            Log.d(TAG, "avg_r: " + avg_r + " avg_g: " + avg_g + " avg_b: " + avg_b + " avg_saturation: " + avg_saturation);
            Log.d(TAG, "suggested red_scale: " + red_scale + " green_scale: " + green_scale + " blue_scale: " + blue_scale + " saturation: " + saturation);
        }

        return new SuggestedLook(red_scale, green_scale, blue_scale, saturation); // final result handed back to the caller
    }

    /** Analyses 1-3 reference bitmaps and returns the average of their individual suggestions.
     *  Each bitmap's result already passed through analyze()'s own sensitivity/damping/clamp
     *  independently, so this just blends 4 already-safe final values together (not the raw
     *  per-photo measurements before those steps) - this softens the effect of any single
     *  reference photo's own AWB/AE capture noise when the user supplies multiple photos of
     *  the same scene/look, without changing analyze()'s existing single-photo behaviour.
     */
    public static SuggestedLook analyzeMultiple(List<Bitmap> bitmaps) {
        if( MyDebug.LOG )
            Log.d(TAG, "PhotoLookMatcher.analyzeMultiple: " + (bitmaps == null ? 0 : bitmaps.size()) + " bitmap(s)");

        if( bitmaps == null || bitmaps.isEmpty() ) {
            // shouldn't happen (caller should always supply at least 1 bitmap), but avoid
            // dividing by zero below and return a fully neutral ("no change") suggestion instead
            return new SuggestedLook(0.0f, 0.0f, 0.0f, 1.0f);
        }

        float sum_red_scale = 0.0f, sum_green_scale = 0.0f, sum_blue_scale = 0.0f, sum_saturation = 0.0f;
        for(Bitmap bitmap : bitmaps) {
            SuggestedLook look = analyze(bitmap);
            sum_red_scale += look.red_scale;
            sum_green_scale += look.green_scale;
            sum_blue_scale += look.blue_scale;
            sum_saturation += look.saturation;
        }

        int n = bitmaps.size();
        // Simple arithmetic mean of each already-clamped field across all supplied photos.
        return new SuggestedLook(sum_red_scale / n, sum_green_scale / n, sum_blue_scale / n, sum_saturation / n);
    }
}
