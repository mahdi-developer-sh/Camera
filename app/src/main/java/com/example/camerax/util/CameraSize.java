package com.example.camerax.util;

import android.os.Build;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.Locale;

public class CameraSize implements Serializable {
    public static final char DIVIDER_UNDERLINE = '_';
    public static final char DIVIDER_MULTIPLE  = 'x';

    public int width;
    public int height;

    public CameraSize(int width, int height) {
        this.width  = width;
        this.height = height;
    }

    public CameraSize(CameraSize cameraSize) {
        this.width  = cameraSize.width;
        this.height = cameraSize.height;
    }

    public static CameraSize fromString(String s) {
        int underlinePosition = s.indexOf(DIVIDER_UNDERLINE);
        int w                 = Integer.parseInt(s.substring(0, underlinePosition));
        int h                 = Integer.parseInt(s.substring(underlinePosition + 1));
        return new CameraSize(w, h);
    }

    public boolean equal(CameraSize cameraSize) {
        return getSum() == cameraSize.getSum();
    }

    public float getMillionPixels() {
        return height * width / 1000000F;
    }

    public CameraSize swap() {
        width  = width + height;
        height = width - height;
        width  = width - height;
        return this;
    }

    public int getSum() {
        return width + height;
    }

    public String getResolutionName() {
        if (isHD()) {
            return "HD";
        } else if (isFHD()) {
            return "FHD";
        } else if (isVGA()) {
            return "VGA";
        } else if (isQVGA()) {
            return "QVGA";
        } else if (width == height) {
            return "Square";
        }
        return "";
    }

    public boolean isHD() {
        return getSum() == 1280 + 720;
    }

    public boolean isFHD() {
        return getSum() == 1920 + 1080;
    }

    public boolean isVGA() {
        return getSum() == 640 + 480;
    }

    public boolean isQVGA() {
        return getSum() == 320 + 240;
    }

    public String getResolutionLabel(boolean isPortrait) {
        if (isPortrait) {
            swap();
        }
        String label = "REC";
        if (isHD()) {
            label += " | HD";
        } else if (isFHD()) {
            label += " | FHD";
        } else if (isVGA()) {
            label += " | VGA";
        } else if (isQVGA()) {
            label += " | QVGA";
        } else if (width == height) {
            label += " | Square 1:1 (" + width + ")";
        } else {
            label += " | " + width + " x " + height;
        }
        return label;
    }

    @NonNull
    @Override
    public String toString() {
        return width + String.valueOf(DIVIDER_UNDERLINE) + height;
    }

    public String toCompleteString(boolean swap, boolean showResolutionName) {
        DecimalFormat decimalFormat = new DecimalFormat("#.##");

        float mp = getMillionPixels();

        String s = "%4d " + DIVIDER_MULTIPLE + " %4d";

        if (swap) {
            s = String.format(Locale.US, s, height, width);
        } else {
            s = String.format(Locale.US, s, width, height);
        }

        s += " (" + decimalFormat.format(mp) + ")";

        if (showResolutionName) {
            s = s + " " + getResolutionName();
        }

        return s;
    }

    public String toSwapString() {
        return height + String.valueOf(DIVIDER_UNDERLINE) + width;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public Size toSize() {
        return new Size(width, height);
    }

    public String toString(String divider) {
        return String.format(Locale.US, "%3d" + divider + "%3d", height, width);
//        return height + divider + width;
    }
}