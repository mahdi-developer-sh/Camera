package com.example.camerax;

import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.OrientationEventListener;
import android.view.Surface;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.camerax.util.CameraUtil;

public abstract class Act_Sensor extends AppCompatActivity {
    private int lastRotation = Surface.ROTATION_0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OrientationEventListener listener = new OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {
            private int r;

            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation <= 45) {
                    r = Surface.ROTATION_0;
                } else if (orientation <= 135) {
                    r = Surface.ROTATION_90;
                } else if (orientation <= 225) {
                    r = Surface.ROTATION_180;
                } else if (orientation <= 315) {
                    r = Surface.ROTATION_270;
                }
                if (lastRotation != r) {
                    lastRotation = r;
                    onChangeOrientation(lastRotation);
                }
            }
        };
        if (listener.canDetectOrientation()) {
            listener.enable();
        }
    }

    public int getInverseRotationDegrees() {
        int r = 360 - getRelativeRotationDegrees();
        return r == 360 ? 0 : r;
    }

    public int getRelativeRotationDegrees() {
        return CameraUtil.ROTATIONS.get(lastRotation);
    }

    public boolean isPortrait() {
        return lastRotation == Surface.ROTATION_0 || lastRotation == Surface.ROTATION_180;
    }

    protected abstract void onChangeOrientation(int surfaceRotation);
}