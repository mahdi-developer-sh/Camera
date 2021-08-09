package com.example.camerax.widgets.layout;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

public class CircularRevealLayout extends ConstraintLayout {
    private final Paint         paint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path          clipPath = new Path();
    private       boolean       isClear  = false;
    private       int           radius   = 0;
    private       ValueAnimator valueAnimator;
    private       float         cx;
    private       float         cy;

    public CircularRevealLayout(@NonNull Context context) {
        super(context);
        init();
    }

    public CircularRevealLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initialization(context, attrs);
    }

    public CircularRevealLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialization(context, attrs);
    }

    public void initialization(Context context, AttributeSet attrs) {
        init();
    }

    public void init() {
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.FILL);
        setWillNotDraw(false);
    }

    public void startClearCircularRipple(View view, Runnable withEndAction) {
        if (valueAnimator != null) {
            if (valueAnimator.isRunning()) {
                return;
            }
        }

        if (view != null) {
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            cx = location[0] + view.getWidth() / 2F;
            cy = location[1] + view.getHeight() / 2F;
        } else {
            cx = getWidth() / 2F;
            cy = getHeight() / 2F;
        }

        valueAnimator = ValueAnimator.ofInt((int) (Math.max(getWidth(), getHeight()) * 1.2F), 0);
        valueAnimator.setInterpolator(new FastOutSlowInInterpolator());
        valueAnimator.setDuration(500);
        valueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                radius = 0;
                update();
                isClear = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                radius = 0;
                if (withEndAction != null) {
                    withEndAction.run();
                }
            }
        });

        valueAnimator.addUpdateListener(animation -> {
            radius = (int) animation.getAnimatedValue();
            update();
            invalidate();
        });

        valueAnimator.start();
    }

    private void update() {
        clipPath.reset();
        clipPath.addCircle(cx, cy, radius, Path.Direction.CW);
    }

    @Override
    public void draw(Canvas canvas) {
        if (isClear) {
            canvas.clipPath(clipPath);
        }
        super.draw(canvas);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (isClear) {
            canvas.clipPath(clipPath);
        }
        return super.drawChild(canvas, child, drawingTime);
    }
}