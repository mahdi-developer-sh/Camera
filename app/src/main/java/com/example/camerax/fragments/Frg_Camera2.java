package com.example.camerax.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.ExifInterface;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Range;
import android.util.Size;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.example.camerax.BuildConfig;
import com.example.camerax.DebugHelper;
import com.example.camerax.fragments.abstraction.Frg_BaseCamera;
import com.example.camerax.preferences.Preferences;
import com.example.camerax.util.CameraSize;
import com.example.camerax.util.CameraUtil;
import com.example.camerax.util.file.AppFile;
import com.example.camerax.util.file.StorageManager;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class Frg_Camera2 extends Frg_BaseCamera {
    private static final String FOCUS_TAG                    = "Focus_tag";
    private static final int    STATE_PREVIEW                = 0;
    private static final int    STATE_WAITING_LOCK           = 1;
    private static final int    STATE_WAITING_PRECAPTURE     = 2;
    private static final int    STATE_WAITING_NON_PRECAPTURE = 3;
    private static final int    STATE_PICTURE_TAKEN          = 4;
    private static final int    MAX_PREVIEW_WIDTH            = 1920;
    private static final int    MAX_PREVIEW_HEIGHT           = 1080;

    private final Semaphore cameraOpenCloseLock = new Semaphore(1);

    public  float                  fingerSpacing = 0;
    public  float                  maximumZoomLevel;
    public  Rect                   zoom;
    private String                 mCameraId;
    private CameraDevice           cameraDevice;
    private CameraCaptureSession   previewCaptureSession;
    private ImageReader            imageReader;
    private CaptureRequest         previewRequest;
    private CaptureRequest.Builder previewRequestBuilder;
    private Size                   previewSize;
    private int                    mState        = STATE_PREVIEW;
    private HandlerThread          backgroundThread;
    private Handler                backgroundHandler;
    private AppFile                file;

    private final ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            backgroundHandler.post(new StorageManager.ImageSaver(reader.acquireNextImage(), file, getContext()));
        }
    };

    private CameraManager         cameraManager;
    private boolean               isFlashSupported    = true;
    private int                   sensorOrientation;
    private CameraCharacteristics characteristics;
    private MediaRecorder         recorder;
    private boolean               isRecordingVideo    = false;
    private float                 zoomLevel           = .0F;
    //    private boolean mAutoFocusSupported;
    private boolean               mManualFocusEngaged = false;
    private Preferences           preferences;

    private final CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result, boolean isComplete) {
            switch (mState) {
                case STATE_PREVIEW: {
                    // وقتی پیش نمایش دوربین به طور عادی کار میکند کاری نداریم
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // این CONTROL_AE_STATE می تواند در بعضی از دستگاه ها NULL شود
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        } else {
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // این CONTROL_AE_STATE می تواند در بعضی از دستگاه ها NULL شود
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // این CONTROL_AE_STATE می تواند در بعضی از دستگاه ها NULL شود
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                }
                case STATE_PICTURE_TAKEN: {
                    if (isComplete) {
                        mState = STATE_PREVIEW;
                        if (preferences.getBoolean(Preferences.Keys.PHOTO_ORIENTATION_PROBLEM, false)) {
                            runOnUiThread(() -> Toast.makeText(getContext(), "در حال پردازش تصویر", Toast.LENGTH_SHORT).show());

                            Runnable runnable = () -> {
                                boolean mirror = false;
                                if (getFacing() == CAMERA_FACING_FRONT) {
                                    mirror = preferences.getBoolean(Preferences.Keys.PHOTO_MIRROR, false);
                                }
                                int exifOrientation = computeExifOrientation(computeRelativeRotation(), mirror);
                                if (exifOrientation == ExifInterface.ORIENTATION_UNDEFINED || exifOrientation == ExifInterface.ORIENTATION_NORMAL) {
                                    return;
                                }
                                file.runOnFileReady(null, () -> {
                                    Bitmap bitmap = BitmapFactory.decodeFile(file.toString());
                                    Matrix matrix = decodeExifOrientation(exifOrientation);
                                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                                    try {
                                        FileOutputStream stream = new FileOutputStream(file);
                                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
                                        stream.close();
                                        stream.flush();
                                    } catch (FileNotFoundException e) {
                                        e.printStackTrace();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    } finally {
                                        bitmap.recycle();
                                        runOnUiThread(() -> callback.onPictureTaken(file));
                                    }
                                });
                            };
                            Thread thread = new Thread(runnable);
                            thread.start();
                        } else {
                            if (getFacing() == CAMERA_FACING_FRONT) {
                                if (preferences.getBoolean(Preferences.Keys.PHOTO_MIRROR, false)) {
                                    final int exifOrientation = computeExifOrientation(computeRelativeRotation(), true);
                                    Matrix    matrix          = decodeExifOrientation(exifOrientation);

                                    file.runOnFileReady(null, () -> {
                                        runOnUiThread(() -> Toast.makeText(getContext(), "در حال اعمال حالت آیینه", Toast.LENGTH_SHORT).show());
                                        Bitmap bitmap = BitmapFactory.decodeFile(file.toString());
//                                        bitmap = flipBitmap(bitmap);
                                        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                                        try {
                                            FileOutputStream fileOutputStream = new FileOutputStream(file);
                                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream);
                                            fileOutputStream.close();
                                            fileOutputStream.flush();
                                        } catch (FileNotFoundException e) {
                                            e.printStackTrace();
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        } finally {
                                            runOnUiThread(() -> callback.onPictureTaken(file));
                                        }

                                    });


                                    return;
                                }
                            }
                            file.runOnFileReady(getActivity(), () -> callback.onPictureTaken(file));
                        }
                    }
                    /*if (getFacing() == CAMERA_FACING_FRONT) {
                        try {
                            ExifInterface exif = new ExifInterface(file.toString());
                            exif.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(ExifInterface.ORIENTATION_FLIP_HORIZONTAL));
                            exif.saveAttributes();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }*/
//                    unlockFocus();
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            process(partialResult, false);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            process(result, true);
        }
    };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            // This method is called when the camera is opened.  We start camera preview here.
            cameraOpenCloseLock.release();
            cameraDevice = camera;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraOpenCloseLock.release();
            cameraDevice.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            DebugHelper.showToast(getContext(), "Error : " + error);
//            if (error == CameraDevice.StateCallback.ERROR_CAMERA_IN_USE) {
//                return;
//            }
//            cameraOpenCloseLock.release();
//            cameraDevice.close();
//            cameraDevice = null;
//            Activity activity = getActivity();
//            if (activity != null) {
//                showToast(getString(R.string.Str_CameraDeviceError));
//                CameraXPreferences preferences = CameraXPreferences.getInstance(activity);
//                if (preferences.hasSizes()) {
//                    preferences.resetSizes();
//                activity.recreate();
//                } else {
//                activity.finish();
//                }
//            }
        }
    };

    public Frg_Camera2() {

    }

    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth, int textureViewHeight,
                                          int maxWidth, int maxHeight, Size aspectRatio) {
//        جمع آوری کنید resolution های پشتیبانی شده را حداقل به اندازه surface پیش نمایش جمع آوری کنید
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();

// جمع آوری کنید resolution های پشتیبانی شده کوچکتر از surface پیش نمایش
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();

        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();

        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }
        //کوچکترین آنها را به اندازه کافی بزرگ انتخاب کنید. اگر کسی به اندازه کافی بزرگ نیست، آن را انتخاب کنید
        // Pick the smallest of those big enough. If there is no one big enough, pick the

        //بزرگترین آنهایی که به اندازه کافی بزرگ نیستند
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
//            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (preferences == null) {
            preferences = Preferences.getInstance(context);
        }

        if (cameraManager == null) {
            cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        }
    }

    private void createCameraPreviewSession() {
        {
            try {
                SurfaceTexture texture = cameraTextureView.getSurfaceTexture();
                /*
                 * ما اندازه بافر پیش فرض را به اندازه پیش نمایش دوربین مورد نظر خود تنظیم می کنیم
                 * */
                // We configure the size of default buffer to be the size of camera preview we want.
                texture.setDefaultBufferSize(previewSize.getWidth(),
                        previewSize.getHeight());

                /*
                 * این همان Surface خروجی است که برای شروع پیش نمایش به آن نیاز داریم
                 * */
                // This is the output Surface we need to start preview.
                Surface surface = new Surface(texture);

                /*
                 * ما یک CaptureRequest.Builder با Surface خروجی تنظیم کردیم
                 * */
                // We set up a CaptureRequest.Builder with the output Surface.
                previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

                //region new code
                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, getFPSRange());
                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, false);
                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
                //endregion

                previewRequestBuilder.addTarget(surface);

                if (zoom != null) {
                    previewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);
                }

                /*
                 * در اینجا ، ما یک CameraCaptureSession برای پیش نمایش دوربین ایجاد می کنیم
                 * */
                cameraDevice.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()),
                        new CameraCaptureSession.StateCallback() {

                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                                /*
                                 * دوربین از قبل بسته شده است
                                 * */
                                if (null == cameraDevice) {
                                    return;
                                }
                                /*
                                 * وقتی Session آماده شد، ما نمایش پیش نمایش را شروع می کنیم
                                 * */
                                Frg_Camera2.this.previewCaptureSession = cameraCaptureSession;
                                try {
                                /*
                                فوکوس خودکار برای پیش نمایش دوربین باید مداوم باشد
                                * */
                                    // Auto focus should be continuous for camera preview.
                                    previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                    /*
                                     * در صورت لزوم فلش یه طور خودکار فعال می شود
                                     * */
//                                    previewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
//                                    previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
//                                    setAutoFlash(previewRequestBuilder);
                                    setFlashMode(getFlashMode(), previewRequestBuilder, false);
                                    /*
                                     * در آخر ما نمایش پیش نمایش دوربین را شروع می کنیم
                                     * */
                                    previewRequest = previewRequestBuilder.build();
                                    cameraCaptureSession.setRepeatingRequest(previewRequest, captureCallback, backgroundHandler);
                                    //                                    setZoom(zoomLevel);
                                } catch (CameraAccessException e) {
                                    e.printStackTrace();
                                }
                            }


                            @Override
                            public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                                Toast.makeText(getContext(), "onConfigureFailed", Toast.LENGTH_SHORT).show();
                            }
                        }, null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    /*
     * این تابع بزرگترین FPS پشتیبانی شده توسط دوربین را بر میگرداند
     * */
    private Range<Integer> getFPSRange() {
        Range<Integer>[] ranges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);

        if (preferences.getBoolean(Preferences.Keys.CAPTURE_HIGH_FPS, false)) {
            List<Range<Integer>> rangeList = Arrays.asList(ranges);

            Range<Integer> upperMax = Collections.max(rangeList, new CompareRages(true));

            Range<Integer> lowerMax = Collections.max(rangeList, new CompareRages(false));

            if (upperMax.getLower() + upperMax.getUpper() > lowerMax.getUpper() + lowerMax.getLower()) {
                return upperMax;
            } else {
                return lowerMax;
            }
        } else {
            return Collections.max(Arrays.asList(ranges), new CompareRages(true));
        }
    }

    /**
     * توالی پیش از تصویر را برای گرفتن یک عکس ثابت اجرا کنید. وقتی از تابع lockFocus پاسخی در mCaptureCallback دریافت میکنیم این تابع باید فراخوانی شود
     * <p>
     * Run the precapture sequence for capturing a still image. This method should be called when
     */
    private void runPrecaptureSequence() {
        try {
            /*
             * به این ترتیب می توان به دوربین گفت که ماشه را تحریک کند
             * */
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            /*
             * به mCaptureCallback بگویید منتظر تنظیم توالی پیش ضبط شود
             * */
            mState = STATE_WAITING_PRECAPTURE;
            previewCaptureSession.capture(previewRequestBuilder.build(), captureCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * یک عکس ثابت بگیرید. این تابع وقتی فراخوانی میشود که ما در mCaptureCallback از هر دو تابع lockFocus پاسخی دریافت می کنیم
     */
    private void captureStillPicture() {
        if (isRecordingVideo || previewCaptureSession == null) return;

        try {
            if (null == cameraDevice) {
                return;
            }
            /*
             * این CaptureRequest.Builder است که ما برای گرفتن عکس از آن استفاده می کنیم
             * */
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());

            /*
             * از حالت AE و AF به عنوان پیش نمایش استفاده کنید
             * */
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            if (zoom != null) {
                captureBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);
            }

            setFlashMode(getFlashMode(), captureBuilder, false);
            captureBuilder.set(CaptureRequest.JPEG_QUALITY, (byte) 100);

            boolean photoOrientationProblem = preferences.getBoolean(Preferences.Keys.PHOTO_ORIENTATION_PROBLEM, false);
            // Orientation
            if (getFacing() == CAMERA_FACING_FRONT) {
                boolean mirror = preferences.getBoolean(Preferences.Keys.PHOTO_MIRROR, false);
                if (!mirror && !photoOrientationProblem) {
                    setOrientation(captureBuilder);
                }
            } else if (!photoOrientationProblem) {
                setOrientation(captureBuilder);
            }

            /*
             * {@link #captureCallback#process}
             */
            mState = STATE_PICTURE_TAKEN;

            //زمانی که عکس گرفته می شود تصویر پیشنمایش زنده دوربین را فریز میکنم
            //برای غیر فعال سازی این مورد دوخط کد زیر کامنت شود
            //region
            previewCaptureSession.stopRepeating();
            previewCaptureSession.abortCaptures();
            //endregion

            clearSurface();
            cameraTextureView.invalidate();

            previewCaptureSession.capture(captureBuilder.build(), captureCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void setOrientation(CaptureRequest.Builder builder) {
        int relativeOrientation = computeRelativeRotation();
        builder.set(CaptureRequest.JPEG_ORIENTATION, relativeOrientation);
    }

    /**
     * فوکوس را باز کنید. این تابع باید با پایان یافتن توالی عکسبرداری ثابت فراخوانی شود
     * <p>
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private void unlockFocus() {
        try {
//            ماشه فوکوس خودکار را بازنشانی کنید
            // Reset the auto-focus trigger
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);

            setFlashMode(getFlashMode(), previewRequestBuilder, false);

            previewCaptureSession.capture(previewRequestBuilder.build(), captureCallback, backgroundHandler);
//            پس از این، دوربین به حالت پیش نمایش طبیعی بر میگردد
            // After this, the camera will go back to the normal state of preview.
            mState = STATE_PREVIEW;

            previewCaptureSession.setRepeatingRequest(previewRequest, captureCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    @Override
    public boolean isSupportFlash() {
        return isFlashSupported;
    }

    @Override
    protected boolean openCamera() {
        return true;
    }

    @SuppressLint("MissingPermission")
    private void openCamera(int width, int height) {
        if (!checkSelfPermissions(getContext())) {
            requestPermissions();
            return;
        }
        setupCameraOutputs(width, height);
        configureTransform(width, height);

        Activity activity = getActivity();
        if (activity == null) return;

        recorder = new MediaRecorder();
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            cameraManager.openCamera(mCameraId, stateCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    private void setupCameraOutputs(int width, int height) {
        Activity activity = getActivity();
        if (activity == null) return;
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                characteristics = cameraManager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);

                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK && getFacing() == CAMERA_FACING_FRONT) {
                    continue;
                }

                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                //برای ثبت تصاویر ثابت، ما از بزرگترین اندازه موجود استفاده می کنیم
                Size largest;
                /*
                 * Output picture size
                 */
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && facing == CameraCharacteristics.LENS_FACING_BACK) {
//                    try {
//                        largest = Collections.max(Arrays.asList(map.getHighResolutionOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());
//                    } catch (Exception e) {
//                        largest = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());
//                    }
//                } else {
                largest = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());
//                }
                if (imageReader != null) {
                    imageReader.close();
                    imageReader = null;
                }

                CameraSize cameraSize = getSizeFromPreferences(getFacing(), true);
//                if (cameraSize == null) {
//                    cameraSize = getSizes(getFacing(), true)[0];
//                }

                if (cameraSize != null) {
                    imageReader = ImageReader.newInstance(cameraSize.width, cameraSize.height, ImageFormat.JPEG, 2);
                } else {
                    imageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(), ImageFormat.JPEG, 2);
                    CameraSize s = new CameraSize(largest.getWidth(), largest.getHeight());

                    preferences.putString(getFacing() == CAMERA_FACING_REAR ?
                            Preferences.Keys.PHOTO_SIZE_REAR : Preferences.Keys.PHOTO_SIZE_FRONT, s.toString());
                }
                imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);

                /*
                 * دریابید که آیا برای بدست آوردن اندازه پیش نمایش نسبت به مختصات سنسور، باید ابعاد را عوض کنیم
                 * */
                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
//                int displayRotation = callback.getRotation();
//                int displayRotation = Surface.ROTATION_0;

                //noinspection ConstantConditions
                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

                boolean swappedDimensions = false;
                switch (displayRotation) {
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if (sensorOrientation == 90 || sensorOrientation == 270) {
                            swappedDimensions = true;
                        }
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if (sensorOrientation == 0 || sensorOrientation == 180) {
                            swappedDimensions = true;
                        }
                        break;
                }

                Point displaySize = new Point();
                activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
                int rotatedPreviewWidth  = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth      = displaySize.x;
                int maxPreviewHeight     = displaySize.y;

                if (swappedDimensions) {
                    rotatedPreviewWidth  = height;
                    rotatedPreviewHeight = width;
                    maxPreviewWidth      = displaySize.y;
                    maxPreviewHeight     = displaySize.x;
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

//                int[] afAvailableModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
//                if (afAvailableModes.length == 0 || (afAvailableModes.length == 1 && afAvailableModes[0] == CameraMetadata.CONTROL_AF_MODE_OFF)) {
//                    mAutoFocusSupported = false;
//                } else {
//                    mAutoFocusSupported = true;
//                }

                /*
                 * خطر، تلاش برای استفاده از اندازه پیش نمایش بیش از حد بزرگ میتواند از محدودیت پهنای باند اتوبوس دوربین فراتر رود، در نتیجه پیش نمایش های بسیار زیبایی ایجاد میکند اما دادههای ضبظ زباله دخیره می شود
                 * */
                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth, maxPreviewHeight, largest);

//                previewSize = new Size(1280,960);
                /*
                 * ما نسبت ابعاد TextureView را به اندازه پیش نمایشی انتخاب کرده ایم متناسب می کنیم
                 * */
                int orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    cameraTextureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight(), true);
                } else {
//                cameraTextureView.post(new Runnable() {
//                    @Override
//                    public void run() {
                    cameraTextureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth(),
                            preferences.getBoolean(Preferences.Keys.FULL_SCREEN_PREVIEW, true));
//                        configureTransform(previewSize.getWidth(),previewSize.getHeight());
//                    }
//                });
                }

                // بررسی کنید فلش پشتیبانی شده است یا خیر
                // Check if the flash is supported.
                Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                isFlashSupported = available == null ? false : available;

                maximumZoomLevel = (characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM));
                mCameraId        = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            /*
             * در حال حاضر با استفاده از Camera2API NPE پرتاب مشود اما در دستگاهی که این کپ اجرا می کند پشتیبانی نمی شود
             * */
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
//            ErrorDialog.newInstance(getString(R.string.camera_error))
//                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
        }
    }

    /**
     * تغییر شکل لازم Matrix را به TextureView پیکربندی میکند. این تابع باید پس از تعیین اندازه پیش نمایش دوربین در setupCameraOutput ها و همچنین اندازه TextureView ثابت شود
     *
     * @param viewWidth  The width of `cameraTextureView`
     * @param viewHeight The height of `cameraTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        if (null == cameraTextureView || null == previewSize || activity == null) {
            return;
        }
        int    rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix   = new Matrix();

        RectF viewRect   = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());

        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);

            float scale = Math.max(
                    (float) viewHeight / previewSize.getHeight(),
                    (float) viewWidth / previewSize.getWidth());

            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        cameraTextureView.setTransform(matrix);
    }

    @Override
    public void onResume() {
        super.onResume();
        resume();
    }

    @Override
    public void onPause() {
        pause();
        super.onPause();
    }

    public void pause() {
        closePreviewSession();
        closeCamera();
        stopBackgroundThread();
    }

    public void resume() {
        startBackgroundThread();
        /*
         * وقتی صفحه خاموش و روشن شد، SurfaceTexture از قبل در دسترس است و onSurfaceTextureAvailable فراخوانی نخواهد شد. در این صورت، ما می توانیم یک دوربین باز کنیم و از اینجا پیش نمایش را شروع کنیم ( در غیر این صورت، تا آماده شدن surface در SurfaceTextureListener منتظر می مانیم)
         * */
        if (cameraTextureView.isAvailable()) {
            if (previewSize != null) {
                openCamera(previewSize.getWidth(), previewSize.getHeight());
            } else {
                openCamera(cameraTextureView.getWidth(), cameraTextureView.getHeight());
            }
        } else {
            cameraTextureView.setSurfaceTextureListener(this);
        }
    }

    private void stopBackgroundThread() {
        if (backgroundThread == null)
            return;

        backgroundThread.quitSafely();

        try {
            backgroundThread.join();
            backgroundThread = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        try {
            cameraOpenCloseLock.acquire();
            if (null != previewCaptureSession) {
//                try {
//                    previewCaptureSession.stopRepeating();
//                    previewCaptureSession.abortCaptures();
//                } catch (CameraAccessException e) {
//                    e.printStackTrace();
//                }
                previewCaptureSession.close();
                previewCaptureSession = null;
            }
//            if (backgroundThread != null) {
            if (null != cameraDevice) {
                cameraDevice.close();
                cameraDevice = null;
            }
//            }
            if (null != imageReader) {
                imageReader.close();
                imageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            cameraOpenCloseLock.release();
        }
    }

    /**
     * فوکوس را به عنوان اولین مرحله برای ثبت عکس ثابت قفل کنید
     * <p>
     * Lock the focus as the first step for a still image capture.
     */
    private void lockFocus() {
        try {
            /*
             * این تابع به دوربین میگوید که فوکوس را قفل کند
             * */
            // This is how to tell the camera to lock focus.
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            /*
             * به mCaptureCallback بگویید منتظر قفل شود
             * */
            // Tell #mCaptureCallback to wait for the lock.
            mState = STATE_WAITING_LOCK;
            previewCaptureSession.capture(previewRequestBuilder.build(), captureCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setupMediaRecorder() {
        Activity activity = getActivity();
        if (activity == null) return;

        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

        recorder.setOrientationHint(computeRelativeRotation());

        file = storageManager.fileFor(AppFile.TYPE_VIDEO);

        CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
        recorder.setOutputFile(file.getAbsolutePath());

        recorder.setVideoFrameRate(profile.videoFrameRate);

        /*
         * Set video encoding bit rate to 6MP
         * */
        recorder.setVideoEncodingBitRate(Math.min(CameraUtil.VIDEO_BIT_RATE, profile.videoBitRate));

//        setVideoFPS(profile.videoFrameRate);
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

        recorder.setAudioEncodingBitRate(profile.audioBitRate);
        recorder.setAudioSamplingRate(profile.audioSampleRate);

        syncVideoSize();

        CameraSize videoRecordingSize = getVideoRecordingSize();
        recorder.setVideoSize(videoRecordingSize.width, videoRecordingSize.height);
        try {
            recorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startRecordingVideo() throws CameraAccessException {
        if (cameraDevice == null || !cameraTextureView.isAvailable() || previewSize == null) {
            return;
        }

        closePreviewSession();
        setupMediaRecorder();

        SurfaceTexture texture = cameraTextureView.getSurfaceTexture();
        if (texture == null) return;
        texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

//      previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
//      setFlashMode(getFlashMode(), previewRequestBuilder, false);

        previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, getFPSRange());
        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, false);
        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);

        if (zoom != null) {
            previewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);
        }

        List<Surface> surfaces       = new ArrayList<>();
        Surface       previewSurface = new Surface(texture);
        surfaces.add(previewSurface);

        previewRequestBuilder.addTarget(previewSurface);
        recorder.setPreviewDisplay(previewSurface);

        Surface recorderSurface = recorder.getSurface();
        surfaces.add(recorderSurface);
        previewRequestBuilder.addTarget(recorderSurface);

        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);

        cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {
                previewCaptureSession = session;
                updatePreview();
                runOnUiThread(() -> {
                    isRecordingVideo = true;
                    recorder.start();
                });
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                DebugHelper.showToast(getContext(), "از وضوح فیلم برداری انتخاب شده پشتیبانی نمیشود");
//                recorder.reset();
//                createCameraPreviewSession();
            }
        }, backgroundHandler);
    }

    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    }

    private void updatePreview() {
        if (null == cameraDevice) {
            return;
        }
        try {
            setUpCaptureRequestBuilder(previewRequestBuilder);
            HandlerThread thread = new HandlerThread("CameraPreview");
            thread.start();
            previewCaptureSession.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closePreviewSession() {
        if (previewCaptureSession != null) {
            try {
                previewCaptureSession.stopRepeating();
                previewCaptureSession.abortCaptures();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            previewCaptureSession.close();
            previewCaptureSession = null;
        }
    }

    @Override
    public void startPreview() {
        createCameraPreviewSession();
    }

    @Override
    public void takePicture() {
        file = storageManager.fileFor(AppFile.TYPE_PHOTO);
//        if (mAutoFocusSupported) {
//            lockFocus();
//        } else {
        captureStillPicture();
//        }
    }

    @Override
    public void setFacing(@FacingDef int facing) {
        if (getFacing() == facing) return;
        super.setFacing(facing);
        zoom      = null;
        zoomLevel = 0;
        pause();
        cameraTextureView.startSwitchCameraAnimation(() -> {
            clearSurface();
            resume();
            callback.onFacingChanged(getFacing());
        });
    }

    @Override
    public void setZoom(float zoomLevel) {
        if (characteristics == null || previewRequestBuilder == null || previewCaptureSession == null) {
            return;
        }
        this.zoomLevel = zoomLevel;
        zoom           = getZoomRect();
        if (zoom != null) {
            try {
                //you can try to add the synchronized object here
                previewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);
                previewCaptureSession.setRepeatingRequest(previewRequestBuilder.build(), isRecordingVideo ? null : captureCallback, backgroundHandler);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void startCaptureVideo() {
        if (!isRecordingVideo) {
            try {
                startRecordingVideo();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }/* else {
            stopCaptureVideo();
            cameraTextureView.getSurfaceTexture().releaseTexImage();
        }*/
        super.startCaptureVideo();
    }

    @Override
    public void stopCaptureVideo() {
        super.stopCaptureVideo();
        if (isRecordingVideo) {
            stopBackgroundThread();
            isRecordingVideo = false;
            try {
                recorder.stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                previewCaptureSession.stopRepeating();
                previewCaptureSession.abortCaptures();
            }/* catch (CameraAccessException e) {
                e.printStackTrace();
            }*/ catch (Exception e) {
                e.printStackTrace();
            }
            recorder.reset();
//            stopBackgroundThread();
            file.runOnFileReady(getActivity(), () -> callback.onEndCaptureVideo(file));
        }
    }

    private void setFlashMode(int flashMode, CaptureRequest.Builder requestBuilder, boolean build) {
        if (!isFlashSupported || previewRequestBuilder == null || previewCaptureSession == null)
            return;
        switch (flashMode) {
            case FLASH_MODE_TORCH: {
                requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                break;
            }
            case FLASH_MODE_OFF: {
                requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                break;
            }
            case FLASH_MODE_AUTO: {
                requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                break;
            }
        }
        if (build) {
            previewRequest = previewRequestBuilder.build();
            try {
                previewCaptureSession.setRepeatingRequest(previewRequest, captureCallback, backgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void setFlashMode(int flashMode/*, boolean temp*/) {
        super.setFlashMode(flashMode/*, temp*/);
        setFlashMode(flashMode, previewRequestBuilder, true);
    }

    @Override
    public boolean isZoomSupported() {
        return true;
    }

    private Rect getZoomRect() {
        try {
            float maxZoom    = (characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)) * 10;
            Rect  activeRect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            if ((zoomLevel <= maxZoom) && (zoomLevel > 1)) {
                int minW = (int) (activeRect.width() / maxZoom);
                int minH = (int) (activeRect.height() / maxZoom);

                int difW = activeRect.width() - minW;
                int difH = activeRect.height() - minH;

                int cropW = difW / 100 * (int) zoomLevel;
                int cropH = difH / 100 * (int) zoomLevel;

                cropW -= cropW & 3;
                cropH -= cropH & 3;
                return new Rect(cropW, cropH, activeRect.width() - cropW, activeRect.height() - cropH);
            } else if (zoomLevel == 0) {
                return new Rect(0, 0, activeRect.width(), activeRect.height());
            }
            return null;
        } catch (Exception e) {
//            Log.e(TAG, "Error during camera init");
            return null;
        }
    }

    @Override
    public float getMaxZoom() {
        if (characteristics != null) {
            return characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) * 10;
        }
        return 0;
    }

    @Override
    public void focus(MotionEvent event) {
        try {
            setFocusArea((int) (cameraTextureView.getWidth() - event.getX()), (int) (event.getY()));
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setFocusArea(int focus_point_x, int focus_point_y) throws CameraAccessException {
        if (mCameraId == null || mManualFocusEngaged || previewCaptureSession == null) return;
        MeteringRectangle focusArea = null;
        if (cameraManager != null) {
            if (characteristics == null) {
                characteristics = cameraManager.getCameraCharacteristics(mCameraId);
            }
            final Rect sensorArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            int        y               = focus_point_y;
            int        x               = focus_point_x;
            if (sensorArraySize != null) {
                y = (int) (((float) focus_point_x / cameraTextureView.getWidth()) * (float) sensorArraySize.height());
                x = (int) (((float) focus_point_y / cameraTextureView.getHeight()) * (float) sensorArraySize.width());
            }
            final int halfTouchLength = 150;
            focusArea = new MeteringRectangle(Math.max(x - halfTouchLength, 0),
                    Math.max(y - halfTouchLength, 0),
                    halfTouchLength * 2,
                    halfTouchLength * 2,
                    MeteringRectangle.METERING_WEIGHT_MAX - 1);
        }
        CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {

            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                super.onCaptureCompleted(session, request, result);
                mManualFocusEngaged = false;
                if (request.getTag() != null && request.getTag().equals(FOCUS_TAG)) { // previously getTag == "Focus_tag"
                    //the focus trigger is complete -
                    //resume repeating (preview surface will get frames), clear AF trigger
                    previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
                    previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
                    previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, null);// As documentation says AF_trigger can be null in some device
                    try {
                        previewCaptureSession.setRepeatingRequest(previewRequestBuilder.build(), captureCallback, backgroundHandler);
                    } catch (CameraAccessException e) {
                        // error handling
                    }
                }
            }

            @Override
            public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                super.onCaptureFailed(session, request, failure);
                mManualFocusEngaged = false;
            }
        };

        previewCaptureSession.stopRepeating(); // Destroy current session
        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);

        //Set all settings for once
        previewCaptureSession.capture(previewRequestBuilder.build(), mCaptureCallback, backgroundHandler);

        if (isMeteringAreaAESupported()) {
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, new MeteringRectangle[]{focusArea});
        }
        if (isMeteringAreaAFSupported()) {
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{focusArea});
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
        }

        previewRequestBuilder.setTag(FOCUS_TAG); //it will be checked inside mCaptureCallback
        previewCaptureSession.capture(previewRequestBuilder.build(), mCaptureCallback, backgroundHandler);

        mManualFocusEngaged = true;
    }

    private boolean isMeteringAreaAFSupported() {
        Integer afRegion = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
        return afRegion != null && afRegion >= 1;
    }

    private boolean isMeteringAreaAESupported() {
        //AE stands for AutoExposure
        Integer aeState = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE);
        return aeState != null && aeState >= 1;
    }

    private synchronized CameraCharacteristics getCharacteristics(Context context, final int cameraFacing) {
        if (context == null) return null;
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                Integer               facing          = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null) {
                    if ((cameraFacing == CAMERA_FACING_FRONT && facing == CameraCharacteristics.LENS_FACING_FRONT) ||
                            (cameraFacing == CAMERA_FACING_REAR && facing == CameraCharacteristics.LENS_FACING_BACK)) {
                        return characteristics;
                    }
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    @NonNull
    private Size[] getHighResolutionSizes(@NonNull StreamConfigurationMap map) {
        Size[] highResolutions = null;
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                highResolutions = map.getHighResolutionOutputSizes(ImageFormat.JPEG);
            }
        } catch (Exception e) {
            highResolutions = map.getOutputSizes(ImageReader.class);
        }
        if (highResolutions == null)
            return new Size[0];
        return highResolutions;
    }

    @Override
    public CameraSize[] getSizes(@FacingDef int facing, boolean isPhoto) {
        CameraCharacteristics characteristics = getCharacteristics(getContext(), facing);
        if (characteristics == null) {
            return null;
        }
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        android.util.Size[]    sizes;
        if (isPhoto) {
            Size[] highResolutionSizes = getHighResolutionSizes(map);

            Size[] outputSizes = map.getOutputSizes(ImageFormat.JPEG);

            sizes = new Size[highResolutionSizes.length + outputSizes.length];
            System.arraycopy(highResolutionSizes, 0, sizes, 0, highResolutionSizes.length);
            System.arraycopy(outputSizes, 0, sizes, highResolutionSizes.length, outputSizes.length);
        } else {
            sizes = map.getOutputSizes(MediaRecorder.class);
        }
        CameraSize[] cameraSizes = new CameraSize[sizes.length];
        Size         size;
        for (int i = 0; i < sizes.length; ++i) {
            size = sizes[i];
            if (isPhoto) {
                cameraSizes[i] = new CameraSize(size.getWidth(), size.getHeight());
            } else {
                cameraSizes[i] = new CameraSize(size.getHeight(), size.getWidth());
            }
        }
        // مرتب سازی نزولی
        Arrays.sort(cameraSizes, new CompareCameraSize(false));
        return cameraSizes;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setOnTouchListener(this);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        super.onTouch(v, event);
        try {
            Rect rect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            if (rect == null) return false;
            float currentFingerSpacing;
            if (event.getPointerCount() == 2) {
                currentFingerSpacing = getFingerSpacing(event);
                float delta = 0.05f;
                if (fingerSpacing != 0) {
                    if (currentFingerSpacing > fingerSpacing) {
                        if ((maximumZoomLevel - zoomLevel) <= delta) {
                            delta = maximumZoomLevel - zoomLevel;
                        }
                        zoomLevel = zoomLevel + delta;
                    } else if (currentFingerSpacing < fingerSpacing) {
                        if ((zoomLevel - delta) < 1f) {
                            delta = zoomLevel - 1f;
                        }
                        zoomLevel = zoomLevel - delta;
                    }
                    if (zoomLevel > maximumZoomLevel) zoomLevel = maximumZoomLevel;
                    float ratio         = (float) 1 / zoomLevel;
                    int   croppedWidth  = rect.width() - Math.round((float) rect.width() * ratio);
                    int   croppedHeight = rect.height() - Math.round((float) rect.height() * ratio);

                    zoom = new Rect(croppedWidth / 2, croppedHeight / 2,
                            rect.width() - croppedWidth / 2, rect.height() - croppedHeight / 2);
                    callback.onZoomChanged(zoomLevel);
                    previewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);
                }
                fingerSpacing = currentFingerSpacing;
            } else {
                return true;
            }
            previewCaptureSession.setRepeatingRequest(previewRequestBuilder.build(), isRecordingVideo ? null : captureCallback, backgroundHandler);
            return true;
        } catch (final Exception e) {
            if (BuildConfig.DEBUG) {
                e.printStackTrace();
            }
        }
        return true;
    }

    @Override
    public boolean isRecording() {
        return isRecordingVideo;
    }

    private int computeRelativeRotation() {
        int deviceOrientationDegrees = callback.getRotationDegrees();
        int sign                     = getFacing() == CAMERA_FACING_FRONT ? 1 : -1;
        return (sensorOrientation - (deviceOrientationDegrees * sign) + 360) % 360;
    }

    private int computeExifOrientation(int rotationDegrees, boolean mirrored) {
        if (rotationDegrees == 0 && !mirrored) {
            return ExifInterface.ORIENTATION_NORMAL;
        } else if (rotationDegrees == 0) {
            return ExifInterface.ORIENTATION_FLIP_HORIZONTAL;
        } else if (rotationDegrees == 180 && !mirrored) {
            return ExifInterface.ORIENTATION_ROTATE_180;
        } else if (rotationDegrees == 180) {
            return ExifInterface.ORIENTATION_FLIP_VERTICAL;
        } else if (rotationDegrees == 270 && mirrored) {
            return ExifInterface.ORIENTATION_TRANSVERSE;
        } else if (rotationDegrees == 90 && !mirrored) {
            return ExifInterface.ORIENTATION_ROTATE_90;
        } else if (rotationDegrees == 90) {
            return ExifInterface.ORIENTATION_TRANSPOSE;
        } else if (rotationDegrees == 270) {
            return ExifInterface.ORIENTATION_TRANSVERSE;
        } else {
            return ExifInterface.ORIENTATION_UNDEFINED;
        }
    }
    /*private int computeExifOrientation(int rotationDegrees, boolean mirrored) {
        if (rotationDegrees == 0 && !mirrored) {
            return ExifInterface.ORIENTATION_NORMAL;
        } else if (rotationDegrees == 0) {
            return ExifInterface.ORIENTATION_FLIP_HORIZONTAL;
        } else if (rotationDegrees == 180 && !mirrored) {
            return ExifInterface.ORIENTATION_ROTATE_180;
        } else if (rotationDegrees == 180 && mirrored) {
            return ExifInterface.ORIENTATION_FLIP_VERTICAL;
        } else if (rotationDegrees == 270 && mirrored) {
            return ExifInterface.ORIENTATION_TRANSVERSE;
        } else if (rotationDegrees == 90 && !mirrored) {
            return ExifInterface.ORIENTATION_ROTATE_90;
        } else if (rotationDegrees == 90 && mirrored) {
            return ExifInterface.ORIENTATION_TRANSPOSE;
        } else if (rotationDegrees == 270 && mirrored) {
            return ExifInterface.ORIENTATION_ROTATE_270;
        } else if (rotationDegrees == 270 && !mirrored) {
            return ExifInterface.ORIENTATION_TRANSVERSE;
        } else {
            return ExifInterface.ORIENTATION_UNDEFINED;
        }
    }*/

    private Matrix decodeExifOrientation(int exifOrientation) {
        Matrix matrix = new Matrix();
        switch (exifOrientation) {
            case ExifInterface.ORIENTATION_NORMAL:
            case ExifInterface.ORIENTATION_UNDEFINED: {
                return matrix;
            }
            case ExifInterface.ORIENTATION_ROTATE_90: {
                matrix.postRotate(90F);
                break;
            }
            case ExifInterface.ORIENTATION_ROTATE_180: {
                matrix.postRotate(180F);
                break;
            }
            case ExifInterface.ORIENTATION_ROTATE_270: {
                matrix.postRotate(270F);
                break;
            }
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: {
                matrix.postScale(-1F, 1F);
                break;
            }
            case ExifInterface.ORIENTATION_FLIP_VERTICAL: {
                matrix.postScale(1F, -1F);
                break;
            }
            case ExifInterface.ORIENTATION_TRANSPOSE: {
                matrix.postScale(-1F, 1F);
                matrix.postRotate(270F);
                break;
            }
            case ExifInterface.ORIENTATION_TRANSVERSE: {
                matrix.postScale(-1F, 1F);
                matrix.postRotate(90F);
                break;
            }
        }
        return matrix;
    }

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
//            if (previewSize != null) {
//                openCamera(previewSize.getWidth(), previewSize.getHeight());
//            } else {
        openCamera(width, height);
//            configureTransform(width, height);
//            openCamera(cameraTextureView.getWidth(),cameraTextureView.getHeight());
//            }
        callback.onReady();
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
//            configureTransform(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

    }

    /**
     * مقایسه دو Size بر اساس area ها
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            /*
             * ما برای اطمینان از سرریز شدن ضربات از اینجا استفاده می کنیم
             * */
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    static class CompareRages implements Comparator<Range<Integer>> {
        boolean upperCheck;

        public CompareRages(boolean upperCheck) {
            this.upperCheck = upperCheck;
        }

        @Override
        public int compare(Range<Integer> o1, Range<Integer> o2) {
            if (upperCheck) {
                return o1.getUpper().compareTo(o2.getUpper());
            } else {
                return o1.getLower().compareTo(o2.getLower());
            }
        }
    }
}