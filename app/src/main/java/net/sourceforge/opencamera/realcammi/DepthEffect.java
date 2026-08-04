package net.sourceforge.opencamera.realcammi;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** [REALCAMMI FORK] Natural, depth-based portrait-style background blur, applied as a
 *  post-capture photo effect (see PostProcessing.postProcessBitmap()).
 *
 *  Design goals (2026-07-20, based on a review of what makes computational bokeh look
 *  "fake" vs natural - see chat log for sources):
 *   1. Blur amount is a CONTINUOUS function of per-pixel depth, never a binary subject/
 *      background mask - avoids the abrupt "cardboard cutout" look.
 *   2. Blur amount SATURATES (plateaus) at a moderate maximum, mimicking how a real lens's
 *      circle of confusion stops growing past a certain background distance - avoids the
 *      "melted background" look of an unbounded blur.
 *   3. The depth map is refined against the image's edges (a fast guided filter, He et al.
 *      2010 - implemented locally with plain box filters, since the OpenCV artifact this
 *      project uses does not include the ximgproc/contrib module that has a ready-made joint
 *      bilateral filter) before use, to reduce haloing/bleeding at fine boundaries (hair etc).
 *   4. The final image is a per-pixel CONTINUOUS alpha blend between a fully sharp layer and
 *      a single blurred layer, weighted by the (continuous, saturating) depth-based amount
 *      from point 1/2 above - NOT a discrete set of blur levels selected per-pixel. This gives
 *      smooth, banding-free gradation with only two image buffers in memory, and is fully
 *      vectorised (whole-Mat Core.multiply/add), with no per-pixel Java loop.
 *
 *  [REALCAMMI FORK PERFORMANCE NOTE] An earlier version of this class computed 5 discrete
 *  blur layers at full photo resolution and picked/blended between them in a per-pixel Java
 *  loop (Mat.get()/put() per pixel). Caught in self-review before ever reaching a device: for
 *  a 12MP photo that is ~12 million individual JNI calls (likely minutes, not seconds), and
 *  5 full-resolution CV_32FC3 buffers is 700MB+ of simultaneous memory - a near-certain OOM on
 *  a phone. Replaced with the design above: the expensive steps (depth estimation, guided
 *  filter, the one Gaussian blur layer) run at a capped working resolution (WORK_LONG_SIDE),
 *  and only the final blend re-touches full resolution, using whole-Mat vector operations.
 *
 *  Uses MiDaS v2.1 small (https://github.com/isl-org/MiDaS), bundled as
 *  assets/midas_v21_small.tflite. Input: resize to 256x256, normalise to [-1,1] per channel -
 *  confirmed against two independent sources for this exact model file (see chat log). Output:
 *  256x256 single-channel relative INVERSE depth (higher value = CLOSER to the camera) -
 *  likewise confirmed against an independent source for this exact model, but not yet verified
 *  against a real capture from this app; if the blur ends up inverted (background sharp,
 *  subject blurred), swap the sign in estimateDepth() where noted.
 */
public class DepthEffect {
    private static final String TAG = "DepthEffect";
    private static final String MODEL_FILENAME = "midas_v21_small.tflite";
    private static final int MODEL_INPUT_SIZE = 256; // MiDaS v2.1 small: fixed 256x256 input

    // Tune 12
    // [REALCAMMI FORK TUNE] Working resolution cap (longest side, px) for the expensive steps
    // (guided filter, the Gaussian blur layer). Output is still full photo resolution - only
    // these intermediate steps are capped, since blurred/depth content has no fine detail that
    // full resolution would preserve anyway. Raise this if the blur/depth edges look too soft
    // relative to the sharp foreground; lower it if this is still too slow/heavy on-device.
    private static final int WORK_LONG_SIDE = 1280;

    // [REALCAMMI FORK TUNE] Maximum blur radius (Gaussian sigma, in px, at the WORK_LONG_SIDE
    // working resolution) for the most out-of-focus background - keep this moderate. Real
    // phone-camera bokeh (small sensor, ~24mm-equivalent focal length) is naturally much
    // subtler than a dedicated portrait lens; a large value here is what makes computational
    // bokeh look fake. Start conservative and only raise it after seeing a real result.
    private static final float DEFAULT_MAX_BLUR_SIGMA = 6.0f;

    // [REALCAMMI FORK TUNE] Controls how quickly blur saturates with distance from the focal
    // plane. Higher = blur ramps up faster and plateaus sooner (more separation, closer to a
    // wide-aperture look); lower = more gradual, deeper apparent depth of field. This is the
    // single most important value to adjust after seeing a real photo - see the saturating
    // curve in apply() below.
    private static final float DEFAULT_FALLOFF_SHARPNESS = 1.25f;

    // [REALCAMMI FORK 2026-08-01] Instance fields (no longer static final) so the Settings
    // "Depth Effect Strength" preset (see setStrengthPreset()) can adjust them per capture,
    // set by PostProcessing.java before calling apply(). Defaults are the 2026-07-21 on-device
    // confirmed baseline ("natural" preset) - unchanged behaviour if setStrengthPreset() is
    // never called.
    private float maxBlurSigma = DEFAULT_MAX_BLUR_SIGMA;

    private float falloffSharpness = DEFAULT_FALLOFF_SHARPNESS;

    private final Interpreter interpreter;

    public DepthEffect(Context context) throws IOException {
        interpreter = new Interpreter(loadModelFile(context));
    }

    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        try (android.content.res.AssetFileDescriptor fd = context.getAssets().openFd(MODEL_FILENAME)) {
            FileInputStream inputStream = new FileInputStream(fd.getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
        }
    }

    public void close() {
        if( interpreter != null )
            interpreter.close();
    }

    /** Sets the blur strength preset chosen in Settings (preference_depth_blur_strength),
     *  before the next call to apply(). Unknown/null keys fall back to "natural" (the
     *  on-device confirmed baseline). [REALCAMMI FORK 2026-08-01]
     *  NOTE: only "natural" (DEFAULT_MAX_BLUR_SIGMA/DEFAULT_FALLOFF_SHARPNESS) has been
     *  confirmed on-device (2026-07-21). "subtle"/"strong"/"very_strong" are proportional
     *  engineering estimates and still need on-device validation before being trusted.
     */
    public void setStrengthPreset(String preset) {
        if( preset == null )
            preset = "natural";
        switch( preset ) {
            case "subtle":
                maxBlurSigma = 4.0f;
                falloffSharpness = 1.6f;
                break;
            case "strong":
                maxBlurSigma = 8.0f;
                falloffSharpness = 1.0f;
                break;
            case "very_strong":
                maxBlurSigma = 10.0f;
                falloffSharpness = 0.8f;
                break;
            case "natural":
            default:
                maxBlurSigma = DEFAULT_MAX_BLUR_SIGMA;
                falloffSharpness = DEFAULT_FALLOFF_SHARPNESS;
                break;
        }
    }

    /** Runs MiDaS on a downscaled copy of [bitmap] and returns a MODEL_INPUT_SIZE x
     *  MODEL_INPUT_SIZE relative depth map (row-major, higher = closer - see class doc comment).
     *  Defensively checks the model's actual output tensor shape rather than assuming it, since
     *  this has not been verified against a real on-device run.
     */
    private float[] estimateDepth(Bitmap bitmap) {
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, true);

        ByteBuffer input = ByteBuffer.allocateDirect(4 * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE * 3);
        input.order(ByteOrder.nativeOrder());
        int[] pixels = new int[MODEL_INPUT_SIZE * MODEL_INPUT_SIZE];
        resized.getPixels(pixels, 0, MODEL_INPUT_SIZE, 0, 0, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE);
        for(int p : pixels) {
            // normalise to [-1, 1] per channel (confirmed preprocessing for this exact model)
            input.putFloat((((p >> 16) & 0xFF) / 127.5f) - 1.0f); // R
            input.putFloat((((p >> 8) & 0xFF) / 127.5f) - 1.0f);  // G
            input.putFloat((( p       & 0xFF) / 127.5f) - 1.0f);  // B
        }
        resized.recycle();

        // [REALCAMMI FORK] Defensive shape check: expected {1, 256, 256} (confirmed for this
        // model against an independent source, but not verified on-device). If the actual
        // model reports a different rank/shape (e.g. a trailing channel dim), fall back to a
        // flat buffer read instead of assuming float[][][] and risking a hard crash.
        int[] outShape = interpreter.getOutputTensor(0).shape();
        float[] depth = new float[MODEL_INPUT_SIZE * MODEL_INPUT_SIZE];
        if( outShape.length == 3 && outShape[1] == MODEL_INPUT_SIZE && outShape[2] == MODEL_INPUT_SIZE ) {
            float[][][] output = new float[1][MODEL_INPUT_SIZE][MODEL_INPUT_SIZE];
            interpreter.run(input, output);
            for(int y = 0; y < MODEL_INPUT_SIZE; y++)
                for(int x = 0; x < MODEL_INPUT_SIZE; x++)
                    depth[y * MODEL_INPUT_SIZE + x] = output[0][y][x];
        }
        else {
            // unexpected shape (e.g. {1,256,256,1}) - read as a flat direct buffer instead
            if( MyDebug.LOG )
                Log.d(TAG, "unexpected MiDaS output shape " + java.util.Arrays.toString(outShape) + ", using flat buffer fallback");
            ByteBuffer outBuf = ByteBuffer.allocateDirect(4 * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE);
            outBuf.order(ByteOrder.nativeOrder());
            interpreter.run(input, outBuf);
            outBuf.rewind();
            for(int i = 0; i < depth.length; i++)
                depth[i] = outBuf.getFloat();
        }

        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for(float v : depth) {
            if( v < min ) min = v;
            if( v > max ) max = v;
        }
        // normalise to [0,1] - avoids depending on MiDaS's arbitrary raw output scale
        float range = Math.max(1e-6f, max - min);
        for(int i = 0; i < depth.length; i++)
            depth[i] = (depth[i] - min) / range;
        return depth;
    }

    /** Applies natural, graduated depth-of-field blur to [bitmap]. Returns a new Bitmap - does
     *  not modify [bitmap] in place. Falls back to returning [bitmap] unchanged on any failure
     *  (model missing/corrupt, out of memory, etc.) - this is a best-effort visual enhancement,
     *  never something that should block saving the photo.
     */
    public Bitmap apply(Bitmap bitmap) {
        Mat depthWork = null, guide = null, depthRefined = null, delta = null, weight = null;
        Mat weightFull = null, weight3ch = null, oneMinusWeight3ch = null;
        Mat sharp = null, blurredSmall = null, blurredFull = null;
        Mat sharpWeighted = null, blurWeighted = null, result = null, resultRgba = null;
        Bitmap workBitmap = null;
        try {
            int fullW = bitmap.getWidth(), fullH = bitmap.getHeight();
            float scale = Math.min(1.0f, WORK_LONG_SIDE / (float) Math.max(fullW, fullH));
            int workW = Math.max(2, Math.round(fullW * scale));
            int workH = Math.max(2, Math.round(fullH * scale));

            workBitmap = Bitmap.createScaledBitmap(bitmap, workW, workH, true);

            // --- Depth map, at working resolution ---
            float[] depthSmall = estimateDepth(workBitmap);
            Mat depthMat = new Mat(MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, CvType.CV_32F);
            depthMat.put(0, 0, depthSmall);
            depthWork = new Mat();
            Imgproc.resize(depthMat, depthWork, new Size(workW, workH), 0, 0, Imgproc.INTER_LINEAR);
            depthMat.release();

            // --- Edge-aware refinement, guided by the working-resolution image ---
            guide = new Mat();
            Utils.bitmapToMat(workBitmap, guide);
            Imgproc.cvtColor(guide, guide, Imgproc.COLOR_RGBA2GRAY);
            guide.convertTo(guide, CvType.CV_32F, 1.0/255.0);
            depthRefined = fastGuidedFilter(guide, depthWork, 15, 1e-3);

            // --- Focus depth: median of the central region (proxy for "the subject" - see
            // class doc comment; a reasonable v1 assumption for typical portrait framing) ---
            float focusDepth = medianOfCentralRegion(depthRefined, workW, workH);

            // --- Per-pixel blur weight in [0,1]: 0 = sharp (at focus), 1 = max background
            // blur. Saturating curve (1 - exp(-k*delta)) - ramps up smoothly then plateaus,
            // rather than growing without bound (see DEFAULT_FALLOFF_SHARPNESS above). ---
            delta = new Mat();
            Core.absdiff(depthRefined, new Scalar(focusDepth), delta);
            weight = new Mat();
            Core.multiply(delta, new Scalar(-falloffSharpness), weight);
            Core.exp(weight, weight);
            Mat ones = Mat.ones(weight.size(), weight.type());
            Core.subtract(ones, weight, weight);
            ones.release();

            // Upsample the (single-channel, cheap) weight map to full resolution
            weightFull = new Mat();
            Imgproc.resize(weight, weightFull, new Size(fullW, fullH), 0, 0, Imgproc.INTER_LINEAR);

            // --- Sharp layer: the ORIGINAL image at full resolution, untouched ---
            sharp = new Mat();
            Utils.bitmapToMat(bitmap, sharp);
            Imgproc.cvtColor(sharp, sharp, Imgproc.COLOR_RGBA2RGB);
            sharp.convertTo(sharp, CvType.CV_32FC3);

            // --- Blurred layer: computed at working resolution (cheaper, and blurred content
            // has no fine detail to lose by upscaling), then upsampled to full resolution ---
            Mat workRgb = new Mat();
            Utils.bitmapToMat(workBitmap, workRgb);
            Imgproc.cvtColor(workRgb, workRgb, Imgproc.COLOR_RGBA2RGB);
            blurredSmall = new Mat();
            int ksize = ((int)(maxBlurSigma * 3) | 1);
            Imgproc.GaussianBlur(workRgb, blurredSmall, new Size(ksize, ksize), maxBlurSigma);
            workRgb.release();
            blurredFull = new Mat();
            Imgproc.resize(blurredSmall, blurredFull, new Size(fullW, fullH), 0, 0, Imgproc.INTER_LINEAR);
            blurredFull.convertTo(blurredFull, CvType.CV_32FC3);

            // --- Mistura Per-Pixel Contínua e Segura (Alpha Blending) ---
// Separamos a imagem nítida e a desfocada nos seus 3 canais nativos (R, G, B)
            List<Mat> sharpChannels = new ArrayList<>();
            List<Mat> blurredChannels = new ArrayList<>();
            Core.split(sharp, sharpChannels);
            Core.split(blurredFull, blurredChannels);

            List<Mat> blendedChannels = new ArrayList<>();

// Processamos cada canal de forma isolada usando o mapa de pesos original de 1 único canal (weightFull)
            for (int i = 0; i < 3; i++) {
                Mat sChan = sharpChannels.get(i);
                Mat bChan = blurredChannels.get(i);
                Mat destChan = new Mat();

                // Executa rigorosamente a fórmula pixel a pixel: dest = sChan * (1 - weight) + bChan * weight
                Mat t1 = new Mat();
                Mat t2 = new Mat();

                // t1 = bChan * weight
                Core.multiply(bChan, weightFull, t1);

                // t2 = sChan * (1 - weight)
                Mat oneMinusW = new Mat();
                // Alterado o nome para onesAlpha para evitar o erro de duplicação
                Mat onesAlpha = Mat.ones(weightFull.size(), weightFull.type());
                Core.subtract(onesAlpha, weightFull, oneMinusW);
                Core.multiply(sChan, oneMinusW, t2);

                // destChan = t1 + t2
                Core.add(t1, t2, destChan);
                blendedChannels.add(destChan);

                // Libertar memória das matrizes temporárias do canal
                t1.release();
                t2.release();
                oneMinusW.release();
                onesAlpha.release();
                sChan.release();
                bChan.release();
            }

// Junta os 3 canais processados de volta numa única imagem colorida tridimensional
            result = new Mat();
            Core.merge(blendedChannels, result);

// Libertar os recursos das listas temporárias
            for(Mat m : sharpChannels) m.release();
            for(Mat m : blurredChannels) m.release();
            for(Mat m : blendedChannels) m.release();


            result.convertTo(result, CvType.CV_8UC3);
            resultRgba = new Mat();
            Imgproc.cvtColor(result, resultRgba, Imgproc.COLOR_RGB2RGBA);

            Bitmap out = Bitmap.createBitmap(fullW, fullH, Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(resultRgba, out);
            return out;
        }
        catch(Throwable t) {
            if( MyDebug.LOG )
                Log.e(TAG, "DepthEffect.apply() failed, returning original bitmap: " + t.getMessage());
            return bitmap;
        }
        finally {
            // release everything - best-effort, a null or already-released Mat is harmless here
            for(Mat m : new Mat[]{ depthWork, guide, depthRefined, delta, weight, weightFull,
                    weight3ch, oneMinusWeight3ch, sharp, blurredSmall, blurredFull,
                    sharpWeighted, blurWeighted, result, resultRgba }) {
                if( m != null ) m.release();
            }
            if( workBitmap != null )
                workBitmap.recycle();
        }
    }

    private float medianOfCentralRegion(Mat depthFull, int w, int h) {
        int cx0 = w / 3, cx1 = 2 * w / 3;
        int cy0 = h / 3, cy1 = 2 * h / 3;
        List<Float> values = new ArrayList<>();
        // sample sparsely rather than every pixel - the central region is large, a few
        // hundred samples is more than enough for a stable median
        int stepX = Math.max(1, (cx1 - cx0) / 32);
        int stepY = Math.max(1, (cy1 - cy0) / 32);
        for(int y = cy0; y < cy1; y += stepY) {
            for(int x = cx0; x < cx1; x += stepX) {
                values.add((float) depthFull.get(y, x)[0]);
            }
        }
        Collections.sort(values);
        return values.get(values.size() / 2);
    }

    /** Edge-preserving upsampling/refinement of [p] guided by [guide]'s edges (He, Sun & Tang,
     *  "Guided Image Filtering", 2010). Both must be single-channel CV_32F, same size.
     *  Implemented with only Imgproc.boxFilter - no ximgproc/contrib module required.
     */
    private Mat fastGuidedFilter(Mat guide, Mat p, int radius, double eps) {
        Size winSize = new Size(radius * 2 + 1, radius * 2 + 1);

        Mat meanI = new Mat(); Imgproc.boxFilter(guide, meanI, CvType.CV_32F, winSize);
        Mat meanP = new Mat(); Imgproc.boxFilter(p, meanP, CvType.CV_32F, winSize);

        Mat corrI = new Mat(); Core.multiply(guide, guide, corrI);
        Imgproc.boxFilter(corrI, corrI, CvType.CV_32F, winSize);
        Mat corrIp = new Mat(); Core.multiply(guide, p, corrIp);
        Imgproc.boxFilter(corrIp, corrIp, CvType.CV_32F, winSize);

        Mat meanIsq = new Mat(); Core.multiply(meanI, meanI, meanIsq);
        Mat varI = new Mat(); Core.subtract(corrI, meanIsq, varI);
        corrI.release(); meanIsq.release();

        Mat meanImeanP = new Mat(); Core.multiply(meanI, meanP, meanImeanP);
        Mat covIp = new Mat(); Core.subtract(corrIp, meanImeanP, covIp);
        corrIp.release(); meanImeanP.release();

        Mat varIeps = new Mat();
        Core.add(varI, new Scalar(eps), varIeps);
        varI.release();
        Mat a = new Mat(); Core.divide(covIp, varIeps, a);
        covIp.release(); varIeps.release();

        Mat aMeanI = new Mat(); Core.multiply(a, meanI, aMeanI);
        Mat b = new Mat(); Core.subtract(meanP, aMeanI, b);
        aMeanI.release(); meanI.release(); meanP.release();

        Mat meanA = new Mat(); Imgproc.boxFilter(a, meanA, CvType.CV_32F, winSize);
        Mat meanB = new Mat(); Imgproc.boxFilter(b, meanB, CvType.CV_32F, winSize);
        a.release(); b.release();

        Mat meanAGuide = new Mat(); Core.multiply(meanA, guide, meanAGuide);
        Mat q = new Mat(); Core.add(meanAGuide, meanB, q);
        meanA.release(); meanB.release(); meanAGuide.release();

        return q;
    }
}
