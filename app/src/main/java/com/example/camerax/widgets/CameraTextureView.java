package com.example.camerax.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.TextureView;
import android.view.View;

public class CameraTextureView extends TextureView {
    /**
     * نسبت عرض
     */
    private int mRatioWidth  = 0;
    /**
     * نسبت ارتفاع
     */
    private int mRatioHeight = 0;

    /**
     * آیا پیش نمایش به صورت تمام صفحه باشد
     */
    private boolean isFullScreen;

    public CameraTextureView(Context context) {
        super(context);
    }

    public CameraTextureView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CameraTextureView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * نسبت ابعاد را تنظیم می کند. اندازه نما بر اساس نسبت محاسبه شده از پارامتر ها اندازه گیری می شود.
     *
     * @param width  Relative horizontal size
     * @param height Relative vertical size
     */
    public void setAspectRatio(int width, int height, boolean isFullScreen) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        mRatioWidth       = width;
        mRatioHeight      = height;
        this.isFullScreen = isFullScreen;
        requestLayout();
    }

    /**
     * زمانی که بخواهیم بین دوربین جلو و عقب جابه جا شویم این از این تابع برای اجرای انیمیشن
     * استفاده می کند.
     *
     * @param action runnable
     */
    public void startSwitchCameraAnimation(Runnable action) {
        animate().alpha(0).withEndAction(() -> {
            action.run();
            animate().alpha(1);
        });
    }

    /**
     * تنظیم مقیاس ها بر اساس نسبت ابعاد محاسبه شده در تابع setAspectRatio
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        /*
         * پیش نمایش تمام صفحه
         * */
        int width  = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        if (0 == mRatioWidth || 0 == mRatioHeight) {
            setMeasuredDimension(width, height);
        } else {
            if (isFullScreen) {
                if (width < height * mRatioWidth / mRatioHeight) {
                    setMeasuredDimension(height * mRatioWidth / mRatioHeight, height);
                } else {
                    setMeasuredDimension(width, width * mRatioHeight / mRatioWidth);
                }
            } else {
                /*
                 * پیش نمایش با نسبت ابعاد اصلی خروجی دوربین
                 * */
                if (width < (height * mRatioWidth / mRatioHeight)) {
                    setMeasuredDimension(width, width * mRatioHeight / mRatioWidth);
                } else {
                    setMeasuredDimension(height * mRatioWidth / mRatioHeight, height);
                }
            }
        }
        /*
         * تنظیم نما در مرکز صفحه
         * */
        post(() -> {
            View view = (View) getParent();
            if (view != null) {
                setY((view.getHeight() - getHeight()) / 2F);
                setX((view.getWidth() - getWidth()) / 2F);
            }
        });
    }
}