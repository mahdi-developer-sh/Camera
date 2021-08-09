package com.example.camerax.widgets.rippleview;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import com.example.camerax.R;

public abstract class RippleView extends View implements View.OnTouchListener {
    private final Paint ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint solidPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);

    /**
     * آیا شکل solid رسم شود
     */
    private boolean solidDrawFlag  = false;
    /**
     * آیا شکل ripple رسم شود
     */
    private boolean rippleDrawFlag = false;

    /**
     * اندازه رسم شکل ها نسبت به اندازه نما
     */
    private float radiusRatio;
    /**
     * مدت زمان رسم ripple
     */
    private int   rippleDuration;
    /**
     * شعاع مربوط به رسم دایره
     */
    private float radius;
    /**
     * رنگ شکل رسم شده به صورت سخت یا solid
     */
    private int   solidColor;

    /**
     * رنگ رسم ripple
     */
    private int rippleColor;

    private ValueAnimator valueAnimator;

    public RippleView(Context context) {
        super(context);
        solidColor  = Color.BLACK;
        rippleColor = Color.RED;
        radiusRatio = 0.8F;
        init();
    }

    public RippleView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initialization(context, attrs);
    }

    public RippleView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialization(context, attrs);
    }

    /**
     * base init
     */
    @CallSuper
    protected void init() {
        ripplePaint.setColor(rippleColor);
        solidPaint.setColor(solidColor);
        setOnTouchListener(this);
    }

    /**
     * مقداردهی اولیه و دریافت مقادیر از xml
     */
    @CallSuper
    protected void initialization(Context context, AttributeSet attrs) {
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.RippleView);
        rippleColor    = typedArray.getColor(R.styleable.RippleView_rv_rippleColor, Color.BLACK);
        solidColor     = typedArray.getColor(R.styleable.RippleView_rv_solidColor, Color.BLACK);
        rippleDuration = typedArray.getInt(R.styleable.RippleView_rv_rippleDuration, 200);
        radiusRatio    = typedArray.getFloat(R.styleable.RippleView_rv_radiusDelta, 0.8F);
        typedArray.recycle();
        init();
    }

    /**
     * خب ripple رو رسم کن
     *
     * @param fromRatio    شروع از نسبت ابعاد
     * @param stickyRipple آیا این شکل ماندگار باشد
     */
    public void drawRipple(float fromRatio, boolean stickyRipple) {
        if (valueAnimator != null) {
            valueAnimator.removeAllUpdateListeners();
            valueAnimator.removeAllListeners();
            valueAnimator = null;
        }
        float baseRadius = getBaseRadius();
        valueAnimator = ValueAnimator.ofFloat(fromRatio * baseRadius, radiusRatio * baseRadius);
        valueAnimator.setInterpolator(new FastOutSlowInInterpolator());
        valueAnimator.setDuration(rippleDuration);
        valueAnimator.addUpdateListener(animation -> {
            radius = (float) animation.getAnimatedValue();
            invalidate();
        });
        valueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                ripplePaint.setAlpha(Color.alpha(rippleColor));
                rippleDrawFlag = true;
                super.onAnimationStart(animation);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!stickyRipple) {
                    rippleDrawFlag = false;
                }
                super.onAnimationEnd(animation);
            }
        });
        valueAnimator.start();
    }

    /**
     * حداکثر مقدار قابل قبول برای radius
     *
     * @return float value
     */
    protected float getBaseRadius() {
        return Math.max(getWidth(), getHeight()) / 2F;
    }

    /**
     * محاسبه radius با توجه به مقدار radiusRatio
     *
     * @return radius
     */
    protected float computeRadius() {
        return getBaseRadius() * radiusRatio;
    }

    /**
     * رسم شکل solid یا ripple در صورت وجود شرایط
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (solidDrawFlag) {
            canvas.drawCircle(getWidth() / 2F, getHeight() / 2F, computeRadius(), solidPaint);
        }
        if (rippleDrawFlag) {
            canvas.drawCircle(getWidth() / 2F, getHeight() / 2F, radius, ripplePaint);
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        return false;
    }

    /**
     * دایره solid را رسم کن
     */
    protected void drawSolidCircle() {
        solidDrawFlag = true;
        invalidate();
    }

    /**
     * رسم solid را پاک کن
     */
    protected void clearSolidCircle() {
        solidDrawFlag = false;
        invalidate();
    }

    /**
     * خب ripple رو پاک کن (:
     */
    protected void clearRipple() {
        if (valueAnimator != null) {
            valueAnimator.removeAllListeners();
            valueAnimator.removeAllUpdateListeners();
            valueAnimator = null;
        }
        valueAnimator = ValueAnimator.ofInt(Color.alpha(rippleColor), 0).setDuration(100);
        valueAnimator.setInterpolator(new LinearInterpolator());
        valueAnimator.addUpdateListener(animation -> {
            ripplePaint.setAlpha((Integer) animation.getAnimatedValue());
            invalidate();
        });
        valueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                rippleDrawFlag = false;
                invalidate();
            }
        });
        valueAnimator.start();
    }
}