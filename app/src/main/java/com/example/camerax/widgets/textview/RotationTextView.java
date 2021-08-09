package com.example.camerax.widgets.textview;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

public class RotationTextView extends androidx.appcompat.widget.AppCompatTextView {
    public RotationTextView(Context context) {
        super(context);
    }

    public RotationTextView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public RotationTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void syncRotation(int degrees) {
        setPivotY(getY() + getHeight() / 2F);
        if (degrees == 0 || degrees == 90) {
            setPivotX(getX());
        } else {
            setPivotX(getX() + getWidth() / 2F);
        }
        setRotation(degrees);
    }
}
