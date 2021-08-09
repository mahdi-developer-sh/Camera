package com.example.camerax;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.MediaController;
import android.widget.VideoView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.request.target.Target;
import com.example.camerax.preferences.Preferences;
import com.example.camerax.util.file.AppFile;
import com.example.camerax.util.file.StorageManager;
import com.example.camerax.util.parcel.CaptureResult;
import com.example.camerax.widgets.layout.CircularRevealLayout;
import com.github.chrisbanes.photoview.PhotoView;

public class Act_Result extends AppCompatActivity implements View.OnClickListener {
    public static final String RESULT_PARCELABLE = "ResultParcelable";
    private MediaController mediaController;
    private PhotoView            photoView;
    private VideoView            videoView;
    private CaptureResult        parcel;
    private CircularRevealLayout circularRevealLayout;

    public static void start(Activity activity, AppFile file) {
        Bundle bundle = new Bundle();
        bundle.putParcelable(Act_Result.RESULT_PARCELABLE, new CaptureResult(file));
        Intent intent = new Intent(activity, Act_Result.class);
        intent.putExtras(bundle);
        activity.startActivity(intent);
        activity.overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle bundle = getIntent().getExtras();
        if (bundle == null) {
            throw new NullPointerException("bundle must be not null");
        }
        parcel = bundle.getParcelable(RESULT_PARCELABLE);
        setContentView(parcel.getFile().getType() == AppFile.TYPE_PHOTO ? R.layout.act_result_photo : R.layout.act_result_video);
        findViews();
    }

    private void findViews() {
        if (parcel.getFile().getType() == AppFile.TYPE_PHOTO) {
            photoView = findViewById(R.id.img);
            photoView.setMaximumScale(40);
        } else {
            videoView = findViewById(R.id.videoView);
        }
        circularRevealLayout = findViewById(R.id.clearLayout);
        findViewById(R.id.btnDelete).setOnClickListener(this);
        findViewById(R.id.btnSave).setOnClickListener(this);
    }

    private void delete() {
        AppFile appFile = parcel.getFile();
        if (appFile.getType() == AppFile.TYPE_VIDEO) {
            setVisibilityViews();
            videoView.pause();
            videoView.stopPlayback();
            videoView.setMediaController(null);
            ViewGroup group = (ViewGroup) videoView.getParent();
            if (group != null) {
                group.removeView(videoView);
            }
        }
        StorageManager.deleteFile(Act_Result.this, parcel.getFile());
        finish();
    }

    private void setVisibilityViews() {
        if (mediaController != null) {
            mediaController.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onBackPressed() {
        setVisibilityViews();
        circularRevealLayout.startClearCircularRipple(null, this::finish);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    @Override
    public void onResume() {
        super.onResume();
        AppFile appFile = parcel.getFile();
        if (appFile.getType() == AppFile.TYPE_PHOTO) {
            RequestBuilder<Drawable> requestBuilder = Glide
                    .with(Act_Result.this)
                    .load(parcel.getFile().getAbsolutePath())
                    .encodeQuality(100)
                    .format(DecodeFormat.PREFER_ARGB_8888)
                    .centerCrop();

            if (Preferences.getInstance(Act_Result.this).getBoolean(Preferences.Keys.GLIDE_LOAD_IMAGE, false)) {
                requestBuilder = requestBuilder.override(Target.SIZE_ORIGINAL);
            }
            requestBuilder.into(photoView);
        } else {
            videoView.setVideoPath(parcel.getFile().getAbsolutePath());
            videoView.seekTo(videoView.getDuration());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mediaController = new MediaController(this) {
                    @Override
                    public void hide() {
                    }
                };
                mediaController.setAnchorView(videoView);
                videoView.setMediaController(mediaController);
                videoView.post(mediaController::show);
            }
        }
    }

    @Override
    public void onClick(View v) {
        setVisibilityViews();
        circularRevealLayout.startClearCircularRipple(v, () -> {
            if (v.getId() == R.id.btnSave) {
                finish();
            } else {
                delete();
            }
        });
    }
}