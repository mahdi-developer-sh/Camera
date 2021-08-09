package com.example.camerax.fragments;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.camerax.BuildConfig;
import com.example.camerax.fragments.abstraction.Frg_BaseCamera;
import com.example.camerax.preferences.Preferences;
import com.example.camerax.util.CameraSize;
import com.example.camerax.util.CameraUtil;
import com.example.camerax.util.file.AppFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@TargetApi(value = Build.VERSION_CODES.JELLY_BEAN)
public class Frg_Camera extends Frg_BaseCamera implements Camera.ShutterCallback, Camera.PictureCallback {
    /**
     * آیا همه چیز آماده است
     *
     * @see #onReady()
     */
    private boolean isReady = false;

    /**
     * hardware.Camera instance
     * جهت استفاده از دستگاه دوربین
     *
     * @see #openCamera()
     * @see #onResume()
     * @see #onDestroy()
     * @see #setFacing(int)
     */
    private Camera camera;

    /**
     * Camera.Parameters instance
     * <p>
     * جهت اعماب تنظیمات و پیکربندی دوربین
     */
    private Camera.Parameters parameters;

    /**
     * نمونه ای از  MediaRecorder جهت ضبط فیلم
     */
    private MediaRecorder recorder = null;

    /**
     * آیا اکنون در حال ضبط فیلم هستیم
     */
    private boolean isRecording = false;

    /**
     * نمونه ای از AppFile برای ذخیره فیلم و عکس ها
     */
    private AppFile file;

    /**
     * شناسه دوربین جلو یا عقب
     * پیشفرض دوربین عقب است
     *
     * @see #setFacing(int)
     */
    private int cameraId = 0;

    /**
     * سطح فعلی زوم
     *
     * @see #setZoom(float)
     * @see #onTouch(View, MotionEvent)
     */
    private int zoomLevel = 0;

    /**
     * اندازه بین دو انگشت در زوم با دو انگشت
     *
     * @see #onTouch(View, MotionEvent)
     */
    private float fingerSpacing = 0;

    /**
     * حداکثر سطح قابل قبول برای زوم
     *
     * @see #initializeCameraParameters()
     * @see #onTouch(View, MotionEvent)
     */
    private int maximumZoomLevel;


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        cameraTextureView.setOnTouchListener(this);
        cameraTextureView.setSurfaceTextureListener(this);
    }

    /**
     * تنظیم FPS مربوط به پیش نمایش زنده دوربین
     */
    private void setFPS() {
        //اگر true باشد به منظور انتخاب بالاترین fps پشتیبانی شده است
        boolean highFPS = Preferences.getInstance(getContext()).getBoolean(Preferences.Keys.CAPTURE_HIGH_FPS, false);

        List<int[]> supportedFps = parameters.getSupportedPreviewFpsRange();

        int[] fps;

        // مرتب سازی صعودی
        Collections.sort(supportedFps, new CompareFpsRange());
        if (highFPS) {
            //انتخاب بالاترین fps
            fps = supportedFps.get(supportedFps.size() - 1);
        } else {
            //انتخاب fps میانی
            fps = supportedFps.get(supportedFps.size() / 2);
        }
        parameters.setPreviewFpsRange(fps[0], fps[1]);
    }

    /**
     * پیکربندی و تنظیم parameters
     */
    private void initializeCameraParameters() {
        //نوع فوکوس
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);

        // تنظیم چرخش پیش نمایش
        parameters.setRotation(90);

        maximumZoomLevel = parameters.getMaxZoom();

        //تنظیم کیفیت Jpeg
        parameters.setJpegQuality(100);

        parameters.setExposureCompensation(0);

        //تنظیم SceneMode که بهترین انتخاب برای دوربین معمولی تنظیم حالت خودکار است
        parameters.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);

        //            set color effect to none
        parameters.setColorEffect(Camera.Parameters.EFFECT_NONE);

        //            set Antibanding to none
        parameters.setAntibanding(Camera.Parameters.ANTIBANDING_OFF);

        parameters.setRecordingHint(false);

        //set white ballance
        if (parameters.getWhiteBalance() != null) {
            parameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
        }


        //region انتخاب بالاترین کیفیت پشتیبانی شده برای پیش نمایش
        List<Camera.Size> sizes        = parameters.getSupportedPreviewSizes();
        Camera.Size       selectedSize = sizes.get(0);
        parameters.setPreviewSize(selectedSize.width, selectedSize.height);
        //endregion

        // تنظیم نسبت ابعاد cameraTextureView
        cameraTextureView.setAspectRatio(selectedSize.height, selectedSize.width, false);

        camera.setParameters(parameters);
        try {
            camera.setPreviewTexture(cameraTextureView.getSurfaceTexture());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (checkSelfPermissions(getContext())) {
            initializeCameraParameters();
            onReady();
        }
    }

    /**
     * همه چیز آماده شد
     *
     * @see #onRequestPermissionsResult(int, String[], int[])
     * @see #onSurfaceTextureAvailable(SurfaceTexture, int, int)
     */
    protected void onReady() {
        isReady = true;
        callback.onReady();
        resume();
    }

    /**
     * دوربین را باز کن
     */
    @Override
    protected boolean openCamera() {
        boolean superResult = super.openCamera();
        if (superResult) {
            if (camera == null) {
                camera = android.hardware.Camera.open();
            }
//            if (parameters == null) {
            parameters = camera.getParameters();
//            }
        }
        return true;
    }

    /**
     * تنظیم اندازه عکس برداری
     */
    private void setPictureSize() {
        CameraSize cameraSize = getSizeFromPreferences(getFacing(), true);
        if (cameraSize != null) {
            parameters.setPictureSize(cameraSize.width, cameraSize.height);
        } else {
            CameraSize s;
            if (getFacing() == CAMERA_FACING_FRONT) {
                s = CameraUtil.frontPhotoSizes[0];
            } else {
                s = CameraUtil.rearPhotoSizes[0];
            }
            parameters.setPictureSize(s.width, s.height);
        }
        camera.setParameters(parameters);
    }

    /**
     * پیش نمایش را شروع کن
     */
    @Override
    public void startPreview() {
        camera.lock();
        camera.startPreview();
        setFlashMode(getFlashMode());
    }

    /**
     * عکس بگیر
     */
    @Override
    public void takePicture() {
        camera.takePicture(this, null, null, this);
    }

    /**
     * تنظیم recorder برای شروع فیلم برداری
     */
    private void initializeMediaRecorder() {
        if (recorder == null) {
            recorder = new MediaRecorder();
        }

        //region جهت فیلم برداری در جهت صحیح
        parameters.setRecordingHint(true);
        recorder.setOrientationHint(90);
        //endregion

        camera.setParameters(parameters);

        //تحویل camera به recorder
        camera.unlock();
        recorder.setCamera(camera);

        file = storageManager.fileFor(AppFile.TYPE_VIDEO);

        recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
        recorder.setProfile(profile);

        recorder.setOutputFile(file.getAbsolutePath());

        recorder.setVideoFrameRate(profile.videoFrameRate);
        recorder.setVideoEncodingBitRate(Math.min(CameraUtil.VIDEO_BIT_RATE, profile.videoBitRate));

        SurfaceTexture surfaceTexture = cameraTextureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(cameraTextureView.getWidth(), cameraTextureView.getHeight());

        Surface surface = new Surface(surfaceTexture);
        recorder.setPreviewDisplay(surface);

        syncVideoSize();

        CameraSize videoRecordingSize = getVideoRecordingSize();

        recorder.setVideoSize(videoRecordingSize.width, videoRecordingSize.height);
        try {
            recorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * ضبط فیلم را شروع کن
     */
    @Override
    public void startCaptureVideo() {
        super.startCaptureVideo();
        if (!isRecording) {
            initializeMediaRecorder();
            isRecording = true;
            recorder.start();
        }
    }

    /**
     * ضبط فیلم را متوقف کن
     */
    @Override
    public void stopCaptureVideo() {
        super.stopCaptureVideo();
        if (isRecording) {
            isRecording = false;

            recorder.stop();
            recorder.reset();

            parameters.setRecordingHint(false);
            camera.setParameters(parameters);

            file.runOnFileReady(getActivity(), () -> callback.onEndCaptureVideo(file));
        }
    }

    /**
     * @param facing set {@link #facing}
     */
    @Override
    public void setFacing(@FacingDef int facing) {
        if (getFacing() == facing) return;

        super.setFacing(facing);

        camera.release();
        clearSurface();

        cameraTextureView.startSwitchCameraAnimation(() -> {
            if (facing == Frg_BaseCamera.CAMERA_FACING_REAR) {
                camera   = android.hardware.Camera.open();
                cameraId = 0;
            } else {
                int                                count = android.hardware.Camera.getNumberOfCameras();
                android.hardware.Camera.CameraInfo info  = new android.hardware.Camera.CameraInfo();
                for (int i = 0; i < count; ++i) {
                    android.hardware.Camera.getCameraInfo(i, info);
                    if (facing == Frg_BaseCamera.CAMERA_FACING_FRONT) {
                        if (info.facing == android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT) {
                            cameraId = i;
                            break;
                        }
                    }
                }
                camera = android.hardware.Camera.open(cameraId);
            }
            initializeCameraParameters();
            camera.startPreview();
            Activity activity = getActivity();
            if (activity != null) {
                setCameraDisplayOrientation(cameraId);
            }
            setZoom(0);

            callback.onFacingChanged(getFacing());
        });
    }

    /**
     * تنظیم زوم
     *
     * @param value zoom level
     *
     * @see #setFacing(int)
     */
    @Override
    public void setZoom(float value) {
        this.zoomLevel = (int) value;
        if (parameters != null) {
            parameters.setZoom(zoomLevel);
            camera.setParameters(parameters);
        }
    }

    /**
     * آیا از زوم پشتیبانی می شود
     */
    @Override
    public boolean isZoomSupported() {
        return parameters.isZoomSupported();
    }

    /**
     * Getter for max zoom level
     *
     * @return {@link #parameters#getMaxZoom()}
     */
    @Override
    public float getMaxZoom() {
        return parameters.getMaxZoom();
    }

    @Override
    public void onShutter() {
        //این جا کاری نداریم
    }

    /**
     * تصویر گرفته شده آماده شد... با data چه کار کنم
     */
    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
        file = storageManager.fileFor(AppFile.TYPE_PHOTO);

        storageManager.savePhoto(file, data);

        file.runOnFileReady(getActivity(), () -> callback.onPictureTaken(file));
    }

    /**
     * تنظیم orientation پیش نمایش
     */
    public void setCameraDisplayOrientation(int cameraId) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
//        int degrees = callback.getRotationDegrees();
        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation /*+ degrees*/) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {
            // back-facing
            result = (info.orientation /*- degrees*/ + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }

    /**
     * تنظیم حالت flash
     */
    @Override
    public void setFlashMode(int flashMode) {
        super.setFlashMode(flashMode);
        switch (flashMode) {
            case FLASH_MODE_AUTO: {
                parameters.setFlashMode(android.hardware.Camera.Parameters.FLASH_MODE_AUTO);
                break;
            }
            case FLASH_MODE_OFF: {
                parameters.setFlashMode(android.hardware.Camera.Parameters.FLASH_MODE_OFF);
                break;
            }
            case FLASH_MODE_TORCH: {
                parameters.setFlashMode(android.hardware.Camera.Parameters.FLASH_MODE_TORCH);
                break;
            }
        }
        camera.setParameters(parameters);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isReady) {
            resume();
        }
    }

    /**
     * @see #onResume()
     * @see #onReady()
     */
    private void resume() {
        setZoom(zoomLevel);
        setFPS();
        setPictureSize();
        startPreview();
    }

    /**
     * فوکوس کن
     *
     * @param event touch MotionEvent
     */
    @Override
    public void focus(MotionEvent event) {
        if (parameters.getMaxNumMeteringAreas() > 0) {
            parameters.setFocusMode(android.hardware.Camera.Parameters.FOCUS_MODE_AUTO);

            List<android.hardware.Camera.Area> areas = new ArrayList<>();

            int x = (int) event.getX();
            int y = (int) event.getY();

            Rect rect = new Rect(x, y, x + 20, y + 20);
            areas.add(new android.hardware.Camera.Area(rect, 1000));

            parameters.setFocusAreas(areas);
            parameters.setMeteringAreas(areas);

            camera.autoFocus((success, camera) -> {
                if (isRecording) {
                    parameters.setFocusMode(android.hardware.Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                } else {
                    parameters.setFocusMode(android.hardware.Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                }
                camera.setParameters(parameters);
            });

            camera.setParameters(parameters);
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (camera != null) {
            //دوربین رو آزاد کن
            camera.release();
            camera = null;
        }
    }

    /**
     * اندازه های پشتیبانی شده را بر میگرداند
     *
     * @param isPhoto اندازه های عکسبرداری یا فیلم برداری
     *
     * @return supported sizes
     */
    private List<Camera.Size> getSupportedSizes(boolean isPhoto) {
        List<android.hardware.Camera.Size> s;
        if (isPhoto) {
            s = parameters.getSupportedPictureSizes();
        } else {
            s = parameters.getSupportedVideoSizes();
        }
        if (s == null) {
            return parameters.getSupportedPreviewSizes();
        }
        return s;
    }

    /**
     * @param facing  {@link #facing}
     *                {@link #CAMERA_FACING_FRONT} for front camera
     *                {@link #CAMERA_FACING_REAR} for rear camera
     * @param isPhoto true for photoSize and false for videoSize
     *
     * @return CameraSizes
     */
    @Override
    public CameraSize[] getSizes(int facing, boolean isPhoto) {
        List<android.hardware.Camera.Size> sizes = getSupportedSizes(isPhoto);

        final int size = sizes.size();

        final CameraSize[] cameraSizes = new CameraSize[size];

        android.hardware.Camera.Size s;

        for (int i = 0; i < size; ++i) {
            s              = sizes.get(i);
            cameraSizes[i] = new CameraSize(s.width, s.height);

            if (!isPhoto) {
                cameraSizes[i].swap();
            }
        }

        Arrays.sort(cameraSizes, new CompareCameraSize(false));

        return cameraSizes;
    }

    /**
     * انجام عمل زوم با دو انگشت
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        super.onTouch(v, event);
        try {
            float currentFingerSpacing;
            if (event.getPointerCount() == 2) {
                currentFingerSpacing = getFingerSpacing(event);
                float delta = 1;
                if (fingerSpacing != 0) {
                    if (currentFingerSpacing > fingerSpacing) {
                        if ((maximumZoomLevel - zoomLevel) <= delta) {
                            delta = maximumZoomLevel - zoomLevel;
                        }
                        zoomLevel = (int) (zoomLevel + delta);
                    } else if (currentFingerSpacing < fingerSpacing) {
                        if ((zoomLevel - delta) < 1f) {
                            delta = zoomLevel - 1f;
                        }
                        zoomLevel = (int) (zoomLevel - delta);
                    }
                }
                if (zoomLevel > maximumZoomLevel) zoomLevel = maximumZoomLevel;
                fingerSpacing = currentFingerSpacing;
                callback.onZoomChanged(zoomLevel);
                setZoom(zoomLevel);
            } else { //Single touch point, needs to return true in order to detect one more touch point
                return true;
            }
            return true;
        } catch (final Exception e) {
            if (BuildConfig.DEBUG)
                e.printStackTrace();
        }
        return true;
    }


    /**
     * Getter for isRecording
     *
     * @return {@link #isRecording)}
     */
    @Override
    public boolean isRecording() {
        return isRecording;
    }

    /**
     * SurfaceTextureCallback method
     */
    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        if (parameters == null) return;
        initializeCameraParameters();
        onReady();
    }


    /**
     * SurfaceTextureCallback method
     */
    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
        camera.startPreview();
        Activity activity = getActivity();
        if (activity != null) {
            setCameraDisplayOrientation(0);
        }
    }

    /**
     * SurfaceTextureCallback method
     */
    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        camera.stopPreview();
        return false;
    }


    /**
     * SurfaceTextureCallback method
     */
    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

    }

    /**
     * مقایسه آرایه هایی با اندازه 2
     */
    public static class CompareFpsRange implements Comparator<int[]> {
        @Override
        public int compare(int[] o1, int[] o2) {

            Integer s1 = o1[0] + o1[1];
            Integer s2 = o2[0] + o2[1];

            return s1.compareTo(s2);
        }
    }
}