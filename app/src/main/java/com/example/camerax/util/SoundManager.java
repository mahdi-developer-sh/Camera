package com.example.camerax.util;

import android.content.Context;
import android.content.res.AssetManager;
import android.media.AudioManager;
import android.media.SoundPool;
import android.util.SparseIntArray;

import java.io.IOException;

public class SoundManager {
    public static SoundManager   instance;
    private final AudioManager   audioManager;
    private final SoundPool      soundPool;
    private final SparseIntArray soundMap;

    /**
     * private constructor
     *
     * @param context context
     */
    private SoundManager(Context context) {
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        soundPool    = new SoundPool(1, AudioManager.STREAM_MUSIC, 0);
        soundMap     = new SparseIntArray();
    }

    public synchronized static SoundManager getInstance(Context context) {
        if (instance == null) {
            instance = new SoundManager(context);
        }
        return instance;
    }

    public void addSoundsFromAssets(AssetManager manager, SoundUnit... units) {
        try {
            for (SoundUnit soundUnit : units) {
                soundMap.put(soundUnit.key, soundPool.load(manager.openFd(soundUnit.name), 1));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void playSoundEffect(int key) {
        int streamId = soundMap.get(key);
        if (streamId > 0) {
            float streamVolumeCurrent = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            float streamVolumeMax     = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            float volume              = streamVolumeCurrent / streamVolumeMax;
            soundPool.play(streamId, volume, volume, 1, 0, 1.0f);
        }
    }

    public static class SoundUnit {
        private final int    key;
        private final String name;

        public SoundUnit(int key, String name) {
            this.key  = key;
            this.name = name;
        }
    }
}