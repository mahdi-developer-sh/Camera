package com.example.camerax;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

public class DebugHelper {
    public static void log(Class<?> c, String message) {
        Log.i(c.getSimpleName(), message);
    }

    public static void log(Context context, String message) {
        Log.i(context.getClass().getSimpleName(), message);
    }

    public static void showToast(Context context, String text) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show();
    }
}