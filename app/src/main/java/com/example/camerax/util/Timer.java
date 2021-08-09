package com.example.camerax.util;

import android.os.Handler;
import android.os.Looper;

/**
 * اجرای وظیفه در یک حلقه با تاخیر مشخص شده
 */
public class Timer {
    private final Handler          handler;
    private final OnUpdateListener onUpdateListener;
    private final long             incrementerCount;
    private       long             time = 0;

    private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            time += incrementerCount;
            onUpdateListener.onUpdate(time);
            handler.postDelayed(this, incrementerCount);
        }
    };

    private Timer(int incrementerCount, OnUpdateListener onUpdateListener) {
        handler               = new Handler(Looper.getMainLooper());
        this.incrementerCount = incrementerCount;
        this.onUpdateListener = onUpdateListener;
    }

    public static Timer create(int incrementerCount, OnUpdateListener onUpdateListener) {
        return new Timer(incrementerCount, onUpdateListener);
    }

    public void reset() {
        handler.removeCallbacks(runnable);
        time = 0;
    }

    public void start() {
        handler.postDelayed(runnable, incrementerCount);
    }

    public interface OnUpdateListener {
        void onUpdate(long time);
    }
}