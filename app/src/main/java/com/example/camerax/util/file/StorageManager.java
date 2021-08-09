package com.example.camerax.util.file;

import android.content.Context;
import android.content.Intent;
import android.media.Image;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;

import androidx.annotation.RequiresApi;

import com.example.camerax.preferences.Preferences;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class StorageManager {
    /**
     * پوشه پیش فرض جهت ذخیره سازی
     *
     * @see #getDefaultStoragePath()
     * @see #getStorageDirectory(int)
     */
    public static final String APP_DIRECTORY_NAME = "CameraX";
    /**
     * نام دایرکتوری تصاویر
     *
     * @see #getStorageDirectory(int)
     */
    public static final String DIRECTORY_PHOTO    = "Photos";
    /**
     * نام دایرکتوری فیلم ها
     *
     * @see #getStorageDirectory(int)
     */
    public static final String DIRECTORY_VIDEO    = "Videos";

    /**
     * @see #getStorageDirectory(int)
     * @see ImageSaver
     */
    private final Context context;

    public StorageManager(Context context) {
        this.context = context;
    }

    /**
     * مسیر پیش فرض ذخیره سازی ر برمیگرداند
     *
     * @return default file path
     */
    public static File getDefaultStoragePath() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), APP_DIRECTORY_NAME);
    }

    /**
     * یک فایل را حذف کن و برنامه های گالری دیگر را از حذف شدن فایل مطلع کن
     *
     * @param context context
     * @param file    file for delete
     */
    public static void deleteFile(Context context, File file) {
        if (file.delete()) {
            galleryCheck(context, file);
        }
    }

    /**
     * با ذخیره یا حذف یک فایل تصویر یا فیلم این تابع فراخوانی شده و موجب آن می شود که سایر برنامه هایی که با
     * تصاویر و فیلم ها کار می کنند از وجود آن فایل مطلع شوند
     *
     * @param context context
     * @param file    file
     */
    private static void galleryCheck(Context context, File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaScannerConnection.scanFile(context,
                    new String[]{file.toString()},
                    new String[]{file.getName()}, null);
        } else {
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            Uri    contentUri      = Uri.fromFile(file);
            mediaScanIntent.setData(contentUri);
            context.sendBroadcast(mediaScanIntent);
        }
    }

    /**
     * این تابع مسیری مناسب را با توجه به type پاس داده شده را برمیگرداند این مسیر خالی از نام فایل است
     *
     * @param type {@link AppFile#type}
     *
     * @return instance of file
     */
    public AppFile getStorageDirectory(int type) {
        String path = Preferences.getInstance(context).getString(Preferences.Keys.DATA_STORAGE, null);

        String child = type == AppFile.TYPE_PHOTO ? DIRECTORY_PHOTO : DIRECTORY_VIDEO;

        if (path == null) {
            return new AppFile(StorageManager.getDefaultStoragePath(), child, type);
        }
        return new AppFile(path, APP_DIRECTORY_NAME + File.separator + child, type);
    }

    /**
     * بررسی می کند آیا این فایل یک فایل نامعتبر است یا نه
     *
     * @param file file
     *
     * @return true for valid and false for invalid
     */
    private boolean isInvalidFile(File file) {
        return !file.exists() && !file.mkdirs();
    }

    /**
     * این تابع یک نام یکتا را بازمیگرداند
     *
     * @param pre       پیشوند نام
     * @param extension پسوند نام
     *
     * @return unique name
     */
    private String getUniqueName(String pre, String extension) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US);
        return pre + format.format(new Date()) + "." + extension;
    }

    /**
     * این تابع یک نام یکتا را به file متصل کرده و آن را برمیگرداند
     *
     * @param file این فایل اکنون قابل ذخیره سازی است
     *
     * @return instance of {@link AppFile}
     */
    private AppFile attachFileName(AppFile file) {
        String fileName;
        if (file.getType() == AppFile.TYPE_PHOTO) {
            fileName = getUniqueName("IMG_", "jpg");
        } else {
            fileName = getUniqueName("VID_", "mp4");
        }
        return new AppFile(file, fileName, file.getType());
    }

    /**
     * این تابع فایلی یکتا را برای ذخیره سازی عکس یا فیلم برمیگرداند
     *
     * @param type {@link AppFile#type}
     *
     * @return instance of AppFile
     */
    public AppFile fileFor(@AppFile.FileTypeDef int type) {
        AppFile file = getStorageDirectory(type);
        if (isInvalidFile(file)) {
            throw new RuntimeException("File is not valid");
        }
        return attachFileName(file);
    }

    /**
     * ذخیره تصویر
     *
     * @param file file
     * @param data picture byte array
     */
    public void savePhoto(AppFile file, byte[] data) {
        FileOutputStream fileOutputStream;
        try {
            fileOutputStream = new FileOutputStream(file);
            fileOutputStream.write(data);
            fileOutputStream.close();
            fileOutputStream.flush();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            StorageManager.galleryCheck(context, file);
        }
    }

    /**
     * ذخیره Image در مسیر مشخص شده
     */
    @RequiresApi(Build.VERSION_CODES.KITKAT)
    public static class ImageSaver implements Runnable {
        private final Image   mImage;
        private final File    mFile;
        private final Context context;

        public ImageSaver(Image image, File file, Context context) {
            mImage       = image;
            mFile        = file;
            this.context = context;
        }

        @Override
        public void run() {
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[]     bytes  = new byte[buffer.remaining()];
            buffer.get(bytes);
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(mFile);
                output.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();
                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                StorageManager.galleryCheck(context, mFile);
            }
        }
    }
}