package net.sourceforge.opencamera.realcammi;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
// [REALCAMMI FORK] needed for applyImageProfile() - post-capture photo tonemap curve
import net.sourceforge.opencamera.realcammi.cameracontroller.CameraController;
import net.sourceforge.opencamera.realcammi.cameracontroller.CameraController2;
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
     *  Gaussian blur radius: 1.15px (previously 1.5px). Amount: see sharpen_amount below -
     *  reference scale used while tuning: 0.5 = Soft (portraits/natural textures),
     *  0.8 = Moderate (balanced, no visible artefacts), 1.2 = High (strong edge sharpening).
     */
    // [REALCAMMI FORK] Piece 4 of AI scene detection: was a hardcoded addWeighted() call at a
    // fixed amount=0.5 ("Soft" on the scale above). Now an instance field, set in
    // postProcessBitmap() right before applyOpenCVSharpen() runs: INDOOR gets a modest bump to
    // 0.65 (still well short of the "Moderate" 0.8 tier) to help text/fine-detail legibility;
    // everything else keeps the previous default of 0.5.
    private float sharpen_amount = 0.5f;

    private Bitmap applyOpenCVSharpen(Bitmap bitmap) {
        if( MyDebug.LOG )
            Log.d(TAG, "applyOpenCVSharpen");
        if( bitmap == null )
            return null;
        try {
            Mat src = new Mat();
            Utils.bitmapToMat(bitmap, src);

            Mat blurred = new Mat();
            Imgproc.GaussianBlur(src, blurred, new Size(0, 0), 1.15); //default 1.5

            // Unsharp mask: sharpened = src * (1 + amount) - blurred * amount
            Mat sharpened = new Mat();
            // sharpen_amount is set dynamically in postProcessBitmap() based on AI scene
            // category - see field comment above for the value scale and current logic.
            Core.addWeighted(src, 1.0 + sharpen_amount, blurred, -sharpen_amount, 0, sharpened);

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

    // [REALCAMMI FORK] NR strength presets for applyOpenCVNR(). Unlike the Camera2 HAL's
    // NOISE_REDUCTION_MODE (FAST/MINIMAL/HIGH_QUALITY — a fixed vendor black box with no
    // strength parameter), these bilateral filter values ARE continuously tunable, which is
    // why NR strength control lives here rather than in the HAL mode selection.
    // To test a different strength on-device, change NR_STRENGTH below and rebuild.
    // LIGHT:  barely more than MINIMAL, keeps max detail, some noise may remain
    // MEDIUM: original/default balance (previously the only option, hardcoded 9/75/75)
    // STRONG: noticeably smoother, use if MEDIUM still shows visible noise
    private static final int NR_STRENGTH_LIGHT = 0;
    private static final int NR_STRENGTH_MEDIUM = 1;
    private static final int NR_STRENGTH_STRONG = 2;
    // [REALCAMMI FORK] Piece 4 of AI scene detection: this used to be `static final`, hardcoded
    // to NR_STRENGTH_LIGHT. Now it's an instance field, set in postProcessBitmap() right before
    // applyOpenCVNR() runs, based on main_activity.getPreview().getCameraController()
    // .getCurrentSceneCategory(): LOW_LIGHT -> STRONG, everything else -> LIGHT (the previous
    // hardcoded default, unchanged for STANDARD/EXTREME_BACKLIT/INDOOR).
    private int NR_STRENGTH = NR_STRENGTH_LIGHT;

    /** Applies noise reduction via Bilateral Filter.
     *  Bilateral filter preserves edges while smoothing flat areas, unlike
     *  Gaussian blur which blurs everything equally.
     *  [REALCAMMI FORK] Parameters now selected from 3 presets (see NR_STRENGTH above)
     *  instead of a single hardcoded d=9, sigmaColor=75, sigmaSpace=75.
     */
    // Tune 9
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

            // [REALCAMMI FORK] preset selection: d / sigmaColor / sigmaSpace
            int nr_d, nr_sigma;
            switch( NR_STRENGTH ) {
                case NR_STRENGTH_LIGHT:
                    nr_d = 6; nr_sigma = 14;
                    break;
                case NR_STRENGTH_STRONG:
                    nr_d = 13; nr_sigma = 110;
                    break;
                case NR_STRENGTH_MEDIUM:
                default:
                    nr_d = 10; nr_sigma = 82;
                    break;
            }

            Mat filtered = new Mat();
            Imgproc.bilateralFilter(src_bgr, filtered, nr_d, nr_sigma, nr_sigma);

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
     *  [REALCAMMI FORK] Tile grid is now scaled to image resolution instead of a
     *  fixed 8x8: on a 12MP+ photo, 8x8 gives ~510x382px tiles, far too coarse to
     *  recover fine local contrast (e.g. sand/water sparkle). Target ~256px tiles instead.
     *  clipLimit=2.0: raising the tile grid density alone (above) already gives a stronger
     *  contrast punch than the original fixed 8x8 grid, so clipLimit was kept moderate
     *  rather than also raised to 3.0 — 3.0 was tried and found too strong/exaggerated
     *  on real shots once combined with the finer tile grid.
     *  Note: the a/b chroma compensation that used to live in this function has moved to
     *  its own applyTonemapDesaturationCompensation(), which runs unconditionally on every
     *  photo regardless of this CLAHE toggle — see that method for the rationale.
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

            // [REALCAMMI FORK] tile grid scaled to resolution instead of fixed 8x8
            int tiles_x = Math.max(8, bgr.cols() / 256);
            int tiles_y = Math.max(8, bgr.rows() / 256);
            // [REALCAMMI FORK BUGFIX] clipLimit was hardcoded to 1.1, contradicting the class
            // doc comment above (clipLimit=3.0, raised from 2.0 for the finer tile grid).
            // Restored to the documented/intended value.
            org.opencv.imgproc.CLAHE clahe = Imgproc.createCLAHE(2.2, new Size(tiles_x, tiles_y)); //Default value 3.0
            Mat l_orig = channels.get(0);
            Mat l_enhanced = new Mat();
            clahe.apply(l_orig, l_enhanced);
            // [REALCAMMI FORK BUGFIX] release the original L-channel Mat before the list loses
            // its only reference to it via set() below — otherwise it's a native memory leak on
            // every photo processed with CLAHE enabled (the cleanup loop further down only sees
            // l_enhanced afterwards, never l_orig).
            l_orig.release();
            channels.set(0, l_enhanced);

            // [REALCAMMI FORK NOTE] the a/b chroma compensation that used to live here was
            // moved into its own applyTonemapDesaturationCompensation(), which now runs
            // unconditionally in postProcessBitmap() regardless of whether this CLAHE toggle
            // is on — see that method for the full rationale. This function now only does the
            // L-channel local contrast enhancement CLAHE was originally meant for.

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
            // [REALCAMMI FORK BUGFIX] l_enhanced is channels.get(0) (set at the line above),
            // already released by the loop just above — releasing it again here was a
            // double-free of the same native Mat (undefined behaviour in OpenCV's native
            // layer, can corrupt state for the *next* OpenCV call in the pipeline, e.g.
            // applyOpenCVSharpen running right after this one).
            lab_enhanced.release();
            bgr_enhanced.release();
            result_rgba.release();
        } catch(Exception e) {
            if( MyDebug.LOG )
                Log.e(TAG, "applyOpenCVCLAHE failed: " + e.getMessage());
        }
        return bitmap;
    }

    /** [REALCAMMI FORK] Compensates for the saturation loss caused by the Camera2
     *  TonemapCurve being applied identically to R, G and B (TonemapCurve has no luma-only
     *  mode — an API constraint, not something fixable in the curve itself). Desaturation
     *  from that mechanism is concentrated in the highlight range, so the boost scales with L.
     *  Runs unconditionally on every photo in postProcessBitmap(), independent of whether
     *  Contrast Enhancement (CLAHE) is enabled — the TonemapCurve itself is always active,
     *  so the desaturation it causes is not an optional stylistic effect to be toggled, it's
     *  a correction for an unavoidable side-effect of the curve. This was previously bundled
     *  inside applyOpenCVCLAHE() and only ran when that toggle was on; moved out so it applies
     *  to every photo regardless.
     *  boost = 1.02 + 0.180*L: a small +2% lift even in shadows, rising to +20% chroma in
     *  highlights. (Formula history below, in case any of these needs revisiting again.)
     *  [REALCAMMI FORK] Was 0.35 originally, but that was never tested on a real capture
     *  before this compensation became unconditional — first real-device test (2026-07-12)
     *  showed warm/orange surfaces (terracotta roof tiles) turning noticeably too yellow.
     *  Dialed back to 0.125 (previously 0.15, retuned again after a second real-device pass).
     *  [REALCAMMI FORK] Updated 2026-07-19: increased to 0.180 (was 0.125) plus a 1.02 base,
     *  to bring more life to midtones without blowing out highlights - see the "UPDATED VALUES"
     *  comment further down, right above the active Core.addWeighted() call, for the exact
     *  current formula. Re-tune these two values (multiplier + base) if still too strong/weak.
     */
    // Tune 10
    private Bitmap applyTonemapDesaturationCompensation(Bitmap bitmap) {
        if( MyDebug.LOG )
            Log.d(TAG, "applyTonemapDesaturationCompensation");
        if( bitmap == null )
            return null;
        try {
            Mat src = new Mat();
            Utils.bitmapToMat(bitmap, src);

            Mat bgr = new Mat();
            Imgproc.cvtColor(src, bgr, Imgproc.COLOR_RGBA2BGR);
            Mat lab = new Mat();
            Imgproc.cvtColor(bgr, lab, Imgproc.COLOR_BGR2Lab);

            java.util.List<Mat> channels = new java.util.ArrayList<>();
            Core.split(lab, channels);

            Mat l_chan = channels.get(0);
            Mat l_norm = new Mat();
            l_chan.convertTo(l_norm, CvType.CV_32F, 1.0/255.0);
            Mat boost = new Mat();
            // boost = 1.0 + 0.125*l_norm: 1.0 in shadows (l_norm=0, no change), rising to 1.125
            // in highlights (l_norm=1, +12.5% chroma) - see class doc comment above for history.
            //Core.addWeighted(l_norm, 0.125, l_norm, 0, 1.0, boost);

            // UPDATED VALUES: We increased the multiplier from 0.125 to 0.180 (an 18% gain instead of 12.5%).
            // We also added a compensation base of 1.02 to bring life to the midtones without blowing out the highlights.
            Core.addWeighted(l_norm, 0.180, l_norm, 0, 1.02, boost);

            Mat a_chan = channels.get(1);
            Mat b_chan = channels.get(2);
            Mat a_f = new Mat();
            Mat b_f = new Mat();
            a_chan.convertTo(a_f, CvType.CV_32F);
            b_chan.convertTo(b_f, CvType.CV_32F);
            Core.subtract(a_f, new org.opencv.core.Scalar(128.0), a_f);
            Core.subtract(b_f, new org.opencv.core.Scalar(128.0), b_f);
            Core.multiply(a_f, boost, a_f);
            Core.multiply(b_f, boost, b_f);
            Core.add(a_f, new org.opencv.core.Scalar(128.0), a_f);
            Core.add(b_f, new org.opencv.core.Scalar(128.0), b_f);
            Core.min(a_f, new org.opencv.core.Scalar(255.0), a_f);
            Core.max(a_f, new org.opencv.core.Scalar(0.0), a_f);
            Core.min(b_f, new org.opencv.core.Scalar(255.0), b_f);
            Core.max(b_f, new org.opencv.core.Scalar(0.0), b_f);
            a_f.convertTo(a_chan, CvType.CV_8U);
            b_f.convertTo(b_chan, CvType.CV_8U);
            channels.set(1, a_chan);
            channels.set(2, b_chan);

            Mat lab_out = new Mat();
            Core.merge(channels, lab_out);
            Mat bgr_out = new Mat();
            Imgproc.cvtColor(lab_out, bgr_out, Imgproc.COLOR_Lab2BGR);
            Mat result_rgba = new Mat();
            Imgproc.cvtColor(bgr_out, result_rgba, Imgproc.COLOR_BGR2RGBA);
            Utils.matToBitmap(result_rgba, bitmap);

            src.release();
            bgr.release();
            lab.release();
            for(Mat ch : channels) ch.release();
            l_norm.release();
            boost.release();
            a_f.release();
            b_f.release();
            lab_out.release();
            bgr_out.release();
            result_rgba.release();
        } catch(Exception e) {
            if( MyDebug.LOG )
                Log.e(TAG, "applyTonemapDesaturationCompensation failed: " + e.getMessage());
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
    // [REALCAMMI FORK NOTE] Matrix below is currently NEUTRAL (1.00/1.00/1.00) while isolating
    // the TonemapCurve per-channel hue-shift investigation (see chat log 2026-07-06). The
    // previously-tuned correction factors (B x0.90, R x1.04, derived from golden hour + midday
    // B/R ratio analysis) are recorded here for when this is restored:
    //   Blue channel:  scale by 0.90 (reduce ~10% to bring B/R ratio from 1.12 -> ~1.00)
    //   Red channel:   scale by 1.04 (slight boost to recover warmth lost by blue reduction)
    //   Green channel: unchanged (1.00)
    /** [REALCAMMI FORK] Returns the resolved (in,out) curve control points - flat array
     *  [in0,out0, in1,out1, ...], each in [0,1] - for whichever Image Profile the user has
     *  selected in Photo settings, or null if none is active (Standard, or Log/Gamma with
     *  zero strength/value).
     *
     *  Deliberately duplicates the curve formulas used live in Camera2Settings.setTonemapProfile()
     *  rather than sharing an instance, because this runs on the ImageSaver background thread,
     *  potentially well after capture, when the live CameraController2/Camera2Settings for the
     *  session may already be closed/replaced - reading straight from
     *  MyApplicationInterface's preference getters (the same ones Preview.java uses) avoids any
     *  dependency on camera object lifecycle. If the curve formulas above are ever changed,
     *  the copies here must be updated to match.
     */
    private float[] getResolvedImageProfileCurve() {
        CameraController.TonemapProfile profile = main_activity.getApplicationInterface().getVideoTonemapProfile();
        if( profile == CameraController.TonemapProfile.TONEMAPPROFILE_OFF )
            return null;

        float [] values = null;
        switch( profile ) {
            case TONEMAPPROFILE_REC709: {
                float [] x_values = new float[] {
                        0.0000f, 0.0667f, 0.1333f, 0.2000f,
                        0.2667f, 0.3333f, 0.4000f, 0.4667f,
                        0.5333f, 0.6000f, 0.6667f, 0.7333f,
                        0.8000f, 0.8667f, 0.9333f, 1.0000f
                };
                values = new float[2*x_values.length];
                int c = 0;
                for(float x_value : x_values) {
                    float out = (x_value < 0.018f) ? 4.5f * x_value : (float)(1.099*Math.pow(x_value, 0.45) - 0.099);
                    values[c++] = x_value;
                    values[c++] = out;
                }
                break;
            }
            case TONEMAPPROFILE_SRGB:
                values = new float [] {
                        0.0000f, 0.0000f, 0.0667f, 0.2864f, 0.1333f, 0.4007f, 0.2000f, 0.4845f,
                        0.2667f, 0.5532f, 0.3333f, 0.6125f, 0.4000f, 0.6652f, 0.4667f, 0.7130f,
                        0.5333f, 0.7569f, 0.6000f, 0.7977f, 0.6667f, 0.8360f, 0.7333f, 0.8721f,
                        0.8000f, 0.9063f, 0.8667f, 0.9389f, 0.9333f, 0.9701f, 1.0000f, 1.0000f
                };
                break;
            case TONEMAPPROFILE_SLOG3: {
                int n_values = 64;
                values = new float[2 * n_values];
                for(int i = 0; i < n_values; i++) {
                    float in = ((float)i) / (n_values - 1.0f);
                    float out;
                    if( in >= 0.01125000f )
                        out = (float)((420.0 + Math.log10((in + 0.01) / 0.19) * 261.5) / 1023.0);
                    else
                        out = (float)(((in * (171.2102946929 - 95.0) / 0.01125) + 95.0) / 1023.0);
                    values[2*i] = in;
                    values[2*i+1] = Math.max(0f, Math.min(1f, out));
                }
                break;
            }
            case TONEMAPPROFILE_LOG: {
                float log_strength = main_activity.getApplicationInterface().getVideoLogProfileStrength();
                if( log_strength == 0.0f )
                    return null;
                int n_values = 64;
                values = new float [2*n_values];
                for(int i=0;i<n_values;i++) {
                    float in = ((float)i) / (n_values-1.0f);
                    float out = (float)(Math.log1p(log_strength * in) / Math.log1p(log_strength));
                    values[2*i] = in;
                    values[2*i+1] = out;
                }
                break;
            }
            case TONEMAPPROFILE_GAMMA: {
                float gamma = main_activity.getApplicationInterface().getVideoProfileGamma();
                if( gamma == 0.0f )
                    return null;
                int n_values = 64;
                values = new float [2*n_values];
                for(int i=0;i<n_values;i++) {
                    float in = ((float)i) / (n_values-1.0f);
                    float out = (float)Math.pow(in, 1.0f/gamma);
                    values[2*i] = in;
                    values[2*i+1] = out;
                }
                break;
            }
            case TONEMAPPROFILE_JTVIDEO:
                values = CameraController2.jtvideo_values_base;
                break;
            case TONEMAPPROFILE_JTLOG:
                values = CameraController2.jtlog_values_base;
                break;
            case TONEMAPPROFILE_JTLOG2:
                values = CameraController2.jtlog2_values_base;
                break;
        }
        return values;
    }

    /** [REALCAMMI FORK] Applies the given (in,out) tonemap curve control points to a captured
     *  photo bitmap, as a 256-entry lookup table applied identically to the R, G and B channels
     *  (matching the live TONEMAP_CURVE(values,values,values) behaviour used for video/preview).
     *  This is how Image Profiles (Rec709/sRGB/Log/Gamma/S-Log3/JT curves) are applied to
     *  photos - see setTonemapProfile() in Camera2Settings.java for why photo capture itself
     *  always stays in the native/Standard tonemap, and the profile "look" is added here
     *  afterwards instead.
     */
    private Bitmap applyImageProfile(Bitmap bitmap, float[] curve_points) {
        if( MyDebug.LOG )
            Log.d(TAG, "applyImageProfile");
        if( bitmap == null || curve_points == null || curve_points.length < 4 )
            return bitmap;

        Mat src = null, rgb = null, dst = null, result_rgba = null, lut = null;
        try {
            int n_points = curve_points.length / 2;
            byte [] lut_bytes = new byte[256];
            for(int v = 0; v < 256; v++) {
                // [REALCAMMI FORK BUGFIX] The curve_points (Rec709/sRGB/Log/Gamma/S-Log3/JT) are
                // OETFs: they expect LINEAR scene-referred input in [0,1], exactly what the
                // sensor delivers live via TONEMAP_MODE. Here the input is instead an already
                // gamma-encoded (Standard-processed, sRGB-ish) JPEG bitmap - feeding it straight
                // into the curve applied a second gamma encoding on top of the first, blowing
                // shadows/midtones out to near-white. Decoding back to approximate linear light
                // first (standard sRGB EOTF) before interpolating the curve fixes this, at no
                // extra runtime cost since it's folded into the same one-time 256-entry LUT.
                float c = v / 255.0f;
                float x = (c <= 0.04045f) ? (c / 12.92f) : (float)Math.pow((c + 0.055) / 1.055, 2.4);

                float out;
                if( x <= curve_points[0] ) {
                    out = curve_points[1];
                }
                else if( x >= curve_points[2*(n_points-1)] ) {
                    out = curve_points[2*(n_points-1)+1];
                }
                else {
                    out = curve_points[1]; // fallback, overwritten by the loop below
                    for(int i = 0; i < n_points - 1; i++) {
                        float x0 = curve_points[2*i], x1 = curve_points[2*(i+1)];
                        if( x >= x0 && x <= x1 ) {
                            float y0 = curve_points[2*i+1], y1 = curve_points[2*(i+1)+1];
                            float t = (x1 > x0) ? (x - x0) / (x1 - x0) : 0.0f;
                            out = y0 + t * (y1 - y0);
                            break;
                        }
                    }
                }
                int byte_val = Math.round(Math.max(0.0f, Math.min(1.0f, out)) * 255.0f);
                lut_bytes[v] = (byte)byte_val;
            }
            lut = new Mat(1, 256, CvType.CV_8UC1);
            lut.put(0, 0, lut_bytes);

            src = new Mat();
            Utils.bitmapToMat(bitmap, src);
            rgb = new Mat();
            Imgproc.cvtColor(src, rgb, Imgproc.COLOR_RGBA2RGB);
            dst = new Mat();
            Core.LUT(rgb, lut, dst); // single-channel lut broadcasts across all 3 channels
            result_rgba = new Mat();
            Imgproc.cvtColor(dst, result_rgba, Imgproc.COLOR_RGB2RGBA);

            Bitmap out_bitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(result_rgba, out_bitmap);
            return out_bitmap;
        }
        catch(Exception e) {
            if( MyDebug.LOG )
                Log.e(TAG, "applyImageProfile failed: " + e.getMessage());
            return bitmap;
        }
        finally {
            if( src != null ) src.release();
            if( rgb != null ) rgb.release();
            if( dst != null ) dst.release();
            if( result_rgba != null ) result_rgba.release();
            if( lut != null ) lut.release();
        }
    }

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
        // [REALCAMMI FORK] Channels set to (0.98/0.98/1.00) - see the class doc comment above
        // for the full rationale.
        // Tune 1
        ColorMatrix cm = new ColorMatrix(new float[] {
                0.980f, 0f,    0f,    0f, 0f,   // R: -2%
                0f,    0.980f, 0f,    0f, 0f,   // G: -2%
                0f,    0f,    1.00f, 0f, 0f,   // B: unchanged
                0f,    0f,    0f,    1f, 0f    // A
        });

        /* [REALCAMMI FORK NOTE] This is a UNIFORM +2% saturation boost, applied equally across
            the whole image. It stacks with applyTonemapDesaturationCompensation() (runs later in
            the pipeline, after NR/Sharpen), which adds a SECOND, separate boost weighted by
            luminance (0.18 coefficient, highlights only). Both are intentionally kept active:
            this one lifts saturation everywhere, the other specifically compensates highlights
            for the TonemapCurve's desaturation. If either one is retuned in isolation later,
            remember the other is still stacking on top of it.*/

        ColorMatrix saturationBoost = new ColorMatrix();
        saturationBoost.setSaturation(1.02f);
        cm.postConcat(saturationBoost);

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

        // [REALCAMMI FORK BUGFIX] Image Profile (Rec709/sRGB/Log/Gamma/S-Log3/JT curves) is
        // applied here, on the captured photo bitmap, rather than live via TONEMAP_MODE - see
        // setTonemapProfile() in Camera2Settings.java for why (confirmed AE darkening drift in
        // photo mode with any profile active). Video mode still applies the profile live and
        // correctly, so this must NOT run there too, or the curve would be applied twice to
        // video snapshot photos.
        // [REALCAMMI FORK] Image Profile (Rec709/sRGB/Log/Gamma/S-Log3/JT curves) applied here,
        // on the captured photo bitmap, rather than live via TONEMAP_MODE - see setTonemapProfile()
        // in Camera2Settings.java for why (confirmed AE darkening drift in photo mode with any
        // profile active). applyImageProfile() decodes back to linear light before applying the
        // curve, matching what the curve expects (fixed 2026-07-19, was double-gamma-encoding
        // and blowing highlights/shadows out to near-white). Video mode still applies the
        // profile live and correctly, so this must NOT run there too, or the curve would be
        // applied twice to video snapshot photos.
        if( !main_activity.getPreview().isVideo() ) {
            float [] image_profile_curve = getResolvedImageProfileCurve();
            if( image_profile_curve != null ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "applying image profile post-processing");
                bitmap = applyImageProfile(bitmap, image_profile_curve);
                if( MyDebug.LOG ) {
                    Log.d(TAG, "Save single image performance: time after image profile: " + (System.currentTimeMillis() - time_s));
                }
            }
        }

        // [REALCAMMI FORK] OpenCV post-processing — applied in order: NR → Sharpen → Desaturation
        // compensation → CLAHE → BlurDetect
        // NR first: clean up noise before sharpening (sharpening amplifies noise if done first)
        // Sharpen second: enhance detail after NR
        // Desaturation compensation third: runs on the clean+sharp image, not before — avoids an
        // extra RGB->Lab->RGB round trip (with 8-bit rounding at each step) sitting in front of
        // NR/Sharpen, which was found to soften fine detail when this ran right after colour
        // correction instead (2026-07-12). Runs unconditionally, regardless of the CLAHE toggle
        // below — the TonemapCurve itself is always active, so this is a correction for an
        // unavoidable side-effect, not an optional stylistic effect.
        // CLAHE fourth: improve local contrast on the final clean+sharp+corrected image
        // BlurDetect last: analyse the final image and warn user if blurry
        if( main_activity.getApplicationInterface().getOpenCVNRPref() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "applying OpenCV noise reduction");
            if( bitmap == null ) {
                bitmap = ImageUtils.loadBitmapWithRotation(data, true);
            }
            // [REALCAMMI FORK] Piece 4 of AI scene detection: LOW_LIGHT gets STRONG NR instead
            // of the usual LIGHT default. All other categories (STANDARD/EXTREME_BACKLIT/INDOOR)
            // keep the previous hardcoded behaviour unchanged.
            NR_STRENGTH = ( main_activity.getPreview().getCameraController().getCurrentSceneCategory()
                    == SceneDetector.SceneCategory.LOW_LIGHT ) ? NR_STRENGTH_STRONG : NR_STRENGTH_LIGHT;
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
            // [REALCAMMI FORK] Piece 4 of AI scene detection: INDOOR gets a modest sharpen
            // bump (helps text/fine-detail legibility indoors); everything else keeps default.
            sharpen_amount = ( main_activity.getPreview().getCameraController().getCurrentSceneCategory()
                    == SceneDetector.SceneCategory.INDOOR ) ? 0.65f : 0.5f;
            bitmap = applyOpenCVSharpen(bitmap);
            if( MyDebug.LOG )
                Log.d(TAG, "Save single image performance: time after OpenCV sharpen: " + (System.currentTimeMillis() - time_s));
        }

        // [REALCAMMI FORK] see note in the block header above — runs here, unconditionally,
        // regardless of the CLAHE toggle immediately below.
        if( bitmap == null ) {
            bitmap = ImageUtils.loadBitmapWithRotation(data, true);
        }
        bitmap = applyTonemapDesaturationCompensation(bitmap);
        if( MyDebug.LOG ) {
            Log.d(TAG, "Save single image performance: time after tonemap desaturation compensation: " + (System.currentTimeMillis() - time_s));
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

        // [REALCAMMI FORK] Natural depth-of-field / portrait background blur (see
        // DepthEffect.java). Deliberately runs LAST, after blur detection above - detectAndWarnBlur()
        // checks whether the PHOTO ACCIDENTALLY came out blurry (motion/focus mistake), and
        // must see the original sharpness, before this intentionally blurs the background.
        if( main_activity.getApplicationInterface().getDepthBlurPref() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "applying depth blur");
            if( bitmap == null ) {
                bitmap = ImageUtils.loadBitmapWithRotation(data, true);
            }
            DepthEffect depthEffect = null;
            try {
                depthEffect = new DepthEffect(main_activity);
                bitmap = depthEffect.apply(bitmap);
            }
            catch(Exception e) {
                // best-effort only - model missing/corrupt should never block saving the photo
                if( MyDebug.LOG )
                    Log.e(TAG, "depth blur failed: " + e.getMessage());
            }
            finally {
                if( depthEffect != null )
                    depthEffect.close();
            }
            if( MyDebug.LOG )
                Log.d(TAG, "Save single image performance: time after depth blur: " + (System.currentTimeMillis() - time_s));
        }

        return new PostProcessBitmapResult(bitmap);
    }
}
