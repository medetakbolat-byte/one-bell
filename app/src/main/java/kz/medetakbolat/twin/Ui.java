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
    public static final int BG = Color.rgb(9,13,18);
    public static final int SURFACE = Color.rgb(18,25,35);
    public static final int SURFACE2 = Color.rgb(24,34,48);
    public static final int TEXT = Color.rgb(245,247,250);
    public static final int MUTED = Color.rgb(147,160,174);
    public static final int ACCENT = Color.rgb(72,183,242);

    private Ui() {}

    public static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    public static TextView button(Context c, String s) {
        TextView t = text(c, s, 14, TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c,14), dp(c,10), dp(c,14), dp(c,10));
        t.setBackground(round(SURFACE2, 14, c));
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    public static GradientDrawable round(int color, int radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c,14), dp(c,12), dp(c,14), dp(c,12));
        l.setBackground(round(SURFACE, 16, c));
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static void margins(View v, int l, int t, int r, int b) {
        ViewGroup.LayoutParams p = v.getLayoutParams();
        LinearLayout.LayoutParams lp;
        if (p instanceof LinearLayout.LayoutParams) lp = (LinearLayout.LayoutParams)p;
        else lp = new LinearLayout.LayoutParams(p == null ? ViewGroup.LayoutParams.MATCH_PARENT : p.width,
                p == null ? ViewGroup.LayoutParams.WRAP_CONTENT : p.height);
        lp.setMargins(dp(v.getContext(),l),dp(v.getContext(),t),dp(v.getContext(),r),dp(v.getContext(),b));
        v.setLayoutParams(lp);
    }

    public static TextView section(Context c, String s) {
        TextView t = text(c, s, 17, TEXT, true);
        t.setPadding(0, dp(c,12), 0, dp(c,8));
        return t;
    }

    public static String time(long ms) {
        long s = Math.max(0, ms/1000);
        long h=s/3600, m=(s%3600)/60, sec=s%60;
        return h>0 ? String.format("%d:%02d:%02d",h,m,sec) : String.format("%d:%02d",m,sec);
    }
}
