package com.example.camerax.widgets.button;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import androidx.appcompat.widget.AppCompatImageView;
import com.example.camerax.R;

public class FlipImageButton extends AppCompatImageView implements View.OnClickListener {
    private int primarySrc;
    private int secondarySrc;
    private boolean isPrimary;

    public FlipImageButton(Context context) {
        super(context);
        init();
    }

    public FlipImageButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialization(context, attrs);
    }

    public FlipImageButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialization(context, attrs);
    }

    private void init() {
        setOnClickListener(this);
    }

    private void initialization(Context context, AttributeSet attrs) {
        init();
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.FlipToggleImageButton);
        primarySrc = typedArray.getResourceId(R.styleable.FlipToggleImageButton_fs_primarySrc, -1);
        secondarySrc = typedArray.getResourceId(R.styleable.FlipToggleImageButton_fs_secondarySrc, -1);
        if (primarySrc != -1) {
            setImageResource(primarySrc);
            isPrimary = true;
        }
        typedArray.recycle();
    }


    private void toggle() {
        if (isPrimary) {
            if (secondarySrc != -1) {
                setImageResource(secondarySrc);
            }
            isPrimary = false;
        } else {
            if (primarySrc != -1) {
                setImageResource(primarySrc);
            }
            isPrimary = true;
        }
        if (onToggleListener != null) {
            onToggleListener.onToggle(isPrimary);
        }
    }

    public void toggle(boolean animate) {
        if (animate) {
            animate().rotationY(90).setDuration(150).withEndAction(() -> {
                toggle();
                animate().rotationY(0).setDuration(150).withEndAction(() -> setRotationY(0));
            });
        } else {
            toggle();
        }
    }

    public interface OnToggleListener {
        void onToggle(boolean b);
    }

    private OnToggleListener onToggleListener = null;

    public void setOnToggleListener(OnToggleListener onToggleListener) {
        this.onToggleListener = onToggleListener;
    }

    public int getPrimarySrc() {
        return primarySrc;
    }

    public void setPrimarySrc(int primarySrc) {
        this.primarySrc = primarySrc;
    }

    public int getSecondarySrc() {
        return secondarySrc;
    }

    public void setSecondarySrc(int secondarySrc) {
        this.secondarySrc = secondarySrc;
    }

    @Override
    public void onClick(View v) {
        toggle(true);
    }
}
