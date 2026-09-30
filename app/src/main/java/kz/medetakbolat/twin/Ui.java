package kz.medetakbolat.twin;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class Ui {
    public static final int BG=Color.rgb(5,8,12);
    public static final int SURFACE=Color.rgb(14,18,24);
    public static final int SURFACE2=Color.rgb(23,29,37);
    public static final int TEXT=Color.rgb(244,247,250);
    public static final int MUTED=Color.rgb(143,153,164);
    public static final int ACCENT=Color.rgb(105,190,239);
    public static final int HAIR=Color.argb(44,255,255,255);

    private Ui(){}

    public static int dp(Context c,int v){return Math.round(v*c.getResources().getDisplayMetrics().density);}

    public static TextView text(Context c,String s,float sp,int color,boolean bold){
        TextView t=new TextView(c);t.setText(s);t.setTextSize(sp);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);t.setMaxLines(2);return t;
    }

    public static TextView icon(Context c,String s){
        TextView t=text(c,s,20,TEXT,false);t.setGravity(Gravity.CENTER);t.setClickable(true);t.setFocusable(true);
        t.setBackgroundColor(Color.TRANSPARENT);t.setPadding(dp(c,8),dp(c,8),dp(c,8),dp(c,8));return t;
    }

    public static TextView pill(Context c,String s){
        TextView t=text(c,s,13,TEXT,true);t.setGravity(Gravity.CENTER);t.setPadding(dp(c,12),dp(c,7),dp(c,12),dp(c,7));
        t.setBackground(round(SURFACE2,18,c));t.setClickable(true);return t;
    }

    public static GradientDrawable round(int color,int radiusDp,Context c){
        GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(c,radiusDp));return g;
    }

    public static LinearLayout row(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}

    public static TextView section(Context c,String s){
        TextView t=text(c,s,17,TEXT,true);t.setPadding(0,dp(c,16),0,dp(c,8));return t;
    }

    public static View hairline(Context c){
        View v=new View(c);v.setBackgroundColor(HAIR);v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,1));return v;
    }

    public static String time(long ms){
        long s=Math.max(0,ms/1000),h=s/3600,m=(s%3600)/60,sec=s%60;
        return h>0?String.format("%d:%02d:%02d",h,m,sec):String.format("%d:%02d",m,sec);
    }
}
