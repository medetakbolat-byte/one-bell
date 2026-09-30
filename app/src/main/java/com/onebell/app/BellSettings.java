package com.onebell.app;

import android.content.Context;

public final class BellSettings {
    private static final String PREF="one_bell_settings";
    private static final String VOL="bell_volume";
    private BellSettings(){}

    public static float volume(Context c){
        return c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getFloat(VOL,0.82f);
    }

    public static void setVolume(Context c,float v){
        float clean=Math.max(0.05f,Math.min(1f,v));
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putFloat(VOL,clean).apply();
    }
}
