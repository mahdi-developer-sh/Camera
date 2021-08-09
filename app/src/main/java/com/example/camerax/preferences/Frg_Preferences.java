package com.example.camerax.preferences;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.ContextThemeWrapper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.example.camerax.Act_Preferences;
import com.example.camerax.R;
import com.example.camerax.fragments.abstraction.Frg_BaseCamera;
import com.example.camerax.util.CameraSize;
import com.example.camerax.util.CameraUtil;
import com.example.camerax.util.file.StorageManager;

import java.io.File;

import lib.folderpicker.FolderPicker;

public class Frg_Preferences extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {
    /**
     * Constant tag for show selectorDialogFragment
     *
     * @see #showSelectorSize
     */
    public static final String SELECTOR_SIZE_DIALOG = "selectorDialog";

    /**
     * کلید برای ذخیره کلید مربوط به preferenceScreen کلیک شده که به Act_Preference#navigateTo پاس داده می شود
     *
     * @see #onCreatePreferences(Bundle, String)
     * {@link Act_Preferences#navigateTo(String, boolean, String)}
     */
    public static final String PREFERENCE_SCREEN_KEY = "key";

    /**
     * Directory picker request code
     *
     * @see #onActivityResult(int, int, Intent)
     * @see #showDirectoryPicker()
     */
    public static final int DIRECTORY_PICKER_REQUEST_CODE = 280;
    Act_Preferences act;
    /**
     * نمونه ای از Preferences برای انجام عملیات بر روی SharedPreference
     *
     * @see #onCreate(Bundle)
     */
    private Preferences preferences;

    public Frg_Preferences() {
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        act = (Act_Preferences) context;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            view.setBackgroundColor(getContext().getColor(R.color.windowBackgroundColor));
        } else {
            int color = getContext().getResources().getColor(R.color.windowBackgroundColor);
            view.setBackgroundColor(color);
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        preferences = Preferences.getInstance(getContext());
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Bundle bundle = getArguments();
        if (bundle != null) {
            setPreferencesFromResource(R.xml.preferences, bundle.getString(PREFERENCE_SCREEN_KEY));
        } else {
            setPreferencesFromResource(R.xml.preferences, PreferenceScreens.PS_ROOT);
        }
//        Toast.makeText(getContext(), "OK", Toast.LENGTH_SHORT).show();
//        sizeParcelable = bundle.getParcelable(CAMERA_SUPPORTED_SIZES_PARCELABLE);
        findKeys();
    }

    @Override
    public void onNavigateToScreen(PreferenceScreen preferenceScreen) {
        Act_Preferences activity = (Act_Preferences) getActivity();
        if (activity != null) {
            activity.navigateTo(preferenceScreen.getKey(), true,
                    preferenceScreen.getTitle().toString());
        }
    }

    /**
     * این تابع زمانی فراخوانی می شود که اندازه عکس یا فیلم انتخاب نشده و این تابع اندازه ای را انتخاب و آن را در قالب مورد نیاز
     * ذخیره میکند و نمونه ای از CameraSize را بر میگرداند
     *
     * @param isRear اگر برابر با true باشد به معنای دوربین عقب است
     *               //@param isVideo اگر برابر با true باشد به انتخاب اندازه برای فیلم است
     *
     * @return Selected CameraSize
     *
     * @see #findKeys()
     */
    private CameraSize saveSuggestedSize(boolean isRear/*, boolean isVideo*/) {
        CameraSize size;
//        if (isVideo) {
//            if (CameraUtil.findCameraSize(Frg_BaseCamera.DEFAULT_VIDEO_SIZE, true, isRear)) {
//                size = new CameraSize(Frg_BaseCamera.DEFAULT_VIDEO_SIZE);
//            } else {
//                if (isRear) {
//                    size = CameraUtil.rearVideoSizes[0];
//                } else {
//                    size = CameraUtil.frontVideoSizes[0];
//                }
//            }
//            preferences.putString(isRear ? Preferences.Keys.VIDEO_SIZE_REAR :
//                    Preferences.Keys.VIDEO_SIZE_FRONT, size.toSwapString());
//        } else {
        if (isRear) {
            size = CameraUtil.rearPhotoSizes[0];
            preferences.putString(Preferences.Keys.PHOTO_SIZE_REAR, size.toString());
        } else {
            size = CameraUtil.frontPhotoSizes[0];
            preferences.putString(Preferences.Keys.PHOTO_SIZE_FRONT, size.toString());
        }
//        }
        return size;
    }

    private String getSizeStr(boolean isRear/*, boolean isPhoto*/) {
        String sizeStr;
//        if (isPhoto) {
        if (isRear) {
            sizeStr = preferences.getString(Preferences.Keys.PHOTO_SIZE_REAR, null);
            if (sizeStr == null) {
                sizeStr = saveSuggestedSize(true).toCompleteString(true, false);
            } else {
                sizeStr = CameraSize.fromString(sizeStr).toCompleteString(true, false);
            }
        } else {
            sizeStr = preferences.getString(Preferences.Keys.PHOTO_SIZE_FRONT, null);
            if (sizeStr == null) {
                sizeStr = saveSuggestedSize(false).toCompleteString(true, true);
            } else {
                sizeStr = CameraSize.fromString(sizeStr).toCompleteString(true, false);
            }
        }
//        } else {
//            if (isRear) {
//                sizeStr = preferences.getString(Preferences.Keys.VIDEO_SIZE_REAR, null);
//                if (sizeStr == null) {
//                    CameraSize size = saveSuggestedSize(true, true);
//                    sizeStr = size.toCompleteString(false, true);
//                } else {
//                    sizeStr = CameraSize.fromString(sizeStr).toCompleteString(true, true);
//                }
//            } else {
//                sizeStr = preferences.getString(Preferences.Keys.VIDEO_SIZE_FRONT, null);
//                if (sizeStr == null) {
//                    CameraSize size = saveSuggestedSize(false, true);
//                    sizeStr = size.toCompleteString(false, true);
//                } else {
//                    sizeStr = CameraSize.fromString(sizeStr).toCompleteString(true, true);
//                }
//            }
//        }
        return sizeStr;
    }

    /**
     * تنظیم تمامی Preference های موجود
     * تنظیم summery ها و سایر موارد
     *
     * @see #onCreatePreferences(Bundle, String)
     */
    private void findKeys() {
        String key = getPreferenceScreen().getKey();
        switch (key) {
            case PreferenceScreens.PS_ROOT: {
                Preference dataStorage = findPreference(Preferences.Keys.DATA_STORAGE);
                String     storagePath = preferences.getString(Preferences.Keys.DATA_STORAGE, null);
                if (storagePath == null) {
                    storagePath = StorageManager.getDefaultStoragePath().getPath();
                } else {
                    storagePath += File.separator + StorageManager.APP_DIRECTORY_NAME;
                }
                dataStorage.setSummary(storagePath);
                dataStorage.setOnPreferenceClickListener(this);
                break;
            }
            case PreferenceScreens.PS_PHOTO: {
                {
                    Preference photoSizeRear = findPreference(Preferences.Keys.PHOTO_SIZE_REAR);
                    photoSizeRear.setOnPreferenceClickListener(this);
                    photoSizeRear.setSummary(getSizeStr(true));
                }
                {
                    Preference photoSizeFront = findPreference(Preferences.Keys.PHOTO_SIZE_FRONT);

                    if (CameraUtil.hasFrontCamera(getContext())) {
                        photoSizeFront.setOnPreferenceClickListener(this);
                        photoSizeFront.setSummary(getSizeStr(false));
                    } else {
                        photoSizeFront.setVisible(false);
                    }
                }
                break;
            }
/*
            case PreferenceScreens.PS_VIDEO: {
                {
                    Preference videoSizeRear = findPreference(Preferences.Keys.VIDEO_SIZE_REAR);
                    videoSizeRear.setOnPreferenceClickListener(this);
                    videoSizeRear.setSummary(getSizeStr(true, false));
                }
                {
                    Preference videoSizeFront = findPreference(Preferences.Keys.VIDEO_SIZE_FRONT);
                    if (CameraUtil.hasFrontCamera(getContext())) {
                        videoSizeFront.setOnPreferenceClickListener(this);
                        videoSizeFront.setSummary(getSizeStr(false, false));
                    } else {
                        videoSizeFront.setVisible(false);
                    }
                }
                break;
            }
*/
        }
    }

    /**
     * ذخیره نمونه CameraSize در کلید پاس داده شده به تابع
     *
     * @param key        Preference key
     * @param cameraSize CameraSize object
     *
     * @see SelectorSizeDialogFragment#onCreateDialog(Bundle)
     */
    private void setSizeSummery(String key, CameraSize cameraSize, boolean pictureSize) {
        findPreference(key).setSummary(cameraSize.toCompleteString(true, !pictureSize));
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        return true;
    }

    /**
     * onPreferenceClick for all preferences
     *
     * @see #findKeys()
     */
    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();
        switch (key) {
            case Preferences.Keys.DATA_STORAGE: {
                showDirectoryPicker();
                break;
            }
            case Preferences.Keys.PHOTO_SIZE_REAR: {
                showSelectorSize(Frg_BaseCamera.CAMERA_FACING_REAR, true);
                break;
            }
            case Preferences.Keys.PHOTO_SIZE_FRONT: {
                showSelectorSize(Frg_BaseCamera.CAMERA_FACING_FRONT, true);
                break;
            }
            /*
            case Preferences.Keys.VIDEO_SIZE_REAR: {
                showSelectorSize(Frg_BaseCamera.CAMERA_FACING_REAR, false);
                break;
            }
            case Preferences.Keys.VIDEO_SIZE_FRONT: {
                showSelectorSize(Frg_BaseCamera.CAMERA_FACING_FRONT, false);
                break;
            }*/
        }
        return true;
    }

    /**
     * فراخوانی این تابع موجب نمایش دیالوگ انتخاب اندازه می شود
     *
     * @param cameraFacing cameraFacing {@link Frg_BaseCamera#CAMERA_FACING_FRONT, Frg_BaseCamera#CAMERA_FACING_REAR}
     *                     اگر برابر با CAMERA_FACING_FRONT شود اندازه های دوربین جلو
     *                     در غیر اینصورت اندازه های دوربین عقب
     * @param isPicture    اگر برابر با true باشد به این معناست که اندازه های مربوط به عکس باید نمایان شود
     */
    public void showSelectorSize(int cameraFacing, boolean isPicture) {
        new SelectorSizeDialogFragment(cameraFacing, isPicture)
                .show(getChildFragmentManager(), SELECTOR_SIZE_DIALOG);
    }

    /**
     * فراخوانی این تابع موجب نمایش directoryPicker می شود
     *
     * @see #onPreferenceClick(Preference)
     */
    public void showDirectoryPicker() {
        Intent intent = new Intent(getContext(), FolderPicker.class);
        intent.putExtra("title", "انتخاب مسیر");
        intent.putExtra("location", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath());
        intent.putExtra("pickFiles", false);
        startActivityForResult(intent, DIRECTORY_PICKER_REQUEST_CODE);
    }

    /**
     * نتیجه DirectoryPicker دریافت می شود و ذخیره می شود
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == DIRECTORY_PICKER_REQUEST_CODE) {
            if (data != null) {
                String path = data.getExtras().getString("data");

                preferences.edit().putString(Preferences.Keys.DATA_STORAGE, path).apply();

                path += File.separator + StorageManager.APP_DIRECTORY_NAME;
                findPreference(Preferences.Keys.DATA_STORAGE).setSummary(path);
            }
        }
    }

    public void saveCameraSize(CameraSize cameraSize, int facing, boolean isPhoto) {
        SharedPreferences.Editor editor = preferences.edit();
        if (facing == Frg_BaseCamera.CAMERA_FACING_REAR) {
            if (isPhoto) {
                editor.putString(Preferences.Keys.PHOTO_SIZE_REAR, cameraSize.toString());
                setSizeSummery(Preferences.Keys.PHOTO_SIZE_REAR, cameraSize, true);
            } else {
                editor.putString(Preferences.Keys.VIDEO_SIZE_REAR, cameraSize.toString());
                setSizeSummery(Preferences.Keys.VIDEO_SIZE_REAR, cameraSize, false);
            }
        } else {
            if (isPhoto) {
                editor.putString(Preferences.Keys.PHOTO_SIZE_FRONT, cameraSize.toString());
                setSizeSummery(Preferences.Keys.PHOTO_SIZE_FRONT, cameraSize, true);
            } else {
                editor.putString(Preferences.Keys.VIDEO_SIZE_FRONT, cameraSize.toString());
                setSizeSummery(Preferences.Keys.VIDEO_SIZE_FRONT, cameraSize, false);
            }
        }
        editor.apply();
    }

    private String getSizeStr(int facing/*, boolean isPhoto*/) {
        String strSize;
//        if (isPhoto) {
        if (facing == Frg_BaseCamera.CAMERA_FACING_REAR) {
            strSize = preferences.getString(Preferences.Keys.PHOTO_SIZE_REAR, null);
        } else {
            strSize = preferences.getString(Preferences.Keys.PHOTO_SIZE_FRONT, null);
        }
//        } else {
//            if (facing == Frg_BaseCamera.CAMERA_FACING_REAR) {
//                strSize = preferences.getString(Preferences.Keys.VIDEO_SIZE_REAR, null);
//            } else {
//                strSize = preferences.getString(Preferences.Keys.VIDEO_SIZE_FRONT, null);
//            }
//        }
        return strSize;
    }

    /**
     * دیالوگ انتخاب اندازه
     *
     * @see #showSelectorSize(int, boolean)
     */
    public static class SelectorSizeDialogFragment extends DialogFragment {
        int     facing;
        boolean isPhoto;

        public SelectorSizeDialogFragment(int facing, boolean isPhoto) {
            this.facing  = facing;
            this.isPhoto = isPhoto;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
            Frg_Preferences frg = (Frg_Preferences) getParentFragment();
            assert frg != null;
            String strSize = frg.getSizeStr(facing);

            CameraSize[]   cameraSizes   = CameraUtil.getCameraSizesArrayReference(facing, isPhoto);
            CharSequence[] charSequences = CameraUtil.toCharSequenceArray(cameraSizes, true, !isPhoto);

            final int currentPosition;
            if (strSize != null) {
                CameraSize cameraSize = CameraSize.fromString(strSize);
                currentPosition = CameraUtil.indexOf(cameraSizes, cameraSize);
            } else {
                currentPosition = 0;
            }
            return new AlertDialog.Builder(new ContextThemeWrapper(getContext(), R.style.ltrTheme))
//                    .setTitle(isPhoto ? "وضوح عکس" : "وضوح فیلم")
                    .setTitle(R.string.Str_PhotoResolution)
                    .setSingleChoiceItems(charSequences, currentPosition, (dialog, which) -> {
                        if (currentPosition == which) {
                            return;
                        }
                        frg.saveCameraSize(cameraSizes[which], facing, isPhoto);
                        dismiss();
                    }).setNegativeButton("لغو", null).create();
        }
    }

    /**
     * PreferenceScreens on preferences.xml
     */
    public static final class PreferenceScreens {
        public static final String PS_ROOT  = "ps_root";
        public static final String PS_PHOTO = "ps_photo";
//        public static final String PS_VIDEO = "ps_video";
    }
}
