package com.onebell.app;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;

public final class BellPlayer {
    private BellPlayer(){}

    public static void playOnce(Context c,Runnable done){
        final ToneGenerator tone=new ToneGenerator(AudioManager.STREAM_ALARM,100);
        tone.startTone(ToneGenerator.TONE_PROP_BEEP2,1100);
        new Handler(Looper.getMainLooper()).postDelayed(()->{
            try{ tone.stopTone(); tone.release(); }catch(Exception ignored){}
            if(done!=null) done.run();
        },1250);
    }
}
