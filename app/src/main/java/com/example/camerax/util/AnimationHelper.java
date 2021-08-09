package com.example.camerax.util;

import android.view.View;
import android.view.ViewPropertyAnimator;

import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

public class AnimationHelper {

    /**
     * اجرای انیمیشن fade بر روی n تا نما
     *
     * @param fadeIn true for fadeIn and false for fadeout
     * @param views  views
     */
    public static void fade(boolean fadeIn, View... views) {
        for (View view : views) {
            ViewPropertyAnimator animator = view.animate().setDuration(120);
            if (fadeIn) {
                animator.alpha(1).withStartAction(() -> view.setVisibility(View.VISIBLE));
            } else {
                animator.alpha(0).withEndAction(() -> view.setVisibility(View.GONE));
            }
            animator.start();
        }
    }

    /**
     * اجرای انیمیشن fade بر روی n تا نما
     *
     * @param view     view
     * @param duration duration
     * @param show     true for show and false for hide
     */
    public static void translationY(View view, int duration, boolean show) {
        ViewPropertyAnimator animator = view.animate()
                .setDuration(duration)
                .setInterpolator(new FastOutSlowInInterpolator());
        if (show) {
            animator.translationY(0)
                    .alpha(1)
                    .withStartAction(() -> view.setVisibility(View.VISIBLE));
        } else {
            animator.translationY(25)
                    .alpha(0)
                    .withEndAction(() -> {
                        view.setTranslationY(-25);
                        view.setVisibility(View.GONE);
                    });
        }
        animator.start();
    }
}