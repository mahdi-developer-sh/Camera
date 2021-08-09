package com.example.camerax.util.file;

import android.app.Activity;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

public class AppFile extends File {
    /**
     * نوع عکس
     */
    public static final int TYPE_PHOTO = 0;
    /**
     * نوع فیلم
     */
    public static final int TYPE_VIDEO = 1;

    /**
     * نوع فایل
     */
    private final int type;

    public AppFile(@Nullable String parent, @NonNull String child, @FileTypeDef int type) {
        super(parent, child);
        this.type = type;
    }

    public AppFile(@Nullable File parent, @NonNull String child, @FileTypeDef int type) {
        super(parent, child);
        this.type = type;
    }

    /**
     * getter for type
     *
     * @return {@link #type}
     */
    public int getType() {
        return type;
    }

    /**
     * برای اطمینان از این که آیا میتوان یک فایل را خواند
     * این تابع یک نمونه از runnable را دریافت کرده و هر زمان که فایل آماده خواندن شد آن را اجرا می کند
     *
     * @param activity اگر null باشد کار وظیفه را در پس زمنیه انجام می دهد، در غیر اینصورت در UI Thread انجام می شود
     * @param task     runnable instance
     */
    public void runOnFileReady(@Nullable Activity activity, @NonNull Runnable task) {
        final Runnable runnable = () -> {
            while (!canRead()) {
                try {
                    Thread.currentThread().join(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if (activity != null) {
                activity.runOnUiThread(task);
            } else {
                task.run();
            }
        };
        new Thread(runnable).start();
    }

    /**
     * مقادیر قابل قبول برای type
     *
     * @see #type
     * @see #AppFile(File, String, int)
     * @see #AppFile(String, String, int)
     */
    @IntDef(value = {TYPE_PHOTO, TYPE_VIDEO})
    public @interface FileTypeDef {

    }
}