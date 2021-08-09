package com.example.camerax.widgets;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.example.camerax.R;

/**
 * انیمیشن چرخش را بر روی تمامی نماهایی که شناسه آن ها در ids باشد اجرا میکند
 */
public class Group extends View {
    /**
     * شناسه هایی که باید انیمشن بپذیرند
     */
    private int[] ids;

    public Group(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initialization(context, attrs);
    }

    public Group(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialization(context, attrs);
    }

    /**
     * مقداردهی اولیه و دریافت از xml
     */
    private void initialization(Context context, AttributeSet attrs) {
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.Group);
        String     strIds     = typedArray.getString(R.styleable.Group_ids);
        if (strIds != null) {
            final String[]  array       = strIds.split(",");
            final String    packageName = context.getPackageName();
            final Resources resources   = context.getResources();

            ids = new int[array.length];
            for (int i = 0; i < array.length; ++i) {
                ids[i] = resources.getIdentifier(array[i], "id", packageName);
            }
        }
        typedArray.recycle();
    }

    private void setRotation(float rotation, boolean animate) {
        ViewGroup group = (ViewGroup) getRootView();
        if (!animate) {
            for (int i : ids) {
                group.findViewById(i).setRotation(rotation);
            }
            animate().rotation(0).setDuration(0).start();
        } else {
            for (int i : ids) {
                group.findViewById(i).animate().rotation(rotation).setDuration(200).start();
            }
            animate().rotation(rotation).setDuration(200).start();
            if (rotation == 360 || rotation == -90) {
                postDelayed(() -> setRotation(rotation == 360 ? 0 : 270, false), 200);
            }
        }
    }

    private float getSafeRotation(float rotation) {
        float r = getRotation();
        if (r == 0) {
            if (rotation == 270) {
                rotation = -90;
            }
        } else if (r == 270) {
            if (rotation == 0) {
                rotation = 360;
            }
        }
        return rotation;
    }

    @Override
    public void setRotation(float rotation) {
        setRotation(getSafeRotation(rotation), true);
    }

    /**
     * این نما جنبه نمایشی ندارد پس عرض و ارتفاع را برابر با 0 قرار میدهم
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(0, 0);
    }

    @Override
    public void setLayoutParams(ViewGroup.LayoutParams params) {
        params.width  = 0;
        params.height = 0;
        super.setLayoutParams(params);
    }
}