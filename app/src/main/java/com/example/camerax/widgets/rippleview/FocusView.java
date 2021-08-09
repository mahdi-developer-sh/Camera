package com.example.camerax.widgets.rippleview;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.MotionEvent;
import androidx.annotation.Nullable;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import com.example.camerax.R;

public class FocusView extends RippleView {
    /**
     * مدت زمانی انیمیشن نما
     */
    private int animationDuration;

    public FocusView(Context context) {
        super(context);
        animationDuration = 250;
    }

    public FocusView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public FocusView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * مقدار دهی اولیه
     */
    @Override
    protected void init() {
        super.init();
        setClickable(false);
        setFocusable(false);
        setAlpha(0);
        setBackgroundResource(R.drawable.focusview_oval);
    }

    /**
     * مقدار دهی اولیه و دریافت مقادیر از xml
     */
    @Override
    protected void initialization(Context context, AttributeSet attrs) {
        super.initialization(context, attrs);
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.FocusView);
        animationDuration = typedArray.getInt(R.styleable.FocusView_fv_animationDuration, 250);
        typedArray.recycle();
    }

    /**
     * انیمیشن فوکوس رو شروع کن
     *
     * @param event MotionEvent
     */
    public void focus(MotionEvent event) {
        drawRipple(0.2F, true);
        setX(event.getRawX() - getWidth() / 2F);
        setY(event.getRawY() - getHeight() / 2F);
        setScaleX(1.8F);
        setScaleY(1.8F);
        animate()
                .setStartDelay(0)
                .alpha(1.0F)
                .scaleY(1.0F)
                .scaleX(1.0F)
                .setDuration(animationDuration)
                .setInterpolator(new FastOutSlowInInterpolator())
                .withEndAction(() -> {
                    clearRipple();
                    animate().setStartDelay(100).alpha(0).setDuration(animationDuration);
                });
    }
}