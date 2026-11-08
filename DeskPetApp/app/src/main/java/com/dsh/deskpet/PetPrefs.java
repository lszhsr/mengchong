package com.dsh.deskpet;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

/** 统一的设置读写：大小、透明度、圆角、显示状态。 */
public final class PetPrefs {

    public static final String NAME = "pet";

    public static final String KEY_SIZE = "size_dp";
    public static final String KEY_ALPHA = "alpha_pct";
    public static final String KEY_CORNER = "corner_pct";
    public static final String KEY_VISIBLE = "visible";

    public static final int SIZE_MIN = 60;
    public static final int SIZE_MAX = 320;
    public static final int SIZE_DEFAULT = 140;
    public static final int ALPHA_DEFAULT = 100;
    public static final int CORNER_DEFAULT = 30;

    private PetPrefs() {
    }

    public static SharedPreferences of(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static File imageFile(Context context) {
        return new File(context.getFilesDir(), "pet.png");
    }

    public static boolean hasImage(Context context) {
        File f = imageFile(context);
        return f.exists() && f.length() > 0;
    }
}
