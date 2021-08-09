package com.example.camerax.preferences;


import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

public class Preferences {
    private static Preferences mInstance;
    private final SharedPreferences mSharedPreferences;

    public Preferences(Context context) {
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static synchronized Preferences getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new Preferences(context);
        }
        return mInstance;
    }
/*
    public boolean hasSizes() {
        String s1 = mSharedPreferences.getString(Keys.PHOTO_SIZE_REAR, null);
        String s2 = mSharedPreferences.getString(Keys.PHOTO_SIZE_FRONT, null);
        String s3 = mSharedPreferences.getString(Keys.VIDEO_SIZE_FRONT, null);
        String s4 = mSharedPreferences.getString(Keys.VIDEO_SIZE_REAR, null);
        return s1 != null || s2 != null || s3 != null || s4 != null;
    }*/

    public boolean getBoolean(String key, boolean defValue) {
        return mSharedPreferences.getBoolean(key, defValue);
    }

//    public void resetSizes() {
//        mSharedPreferences.edit().clear().apply();
//    }

    public String getString(String key, String defValue) {
        return mSharedPreferences.getString(key, defValue);
    }

    public void putString(String key, String value) {
        mSharedPreferences.edit().putString(key, value).apply();
    }

    public SharedPreferences.Editor edit() {
        return mSharedPreferences.edit();
    }

    public static final class Keys {
        public static final String DATA_STORAGE = "dataStorage";
        public static final String PHOTO_SIZE_REAR = "photoSizeRear";
        public static final String PHOTO_SIZE_FRONT = "photoSizeFront";
        public static final String VIDEO_SIZE_REAR = "videoSizeRear";
        public static final String VIDEO_SIZE_FRONT = "videoSizeFront";
        public static final String PHOTO_MIRROR = "photoMirror";
        public static final String CAPTURE_HIGH_FPS = "captureHighFPS";
        public static final String GLIDE_LOAD_IMAGE = "glideLoadImage";
        public static final String FULL_SCREEN_PREVIEW = "fullScreenPreview";
        public static final String PHOTO_ORIENTATION_PROBLEM = "photoOrientationProblem";
    }
}
