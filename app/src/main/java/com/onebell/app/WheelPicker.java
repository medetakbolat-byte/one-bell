package com.onebell.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

public final class WheelPicker extends View {
    private final Paint text=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint band=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int value;
    private final int max;
    private float downY,lastY,travel;
    private boolean moved;

    public WheelPicker(Context c,int max,int initial){
        super(c);
        this.max=max;
        this.value=wrap(initial);
        setMinimumWidth(dp(118));
        setMinimumHeight(dp(310));
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(android.graphics.Typeface.create("sans",android.graphics.Typeface.NORMAL));
        band.setColor(Color.rgb(29,25,52));
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1));
        stroke.setColor(Color.rgb(116,96,234));
        setFocusable(true);
    }

    public int getValue(){ return value; }
    public void setValue(int v){ value=wrap(v); invalidate(); }

    private int wrap(int v){
        int n=max+1;
        v%=n;
        if(v<0)v+=n;
        return v;
    }

    private void step(int delta){
        value=wrap(value+delta);
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        invalidate();
    }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float cx=getWidth()/2f, cy=getHeight()/2f;
        float row=dp(58);
        RectF r=new RectF(dp(6),cy-row*.72f,getWidth()-dp(6),cy+row*.72f);
        c.drawRoundRect(r,dp(18),dp(18),band);
        c.drawRoundRect(r,dp(18),dp(18),stroke);

        for(int offset=-2;offset<=2;offset++){
            int v=wrap(value+offset);
            float y=cy+offset*row;
            float size=offset==0?43:(Math.abs(offset)==1?28:22);
            int alpha=offset==0?255:(Math.abs(offset)==1?135:55);
            text.setTextSize(dpF(size));
            text.setColor(Color.argb(alpha,236,233,250));
            Paint.FontMetrics fm=text.getFontMetrics();
            float base=y-(fm.ascent+fm.descent)/2f;
            c.drawText(String.format(Locale.US,"%02d",v),cx,base,text);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e){
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                downY=lastY=e.getY(); travel=0; moved=false;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                float dy=e.getY()-lastY;
                lastY=e.getY(); travel+=dy;
                if(Math.abs(e.getY()-downY)>dp(5)) moved=true;
                float threshold=dp(34);
                while(travel>=threshold){ step(-1); travel-=threshold; }
                while(travel<=-threshold){ step(1); travel+=threshold; }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if(!moved){
                    float cy=getHeight()/2f;
                    if(e.getY()<cy-dp(22)) step(-1);
                    else if(e.getY()>cy+dp(22)) step(1);
                }
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
        }
        return super.onTouchEvent(e);
    }

    private int dp(int n){ return Math.round(n*getResources().getDisplayMetrics().density); }
    private float dpF(float n){ return n*getResources().getDisplayMetrics().scaledDensity; }
}
