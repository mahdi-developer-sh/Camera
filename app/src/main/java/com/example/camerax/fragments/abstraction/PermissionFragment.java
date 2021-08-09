package com.example.camerax.fragments.abstraction;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.example.camerax.R;

public abstract class PermissionFragment extends Fragment {
    public static final String PERMISSION_DIALOG = "confirmationDialog";
    public static final int REQUEST_CODE_PERMISSIONS = 101;
    private ConfirmationPermissionDialog confirmationPermissionDialog;

    private static final String[] PERMISSIONS = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO
    };

    protected boolean checkSelfPermissions(Context context) {
        int camera = ActivityCompat.checkSelfPermission(context, PERMISSIONS[0]);
        int storage = ActivityCompat.checkSelfPermission(context, PERMISSIONS[1]);
        int audio = ActivityCompat.checkSelfPermission(context, PERMISSIONS[2]);
        return camera == storage && storage == audio && audio == PackageManager.PERMISSION_GRANTED;
    }

    protected void requestPermissions() {
        if (confirmationPermissionDialog != null && confirmationPermissionDialog.isAdded()) {
            return;
        }
        for (String p : PERMISSIONS) {
            if (shouldShowRequestPermissionRationale(p)) {
                confirmationPermissionDialog = new ConfirmationPermissionDialog();
                confirmationPermissionDialog.show(getChildFragmentManager(), PERMISSION_DIALOG);
                return;
            }
        }
        requestPermissions(PERMISSIONS, REQUEST_CODE_PERMISSIONS);
    }

    /*
     * نمایش دیالوگ تایید جواز دوربین و حافظه
     * */
    public static final class ConfirmationPermissionDialog extends DialogFragment {
        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(requireActivity())
                    .setMessage(R.string.Srt_ConfirmationPermissionMessage)
                    .setTitle(R.string.Str_ConfirmationPermissionTitle)
                    .setCancelable(false)
                    .setPositiveButton("تایید", (dialog, which) -> {
                        dismiss();
                        if (parent != null) {
                            parent.requestPermissions(PERMISSIONS, REQUEST_CODE_PERMISSIONS);
                        }
                    })
                    .setNegativeButton("بی خیال", (dialog, which) -> {
                        Activity activity = getActivity();
                        if (activity != null) {
                            activity.finish();
                        }
                    }).create();
        }
    }
}