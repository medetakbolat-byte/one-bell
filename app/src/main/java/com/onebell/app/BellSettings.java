package com.onebell.app;

import android.content.Context;

public final class BellSettings {
    private static final String PREF="one_bell_settings";
    private static final String VOL="bell_volume";
    private static final String URI="custom_sound_uri";
    private static final String NAME="custom_sound_name";
    private BellSettings(){}

    public static float volume(Context c){
        return c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getFloat(VOL,0.82f);
    }

    public static void setVolume(Context c,float v){
        float clean=Math.max(0.05f,Math.min(1f,v));
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putFloat(VOL,clean).apply();
    }

    public static String soundUri(Context c){
        return c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(URI,"");
    }

    public static String soundName(Context c){
        return c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(NAME,"One Bell");
    }

    public static void setSound(Context c,String uri,String name){
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
            .putString(URI,uri==null?"":uri)
            .putString(NAME,(name==null||name.trim().isEmpty())?"Custom audio":name)
            .apply();
    }

    public static void clearSound(Context c){
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
            .remove(URI).putString(NAME,"One Bell").apply();
    }
}
