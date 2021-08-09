package com.example.camerax.widgets.layout;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.camerax.R;

/**
 * این نما قابلیت رسم افکت عکاسی را داراست
 */
public class FLayout extends FrameLayout {
    /**
     * Paint instance for draw effect
     */
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /**
     * true value for draw effect
     */
    boolean draw = false;
    /**
     * حداکثر اندازه stroke
     */
    private int maxStrokeWidth;
    /**
     * مدت زمان اجرای انیمیشن
     */
    private int effectDuration;
    /**
     * stroke width
     */
    private int strokeWidth = 0;

    private ValueAnimator animator;

    public FLayout(@NonNull Context context) {
        super(context);
        init();
    }

    public FLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initialization(context, attrs);
    }

    public FLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialization(context, attrs);
    }


    /**
     * مقداردهی اولیه و دریافت مقادیر از xml
     */
    private void initialization(Context context, AttributeSet attrs) {
        init();
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.FLayout);
        maxStrokeWidth = typedArray.getDimensionPixelSize(R.styleable.FLayout_effectWidth, 20);
        effectDuration = typedArray.getInt(R.styleable.FLayout_effectDuration, 150);
        paint.setColor(typedArray.getColor(R.styleable.FLayout_effectColor, Color.WHITE));
        typedArray.recycle();
    }

    /**
     * مقداردهی اولیه
     */
    private void init() {
        setWillNotDraw(false);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0);
    }

    /**
     * جلوه رو رسم کند و action را اجرا کن
     *
     * @param withStartAction startAction
     */
    public void startTakePictureEffect(Runnable withStartAction) {
        if (animator != null) {
            animator.removeAllUpdateListeners();
            animator.removeAllListeners();
            animator = null;
        }
        draw     = true;
        animator = ValueAnimator.ofInt(0, maxStrokeWidth);
        animator.setDuration(effectDuration);
        animator.setInterpolator(new OvershootInterpolator());
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                if (withStartAction != null) {
                    withStartAction.run();
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                draw = false;
            }
        });
        animator.addUpdateListener(animation -> {
            strokeWidth = (int) animation.getAnimatedValue();
            paint.setStrokeWidth(strokeWidth);
            invalidate();
        });
        animator.start();
    }

    @Override
    public void onDrawForeground(Canvas canvas) {
        super.onDrawForeground(canvas);
        if (draw) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        }
    }
}