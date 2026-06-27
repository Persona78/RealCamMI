package net.sourceforge.opencamera.realcammi;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
// [REALCAMMI FORK] OpenCV imports for advanced post-processing
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.MatOfDouble;
//import android.location.Address; // don't use until we have info for data privacy!
//import android.location.Geocoder; // don't use until we have info for data privacy!
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;

/** Methods to apply post processing to resultant images.
 */
public class PostProcessing {
    private static final String TAG = "PostProcessing";

    private final MainActivity main_activity;

    private final Paint p = new Paint();

    PostProcessing(MainActivity main_activity) {
        if( MyDebug.LOG )
            Log.d(TAG, "PostProcessing");
        this.main_activity = main_activity;

        p.setAntiAlias(true);
    }

    /** Computes the width and height of a centred crop region after having rotated an image.
     * @param result - Array of length 2 which will be filled with the returned width and height.
     * @param level_angle_rad_abs - Absolute value of angle of rotation, in radians.
     * @param w0 - Rotated width.
     * @param h0 - Rotated height.
     * @param w1 - Original width.
     * @param h1 - Original height.
     * @param max_width - Maximum width to return.
     * @param max_height - Maximum height to return.
     * @return - Whether a crop region could be successfully calculated.
     */
    public static boolean autoStabiliseCrop(int [] result, double level_angle_rad_abs, double w0, double h0, int w1, int h1, int max_width, int max_height) {
        boolean ok = false;
        result[0] = 0;
        result[1] = 0;

        double tan_theta = Math.tan(level_angle_rad_abs);
        double sin_theta = Math.sin(level_angle_rad_abs);
        double denom = ( h0/w0 + tan_theta );
        double alt_denom = ( w0/h0 + tan_theta );
        if( denom < 1.0e-14 ) {
            if( MyDebug.LOG )
                Log.d(TAG, "zero denominator?!");
        }
        else if( alt_denom < 1.0e-14 ) {
            if( MyDebug.LOG )
                Log.d(TAG, "zero alt denominator?!");
        }
        else {
            int w2 = (int)(( h0 + 2.0*h1*sin_theta*tan_theta - w0*tan_theta ) / denom);
            int h2 = (int)(w2*h0/w0);
            int alt_h2 = (int)(( w0 + 2.0*w1*sin_theta*tan_theta - h0*tan_theta ) / alt_denom);
            int alt_w2 = (int)(alt_h2*w0/h0);
            if( MyDebug.LOG ) {
                //Log.d(TAG, "h0 " + h0 + " 2.0*h1*sin_theta*tan_theta " + 2.0*h1*sin_theta*tan_theta + " w0*tan_theta " + w0*tan_theta + " / h0/w0 " + h0/w0 + " tan_theta " + tan_theta);
                Log.d(TAG, "w2 = " + w2 + " , h2 = " + h2);
                Log.d(TAG, "alt_w2 = " + alt_w2 + " , alt_h2 = " + alt_h2);
            }
            if( alt_w2 < w2 ) {
                if( MyDebug.LOG ) {
                    Log.d(TAG, "chose alt!");
                }
                w2 = alt_w2;
                h2 = alt_h2;
            }
            if( w2 <= 0 )
                w2 = 1;
            else if( w2 > max_width )
                w2 = max_width;
            if( h2 <= 0 )
                h2 = 1;
            else if( h2 > max_height )
                h2 = max_height;

            ok = true;
            result[0] = w2;
            result[1] = h2;
        }
        return ok;
    }

    /** Performs the auto-stabilise algorithm on the image.
     * @param data The jpeg data.
     * @param bitmap Optional argument - the bitmap if already unpacked from the jpeg data.
     * @param level_angle The angle in degrees to rotate the image.
     * @param is_front_facing Whether the camera is front-facing.
     * @return A bitmap representing the auto-stabilised jpeg.
     */
    private Bitmap autoStabilise(byte [] data, Bitmap bitmap, double level_angle, boolean is_front_facing) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "autoStabilise");
            Log.d(TAG, "level_angle: " + level_angle);
            Log.d(TAG, "is_front_facing: " + is_front_facing);
        }
        while( level_angle < -90 )
            level_angle += 180;
        while( level_angle > 90 )
            level_angle -= 180;
        if( MyDebug.LOG )
            Log.d(TAG, "auto stabilising... angle: " + level_angle);
        if( bitmap == null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "need to decode bitmap to auto-stabilise");
            // bitmap doesn't need to be mutable here, as this won't be the final bitmap returned from the auto-stabilise code
            bitmap = ImageUtils.loadBitmapWithRotation(data, false);
            if( bitmap == null ) {
                main_activity.getPreview().showToast(null, R.string.failed_to_auto_stabilise);
                System.gc();
            }
        }
        if( bitmap != null ) {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            if( MyDebug.LOG ) {
                Log.d(TAG, "level_angle: " + level_angle);
                Log.d(TAG, "decoded bitmap size " + width + ", " + height);
                Log.d(TAG, "bitmap size: " + width*height*4);
            }
                /*for(int y=0;y<height;y++) {
                    for(int x=0;x<width;x++) {
                        int col = bitmap.getPixel(x, y);
                        col = col & 0xffff0000; // mask out red component
                        bitmap.setPixel(x, y, col);
                    }
                }*/
            Matrix matrix = new Matrix();
            double level_angle_rad_abs = Math.abs( Math.toRadians(level_angle) );
            int w1 = width, h1 = height;
            double w0 = (w1 * Math.cos(level_angle_rad_abs) + h1 * Math.sin(level_angle_rad_abs));
            double h0 = (w1 * Math.sin(level_angle_rad_abs) + h1 * Math.cos(level_angle_rad_abs));
            // apply a scale so that the overall image size isn't increased
            float orig_size = w1*h1;
            float rotated_size = (float)(w0*h0);
            float scale = (float)Math.sqrt(orig_size/rotated_size);
            if( main_activity.test_low_memory ) {
                if( MyDebug.LOG ) {
                    Log.d(TAG, "TESTING LOW MEMORY");
                    Log.d(TAG, "scale was: " + scale);
                }
                // test 20MP on Galaxy Nexus or Nexus 7; 29MP on Nexus 6 and 36MP OnePlus 3T
                if( width*height >= 7500 )
                    scale *= 1.5f;
                else
                    scale *= 2.0f;
            }
            if( MyDebug.LOG ) {
                Log.d(TAG, "w0 = " + w0 + " , h0 = " + h0);
                Log.d(TAG, "w1 = " + w1 + " , h1 = " + h1);
                Log.d(TAG, "scale = sqrt " + orig_size + " / " + rotated_size + " = " + scale);
            }
            matrix.postScale(scale, scale);
            w0 *= scale;
            h0 *= scale;
            // warning "Possibly lossy implicit cast in compound assignment" suppressed:
            // it's intentional that we multiply int by float, and implicitly cast back to int
            // (the suggested solution is to first cast the float to int before multiplying, which
            // we don't want)
            //noinspection lossy-conversions
            w1 *= scale;
            //noinspection lossy-conversions
            h1 *= scale;
            if( MyDebug.LOG ) {
                Log.d(TAG, "after scaling: w0 = " + w0 + " , h0 = " + h0);
                Log.d(TAG, "after scaling: w1 = " + w1 + " , h1 = " + h1);
            }
            if( is_front_facing ) {
                matrix.postRotate((float)-level_angle);
            }
            else {
                matrix.postRotate((float)level_angle);
            }
            Bitmap new_bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
            // careful, as new_bitmap is sometimes not a copy!
            if( new_bitmap != bitmap ) {
                bitmap.recycle();
                bitmap = new_bitmap;
            }
            System.gc();
            if( MyDebug.LOG ) {
                Log.d(TAG, "rotated and scaled bitmap size " + bitmap.getWidth() + ", " + bitmap.getHeight());
                Log.d(TAG, "rotated and scaled bitmap size: " + bitmap.getWidth()*bitmap.getHeight()*4);
            }

            int [] crop = new int [2];
            if( autoStabiliseCrop(crop, level_angle_rad_abs, w0, h0, w1, h1, bitmap.getWidth(), bitmap.getHeight()) ) {
                int w2 = crop[0];
                int h2 = crop[1];
                int x0 = (bitmap.getWidth()-w2)/2;
                int y0 = (bitmap.getHeight()-h2)/2;
                if( MyDebug.LOG ) {
                    Log.d(TAG, "x0 = " + x0 + " , y0 = " + y0);
                }
                new_bitmap = Bitmap.createBitmap(bitmap, x0, y0, w2, h2);
                if( new_bitmap != bitmap ) {
                    bitmap.recycle();
                    bitmap = new_bitmap;
                }
                System.gc();
            }

            if( MyDebug.LOG )
                Log.d(TAG, "bitmap is mutable?: " + bitmap.isMutable());
            // Usually createBitmap will return a mutable bitmap, but not if the source bitmap (which we set as immutable)
            // is returned (if the level angle is (tolerantly) 0.
            // see testPhotoStamp() for testing this.
            if( !bitmap.isMutable() ) {
                new_bitmap = bitmap.copy(bitmap.getConfig(), true);
                bitmap.recycle();
                bitmap = new_bitmap;
            }
        }
        return bitmap;
    }

    /** Mirrors the image.
     * @param data The jpeg data.
     * @param bitmap Optional argument - the bitmap if already unpacked from the jpeg data.
     * @return A bitmap representing the mirrored jpeg.
     */
    private static Bitmap mirrorImage(byte [] data, Bitmap bitmap) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "mirrorImage");
        }
        if( bitmap == null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "need to decode bitmap to mirror");
            // bitmap doesn't need to be mutable here, as this won't be the final bitmap returned from the mirroring code
            bitmap = ImageUtils.loadBitmapWithRotation(data, false);
            if( bitmap == null ) {
                // don't bother warning to the user - we simply won't mirror the image
                System.gc();
            }
        }
        if( bitmap != null ) {
            Matrix matrix = new Matrix();
            matrix.preScale(-1.0f, 1.0f);
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            Bitmap new_bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
            // careful, as new_bitmap is sometimes not a copy!
            if( new_bitmap != bitmap ) {
                bitmap.recycle();
                bitmap = new_bitmap;
            }
            if( MyDebug.LOG )
                Log.d(TAG, "bitmap is mutable?: " + bitmap.isMutable());
        }
        return bitmap;
    }

    /** Applies any photo stamp options (if they exist).
     * @param data The jpeg data.
     * @param bitmap Optional argument - the bitmap if already unpacked from the jpeg data.
     * @return A bitmap representing the stamped jpeg. Will be null if the input bitmap is null and
     *         no photo stamp is applied.
     */
    private Bitmap stampImage(final ImageSaver.Request request, byte [] data, Bitmap bitmap) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "stampImage");
        }
        //final MyApplicationInterface applicationInterface = main_activity.getApplicationInterface();
        boolean dategeo_stamp = request.preference_stamp.equals("preference_stamp_yes");
        boolean text_stamp = !request.preference_textstamp.isEmpty();
        if( dategeo_stamp || text_stamp ) {
            if( bitmap == null ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "decode bitmap in order to stamp info");
                bitmap = ImageUtils.loadBitmapWithRotation(data, true);
                if( bitmap == null ) {
                    main_activity.getPreview().showToast(null, R.string.failed_to_stamp);
                    System.gc();
                }
            }
            if( bitmap != null ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "stamp info to bitmap: " + bitmap);
                if( MyDebug.LOG )
                    Log.d(TAG, "bitmap is mutable?: " + bitmap.isMutable());

                String stamp_string = "";
                /* We now stamp via a TextView instead of using MyApplicationInterface.drawTextWithBackground().
                 * This is important in order to satisfy the Google emoji policy...
                 */

                int font_size = request.font_size;
                int color = request.color;
                String pref_style = request.pref_style;
                if( MyDebug.LOG )
                    Log.d(TAG, "pref_style: " + pref_style);
                String preference_stamp_dateformat = request.preference_stamp_dateformat;
                String preference_stamp_timeformat = request.preference_stamp_timeformat;
                String preference_stamp_gpsformat = request.preference_stamp_gpsformat;
                int width = bitmap.getWidth();
                int height = bitmap.getHeight();
                if( MyDebug.LOG ) {
                    Log.d(TAG, "decoded bitmap size " + width + ", " + height);
                    Log.d(TAG, "bitmap size: " + width*height*4);
                }
                Canvas canvas = new Canvas(bitmap);
                p.setColor(Color.WHITE);
                // we don't use the density of the screen, because we're stamping to the image, not drawing on the screen (we don't want the font height to depend on the device's resolution)
                // instead we go by 1 pt == 1/72 inch height, and scale for an image height (or width if in portrait) of 4" (this means the font height is also independent of the photo resolution)
                int smallest_size = Math.min(width, height);
                float scale = ((float)smallest_size) / (72.0f*4.0f);
                int font_size_pixel = (int)(font_size * scale + 0.5f); // convert pt to pixels
                if( MyDebug.LOG ) {
                    Log.d(TAG, "scale: " + scale);
                    Log.d(TAG, "font_size: " + font_size);
                    Log.d(TAG, "font_size_pixel: " + font_size_pixel);
                }
                p.setTextSize(font_size_pixel);
                int offset_x = (int)(8 * scale + 0.5f); // convert pt to pixels
                int offset_y = (int)(8 * scale + 0.5f); // convert pt to pixels
                int diff_y = (int)((font_size+4) * scale + 0.5f); // convert pt to pixels
                int ypos = height - offset_y;
                p.setTextAlign(Paint.Align.RIGHT);
                MyApplicationInterface.Shadow draw_shadowed = MyApplicationInterface.Shadow.SHADOW_NONE;
                switch( pref_style ) {
                    case "preference_stamp_style_shadowed":
                        draw_shadowed = MyApplicationInterface.Shadow.SHADOW_OUTLINE;
                        break;
                    case "preference_stamp_style_plain":
                        draw_shadowed = MyApplicationInterface.Shadow.SHADOW_NONE;
                        break;
                    case "preference_stamp_style_background":
                        draw_shadowed = MyApplicationInterface.Shadow.SHADOW_BACKGROUND;
                        break;
                }
                if( MyDebug.LOG )
                    Log.d(TAG, "draw_shadowed: " + draw_shadowed);
                if( dategeo_stamp ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "stamp date");
                    // doesn't respect user preferences such as 12/24 hour - see note about in draw() about DateFormat.getTimeInstance()
                    String date_stamp = TextFormatter.getDateString(preference_stamp_dateformat, request.current_date);
                    String time_stamp = TextFormatter.getTimeString(preference_stamp_timeformat, request.current_date);
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "date_stamp: " + date_stamp);
                        Log.d(TAG, "time_stamp: " + time_stamp);
                    }
                    if( !date_stamp.isEmpty() || !time_stamp.isEmpty() ) {
                        String datetime_stamp = "";
                        if( !date_stamp.isEmpty() )
                            datetime_stamp += date_stamp;
                        if( !time_stamp.isEmpty() ) {
                            if( !datetime_stamp.isEmpty() )
                                datetime_stamp += " ";
                            datetime_stamp += time_stamp;
                        }
                        //applicationInterface.drawTextWithBackground(canvas, p, datetime_stamp, color, Color.BLACK, width - offset_x, ypos, MyApplicationInterface.Alignment.ALIGNMENT_BOTTOM, null, draw_shadowed);
                        if( stamp_string.isEmpty() )
                            stamp_string = datetime_stamp;
                        else
                            stamp_string = datetime_stamp + "\n" + stamp_string;
                    }
                    ypos -= diff_y;
                    String gps_stamp = main_activity.getTextFormatter().getGPSString(preference_stamp_gpsformat, request.preference_units_distance, request.store_location, request.location, request.store_geo_direction, request.geo_direction);
                    if( !gps_stamp.isEmpty() ) {
                        // don't log gps_stamp, in case of privacy!

                        /*Address address = null;
                        if( request.store_location && !request.preference_stamp_geo_address.equals("preference_stamp_geo_address_no") ) {
                            boolean block_geocoder;
                            synchronized(this) {
                                block_geocoder = app_is_paused;
                            }
                            // try to find an address
                            // n.b., if we update the class being used, consider whether the info on Geocoder in preference_stamp_geo_address_summary needs updating
                            if( block_geocoder ) {
                                // seems safer to not try to initiate potential network connections (via geocoder) if RealCamMI
                                // has paused and we're still saving images
                                if( MyDebug.LOG )
                                    Log.d(TAG, "don't call geocoder for photostamp as app is paused");
                            }
                            else if( Geocoder.isPresent() ) {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "geocoder is present");
                                Geocoder geocoder = new Geocoder(main_activity, Locale.getDefault());
                                try {
                                    List<Address> addresses = geocoder.getFromLocation(request.location.getLatitude(), request.location.getLongitude(), 1);
                                    if( addresses != null && addresses.size() > 0 ) {
                                        address = addresses.get(0);
                                        // don't log address, in case of privacy!
                                        if( MyDebug.LOG ) {
                                            Log.d(TAG, "max line index: " + address.getMaxAddressLineIndex());
                                        }
                                    }
                                }
                                catch(Exception e) {
                                    MyDebug.logStackTrace(TAG, "failed to read from geocoder", e);
                                }
                            }
                            else {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "geocoder not present");
                            }
                        }*/

                        //if( address == null || request.preference_stamp_geo_address.equals("preference_stamp_geo_address_both") )
                        {
                            if( MyDebug.LOG )
                                Log.d(TAG, "display gps coords");
                            // want GPS coords (either in addition to the address, or we don't have an address)
                            // we'll also enter here if store_location is false, but we have geo direction to display
                            //applicationInterface.drawTextWithBackground(canvas, p, gps_stamp, color, Color.BLACK, width - offset_x, ypos, MyApplicationInterface.Alignment.ALIGNMENT_BOTTOM, null, draw_shadowed);
                            if( stamp_string.isEmpty() )
                                stamp_string = gps_stamp;
                            else
                                stamp_string = gps_stamp + "\n" + stamp_string;
                            ypos -= diff_y;
                        }
                        /*else if( request.store_geo_direction ) {
                            if( MyDebug.LOG )
                                Log.d(TAG, "not displaying gps coords, but need to display geo direction");
                            // we are displaying an address instead of GPS coords, but we still need to display the geo direction
                            gps_stamp = main_activity.getTextFormatter().getGPSString(preference_stamp_gpsformat, request.preference_units_distance, false, null, request.store_geo_direction, request.geo_direction);
                            if( gps_stamp.length() > 0 ) {
                                // don't log gps_stamp, in case of privacy!
                                //applicationInterface.drawTextWithBackground(canvas, p, gps_stamp, color, Color.BLACK, width - offset_x, ypos, MyApplicationInterface.Alignment.ALIGNMENT_BOTTOM, null, draw_shadowed);
                                if( stamp_string.length() == 0 )
                                    stamp_string = gps_stamp;
                                else
                                    stamp_string = gps_stamp + "\n" + stamp_string;
                                ypos -= diff_y;
                            }
                        }*/

                        /*if( address != null ) {
                            for(int i=0;i<=address.getMaxAddressLineIndex();i++) {
                                // write in reverse order
                                String addressLine = address.getAddressLine(address.getMaxAddressLineIndex()-i);
                                //applicationInterface.drawTextWithBackground(canvas, p, addressLine, color, Color.BLACK, width - offset_x, ypos, MyApplicationInterface.Alignment.ALIGNMENT_BOTTOM, null, draw_shadowed);
                                if( stamp_string.length() == 0 )
                                    stamp_string = addressLine;
                                else
                                    stamp_string = addressLine + "\n" + stamp_string;
                                ypos -= diff_y;
                            }
                        }*/
                    }
                }
                if( text_stamp ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "stamp text");

                    //applicationInterface.drawTextWithBackground(canvas, p, request.preference_textstamp, color, Color.BLACK, width - offset_x, ypos, MyApplicationInterface.Alignment.ALIGNMENT_BOTTOM, null, draw_shadowed);
                    if( stamp_string.isEmpty() )
                        stamp_string = request.preference_textstamp;
                    else
                        stamp_string = request.preference_textstamp + "\n" + stamp_string;

                    //noinspection UnusedAssignment
                    ypos -= diff_y;
                }

                if( !stamp_string.isEmpty() ) {
                    // don't log stamp_string, in case of privacy!

                    @SuppressLint("InflateParams")
                    final View stamp_view = LayoutInflater.from(main_activity).inflate(R.layout.stamp_image_text, null);
                    final LinearLayout layout = stamp_view.findViewById(R.id.layout);
                    final TextView textview = stamp_view.findViewById(R.id.text_view);

                    textview.setVisibility(View.VISIBLE);
                    textview.setTextColor(color);
                    textview.setTextSize(TypedValue.COMPLEX_UNIT_PX, font_size_pixel);
                    textview.setText(stamp_string);
                    if( draw_shadowed == MyApplicationInterface.Shadow.SHADOW_OUTLINE ) {
                        //noinspection PointlessArithmeticExpression
                        float shadow_radius = (1.0f * scale + 0.5f); // convert pt to pixels
                        shadow_radius = Math.max(shadow_radius, 1.0f);
                        if( MyDebug.LOG )
                            Log.d(TAG, "shadow_radius: " + shadow_radius);
                        textview.setShadowLayer(shadow_radius, 0.0f, 0.0f, Color.BLACK);
                    }
                    else if( draw_shadowed == MyApplicationInterface.Shadow.SHADOW_BACKGROUND ) {
                        textview.setBackgroundColor(Color.argb(64, 0, 0, 0));
                    }
                    //textview.setBackgroundColor(Color.BLACK); // test
                    textview.setGravity(Gravity.END); // so text is right-aligned - important when there are multiple lines

                    layout.measure(canvas.getWidth(), canvas.getHeight());
                    layout.layout(0, 0, canvas.getWidth(), canvas.getHeight());
                    canvas.translate(width - offset_x - textview.getWidth(), height - offset_y - textview.getHeight());
                    layout.draw(canvas);
                }
            }
        }
        return bitmap;
    }

    // =========================================================================
    // [REALCAMMI FORK] OpenCV post-processing methods
    // All methods follow the same pattern: accept a Bitmap, convert to OpenCV
    // Mat, process, convert back. OpenCV must be initialised before calling
    // (handled by OpenCameraApplication via OpenCVLoader.initLocal()).
    // =========================================================================

    /** Applies adaptive sharpening via Unsharp Mask (USM).
     *  USM works by subtracting a blurred version of the image from itself,
     *  amplifying fine detail without introducing colour fringing.
     *  Strength: radius=1.5px gaussian, amount=1.2, threshold=0.
     */
    private Bitmap applyOpenCVSharpen(Bitmap bitmap) {
        if( MyDebug.LOG )
            Log.d(TAG, "applyOpenCVSharpen");
        if( bitmap == null )
            return null;
        try {
            Mat src = new Mat();
            Utils.bitmapToMat(bitmap, src);

            Mat blurred = new Mat();
            Imgproc.GaussianBlur(src, blurred, new Size(0, 0), 1.5);

            // Unsharp mask: sharpened = src * (1 + amount) - blurred * amount
            Mat sharpened = new Mat();
            Core.addWeighted(src, 2.2, blurred, -1.2, 0, sharpened);

            Utils.matToBitmap(sharpened, bitmap);

            src.release();
            blurred.release();
            sharpened.release();
        } catch(Exception e) {
            if( MyDebug.LOG )
                Log.e(TAG, "applyOpenCVSharpen failed: " + e.getMessage());
        }
        return bitmap;
    }

    /** Applies noise reduction via Bilateral Filter.
     *  Bilateral filter preserves edges while smoothing flat areas, unlike
     *  Gaussian blur which blurs everything equally.
     *  Parameters: d=9, sigmaColor=75, sigmaSpace=75 — balanced NR for photos.
     */
    private Bitmap applyOpenCVNR(Bitmap bitmap) {
        if( MyDebug.LOG )
            Log.d(TAG, "applyOpenCVNR");
        if( bitmap == null )
            return null;
        try {
            Mat src = new Mat();
            Utils.bitmapToMat(bitmap, src);

            // Bilateral filter requires 8-bit single or 3-channel image
            Mat src_bgr = new Mat();
            Imgproc.cvtColor(src, src_bgr, Imgproc.COLOR_RGBA2BGR);

            Mat filtered = new Mat();
            Imgproc.bilateralFilter(src_bgr, filtered, 9, 75, 75);

            Mat result_rgba = new Mat();
            Imgproc.cvtColor(filtered, result_rgba, Imgproc.COLOR_BGR2RGBA);
            Utils.matToBitmap(result_rgba, bitmap);

            src.release();
            src_bgr.release();
            filtered.release();
            result_rgba.release();
        } catch(Exception e) {
            if( MyDebug.LOG )
                Log.e(TAG, "applyOpenCVNR failed: " + e.getMessage());
        }
        return bitmap;
    }

    /** Applies CLAHE (Contrast Limited Adaptive Histogram Equalisation).
     *  CLAHE improves local contrast — dark areas get more detail without
     *  blowing out highlights. Applied only to the luminance (L) channel
     *  in Lab colour space to avoid colour shifts.
     *  clipLimit=2.0, tileSize=8x8.
     */
    private Bitmap applyOpenCVCLAHE(Bitmap bitmap) {
        if( MyDebug.LOG )
            Log.d(TAG, "applyOpenCVCLAHE");
        if( bitmap == null )
            return null;
        try {
            Mat src = new Mat();
            Utils.bitmapToMat(bitmap, src);

            // Convert RGBA → BGR → Lab
            Mat bgr = new Mat();
            Imgproc.cvtColor(src, bgr, Imgproc.COLOR_RGBA2BGR);
            Mat lab = new Mat();
            Imgproc.cvtColor(bgr, lab, Imgproc.COLOR_BGR2Lab);

            // Split channels, apply CLAHE only to L channel
            java.util.List<Mat> channels = new java.util.ArrayList<>();
            Core.split(lab, channels);

            org.opencv.imgproc.CLAHE clahe = Imgproc.createCLAHE(2.0, new Size(8, 8));
            Mat l_enhanced = new Mat();
            clahe.apply(channels.get(0), l_enhanced);
            channels.set(0, l_enhanced);

            // Merge back and convert to RGBA
            Mat lab_enhanced = new Mat();
            Core.merge(channels, lab_enhanced);
            Mat bgr_enhanced = new Mat();
            Imgproc.cvtColor(lab_enhanced, bgr_enhanced, Imgproc.COLOR_Lab2BGR);
            Mat result_rgba = new Mat();
            Imgproc.cvtColor(bgr_enhanced, result_rgba, Imgproc.COLOR_BGR2RGBA);
            Utils.matToBitmap(result_rgba, bitmap);

            src.release();
            bgr.release();
            lab.release();
            for(Mat ch : channels) ch.release();
            l_enhanced.release();
            lab_enhanced.release();
            bgr_enhanced.release();
            result_rgba.release();
        } catch(Exception e) {
            if( MyDebug.LOG )
                Log.e(TAG, "applyOpenCVCLAHE failed: " + e.getMessage());
        }
        return bitmap;
    }

    /** Detects whether a photo is blurry using Laplacian variance.
     *  Returns the blur score — higher = sharper. Values below ~100 indicate
     *  a noticeably blurry image. Shows a toast warning to the user if blurry.
     */
    private void detectAndWarnBlur(Bitmap bitmap) {
        if( MyDebug.LOG )
            Log.d(TAG, "detectAndWarnBlur");
        if( bitmap == null )
            return;
        try {
            Mat src = new Mat();
            Utils.bitmapToMat(bitmap, src);

            Mat gray = new Mat();
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY);

            Mat laplacian = new Mat();
            Imgproc.Laplacian(gray, laplacian, CvType.CV_64F);

            MatOfDouble mean = new MatOfDouble();
            MatOfDouble stddev = new MatOfDouble();
            Core.meanStdDev(laplacian, mean, stddev);

            double variance = stddev.get(0, 0)[0];
            double blur_score = variance * variance;

            if( MyDebug.LOG )
                Log.d(TAG, "blur score (Laplacian variance): " + blur_score);

            // Threshold: below 100 = likely blurry
            if( blur_score < 100.0 ) {
                main_activity.runOnUiThread(() ->
                    main_activity.getPreview().showToast(null,
                        "⚠ Blurry photo detected (score: " + (int)blur_score + ")", true)
                );
            }

            src.release();
            gray.release();
            laplacian.release();
            mean.release();
            stddev.release();
        } catch(Exception e) {
            if( MyDebug.LOG )
                Log.e(TAG, "detectAndWarnBlur failed: " + e.getMessage());
        }
    }

    // [REALCAMMI FORK] applyColorCorrection()
    // Corrects the systematic blue colour cast (B/R ratio ~1.12 vs ~1.00 in stock camera)
    // that cannot be fixed via COLOR_CORRECTION_TRANSFORM when AWB is in AUTO mode (the HAL
    // ignores that field when the 3A pipeline is running). Applied as a post-processing step
    // on the captured bitmap instead.
    //
    // Uses ColorMatrix for GPU-accelerated per-channel scaling — much faster than iterating
    // pixels individually (critical for multi-megapixel images).
    //
    // Correction factors derived from B/R ratio analysis (golden hour + midday comparisons):
    //   Blue channel:  scale by 0.90 (reduce ~10% to bring B/R ratio from 1.12 → ~1.00)
    //   Red channel:   scale by 1.04 (slight boost to recover warmth lost by blue reduction)
    //   Green channel: unchanged (1.00)
    private Bitmap applyColorCorrection(byte[] data, Bitmap bitmap) {
        if( MyDebug.LOG )
            Log.d(TAG, "applyColorCorrection");

        if( bitmap == null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "need to decode bitmap for color correction");
            bitmap = ImageUtils.loadBitmapWithRotation(data, true);
            if( bitmap == null ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "failed to decode bitmap for color correction");
                System.gc();
                return null;
            }
        }

        // ColorMatrix row order: [ R, G, B, A, offset ]
        // Row 0 = output R, Row 1 = output G, Row 2 = output B, Row 3 = output A
        // Scale R slightly up, G unchanged, B slightly down; no cross-channel mixing,
        // no alpha change, no offset.
        ColorMatrix cm = new ColorMatrix(new float[] {
            1.01f, 0f,    0f,    0f, 0f,   // R_out = 1.01 * R_in  (reduced from 1.04 to reduce warm/yellow cast)
            0f,    1.00f, 0f,    0f, 0f,   // G_out = 1.00 * G_in
            0f,    0f,    0.92f, 0f, 0f,   // B_out = 0.92 * B_in  (increased from 0.90 to reduce warm/yellow cast)
            0f,    0f,    0f,    1f, 0f    // A_out = A_in (unchanged)
        });

        Bitmap corrected = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(corrected);
        Paint paint = new Paint();
        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        canvas.drawBitmap(bitmap, 0, 0, paint);

        if( corrected != bitmap ) {
            bitmap.recycle();
        }
        return corrected;
    }

    static class PostProcessBitmapResult {
        final Bitmap bitmap;

        PostProcessBitmapResult(Bitmap bitmap) {
            this.bitmap = bitmap;
        }
    }

    /** Performs post-processing on the data, or bitmap if non-null, for saveSingleImageNow.
     */
    PostProcessBitmapResult postProcessBitmap(final ImageSaver.Request request, byte[] data, Bitmap bitmap, boolean ignore_exif_orientation) throws IOException {
        if( MyDebug.LOG )
            Log.d(TAG, "postProcessBitmap");
        long time_s = System.currentTimeMillis();

        if( !ignore_exif_orientation ) {
            if( bitmap != null ) {
                // rotate the bitmap if necessary for exif tags
                if( MyDebug.LOG )
                    Log.d(TAG, "rotate pre-existing bitmap for exif tags?");
                bitmap = ImageUtils.rotateForExif(bitmap, data);
            }
        }

        if( request.do_auto_stabilise ) {
            bitmap = autoStabilise(data, bitmap, request.level_angle, request.is_front_facing);
        }
        if( MyDebug.LOG ) {
            Log.d(TAG, "Save single image performance: time after auto-stabilise: " + (System.currentTimeMillis() - time_s));
        }
        if( request.mirror ) {
            bitmap = mirrorImage(data, bitmap);
        }
        if( request.image_format != ImageSaver.Request.ImageFormat.STD && bitmap == null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "need to decode bitmap to convert file format");
            bitmap = ImageUtils.loadBitmapWithRotation(data, true);
            if( bitmap == null ) {
                // if we can't load bitmap for converting file formats, don't want to continue
                System.gc();
                throw new IOException();
            }
        }
        if( request.remove_device_exif != ImageSaver.Request.RemoveDeviceExif.OFF && bitmap == null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "need to decode bitmap to strip exif tags");
            // if removing device exif data, it's easier to do this by going through the codepath that
            // resaves the bitmap, and then we avoid transferring/adding exif tags that we don't want
            bitmap = ImageUtils.loadBitmapWithRotation(data, true);
            if( bitmap == null ) {
                // if we can't load bitmap for removing device tags, don't want to continue
                System.gc();
                throw new IOException();
            }
        }
        bitmap = stampImage(request, data, bitmap);
        if( MyDebug.LOG ) {
            Log.d(TAG, "Save single image performance: time after photostamp: " + (System.currentTimeMillis() - time_s));
        }
        // [REALCAMMI FORK] apply blue colour cast correction if enabled by the user
        if( main_activity.getApplicationInterface().getColorCorrectionPref() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "applying color correction post-processing");
            bitmap = applyColorCorrection(data, bitmap);
            if( MyDebug.LOG ) {
                Log.d(TAG, "Save single image performance: time after color correction: " + (System.currentTimeMillis() - time_s));
            }
        }

        // [REALCAMMI FORK] OpenCV post-processing — applied in order: NR → Sharpen → CLAHE → BlurDetect
        // NR first: clean up noise before sharpening (sharpening amplifies noise if done first)
        // Sharpen second: enhance detail after NR
        // CLAHE third: improve local contrast on the final clean+sharp image
        // BlurDetect last: analyse the final image and warn user if blurry
        if( main_activity.getApplicationInterface().getOpenCVNRPref() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "applying OpenCV noise reduction");
            if( bitmap == null ) {
                bitmap = ImageUtils.loadBitmapWithRotation(data, true);
            }
            bitmap = applyOpenCVNR(bitmap);
            if( MyDebug.LOG )
                Log.d(TAG, "Save single image performance: time after OpenCV NR: " + (System.currentTimeMillis() - time_s));
        }
        if( main_activity.getApplicationInterface().getOpenCVSharpenPref() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "applying OpenCV sharpening");
            if( bitmap == null ) {
                bitmap = ImageUtils.loadBitmapWithRotation(data, true);
            }
            bitmap = applyOpenCVSharpen(bitmap);
            if( MyDebug.LOG )
                Log.d(TAG, "Save single image performance: time after OpenCV sharpen: " + (System.currentTimeMillis() - time_s));
        }
        if( main_activity.getApplicationInterface().getOpenCVCLAHEPref() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "applying OpenCV CLAHE");
            if( bitmap == null ) {
                bitmap = ImageUtils.loadBitmapWithRotation(data, true);
            }
            bitmap = applyOpenCVCLAHE(bitmap);
            if( MyDebug.LOG )
                Log.d(TAG, "Save single image performance: time after OpenCV CLAHE: " + (System.currentTimeMillis() - time_s));
        }
        if( main_activity.getApplicationInterface().getOpenCVBlurDetectPref() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "running OpenCV blur detection");
            if( bitmap == null ) {
                bitmap = ImageUtils.loadBitmapWithRotation(data, true);
            }
            detectAndWarnBlur(bitmap);
        }

        return new PostProcessBitmapResult(bitmap);
    }
}
