package net.sourceforge.opencamera.realcammi;

/** Stores all of the string keys used for SharedPreferences.
 */
public class PreferenceKeys {
    // must be static, to safely call from other Activities

    /** If this preference is set, no longer show the intro dialog.
     */
    public static final String FirstTimePreferenceKey = "done_first_time";

    /** This preference stores the version number seen by the user - used to show "What's New" dialog.
     */
    public static final String LatestVersionPreferenceKey = "latest_version";

    /** This preference stores whether to allow showing the "What's New" dialog.
     */
    public static final String ShowWhatsNewPreferenceKey = "preference_show_whats_new";

    /** If this preference is set, no longer show the auto-stabilise info dialog.
     */
    public static final String AutoStabiliseInfoPreferenceKey = "done_auto_stabilise_info";

    /** If this preference is set, no longer show the HDR info dialog.
     */
    public static final String HDRInfoPreferenceKey = "done_hdr_info";

    /** If this preference is set, no longer show the Panorama info dialog.
     */
    public static final String PanoramaInfoPreferenceKey = "done_panorama_info";

    /** If this preference is set, no longer show the raw info dialog.
     */
    public static final String RawInfoPreferenceKey = "done_raw_info";

    /** If this preference is set, no longer show the dialog for poor magnetic accuracy
     */
    public static final String MagneticAccuracyPreferenceKey = "done_magnetic_accuracy";

    public static final String CameraAPIPreferenceDefault = "preference_camera_api_old";
    public static final String CameraAPIPreferenceKey = "preference_camera_api";

    private static String getCameraIDKey(int cameraId, String cameraIdSPhysical) {
        if( cameraIdSPhysical != null )
            return cameraId + "_" + cameraIdSPhysical;
        else
            return String.valueOf(cameraId);
    }

    // don't set to be specific for physical cameras, as too confusing to have lots of different flash preferences
    // also in Preview, we don't save the flash back if not supported
    public static String getFlashPreferenceKey(int cameraId) {
        return "flash_value_" + cameraId;
    }

    public static String getFocusPreferenceKey(int cameraId, boolean is_video) {
        return "focus_value_" + cameraId + "_" + is_video;
    }

    public static final String FocusAssistPreferenceKey = "preference_focus_assist";

    public static String getResolutionPreferenceKey(int cameraId, String cameraIdSPhysical) {
        return "camera_resolution_" + getCameraIDKey(cameraId, cameraIdSPhysical);
    }

    public static String getVideoQualityPreferenceKey(int cameraId, String cameraIdSPhysical, boolean high_speed) {
        return "video_quality_" + getCameraIDKey(cameraId, cameraIdSPhysical) + (high_speed ? "_highspeed" : "");
    }

    public static final String OptimiseFocusPreferenceKey = "preference_photo_optimise_focus";

    public static final String ImageFormatPreferenceKey = "preference_image_format";

    public static final String IsVideoPreferenceKey = "is_video";

    public static final String ExposurePreferenceKey = "preference_exposure";

    public static final String ColorEffectPreferenceKey = "preference_color_effect";

    public static final String SceneModePreferenceKey = "preference_scene_mode";

    public static final String WhiteBalancePreferenceKey = "preference_white_balance";

    public static final String WhiteBalanceTemperaturePreferenceKey = "preference_white_balance_temperature";

    public static final String AntiBandingPreferenceKey = "preference_antibanding";

    public static final String EdgeModePreferenceKey = "preference_edge_mode";

    public static final String CameraNoiseReductionModePreferenceKey = "preference_noise_reduction_mode"; // n.b., this is for the Camera driver noise reduction mode, not RealCamMI's NR photo mode

    public static final String ISOPreferenceKey = "preference_iso";

    public static final String ExposureTimePreferenceKey = "preference_exposure_time";

    /** [REALCAMMI FORK 2026-08-31] User-configurable extra EV compensation applied to every still
     *  capture, entered via a GCam-style numeric dialog (MyEditTextPreference) rather than a
     *  slider. Default "0" means no extra compensation is applied.
     */
    public static final String ExtraExposureCompensationPreferenceKey = "preference_extra_exposure_compensation";
    public static final String ExtraExposureCompensationDefault = "0";

    /** [REALCAMMI FORK 2026-08-31] User-configurable "Color boost" fields (GCam-style numeric
     *  dialogs, not sliders) for PostProcessing.applyColorBoostMat(). Defaults are a genuine
     *  no-op (scale fields 0.000, meaning an effective multiplier of 1+value; offset 0; saturation
     *  1.0; hue 0 degrees) until the user edits one.
     */
    public static final String ColorBoostRedScalePreferenceKey = "preference_color_boost_red_scale";
    public static final String ColorBoostRedScaleDefault = "0.000";
    public static final String ColorBoostGreenScalePreferenceKey = "preference_color_boost_green_scale";
    public static final String ColorBoostGreenScaleDefault = "0.000";
    public static final String ColorBoostBlueScalePreferenceKey = "preference_color_boost_blue_scale";
    public static final String ColorBoostBlueScaleDefault = "0.000";
    public static final String ColorBoostOffsetPreferenceKey = "preference_color_boost_offset";
    public static final String ColorBoostOffsetDefault = "0.000";
    public static final String ColorBoostSaturationPreferenceKey = "preference_color_boost_saturation";
    public static final String ColorBoostSaturationDefault = "1.000";
    public static final String ColorBoostHuePreferenceKey = "preference_color_boost_hue";
    public static final String ColorBoostHueDefault = "0.000";

    /** [REALCAMMI FORK 2026-08-31] Persists the chosen metering mode (Matrix/Center-weighted/Spot)
     *  across app restarts and camera re-opens, and so it's captured by Save/Restore Settings
     *  like every other SharedPreference. Stored as a readable string (not an ordinal/int) so a
     *  saved settings .xml file is self-explanatory if inspected by hand.
     */
    public static final String MeteringModePreferenceKey = "preference_metering_mode";
    public static final String MeteringModeValueMatrix = "matrix";
    public static final String MeteringModeValueCenterWeighted = "center_weighted";
    public static final String MeteringModeValueSpot = "spot";
    public static final String MeteringModeDefault = MeteringModeValueMatrix;

    public static final String RawPreferenceKey = "preference_raw";

    public static final String AllowRawForExpoBracketingPreferenceKey = "preference_raw_expo_bracketing";

    public static final String AllowRawForFocusBracketingPreferenceKey = "preference_raw_focus_bracketing";

    public static final String PanoramaCropPreferenceKey = "preference_panorama_crop";

    public static final String PanoramaSaveExpoPreferenceKey = "preference_panorama_save";

    public static final String ExpoBracketingNImagesPreferenceKey = "preference_expo_bracketing_n_images";

    public static final String ExpoBracketingStopsPreferenceKey = "preference_expo_bracketing_stops";

    public static final String FocusDistancePreferenceKey = "preference_focus_distance";

    public static final String FocusBracketingTargetDistancePreferenceKey = "preference_focus_bracketing_target_distance";

    public static final String FocusBracketingAutoSourceDistancePreferenceKey = "preference_focus_bracketing_auto_source_distance";

    public static final String FocusBracketingNImagesPreferenceKey = "preference_focus_bracketing_n_images";

    public static final String FocusBracketingAddInfinityPreferenceKey = "preference_focus_bracketing_add_infinity";

    public static final String VolumeKeysPreferenceKey = "preference_volume_keys";

    public static final String AudioControlPreferenceKey = "preference_audio_control";

    public static final String AudioNoiseControlSensitivityPreferenceKey = "preference_audio_noise_control_sensitivity";

    public static final String QualityPreferenceKey = "preference_quality";

    public static final String AutoStabilisePreferenceKey = "preference_auto_stabilise";

    public static final String ColorCorrectionPreferenceKey = "preference_color_correction";

    // [REALCAMMI FORK] OpenCV post-processing features
    public static final String OpenCVSharpenPreferenceKey   = "preference_opencv_sharpen";

    public static final String OpenCVNRPreferenceKey        = "preference_opencv_nr";
    // [REALCAMMI FORK] Manual NR strength preset (LOWER_LIGHT,LIGHT/MEDIUM/STRONG), used only when AI Scene
    // Detection is off. When AI Scene Detection is on, it overrides this and manages strength
    // itself (LOW_LIGHT -> STRONG, else -> LOWER_LIGHT) — see PostProcessing.postProcessBitmap().
    public static final String NRStrengthPreferenceKey       = "preference_opencv_nr_strength";

    public static final String OpenCVCLAHEPreferenceKey     = "preference_opencv_clahe";
    // [REALCAMMI FORK] natural depth-of-field / portrait background blur, applied post-capture
    public static final String DepthBlurPreferenceKey       = "preference_depth_blur";
    // [REALCAMMI FORK 2026-08-01] Settings-based strength preset for the depth-of-field blur
    // (see DepthEffect.java setStrengthPreset()). Chosen before shooting, not live-previewed.
    public static final String DepthBlurStrengthPreferenceKey = "preference_depth_blur_strength";

    public static final String OpenCVBlurDetectPreferenceKey = "preference_opencv_blur_detect";


    public static final String PhotoModePreferenceKey = "preference_photo_mode";

    public static final String AutoHDRPreferenceKey = "preference_auto_hdr";
    public static final String AISceneDetectionPreferenceKey = "preference_ai_scene_detection"; // [REALCAMMI FORK]
    // [REALCAMMI FORK] Auto Exposure Correction: independent of AISceneDetectionPreferenceKey,
    // same pattern as AutoHDRPreferenceKey - applies -EV automatically on OVEREXPOSED scenes.
    public static final String AutoExposureCorrectionPreferenceKey = "preference_auto_exposure_correction";
    // [REALCAMMI FORK] Auto Night Mode: independent of AISceneDetectionPreferenceKey, same
    // extension-reopen pattern as AutoHDRPreferenceKey - reopens in EXTENSION_NIGHT on LOW_LIGHT.
    public static final String AutoNightModePreferenceKey = "preference_auto_night_mode";
    // [REALCAMMI FORK] Internal-only marker (no Settings UI entry), not a user preference. Set
    // true right when an Auto HDR/Night software fallback switches PhotoMode away from Standard,
    // cleared back to false once it successfully restores PhotoMode to Standard. Checked once on
    // MainActivity.onResume() - if found true there, the previous fallback cycle never completed
    // (app was killed/backgrounded mid-flow), so PhotoMode is force-reset to Standard and this
    // marker cleared, instead of leaving the user silently stuck in HDR/NoiseReduction mode.
    public static final String AutoFallbackPendingPreferenceKey = "internal_auto_fallback_pending";
    // [REALCAMMI FORK] Internal-only marker (no Settings UI entry). Stashes the user's own
    // "Expo Bracketing N Images" value while the Auto HDR software fallback is engaged, so it
    // can force exactly 3 images (the only count ImageSaver's HDR merge supports - see
    // ImageSaver.saveImageNow()'s "expected either 1 or 3 images for hdr" check) without
    // permanently overwriting the user's own preference for manual Expo Bracketing mode.
    public static final String AutoHDRFallbackPrevNImagesPreferenceKey = "internal_auto_hdr_fallback_prev_n_images";


    public static final String HDRSaveExpoPreferenceKey = "preference_hdr_save_expo";

    public static final String HDRTonemappingPreferenceKey = "preference_hdr_tonemapping";

    public static final String HDRContrastEnhancementPreferenceKey = "preference_hdr_contrast_enhancement";

    public static final String NRSaveExpoPreferenceKey = "preference_nr_save";

    public static final String FastBurstNImagesPreferenceKey = "preference_fast_burst_n_images";

    // [REALCAMMI FORK 2026-09-18] "Best Shot" - only takes effect in Standard photo mode (see
    // MyApplicationInterface.getBestShotPref()). Off by default: this quietly reuses the FastBurst
    // capture mechanism to take a few quick frames instead of one, then keeps only the sharpest
    // and discards the rest - see onPictureTaken()'s handleBestShotFrame() for the full design.
    // Unlike this fork's earlier fixes (calibrating existing, already-working code), this is new
    // capture-path logic that has not been tested on real hardware - treat with extra caution and
    // verify carefully (photo count, timing, memory) before trusting it, and this preference is
    // the way to fully disable it if anything looks wrong.
    public static final String BestShotPreferenceKey = "preference_best_shot";
    public static final int BestShotNImages = 3;

    public static final String LocationPreferenceKey = "preference_location";

    public static final String RemoveDeviceExifPreferenceKey = "preference_remove_device_exif";

    public static final String GPSDirectionPreferenceKey = "preference_gps_direction";

    public static final String RequireLocationPreferenceKey = "preference_require_location";

    public static final String ExifArtistPreferenceKey = "preference_exif_artist";

    public static final String ExifCopyrightPreferenceKey = "preference_exif_copyright";

    public static final String StampPreferenceKey = "preference_stamp";

    public static final String StampDateFormatPreferenceKey = "preference_stamp_dateformat";

    public static final String StampTimeFormatPreferenceKey = "preference_stamp_timeformat";

    public static final String StampGPSFormatPreferenceKey = "preference_stamp_gpsformat";

    //public static final String StampGeoAddressPreferenceKey = "preference_stamp_geo_address";

    public static final String UnitsDistancePreferenceKey = "preference_units_distance";

    public static final String TextStampPreferenceKey = "preference_textstamp";

    public static final String StampFontSizePreferenceKey = "preference_stamp_fontsize";

    public static final String StampFontColorPreferenceKey = "preference_stamp_font_color";

    public static final String StampStyleKey = "preference_stamp_style";

    public static final String VideoSubtitlePref = "preference_video_subtitle";

    public static final String FrontCameraMirrorKey = "preference_front_camera_mirror";

    public static final String EnableRemote = "preference_enable_remote";

    public static final String RemoteName = "preference_remote_device_name";

    public static final String RemoteType = "preference_remote_type";

    public static final String RemoteVideoMode = "preference_remote_video_mode";

    public static final String WaterType = "preference_water_type";

    //public static final String BackgroundPhotoSavingPreferenceKey = "preference_background_photo_saving";

    public static final String Camera2FakeFlashPreferenceKey = "preference_camera2_fake_flash";

    public static final String Camera2DummyCaptureHackPreferenceKey = "preference_camera2_dummy_capture_hack";

    public static final String Camera2FastBurstPreferenceKey = "preference_camera2_fast_burst";

    public static final String Camera2PhotoVideoRecordingPreferenceKey = "preference_camera2_photo_video_recording";

    public static final String UIPlacementPreferenceKey = "preference_ui_placement";

    public static final String TouchCapturePreferenceKey = "preference_touch_capture";

    public static final String PausePreviewPreferenceKey = "preference_pause_preview";

    public static final String ShowToastsPreferenceKey = "preference_show_toasts";

    public static final String ThumbnailAnimationPreferenceKey = "preference_thumbnail_animation";

    public static final String TakePhotoBorderPreferenceKey = "preference_take_photo_border";

    public static final String DimWhenDisconnectedPreferenceKey = "preference_remote_disconnect_screen_dim";

    public static final String AllowHapticFeedbackPreferenceKey = "preference_allow_haptic_feedback";

    public static final String ShowWhenLockedPreferenceKey = "preference_show_when_locked";

    public static final String AllowLongPressPreferenceKey = "preference_allow_long_press";

    public static final String StartupFocusPreferenceKey = "preference_startup_focus";

    public static final String MultiCamButtonPreferenceKey = "preference_multi_cam_button";

    public static final String KeepDisplayOnPreferenceKey = "preference_keep_display_on";

    public static final String MaxBrightnessPreferenceKey = "preference_max_brightness";

    public static final String UsingSAFPreferenceKey = "preference_using_saf";

    public static final String SaveLocationPreferenceKey = "preference_save_location";

    public static final String SaveLocationSAFPreferenceKey = "preference_save_location_saf";

    public static final String SaveLocationHistoryBasePreferenceKey = "save_location_history";

    public static final String SaveLocationHistorySAFBasePreferenceKey = "save_location_history_saf";

    public static final String SavePhotoPrefixPreferenceKey = "preference_save_photo_prefix";

    public static final String SaveVideoPrefixPreferenceKey = "preference_save_video_prefix";

    public static final String SaveZuluTimePreferenceKey = "preference_save_zulu_time";

    public static final String SaveIncludeMillisecondsPreferenceKey = "preference_save_include_milliseconds";

    public static final String ShowZoomSliderControlsPreferenceKey = "preference_show_zoom_slider_controls";

    public static final String ShowTakePhotoPreferenceKey = "preference_show_take_photo";

    public static final String ShowFaceDetectionPreferenceKey = "preference_show_face_detection";

    public static final String ShowCycleLockOrientationPreferenceKey = "preference_show_cycle_lock_orientation";

    public static final String ShowPreviewShotsPreferenceKey = "preference_show_preview_shots";

    public static final String ShowCycleFlashPreferenceKey = "preference_show_cycle_flash";

    public static final String ShowFocusPeakingPreferenceKey = "preference_show_focus_peaking";

    public static final String ShowAutoLevelPreferenceKey = "preference_show_auto_level";

    public static final String ShowStampPreferenceKey = "preference_show_stamp";

    public static final String ShowTextStampPreferenceKey = "preference_show_textstamp";

    public static final String ShowStoreLocationPreferenceKey = "preference_show_store_location";

    public static final String ShowCycleRawPreferenceKey = "preference_show_cycle_raw";

    public static final String ShowColorCorrectionPreferenceKey = "preference_show_color_correction";

    // [REALCAMMI FORK] OpenCV feature show-icon keys
    public static final String ShowOpenCVSharpenPreferenceKey    = "preference_show_opencv_sharpen";
    public static final String ShowOpenCVNRPreferenceKey         = "preference_show_opencv_nr";
    public static final String ShowOpenCVCLAHEPreferenceKey      = "preference_show_opencv_clahe";
    public static final String ShowOpenCVBlurDetectPreferenceKey = "preference_show_opencv_blur_detect";
    public static final String ShowDepthBlurPreferenceKey        = "preference_show_depth_blur";

    public static final String ShowWhiteBalanceLockPreferenceKey = "preference_show_white_balance_lock";

    public static final String ShowExposureLockPreferenceKey = "preference_show_exposure_lock";

    public static final String ShowZoomPreferenceKey = "preference_show_zoom";

    public static final String ShowISOPreferenceKey = "preference_show_iso";

    public static final String HistogramPreferenceKey = "preference_histogram";

    public static final String ZebraStripesPreferenceKey = "preference_zebra_stripes";

    public static final String ZebraStripesForegroundColorPreferenceKey = "preference_zebra_stripes_foreground_color";

    public static final String ZebraStripesBackgroundColorPreferenceKey = "preference_zebra_stripes_background_color";

    public static final String FocusPeakingPreferenceKey = "preference_focus_peaking";

    public static final String FocusPeakingColorPreferenceKey = "preference_focus_peaking_color";

    public static final String PreShotsPreferenceKey = "preference_save_preshots";

    public static final String ShowVideoMaxAmpPreferenceKey = "preference_show_video_max_amp";

    public static final String ShowAnglePreferenceKey = "preference_show_angle";

    public static final String ShowAngleLinePreferenceKey = "preference_show_angle_line";

    public static final String ShowPitchLinesPreferenceKey = "preference_show_pitch_lines";

    public static final String ShowGeoDirectionLinesPreferenceKey = "preference_show_geo_direction_lines";

    public static final String ShowAngleHighlightColorPreferenceKey = "preference_angle_highlight_color";

    public static final String CalibratedLevelAnglePreferenceKey = "preference_calibrate_level_angle";

    public static final String ShowGeoDirectionPreferenceKey = "preference_show_geo_direction";

    public static final String ShowFreeMemoryPreferenceKey = "preference_free_memory";

    public static final String ShowTimePreferenceKey = "preference_show_time";

    public static final String ShowCameraIDPreferenceKey = "preference_show_camera_id";

    public static final String ShowBatteryPreferenceKey = "preference_show_battery";

    public static final String ShowGridPreferenceKey = "preference_grid";

    public static final String ShowCropGuidePreferenceKey = "preference_crop_guide";

    public static final String FaceDetectionPreferenceKey = "preference_face_detection";

    public static final String GhostImagePreferenceKey = "preference_ghost_image";

    public static final String GhostSelectedImageSAFPreferenceKey = "preference_ghost_selected_image_saf";

    public static final String GhostImageAlphaPreferenceKey = "ghost_image_alpha";

    public static final String VideoStabilizationPreferenceKey = "preference_video_stabilization";

    public static final String ForceVideo4KPreferenceKey = "preference_force_video_4k";

    public static final String VideoFormatPreferenceKey = "preference_video_output_format";

    public static final String VideoBitratePreferenceKey = "preference_video_bitrate";

    public static String getVideoFPSPreferenceKey(int cameraId, String cameraIdSPhysical) {
        // for cameraId==0 and cameraIdSPhysical==null, we return preference_video_fps instead of preference_video_fps_0, for
        // backwards compatibility for people upgrading
        return "preference_video_fps" + ((cameraId==0 && cameraIdSPhysical==null) ? "" : ("_"+getCameraIDKey(cameraId, cameraIdSPhysical)));
    }

    public static String getVideoCaptureRatePreferenceKey(int cameraId, String cameraIdSPhysical) {
        return "preference_capture_rate_" + getCameraIDKey(cameraId, cameraIdSPhysical);
    }

    public static final String VideoLogPreferenceKey = "preference_video_log";

    public static final String VideoProfileGammaPreferenceKey = "preference_video_profile_gamma";

    /** [REALCAMMI FORK 2026-09-02] Blend strength (0-100%) for the Image Profile curve
     *  (Rec709/sRGB/Log/Gamma/S-Log3/JT*) applied post-capture in PostProcessing.applyImageProfileMat().
     *  0 = profile fully OFF (identical visual result to TONEMAPPROFILE_OFF, short-circuited before
     *  the LUT is even built); 100 = the profile's full, unblended effect.
     *  [REALCAMMI FIX 2026-09-15] Corrected this comment, which used to claim 100 was "the
     *  default, so existing users see no change until they adjust this" - that was the actually
     *  outdated part, not the "10" value below. Confirmed with the author: 10 is the intentional
     *  default - the full 100% effect looks exaggerated/overpowering out of the box, so this
     *  starts subtle and the user raises it to taste via the Settings slider.
     */
    public static final String ImageProfileStrengthPreferenceKey = "preference_image_profile_strength";
    public static final String ImageProfileStrengthDefault = "10";

    /** [REALCAMMI FORK 2026-09-04] Blend strength (0-100%) for the optional "Sharpening (OpenCV)"
     *  effect (PostProcessing.applyOpenCVSharpenMat()) - separate from the always-on baseline
     *  sharpen, which this does not affect. 0 = no extra sharpening from this step (the on-screen
     *  toggle can still be left on with no visible effect); 100 = today's existing full effect
     *  (including the scene-adaptive indoor boost).
     *  [REALCAMMI FIX 2026-09-15] Same correction as ImageProfileStrengthDefault above: 10 is the
     *  intentional default (100 looks exaggerated/overpowering straight away), not a leftover bug.
     */
    public static final String SharpnessStrengthPreferenceKey = "preference_sharpness_strength";
    public static final String SharpnessStrengthDefault = "10";

    public static final String VideoMaxDurationPreferenceKey = "preference_video_max_duration";

    public static final String VideoRestartPreferenceKey = "preference_video_restart";

    public static final String VideoMaxFileSizePreferenceKey = "preference_video_max_filesize";

    public static final String VideoRestartMaxFileSizePreferenceKey = "preference_video_restart_max_filesize";

    public static final String VideoFlashPreferenceKey = "preference_video_flash";

    public static final String VideoLowPowerCheckPreferenceKey = "preference_video_low_power_check";

    public static final String LockVideoPreferenceKey = "preference_lock_video";

    public static final String RecordAudioPreferenceKey = "preference_record_audio";

    public static final String RecordAudioChannelsPreferenceKey = "preference_record_audio_channels";

    public static final String RecordAudioSourcePreferenceKey = "preference_record_audio_src";

    public static final String PreviewSizePreferenceKey = "preference_preview_size";

    public static final String RotatePreviewPreferenceKey = "preference_rotate_preview";

    public static final String LockOrientationPreferenceKey = "preference_lock_orientation";

    public static final String TimerPreferenceKey = "preference_timer";

    public static final String TimerBeepPreferenceKey = "preference_timer_beep";

    public static final String TimerSpeakPreferenceKey = "preference_timer_speak";

    // note for historical reasons the preference refers to burst; the feature was renamed to
    // "repeat" in v1.43, but we still need to use the old string to avoid changing user settings
    // when people upgrade
    public static final String RepeatModePreferenceKey = "preference_burst_mode";

    // see note about "repeat" vs "burst" under RepeatModePreferenceKey
    public static final String RepeatIntervalPreferenceKey = "preference_burst_interval";

    public static final String ShutterSoundPreferenceKey = "preference_shutter_sound";

    public static final String ImmersiveModePreferenceKey = "preference_immersive_mode";
    public static final String AddYPRToComments="preference_comment_ypr";

    public static final String GalleryPreferenceKey = "preference_gallery";
}
