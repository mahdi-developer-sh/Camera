package com.example.camerax.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.SparseIntArray;
import android.view.Surface;

import com.example.camerax.fragments.abstraction.Frg_BaseCamera;

import java.text.DecimalFormat;

public class CameraUtil {
    // 6MP Default video bitrate
    public static final int VIDEO_BIT_RATE = 6 * 1024 * 1024;

    /**
     * اندازه های پشتیبانی شده عکس مربوط به دوربین جلو
     */
    public static CameraSize[] frontPhotoSizes;
    /**
     * اندازه های پشتیبانی شده عکس مربوط به دوربین عقب
     */
    public static CameraSize[] rearPhotoSizes;
    /**
     * اندازه های پشتیبانی شده فیلم مربوط به دوربین جلو
     */
    public static CameraSize[] frontVideoSizes;
    /**
     * اندازه های پشتیبانی شده فیلم مربوط به دوربین عقب
     */
    public static CameraSize[] rearVideoSizes;

    // rotations
    public static SparseIntArray ROTATIONS = new SparseIntArray();

    static {
        ROTATIONS.append(Surface.ROTATION_0, 0);
        ROTATIONS.append(Surface.ROTATION_90, 90);
        ROTATIONS.append(Surface.ROTATION_180, 180);
        ROTATIONS.append(Surface.ROTATION_270, 270);
    }

    /**
     * آیا سخت افزار دوربین جلو موجود است
     */
    public static boolean hasFrontCamera(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT);
    }

    /**
     * آیا سخت افزار فلش موجود است
     */
    public static boolean isSupportFlash(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
    }

    /**
     * این تابع CameraSize موجود در پارامتر خود را بین اندازه ها جستجو میکند اگر پیدا کرد true وگرنه false
     *
     * @param cameraSize CameraSize instance
     * @param isVideo    اندازه فیلم یا عکس
     * @param isRear     دوربین جلو یا عقب
     *
     * @return true for found and false for not found
     */
    public static boolean findCameraSize(CameraSize cameraSize, boolean isVideo, boolean isRear) {
        CameraSize[] sizes;
        if (isRear) {
            if (isVideo) {
                sizes = rearVideoSizes;
            } else {
                sizes = rearPhotoSizes;
            }
        } else {
            if (isVideo) {
                sizes = frontVideoSizes;
            } else {
                sizes = frontPhotoSizes;
            }
        }
        for (CameraSize s : sizes) {
            if (s.equal(cameraSize)) {
                return true;
            }
        }
        return false;
    }

    /**
     * این تابع ایندکس نمونه CameraSize پاس داده شده را در صورت وجود برمیگرداند
     * در غیر اینصورت 0 را برمیگرداند
     *
     * @param cameraSizes CameraSizes array
     * @param cameraSize  CameraSize
     *
     * @return index of cameraSize in cameraSizes
     */
    public static int indexOf(CameraSize[] cameraSizes, CameraSize cameraSize) {
        CameraSize s;
        for (int i = 0; i < cameraSizes.length; ++i) {
            s = cameraSizes[i];
            if (s.width == cameraSize.width && s.height == cameraSize.height) {
                return i;
            }
        }
        return -1;
    }

    public static CameraSize[] getCameraSizesArrayReference(int facing, boolean isPhoto) {
        if (facing == Frg_BaseCamera.CAMERA_FACING_FRONT) {
            if (isPhoto)
                return frontPhotoSizes;
            return frontVideoSizes;
        } else {
            if (isPhoto)
                return rearPhotoSizes;
            return rearVideoSizes;
        }
    }

    public static CharSequence[] toCharSequenceArray(CameraSize[] cameraSizes, boolean showMP, boolean showResolutionName) {
        CharSequence[] sequences = new CharSequence[cameraSizes.length];
        DecimalFormat  df        = new DecimalFormat("#.##");
        CameraSize     size;
        String         str;
        for (int i = 0; i < sequences.length; ++i) {
            size = cameraSizes[i];
            str  = size.toString(" x ");
            if (showMP) {
                str = str + " (" + (df.format(size.getMillionPixels())) + "MP) ";
                if (showResolutionName) {
                    str = str + size.getResolutionName();
                }
            } else {
                str = str + "  " + size.getResolutionName();
            }

            sequences[i] = str;
        }
        return sequences;
    }

    public static void releaseSizes() {
        frontPhotoSizes = null;
        rearPhotoSizes  = null;
        frontVideoSizes = null;
        rearVideoSizes  = null;
    }
}