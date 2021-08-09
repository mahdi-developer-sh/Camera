package com.example.camerax.util.parcel;

import android.os.Parcel;
import android.os.Parcelable;

import com.example.camerax.util.file.AppFile;

public class CaptureResult implements Parcelable {
    public static final Creator<CaptureResult> CREATOR = new Creator<CaptureResult>() {
        @Override
        public CaptureResult createFromParcel(Parcel in) {
            return new CaptureResult(in);
        }

        @Override
        public CaptureResult[] newArray(int size) {
            return new CaptureResult[size];
        }
    };

    private final AppFile file;

    public CaptureResult(AppFile file) {
        this.file = file;
    }

    protected CaptureResult(Parcel in) {
        file = (AppFile) in.readValue(getClass().getClassLoader());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeValue(file);
    }

    public AppFile getFile() {
        return file;
    }
}