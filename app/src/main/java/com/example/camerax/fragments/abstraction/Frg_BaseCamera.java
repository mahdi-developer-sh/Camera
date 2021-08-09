package com.example.camerax.fragments.abstraction;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.example.camerax.fragments.Frg_Camera;
import com.example.camerax.fragments.Frg_Camera2;
import com.example.camerax.preferences.Preferences;
import com.example.camerax.util.CameraSize;
import com.example.camerax.util.CameraUtil;
import com.example.camerax.util.Timer;
import com.example.camerax.util.file.AppFile;
import com.example.camerax.util.file.StorageManager;
import com.example.camerax.widgets.CameraTextureView;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
public abstract class Frg_BaseCamera extends PermissionFragment implements View.OnTouchListener,
        TextureView.SurfaceTextureListener {
    /**
     * در این حالت فلش خاموش است
     */
    public static final int FLASH_MODE_OFF   = 0;
    /**
     * در این حالت فلش روشن است
     */
    public static final int FLASH_MODE_TORCH = 1;
    /**
     * در این حالت فلش به طور خودکار و وابسته به نور محیط روشن یا خاموش می شود
     */
    public static final int FLASH_MODE_AUTO  = 2;

    /**
     * دوربین عقب
     */
    public static final int CAMERA_FACING_REAR  = 0;
    /**
     * دوربین جلو
     */
    public static final int CAMERA_FACING_FRONT = 1;

    /**
     * وضوح HD اندازه پیشفرض فیلم برداری است
     */
    public static final CameraSize DEFAULT_VIDEO_SIZE = new CameraSize(1280, 720);

    /**
     * مقدار متغییر facing بیانگر دوربین فعال است که این مقادیر میتواند یکی از ثابت ها باشد
     * <br>
     * مقدار پیشفرض برابر با Frg_BaseCamera#CAMERA_FACING_REAR است که دوربین عقب است
     * <br>
     * {@link #CAMERA_FACING_REAR,#CAMERA_FACING_FRONT}
     *
     * @see #setFacing(int)
     */
    protected int facing = CAMERA_FACING_REAR;

    /**
     * نمونه ای از Frg_BaseCamera#Callback
     * <p>
     * این callback شامل توابع پایه است که در اکتیویتی نیاز به پیاده سازی دارد
     * <p>
     * نمونه سازی از این فرگمنت وابسته به پیاده سازی این callback در اکتیویتی دارد
     *
     * @see Frg_BaseCamera.Callback
     * @see #onAttach(Context)
     */
    protected Callback callback;

    /**
     * نمونه ای از StorageManager جهت انجام عملیات کار با فایل
     *
     * @see #onActivityCreated(Bundle)
     */
    protected StorageManager storageManager;

    /**
     * نمونه CameraTextureView برای پیش نمایش دوربین
     *
     * @see #onCreateView(LayoutInflater, ViewGroup, Bundle)
     */
    protected CameraTextureView cameraTextureView = null;

    /**
     *
     */
    protected OnTouchListener onTouchListener = null;

    /**
     * این متغییر بیانگر حالت فلش است
     * مقادیر قابل قبول یکی از موارد زیر است
     * <p>
     * {@link FlashModeDef}
     */
    private int flashMode = FLASH_MODE_OFF;

    /**
     * نمونه اس از Timer برای فراخوانی منظم تابع onUpdateVideoDuration که مدت زمان ضبط فیلم را اطلاع می دهد
     *
     * @see #startCaptureVideo()
     * @see #stopCaptureVideo()
     */
    private Timer      timer = null;
    /**
     * نمونه ای از CameraSize که اندازه فیلم برداری را در بر میگیرد
     *
     * @see #getVideoRecordingSize()
     * @see #syncVideoSize()
     */
    private CameraSize videoRecordingSize;

    public Frg_BaseCamera() {
    }

    public static Frg_BaseCamera getInstance() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return new Frg_Camera2();
        } else {
            return new Frg_Camera();
        }
    }

    /**
     * فراخوانی این تابع موجب پاکسازی SurfaceTexture مربوط به CameraTextureView می شود
     * <p>
     * {@link Frg_Camera2#setFacing(int),Frg_Camera#setFacing(int)}
     */
    public void clearSurface() {
        if (cameraTextureView == null) return;

        SurfaceTexture texture = cameraTextureView.getSurfaceTexture();

        if (texture == null) {
            return;
        }

        EGL10      egl     = (EGL10) EGLContext.getEGL();
        EGLDisplay display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        egl.eglInitialize(display, null);

        int[] attribList = {
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_RENDERABLE_TYPE, EGL10.EGL_WINDOW_BIT,
                EGL10.EGL_NONE, 0,      // placeholder for recordable [@-3]
                EGL10.EGL_NONE
        };
        EGLConfig[] configs    = new EGLConfig[1];
        int[]       numConfigs = new int[1];
        egl.eglChooseConfig(display, attribList, configs, configs.length, numConfigs);
        EGLConfig config = configs[0];
        EGLContext context = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, new int[]{
                12440, 2,
                EGL10.EGL_NONE
        });
        EGLSurface eglSurface = egl.eglCreateWindowSurface(display, config, texture,
                new int[]{
                        EGL10.EGL_NONE
                });

        egl.eglMakeCurrent(display, eglSurface, eglSurface, context);
//        GLES20.glClearColor(0, 0, 0, 1);
//        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        egl.eglSwapBuffers(display, eglSurface);
        egl.eglDestroySurface(display, eglSurface);
        egl.eglMakeCurrent(
                display,
                EGL10.EGL_NO_SURFACE,
                EGL10.EGL_NO_SURFACE,
                EGL10.EGL_NO_CONTEXT
        );
        egl.eglDestroyContext(display, context);
        egl.eglTerminate(display);
    }

    /**
     * این تابع نمونه ای از Runnable را گرفته و آن را در UI Thread اجرا می کند
     *
     * @param runnable runnable
     */
    public void runOnUiThread(Runnable runnable) {
        Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(runnable);
        }
    }

    /**
     * زمانی که context در دسترس قرار گرفت و فرگمت attach شد بررسی میشود که آیا context نمونه ای از Callback را پیاده سازی کرده یا نه
     * <p>
     * اگر context نمونه ای از Frg_BaseCamera#Callback نبود exception پرتاب میشود
     *
     * @param context context
     */
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (!(context instanceof Callback)) {
            throw new ClassCastException(context.toString() + " Activity must be instance of BaseCameraFragment.Callback");
        }
        callback = (Callback) context;
    }

    /**
     * این جا cameraTextureView را مقداردهی کرده و به عنوان View به onCreateView برگردانده می شود
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return cameraTextureView = new CameraTextureView(getContext());
    }

    /**
     * مقدار دهی storageManager
     */
    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        storageManager = new StorageManager(getContext());
    }

    @Override
    public void onResume() {
        super.onResume();
        openCamera();
    }

    protected boolean openCamera() {
        if (!checkSelfPermissions(getContext())) {
            requestPermissions();
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean denied = false;
        for (int i : grantResults) {
            if (i == PackageManager.PERMISSION_DENIED) {
                denied = true;
                requestPermissions();
                break;
            }
        }
        if (!denied) {
            openCamera();
        }
    }

    /**
     * شروع ضبط فیلم
     * <p>
     * این تابع ر هر api به گونه ای متفاوت است
     * <p>
     * وجه مشترک راه اندازی timer است
     */
    @CallSuper
    public void startCaptureVideo() {
        if (timer == null) {
            /*
            هر یک ثانیه تابع callback#onUpdateVideoDuration فراخوانی می شود
             */
            timer = Timer.create(1000, time -> callback.onUpdateVideoDuration(time));
        }
        timer.start();
    }

    /**
     * پایان فیلم برداری
     */
    @CallSuper
    public void stopCaptureVideo() {
        timer.reset();
    }

    /**
     * Getter for facing
     *
     * @return {@link #facing}
     */
    public int getFacing() {
        return facing;
    }

    /**
     * Setter for facing
     *
     * @param facing set {@link #facing}
     */
    @CallSuper
    public void setFacing(@FacingDef int facing) {
        this.facing = facing;
    }

    /**
     * آیا از فلش پشتیبانی می شود
     *
     * @return boolean
     */
    public boolean isSupportFlash() {
        return CameraUtil.isSupportFlash(getContext());
    }

    /**
     * Getter for flashMode
     *
     * @return {@link #flashMode}
     */
    public int getFlashMode() {
        return flashMode;
    }

    /**
     * Setter for flashMode
     *
     * @param flashMode set {@link #flashMode}
     */
    @CallSuper
    public void setFlashMode(@FlashModeDef int flashMode) {
        this.flashMode = flashMode;
    }

    /**
     * این تابع اندازه ذخیره شده در SharedPreferences را دریافت و آن را در قالب آبجکتی از CameraSize برمیگرداند
     * اندازه بر اساس facing و isPhoto که به تابع پاس داده می شود در SharedPreferences جستجو می شوند و در صورت وجود به تابع برگردانده می شوند
     *
     * @param facing  {@link #facing}
     *                {@link #CAMERA_FACING_FRONT} for front camera
     *                {@link #CAMERA_FACING_REAR} for rear camera
     * @param isPhoto true for photoSize and false for videoSize
     *
     * @return Instance of {@link CameraSize}
     */
    protected CameraSize getSizeFromPreferences(@FacingDef int facing, boolean isPhoto) {
        String key;

        if (isPhoto) {
            if (facing == CAMERA_FACING_REAR) {
                key = Preferences.Keys.PHOTO_SIZE_REAR;
            } else {
                key = Preferences.Keys.PHOTO_SIZE_FRONT;
            }
        } else {
            if (facing == CAMERA_FACING_REAR) {
                key = Preferences.Keys.VIDEO_SIZE_REAR;
            } else {
                key = Preferences.Keys.VIDEO_SIZE_FRONT;
            }
        }

        String strSize = Preferences.getInstance(getContext()).getString(key, null);

        if (strSize != null) {
            if (!isPhoto) {
                return CameraSize.fromString(strSize).swap();
            }
            return CameraSize.fromString(strSize);
        }
        return null;
    }

    /**
     * در زوم با دو انگشت استفاده میشود
     * <br>
     * فاصله بین دو انگشت روی CameraTextureView را برمیگرداند
     * <p>
     * {@link Frg_Camera2#onTouch(View, MotionEvent)}<br>
     * {@link Frg_Camera#onTouch(View, MotionEvent)}
     *
     * @param event motionEvent
     *
     * @return finger spacing
     */
    protected float getFingerSpacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    @CallSuper
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (onTouchListener != null) {
            onTouchListener.onTouch(event);
        }
        return false;
    }

    /**
     * تابع پایه برای انجام عمل زوم دوربین با استفاده از لمس بر روی CameraTextureView
     *
     * @param event touch MotionEvent
     */
    public abstract void focus(MotionEvent event);

    /**
     * وظیفه این تابع بازگرداندن اندازه های پشتیبانی شده از دوربین جلو یا عقب برای فیلم برداری یا عکسبرداری است
     *
     * @param facing  {@link #facing}
     *                {@link #CAMERA_FACING_FRONT} for front camera
     *                {@link #CAMERA_FACING_REAR} for rear camera
     * @param isPhoto true for photoSize and false for videoSize
     *
     * @return instance of {@link CameraSize}
     */
    public abstract CameraSize[] getSizes(int facing, boolean isPhoto);

    /**
     * فراخوانی این تابع موجپ شروع پیش نمایش زنده دوربین در cameraTextureView می شود
     * <p>
     * به این نکته باید توجه داشت که فراخوانی این تابع قبل از باز کردن دوربین و قبل از پیکربندی دوربین موجب
     * بازگرداندن Exception شده و موجب کرش برنامه می شود
     * <p>
     * {@link Frg_Camera2#startPreview()}
     * {@link Frg_Camera#startPreview()}
     */
    public abstract void startPreview();

    /**
     * یک عکس بگیر
     * <p>
     * {@link Frg_Camera2#takePicture()}
     * {@link Frg_Camera#takePicture()}
     */
    public abstract void takePicture();

    /**
     * زوم کن
     *
     * @param value zoom level
     */
    public abstract void setZoom(float value);

    /**
     * آیا دوربین فعال از زوم پشتیبانی می کند
     *
     * @return true for supported and false for not supported
     */
    public abstract boolean isZoomSupported();

    /**
     * حداکثر مقدار قابل قبول برای زوم
     *
     * @return max zoom level
     */
    public abstract float getMaxZoom();

    /**
     * آیا دوربین در حال ظبط فیلم است
     *
     * @return true for recording and false for not recording
     */
    public abstract boolean isRecording();

    /**
     * این تابع اندازه ای را که با آن فیلم برداری می شود را برمیگرداند
     *
     * @return instance of {@link CameraSize}
     */
    public CameraSize getVideoRecordingSize() {
        return this.videoRecordingSize;
    }

    public void setOnTouchListener(OnTouchListener onTouchListener) {
        this.onTouchListener = onTouchListener;
    }

    /**
     * این تابع با توجه به اندازه پیشفرض فیلم برداری یک اندازه امن را برای فیلم برداری انتخاب و آن را بر می گرداند
     *
     * @return instance of {@link CameraSize}
     *
     * @see #DEFAULT_VIDEO_SIZE
     */
    private CameraSize getSafeVideoSize() {
        CameraSize maxSupportedSize = new CameraSize(
                Collections.max(
                        Arrays.asList(getFacing() == CAMERA_FACING_REAR ? CameraUtil.rearVideoSizes
                                : CameraUtil.frontVideoSizes), new CompareCameraSize(true))
        ).swap();
        if (maxSupportedSize.getSum() < DEFAULT_VIDEO_SIZE.getSum()) {
            return maxSupportedSize;
        }
        return new CameraSize(DEFAULT_VIDEO_SIZE);
    }

    /**
     * با توجه به اندازه پیش فرض و اندازه انتخابی در تنظیمات یک اندازه نزدیک انتخاب کن
     */
    protected void syncVideoSize() {
        videoRecordingSize = getSizeFromPreferences(getFacing(), false);
        if (videoRecordingSize == null) {
            videoRecordingSize = getSafeVideoSize();
        }
    }

    /**
     * مقادیر قابل قبول flashMode
     *
     * @see #flashMode
     * @see #setFlashMode(int)
     */
    @IntDef({FLASH_MODE_OFF, FLASH_MODE_TORCH, FLASH_MODE_AUTO})
    public @interface FlashModeDef {

    }

    /**
     * مقادیر قابل قبول برای facing
     *
     * @see #setFacing(int)
     * @see #facing
     */
    @IntDef(value = {CAMERA_FACING_REAR, CAMERA_FACING_FRONT})
    public @interface FacingDef {
    }

    /**
     * این Callback در Act_Main پیاده سازی شده
     *
     * @see com.example.camerax.Act_Main
     */
    public interface Callback {
        /**
         * همانطور که از نام تابع مشخص است این تابع از طرف فرگمنت های مربوط به CameraApi فراخوانی می شود
         * <br>
         * این تابع زمانی فراخوانی می شود که همه چیز آماده باشد
         * <br>
         * پس در اکتیویتی هر کاری که نیاز به آماده باش دوربین دارد در این تابع انجام می شود
         */
        void onReady();

        /**
         * عکس گرفته شده و فایل مربوط به عکس به این تابع ارسال می شود
         *
         * @param file picture file
         */
        void onPictureTaken(AppFile file);

        /**
         * ضبظ فیلم تمام شد و اکنون فایل مربوط به فیلم به این تابع ارسال می شود
         *
         * @param videoFile video file
         */
        void onEndCaptureVideo(AppFile videoFile);

        /**
         * این تابع مدت زمان فیلم برداری را اطلاع می دهد
         *
         * @param milliSeconds video duration
         */
        void onUpdateVideoDuration(long milliSeconds);

        /**
         * زمانی که زوم دوربین تغییر می کند این تابع جهت اطلاع فراخوانی می شود
         *
         * @param zoomLevel current zoom level
         */
        void onZoomChanged(float zoomLevel);

        /**
         * زمانی که دوربین فعال به دوربین جلو یا عقب تغییر می کند این تابع فراخوانی می شود
         *
         * @param cameraFacing current camera facing
         */
        void onFacingChanged(@FacingDef int cameraFacing);

        /**
         * فراخوانی این تابع برای بدست آوردن چرخش مربوط به اکتیویتی بر حسب درجه است
         * <br>
         * در اکتیویتی باید مقدار صحیح به این تابع بازگردانده شود تا عکس ها و فیلم های ضبط شده در جهت درست قرار گیرند
         *
         * @return rotation value per degrees
         */
        int getRotationDegrees();
    }

    public interface OnTouchListener {
        void onTouch(MotionEvent event);
    }

    /**
     * مقایسه دو CameraSize
     *
     * @see #getSafeVideoSize()
     * @see Frg_Camera#getSizes(int, boolean)
     * @see Frg_Camera2#getSizes(int, boolean)
     */
    public static class CompareCameraSize implements Comparator<CameraSize> {
        /**
         * اگر flag == true باشد آبجکت 1 با آبجکت دو مقایسه شده و برای مرتب سازی صعودی استفاده می شود
         * اگر flag == false باشد آبجکت 2 با آبجکت 1 مقایسه شده و برای مرتب سازی نزولی استفاده می شود
         */
        private final boolean desc;

        public CompareCameraSize(boolean flag) {
            this.desc = flag;
        }

        @Override
        public int compare(CameraSize o1, CameraSize o2) {
            if (desc) {
                return Integer.compare(o1.getSum(), o2.getSum());
            }
            return Integer.compare(o2.getSum(), o1.getSum());
        }
    }
}