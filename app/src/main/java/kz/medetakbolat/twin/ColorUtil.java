package kz.medetakbolat.twin;

import android.graphics.Color;

public final class ColorUtil {
    private ColorUtil(){}
    public static int alpha(int color,int alpha){return Color.argb(alpha,Color.red(color),Color.green(color),Color.blue(color));}
}
