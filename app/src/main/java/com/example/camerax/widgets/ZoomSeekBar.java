package com.example.camerax.widgets;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSeekBar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import com.example.camerax.R;

public class ZoomSeekBar extends ConstraintLayout implements SeekBar.OnSeekBarChangeListener, View.OnClickListener {
    private ImageView zoomOut, zoomIn;
    private AppCompatSeekBar seekBar;
    private int animationDuration;
    private int incrementValue;

    public ZoomSeekBar(Context context) {
        super(context);
        init();
    }

    public ZoomSeekBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initialization(context, attrs);
    }

    public ZoomSeekBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialization(context, attrs);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            show();
            postHide();
        }
        return super.onTouchEvent(event);
    }

    private void init() {
        inflate(getContext(), R.layout.zoomseekbar, this);
        zoomIn = findViewById(R.id.zoomIn);
        zoomOut = findViewById(R.id.zoomOut);
        zoomIn.setOnClickListener(this);
        zoomOut.setOnClickListener(this);
        seekBar = findViewById(R.id.seekBar);
        setBackgroundColor(Color.TRANSPARENT);
        setClipChildren(false);
        setClipToPadding(false);
        setPadding(15, 15, 15, 15);
        seekBar.setOnSeekBarChangeListener(this);
    }

    private void initialization(Context context, AttributeSet attrs) {
        init();
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.ZoomSeekBar);
        animationDuration = typedArray.getInt(R.styleable.ZoomSeekBar_zsb_animationDuration, 200);
        int max = typedArray.getInt(R.styleable.ZoomSeekBar_zsb_max, 10);
        seekBar.setMax(max);
        int iconTint = typedArray.getInt(R.styleable.ZoomSeekBar_zsb_iconTint, Color.WHITE);
        zoomIn.setColorFilter(iconTint);
        zoomOut.setColorFilter(iconTint);
        incrementValue = typedArray.getInt(R.styleable.ZoomSeekBar_zsb_incrementValue, 1);
        typedArray.recycle();
    }

    public void show() {
        removeCallbacks(runnable);
        if (getAlpha() != 0) return;
        animate().alpha(1).setDuration(animationDuration);
    }

    public void hide() {
        animate().alpha(0).setDuration(animationDuration);
    }

    public void postHide() {
        postDelayed(runnable, 900);
    }

    private final Runnable runnable = this::hide;

    public void setMax(int maxZoom) {
        seekBar.setMax(maxZoom);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (onProgressChangedListener != null && callCallback) {
            onProgressChangedListener.onZoomChanged(progress);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        callCallback = true;
        show();
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        postHide();
    }

    @Override
    public void onClick(View v) {
        show();
        int progress = seekBar.getProgress();
        if (v.getId() == R.id.zoomIn) {
            if (progress < seekBar.getMax()) {
                setProgress(progress + incrementValue, true);
            }
        } else {
            if (progress > 0) {
                setProgress(progress - incrementValue, true);
            }
        }
        postHide();
    }

    private ValueAnimator valueAnimator;

    public void setProgress(int progress) {
        callCallback = false;
        seekBar.setProgress(progress);
    }

    private boolean callCallback = true;

    public void setProgress(int progress, boolean animate) {
        callCallback = true;
        if (animate) {
            if (valueAnimator != null) {
                valueAnimator.removeAllUpdateListeners();
                valueAnimator.removeAllListeners();
                valueAnimator = null;
            }
            int current = seekBar.getProgress();
            valueAnimator = ValueAnimator.ofInt(current, progress);
            valueAnimator.setInterpolator(new FastOutSlowInInterpolator());
            valueAnimator.setDuration(230);
            valueAnimator.addUpdateListener(animation -> seekBar.setProgress((Integer) animation.getAnimatedValue()));
            valueAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    super.onAnimationStart(animation);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    if (onProgressChangedListener != null) {
                        onProgressChangedListener.onEndZoomChangeAnimate(seekBar.getProgress());
                    }
                }
            });
            valueAnimator.start();
        } else {
            seekBar.setProgress(progress);
        }
    }

    public int getMax() {
        return seekBar.getMax();
    }

    public interface OnProgressChangedListener {
        void onZoomChanged(int progress);

        void onEndZoomChangeAnimate(int progress);
    }

    private OnProgressChangedListener onProgressChangedListener = null;

    public void setOnProgressChangedListener(OnProgressChangedListener onProgressChangedListener) {
        this.onProgressChangedListener = onProgressChangedListener;
    }
}