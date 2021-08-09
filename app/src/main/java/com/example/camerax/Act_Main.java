package com.example.camerax;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;

import com.example.camerax.fragments.abstraction.Frg_BaseCamera;
import com.example.camerax.util.AnimationHelper;
import com.example.camerax.util.CameraUtil;
import com.example.camerax.util.file.AppFile;
import com.example.camerax.widgets.Group;
import com.example.camerax.widgets.ZoomSeekBar;
import com.example.camerax.widgets.button.ToggleArrayImageButton;
import com.example.camerax.widgets.layout.FLayout;
import com.example.camerax.widgets.rippleview.FocusView;
import com.example.camerax.widgets.rippleview.ShutterView;
import com.example.camerax.widgets.textview.BlinkTextView;
import com.example.camerax.widgets.textview.RotationTextView;

public final class Act_Main extends Act_Sensor implements Frg_BaseCamera.Callback,
        ToggleArrayImageButton.OnToggleListener, ShutterView.OnShutterListener,
        ZoomSeekBar.OnProgressChangedListener {

    private ToggleArrayImageButton btnToggleCamera;
    private ToggleArrayImageButton btnFlash;
    private BlinkTextView          tvBlinkDuration;
    private RotationTextView       tvRecordLabel;
    private View                   btnSettings;
    private Frg_BaseCamera         cameraFragment;
    private ZoomSeekBar            zoomSeekBar;
    private FLayout                fLayout;
    private Group                  rotationViewsGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_main);
        if (savedInstanceState == null) {
            cameraFragment = Frg_BaseCamera.getInstance();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, cameraFragment)
                    .commit();
        }
    }

    @Override
    protected void onChangeOrientation(int surfaceRotation) {
        if (cameraFragment.isRecording()) {
            tvBlinkDuration.syncLocation(getInverseRotationDegrees());
            tvRecordLabel.syncRotation(getInverseRotationDegrees());
        }
        if (rotationViewsGroup != null) {
            rotationViewsGroup.setRotation(getInverseRotationDegrees());
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void findAndSetupViews() {
        fLayout            = findViewById(R.id.fragmentContainer);
        rotationViewsGroup = findViewById(R.id.group);
        zoomSeekBar        = findViewById(R.id.zoomSeekBar);
        tvBlinkDuration    = findViewById(R.id.blinkTV);
        tvRecordLabel      = findViewById(R.id.tvRecLabel);
        btnSettings        = findViewById(R.id.btnSettings);
        btnFlash           = findViewById(R.id.btnFlash);
        btnToggleCamera    = findViewById(R.id.btnToggleCamera);

        tvBlinkDuration.setPlaceHolders(
                findViewById(R.id.placeHolderTop),
                findViewById(R.id.placeHolderBottom)
        );

        FocusView   focusView   = findViewById(R.id.focusView);
        ShutterView shutterView = findViewById(R.id.shutterView);

        shutterView.setOnShutterListener(this, getWindowManager());

        btnSettings.setOnClickListener(this::settingButtonOnClick);

        if (cameraFragment != null && !cameraFragment.isZoomSupported()) {
            zoomSeekBar.setVisibility(View.GONE);
        }

        zoomSeekBar.setOnProgressChangedListener(this);
        zoomSeekBar.setMax((int) cameraFragment.getMaxZoom());

        if (CameraUtil.hasFrontCamera(this)) {
            btnToggleCamera.setOnToggleListener(this);
        } else {
            btnToggleCamera.setVisibility(View.GONE);
        }

        if (!cameraFragment.isSupportFlash()) {
            btnFlash.setVisibility(View.GONE);
        } else {
            btnFlash.setOnToggleListener(this);
        }

        cameraFragment.setOnTouchListener(event -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                focusView.focus(event);
                cameraFragment.focus(event);
            }
        });
    }

    /**
     * اگر b == true باشد tvVideoDuration نمایش می یابد
     * و در غیر اینصورت tvVideoDuration مخفی می شود
     *
     * @param b visibility boolean flag
     */
    private void syncViews(boolean b) {
        tvBlinkDuration.setVisibility(b);
        tvBlinkDuration.syncLocation(getInverseRotationDegrees());
//        tvRecordLabel.syncRotation(getInverseRotationDegrees());

        tvRecordLabel.setText(cameraFragment.getVideoRecordingSize().getResolutionLabel(isPortrait()));
        tvRecordLabel.setVisibility(View.VISIBLE);
        tvRecordLabel.setAlpha(1.0F);

        tvRecordLabel.syncRotation(getInverseRotationDegrees());

        AnimationHelper.fade(b, tvRecordLabel);
        if (b) {
            AnimationHelper.fade(false, btnToggleCamera, btnSettings);
        } else {
            AnimationHelper.fade(true, btnSettings);
        }
    }

    @Override
    public void onShutterAction(@ShutterView.ActionDef int state) {
        switch (state) {
            case ShutterView.ACTION_TAKE_PICTURE: {
                fLayout.startTakePictureEffect(() -> cameraFragment.takePicture());
                break;
            }
            case ShutterView.ACTION_START_RECORDING: {
                cameraFragment.startCaptureVideo();
                syncViews(true);
                break;
            }
            case ShutterView.ACTION_END_RECORDING: {
                syncViews(false);
                cameraFragment.stopCaptureVideo();
                break;
            }
            case ShutterView.ACTION_NONE:
                break;
        }
    }

    @Override
    public void onStartSeeking() {
        zoomSeekBar.show();
    }

    @Override
    public void onEndSeeking() {
        zoomSeekBar.postHide();
    }

    /**
     * زمانی که روی دکمه shutter لمس شود و به سمت بالا یا پایین کشیده شود می بایستی که zoom تغییر کند
     * با انجام عمل لمس و کشیدن این تابع فراخوانی می شود و مقداری بین 0 تا 1 را برمی گرداند که باید بر اساس این مقدار
     * مقدار zoomSeekBar از 0 تا MAX تغییر داد
     *
     * @param percent مقداری براساس درصد بین 0.0 تا 1.0
     */
    @Override
    public void onShutterSeekChanged(float percent) {
        zoomSeekBar.setProgress((int) (percent * zoomSeekBar.getMax()), false);
    }

    /**
     * زمانی که روی دکمه های btnFlash و btnToggleCamera کلید شد این تابع از callback فراخوانی می شود
     * <p>
     * btnFlash && btnToggleCamera onToggle
     */
    @Override
    public void onToggle(ToggleArrayImageButton v, int current) {
        int id = v.getId();
        if (id == R.id.btnToggleCamera) {
            cameraFragment.setFacing(current == 0 ? Frg_BaseCamera.CAMERA_FACING_REAR : Frg_BaseCamera.CAMERA_FACING_FRONT);
        } else if (id == R.id.btnFlash) {
            cameraFragment.setFlashMode(current);
        }
    }

    /**
     * در صوریت تغییر مقدار ZoomSeekBar این تابع از callBack فراخوانی می شود
     *
     * @param value zoom value
     */
    @Override
    public void onZoomChanged(int value) {
        cameraFragment.setZoom(value);
    }

    /**
     * در صورتی که مقدار ZoomSeekBar با انیمیشن تغییر کند، زمانی که انیمیشن تمام شود این تابع از callBack مربوطه فراخوانی می شود
     *
     * @param value zoom value
     */
    @Override
    public void onEndZoomChangeAnimate(int value) {

    }

    /**
     * زمانی که همه چیز در cameraFragment آماده باشد این تابع از BaseCameraFragment#CallBack فراخوانی میشود
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onReady() {
        if (CameraUtil.frontPhotoSizes == null && cameraFragment != null) {
            CameraUtil.frontVideoSizes = cameraFragment.getSizes(Frg_BaseCamera.CAMERA_FACING_FRONT, false);
            CameraUtil.rearVideoSizes  = cameraFragment.getSizes(Frg_BaseCamera.CAMERA_FACING_REAR, false);
            CameraUtil.frontPhotoSizes = cameraFragment.getSizes(Frg_BaseCamera.CAMERA_FACING_FRONT, true);
            CameraUtil.rearPhotoSizes  = cameraFragment.getSizes(Frg_BaseCamera.CAMERA_FACING_REAR, true);
        }
        findAndSetupViews();
        cameraFragment.setFlashMode(Frg_BaseCamera.FLASH_MODE_OFF/*, false*/);
    }

    @Override
    public void onPictureTaken(AppFile imageFile) {
        Act_Result.start(this, imageFile);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onEndCaptureVideo(AppFile videoFile) {
        tvBlinkDuration.updateDurationTextView(0);
        if (CameraUtil.hasFrontCamera(this)) {
            AnimationHelper.fade(true, btnToggleCamera);
        }
        Act_Result.start(this, videoFile);
    }

    @Override
    public void onZoomChanged(float zoomLevel) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (zoomLevel == 1) {
                zoomLevel = 0;
            }
            zoomSeekBar.setProgress((int) (zoomLevel * 10));
        } else {
            zoomSeekBar.setProgress((int) zoomLevel);
        }
    }

    @Override
    public void onUpdateVideoDuration(long milliSeconds) {
        tvBlinkDuration.updateDurationTextView(milliSeconds);
    }

    @Override
    public void onFacingChanged(int cameraFacing) {
        AnimationHelper.translationY(btnFlash, 120, cameraFragment.isSupportFlash());
    }

    @Override
    public int getRotationDegrees() {
        return getRelativeRotationDegrees();
    }

    private void settingButtonOnClick(View v) {
        startActivity(new Intent(Act_Main.this, Act_Preferences.class));
    }

    @Override
    protected void onDestroy() {
        CameraUtil.releaseSizes();
        super.onDestroy();
    }
}