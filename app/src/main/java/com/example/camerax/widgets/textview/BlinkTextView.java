package com.example.camerax.widgets.textview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.constraintlayout.widget.Placeholder;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import com.example.camerax.R;

public class BlinkTextView extends AppCompatTextView {
    /**
     * Paint object for draw blink circle
     */
    private final Paint blinkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /**
     * blink circle radius
     */
    private float   blinkRadius;
    /**
     * مدت زمان چشمک در حالت روشن
     */
    private int     blinkVisibleDuration;
    /**
     * مدت زمان چشمک در حالت خاموش
     */
    private int     blinkInVisibleDuration;
    /**
     * آیا دایره رسم شود
     */
    private boolean isDrawCircle = false;

    /**
     * این runnable هر بار مقدار isDrawCircle را تغییر داده و سپس تابع invalidate را فراخوانده تا عمل رسم دوباره انجام شود
     */
    private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            isDrawCircle = !isDrawCircle;
            invalidate();
            postDelayed(this, isDrawCircle ? blinkVisibleDuration : blinkInVisibleDuration);
        }
    };

    /**
     * Right PlaceHolder
     */
    private Placeholder rp;
    /**
     * Left PlaceHolder
     */
    private Placeholder lp;

    /**
     * blink gravity
     */
    private int blinkGravity;

    public BlinkTextView(@NonNull Context context) {
        super(context);
    }

    public BlinkTextView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initialization(context, attrs);
    }

    public BlinkTextView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialization(context, attrs);
    }

    /**
     * مقدار دهی اولیه و دریافت مقادیر از xml
     */
    protected void initialization(Context context, AttributeSet attrs) {
        TypedArray typedArray     = context.obtainStyledAttributes(attrs, R.styleable.BlinkTextView);
        int        btv_blinkColor = typedArray.getColor(R.styleable.BlinkTextView_btv_blinkColor, Color.RED);
        blinkRadius            = typedArray.getDimension(R.styleable.BlinkTextView_btv_blinkRadius, 10);
        blinkVisibleDuration   = typedArray.getInt(R.styleable.BlinkTextView_btv_blinkVisibleDuration, 500);
        blinkGravity           = typedArray.getInt(R.styleable.BlinkTextView_btv_blinkGravity, 0);
        blinkInVisibleDuration = typedArray.getInt(R.styleable.BlinkTextView_btv_blinkInVisibleDuration, 250);
        typedArray.recycle();
        blinkPaint.setStyle(Paint.Style.FILL);
        blinkPaint.setColor(btv_blinkColor);
    }

    /**
     * تغییر visibility به همراه انیمیشن
     *
     * @param isVisible true for VISIBLE and false for INVISIBLE
     */
    public void setVisibility(boolean isVisible) {
        if (isVisible) {
            setAlpha(0);
            setScaleX(0.8F);
            setScaleY(0.8F);
            setVisibility(View.VISIBLE);
            animate()
                    .alpha(1)
                    .scaleX(1)
                    .scaleY(1)
                    .setDuration(300)
                    .setInterpolator(new FastOutSlowInInterpolator())
                    .withEndAction(this::startBlink)
                    .start();
        } else {
            animate()
                    .alpha(0)
                    .scaleX(0.8F)
                    .scaleY(0.8F)
                    .setInterpolator(new FastOutSlowInInterpolator())
                    .setDuration(300)
                    .withStartAction(this::stopBlink)
                    .withEndAction(() -> {
                        setVisibility(INVISIBLE);
                        reset();
                    })
                    .start();
        }
    }

    /**
     * چشمک زدن را آغاز کن
     */
    private void startBlink() {
        runnable.run();
    }

    /**
     * چشمک زدن را متوقف کن
     */
    private void stopBlink() {
        removeCallbacks(runnable);
        isDrawCircle = false;
        invalidate();
    }

    /**
     * در صورتی که isDrawCircle == true باشد دایره را رسم می کند
     */
    @Override
    public void onDrawForeground(Canvas canvas) {
        super.onDrawForeground(canvas);
        if (isDrawCircle) {
            canvas.drawCircle(blinkGravity == 0 ? 0 + blinkRadius : getWidth(), getHeight() / 2F, blinkRadius, blinkPaint);
        }
    }

    /**
     * مدت زمانی پاس داده شده برحسب میلی ثانیه را در قالب 00:00 به عنوان متن تنظیم می کند
     *
     * @param duration milliSecond
     */
    @SuppressLint("SetTextI18n")
    public void updateDurationTextView(long duration) {
        int seconds = (int) (duration / 1000);
        int min     = seconds / 60;
        seconds -= min * 60;
        String s;

        if (min > 9) {
            s = String.valueOf(min);
        } else {
            s = "0" + min;
        }

        s += " : ";

        if (seconds > 9) {
            s += String.valueOf(seconds);
        } else {
            s += "0" + seconds;
        }

        setText(s);
    }

    /**
     * متن را بازنشانی کند
     */
    @SuppressLint("SetTextI18n")
    public void reset() {
        setText("00 : 00");
    }

    /**
     * Setter for rp and lp
     *
     * @param rp right PlaceHolder
     * @param lp left PlaceHolder
     */
    public void setPlaceHolders(Placeholder rp, Placeholder lp) {
        this.rp = rp;
        this.lp = lp;
    }

    /**
     * چرخش صفحه نمایش را گرفته و مکان را به طور هوشمند و با توجه به rp, lp تنظیم میکند
     *
     * @param rotationDegrees rotation degrees
     */
    public void syncLocation(int rotationDegrees) {
        int v = getVisibility();
        setTranslationX(0);
        switch (rotationDegrees) {
            case 0: {
                setPivotY(getHeight() / 2F);
                setPivotX(getWidth() / 2F);
                rp.setContentId(-1);
                lp.setContentId(-1);
                setRotation(0);
                break;
            }
            case 90: {
                setPivotY(getHeight());
                setPivotX(getWidth() - getHeight());
                setRotation(90);
                rp.setContentId(getId());
                break;
            }
            case 180: {
                setPivotY(getHeight() / 2F);
                setPivotX(getWidth() / 2F);
                rp.setContentId(-1);
                lp.setContentId(-1);
                setRotation(180);
                break;
            }
            case 270: {
                setPivotY(0);
                setPivotX(getWidth() - getHeight());
                setTranslationX(-(getWidth() - getHeight()));
                rp.setContentId(-1);
                setRotation(-90);
                lp.setContentId(getId());
                break;
            }
        }
        requestLayout();
        if (v == View.INVISIBLE) {
            setVisibility(false);
        }
    }
}