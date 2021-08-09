package com.example.camerax.widgets.button;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import com.example.camerax.R;

public class ToggleArrayImageButton extends AppCompatImageView implements View.OnClickListener {
    private int[] sources;
    private int currentPosition;
    private AnimationType animType;
    private int animationDuration;

    public enum AnimationType {
        HORIZONTAL_FLIP(0),
        VERTICAL_TRANSLATION(1);
        private final int index;

        AnimationType(int index) {
            this.index = index;
        }

        public static AnimationType fromIndex(int index) {
            for (AnimationType type : AnimationType.values()) {
                if (type.index == index)
                    return type;
            }
            return null;
        }
    }

    public ToggleArrayImageButton(Context context) {
        super(context);
        init();
        animationDuration = 150;
        animType = AnimationType.HORIZONTAL_FLIP;
    }

    public ToggleArrayImageButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialization(context, attrs);
    }

    public ToggleArrayImageButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialization(context, attrs);
    }

    private void init() {
        setOnClickListener(this);
    }

    private void initialization(Context context, AttributeSet attrs) {
        init();
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.ToggleArrayImageButton);
        currentPosition = typedArray.getInt(R.styleable.ToggleArrayImageButton_t_currentPosition, 0);
        animType = AnimationType.fromIndex(typedArray.getInt(R.styleable.ToggleArrayImageButton_t_animType, AnimationType.HORIZONTAL_FLIP.index));
        animationDuration = typedArray.getInt(R.styleable.ToggleArrayImageButton_t_animationDuration, 150);
        int arrayId = typedArray.getResourceId(R.styleable.ToggleArrayImageButton_t_srcArray, -1);
        if (arrayId != -1) {
            TypedArray array = typedArray.getResources().obtainTypedArray(arrayId);
            int count = array.length();
            this.sources = new int[count];
            for (int i = 0; i < count; ++i) {
                this.sources[i] = array.getResourceId(i, -1);
            }
            array.recycle();
            if (currentPosition >= count || currentPosition < 0) {
                currentPosition = 0;
            }
            setImageResource(this.sources[currentPosition]);
        }

        typedArray.recycle();
    }

    private void toNextPosition() {
        if (++currentPosition >= sources.length) {
            currentPosition = 0;
        }
        setImageResource(sources[currentPosition]);
    }

    @CallSuper
    protected void onToggleChanged() {
        if (this.onToggleListener != null) {
            this.onToggleListener.onToggle(this, currentPosition);
        }
    }

    public void toNext(boolean animate) {
        if (animate) {
            switch (animType) {
                case HORIZONTAL_FLIP:
                    animate()
                            .rotationY(90)
                            .setDuration(animationDuration)
                            .withEndAction(() -> {
                                toNextPosition();
                                animate()
                                        .rotationY(0)
                                        .setDuration(animationDuration)
                                        .withEndAction(() -> {
                                            setRotationY(0);
                                            onToggleChanged();
                                        });
                            });
                    break;
                case VERTICAL_TRANSLATION:
                    animate()
                            .translationY(getHeight() / 2F)
                            .alpha(0)
                            .setInterpolator(new FastOutSlowInInterpolator())
                            .setDuration(animationDuration)
                            .withEndAction(() -> {
                                setTranslationY(-getHeight() / 2F);
                                toNextPosition();
                                animate()
                                        .translationY(0)
                                        .alpha(1)
                                        .setDuration(animationDuration)
                                        .setInterpolator(new FastOutSlowInInterpolator())
                                        .withEndAction(this::onToggleChanged);
                            });
                    break;
            }
        } else {
            toNextPosition();
        }
    }

    public interface OnToggleListener {
        void onToggle(ToggleArrayImageButton v, int current);
    }

    private OnToggleListener onToggleListener = null;

    public void setOnToggleListener(@Nullable OnToggleListener onToggleListener) {
        this.onToggleListener = onToggleListener;
    }

    @Override
    public void onClick(View v) {
        toNext(true);
    }
}