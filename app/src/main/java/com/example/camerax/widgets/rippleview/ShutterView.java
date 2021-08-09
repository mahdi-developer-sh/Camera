package com.example.camerax.widgets.rippleview;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;

import com.example.camerax.R;
import com.example.camerax.util.SoundManager;

public class ShutterView extends RippleView implements View.OnClickListener {
    /**
     * هیج عملی انجام نشده
     */
    public static final int ACTION_NONE            = 0;
    /**
     * روی دکمه شاتر کلیک شده و الان باید عکس گرفته شود
     */
    public static final int ACTION_TAKE_PICTURE    = 1;
    /**
     * با لمس طولانی روی دکمه شاتر رنگ دکمه قرمز شده و اکنون باید فیلم برداری شروع شود
     */
    public static final int ACTION_START_RECORDING = 2;
    /**
     * بعد از وقوع ACTION_START_RECORDING وقتی روی دکمه شاتر کلیک شود می بایستی ضبط فیلم پایان بپذیرد
     */
    public static final int ACTION_END_RECORDING   = 3;

    /**
     * شناسه صدای عکاسی
     */
    public static final int SOUND_TAKE_PICTURE          = 0;
    /**
     * شناسه صدای شروع ضبط فیلم
     */
    public static final int SOUND_START_RECORDING_VIDEO = 1;
    /**
     * شناسه صدای پایان ضبط فیلم
     */
    public static final int SOUND_END_RECORDING_VIDEO   = 2;

    /**
     * با رفتار کاربر action های متفاوتی به وقوع می پیوندند
     * این متغیر برابر با action جدید است
     */
    private int action = ACTION_NONE;

    /**
     * مدیر و پخش کننده صداهای موجود در بافر
     */
    private SoundManager soundManager;

    private OnShutterListener onShutterListener = null;

    private final Runnable runnable = () -> {
        drawRipple(0, true);
        setClickable(false);
        onShutterDelayedTouch();
    };

    private int     seekBound    = 400;
    private boolean hasCallback  = false;
    private int     delayedTouchDuration;
    private boolean onSeekChange = false;

    public ShutterView(Context context) {
        super(context);
        delayedTouchDuration = 1000;
    }

    public ShutterView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ShutterView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * مقداردهی اولیه
     */
    @Override
    protected void init() {
        super.init();
        seekBound = getContext().getResources().getDisplayMetrics().heightPixels / 2;
        setBackgroundResource(R.drawable.camera_btn);
        setOnClickListener(this);
        setupSoundEffects(getContext());
    }

    /**
     * تنظیم و راه اندازی sound
     */
    private void setupSoundEffects(Context context) {
        soundManager = SoundManager.getInstance(context);
        soundManager.addSoundsFromAssets(context.getAssets(),
                new SoundManager.SoundUnit(SOUND_TAKE_PICTURE, "takePicture.mp3"),
                new SoundManager.SoundUnit(SOUND_START_RECORDING_VIDEO, "stopRecording.wav"),
                new SoundManager.SoundUnit(SOUND_END_RECORDING_VIDEO, "stopRecording.wav"));
    }

    /**
     * مقداردهی اولیه و دریافت از xml
     */
    @Override
    protected void initialization(Context context, AttributeSet attrs) {
        super.initialization(context, attrs);
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.ShutterView);
        delayedTouchDuration = typedArray.getInt(R.styleable.ShutterView_sv_delayedTouchDuration, 1000);
        typedArray.recycle();
    }

    /**
     * جهت انجام عمل کشیدن به بالا و پایین
     */
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (!hasCallback && action != ACTION_START_RECORDING) {
            postDelayed(runnable, delayedTouchDuration);
            hasCallback = true;
        }
        int motionAction = event.getAction();
        switch (motionAction) {
            case MotionEvent.ACTION_DOWN: {
                drawSolidCircle();
                break;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                clearSolidCircle();
                if (onSeekChange) {
                    if (onShutterListener != null) {
                        onShutterListener.onEndSeeking();
                    }
                    onSeekChange = false;
                }
                if (hasCallback) {
                    removeCallbacks(runnable);
                    hasCallback = false;
                    post(() -> setClickable(true));
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (onShutterListener != null) {
                    float y = event.getY();
                    if (y <= 0 && y >= -seekBound) {
                        float percent = Math.abs(y) / 350;
                        onShutterSeekChanged(percent);
                    } else if (y < -seekBound) {
                        onShutterSeekChanged(1.0F);
                    } else if (y > getHeight()) {
                        onShutterSeekChanged(0.0F);
                    }
                }
                break;
            }
        }
        return super.onTouch(v, event);
    }

    /**
     * @param percent seek percent
     *
     * @see #onTouch(View, MotionEvent)
     */
    @CallSuper
    protected void onShutterSeekChanged(float percent) {
        if (!onSeekChange) {
            onShutterListener.onStartSeeking();
            onSeekChange = true;
            if (action != ACTION_START_RECORDING) {
                removeCallbacks(runnable);
                setAction(ACTION_NONE);
            }
            return;
        }
        onShutterListener.onShutterSeekChanged(percent);
    }

    /**
     * @see #runnable
     */
    private void onShutterDelayedTouch() {
        setAction(ACTION_START_RECORDING);
    }

    /**
     * ShutterView onClick
     */
    @Override
    public void onClick(View v) {
        // scale animation
        animate().scaleY(1.05F).scaleX(1.05F).setStartDelay(0).setDuration(100)
                .setInterpolator(new OvershootInterpolator())
                .withEndAction(() -> animate()
                        .scaleX(1)
                        .scaleY(1)
                        .setDuration(150)
                        .setInterpolator(new OvershootInterpolator())
                        .start()).start();

        if (action == ACTION_START_RECORDING) {
            setAction(ACTION_END_RECORDING);
            setAction(ACTION_NONE);
            post(() -> setClickable(true));
        } else {
            setAction(ACTION_TAKE_PICTURE);
        }
        // پاکسازی دایره سخت
        clearSolidCircle();
    }

    /**
     * Setter for {@link #onShutterListener}
     *
     * @param onShutterListener {@link #onShutterListener}
     * @param manager           windowManager for setup seeking
     */
    public void setOnShutterListener(OnShutterListener onShutterListener, WindowManager manager) {
        this.onShutterListener = onShutterListener;
        if (onShutterListener != null) {
            // تنظیم محدوده قابل کشیدن به یک سوم ارتفاع صفحه نمایش
            if (manager != null) {
                DisplayMetrics displayMetrics = new DisplayMetrics();
                manager.getDefaultDisplay().getMetrics(displayMetrics);
                this.seekBound = displayMetrics.heightPixels / 3;
            }
        }
    }

    /**
     * یک action تنظیم کن و تابع callback مربوطه رو فراخوانی کن
     *
     * @param action action
     *
     * @see #onClick(View)
     */
    private void setAction(@ActionDef int action) {
        this.action = action;
        switch (action) {
            case ACTION_TAKE_PICTURE: {
                soundManager.playSoundEffect(SOUND_TAKE_PICTURE);
                break;
            }
            case ACTION_START_RECORDING: {
                soundManager.playSoundEffect(SOUND_START_RECORDING_VIDEO);
                break;
            }
            case ACTION_END_RECORDING: {
                clearRipple();
                soundManager.playSoundEffect(SOUND_END_RECORDING_VIDEO);
                break;
            }
            case ACTION_NONE:
                break;
        }
        if (onShutterListener != null) {
            onShutterListener.onShutterAction(action);
        }
    }

    /**
     * مقادریر معتبر برای action
     *
     * @see #setAction(int)
     */
    @IntDef({ACTION_NONE, ACTION_TAKE_PICTURE, ACTION_START_RECORDING, ACTION_END_RECORDING})
    public @interface ActionDef {

    }

    public interface OnShutterListener {

        /**
         * رفتارهای کاربر موجب وقوع action هامی شود
         * زمان وقوع یک action این تابع فراخوانی می شود
         *
         * @param action {@link ShutterView.ActionDef}
         */
        void onShutterAction(@ActionDef int action);

        /**
         * زمانی که روی دکمه شاتر نگه داشته شد و عمل کشیدن به بالا یا پایین شروع شود این تابع فراخوانی میشود
         */
        void onStartSeeking();

        /**
         * زمانی که عمل کشیدن به بالا یا پایین به پایان انجامید این تابع فراخوانی می شود
         */
        void onEndSeeking();

        /**
         * با انجام عمل کشیدن به بالا یا پایین این تابع جهت اطلاع به percent فراخوانی می شود
         *
         * @param percent مقداری بین 0.0 تا 1.0
         */
        void onShutterSeekChanged(float percent);
    }
}