package net.sourceforge.opencamera.realcammi;

import android.graphics.Bitmap;
import android.util.Log;

import io.github.crow_misia.libyuv.AbgrBuffer;
import io.github.crow_misia.libyuv.FilterMode;

/** [REALCAMMI FORK LIBYUV 2026-10-04] Box-filter downscaling of ARGB_8888 bitmaps with libyuv
 *  (Google, BSD-3-Clause), through the crow-misia/libyuv-android JNI wrapper (Apache-2.0).
 *
 *  Why box: upstream libyuv documents kFilterBox as averaging ("highest quality") and
 *  kFilterBilinear as "faster than box, but lower quality scaling down". DepthEffect reduces a
 *  multi-megapixel photo to a ~1280 px working copy and to the 256x256 MiDaS input, which are
 *  exactly the large downscale factors where averaging matters. NOTE: box is the slower of the two
 *  libyuv filters, so the expected gain here is mainly quality; whether it is faster than
 *  Bitmap.createScaledBitmap() on the target device has NOT been measured.
 *
 *  Contract: scaleBox() never throws. It returns null whenever it cannot or should not handle the
 *  request (unsupported bitmap, size guard, native error, out of memory...), and the caller must
 *  then fall back to Bitmap.createScaledBitmap().
 *
 *  Memory: the source pixels are mirrored once into a native (off-Java-heap) buffer, so the call
 *  transiently needs about srcWidth * srcHeight * 4 extra bytes. MAX_SOURCE_BYTES caps that.
 */
final class LibYuvScaler {
    private static final String TAG = "LibYuvScaler";

    // Above this source size (bytes) the native mirror copy is skipped and the caller falls back.
    // 256 MiB corresponds to about 64 megapixels of ARGB_8888.
    private static final long MAX_SOURCE_BYTES = 256L * 1024L * 1024L;

    private LibYuvScaler() {
    }

    /** Returns a new ARGB_8888 bitmap of dstW x dstH, or null if the caller must fall back. */
    static Bitmap scaleBox(Bitmap src, int dstW, int dstH) {
        if( src == null || src.isRecycled() )
            return null;
        if( dstW <= 0 || dstH <= 0 )
            return null;
        // copyPixelsToBuffer() cannot read HARDWARE bitmaps, and the libyuv buffers used below are
        // 4 bytes per pixel.
        if( src.getConfig() != Bitmap.Config.ARGB_8888 )
            return null;
        final int srcW = src.getWidth();
        final int srcH = src.getHeight();
        // Only plain downscales: same-size is left to the original code path (keeps its behaviour
        // unchanged) and upscaling is not what the box filter is for.
        if( dstW > srcW || dstH > srcH || (dstW == srcW && dstH == srcH) )
            return null;
        // The libyuv buffers are tightly packed (rowBytes == width * 4); refuse padded bitmaps.
        if( src.getRowBytes() != srcW * 4 )
            return null;
        if( (long)srcW * (long)srcH * 4L > MAX_SOURCE_BYTES )
            return null;

        AbgrBuffer srcBuffer = null;
        AbgrBuffer dstBuffer = null;
        try {
            srcBuffer = AbgrBuffer.Factory.allocate(srcW, srcH);
            src.copyPixelsToBuffer(srcBuffer.asBuffer());
            dstBuffer = AbgrBuffer.Factory.allocate(dstW, dstH);
            srcBuffer.scale(dstBuffer, FilterMode.BOX);
            // asBitmap() copies the pixels into a new Bitmap, so the native buffers can be freed below.
            return dstBuffer.asBitmap();
        }
        catch(Throwable t) {
            // includes UnsatisfiedLinkError / NoSuchMethodError / OutOfMemoryError: never block the photo
            if( MyDebug.LOG )
                Log.e(TAG, "libyuv scale failed, falling back: " + t);
            return null;
        }
        finally {
            if( srcBuffer != null )
                srcBuffer.close();
            if( dstBuffer != null )
                dstBuffer.close();
        }
    }
}
