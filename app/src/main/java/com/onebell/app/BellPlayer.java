package com.onebell.app;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.media.ToneGenerator;

import java.util.concurrent.atomic.AtomicBoolean;

public final class BellPlayer {
    private BellPlayer(){}

    public static void playOnce(Context c,Runnable done){
        AtomicBoolean finished=new AtomicBoolean(false);
        try{
            MediaPlayer player=new MediaPlayer();
            AudioAttributes attrs=new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
            player.setAudioAttributes(attrs);

            String custom=BellSettings.soundUri(c);
            if(custom!=null && !custom.isEmpty()){
                player.setDataSource(c,Uri.parse(custom));
            }else{
                AssetFileDescriptor afd=c.getResources().openRawResourceFd(R.raw.one_bell);
                player.setDataSource(afd.getFileDescriptor(),afd.getStartOffset(),afd.getLength());
                afd.close();
            }

            float v=BellSettings.volume(c);
            player.setVolume(v,v);
            player.setLooping(false);
            player.setOnCompletionListener(mp->{
                try{mp.release();}catch(Exception ignored){}
                finishOnce(finished,done);
            });
            player.setOnErrorListener((mp,what,extra)->{
                try{mp.release();}catch(Exception ignored){}
                finishOnce(finished,done);
                return true;
            });
            player.prepare();
            player.start();

            new Handler(Looper.getMainLooper()).postDelayed(()->{
                if(finished.compareAndSet(false,true)){
                    try{if(player.isPlaying())player.stop();}catch(Exception ignored){}
                    try{player.release();}catch(Exception ignored){}
                    if(done!=null)done.run();
                }
            },15000L);
        }catch(Exception e){
            fallback(done);
        }
    }

    private static void finishOnce(AtomicBoolean flag,Runnable done){
        if(flag.compareAndSet(false,true) && done!=null)done.run();
    }

    private static void fallback(Runnable done){
        ToneGenerator tone=new ToneGenerator(AudioManager.STREAM_ALARM,100);
        tone.startTone(ToneGenerator.TONE_PROP_BEEP2,900);
        new Handler(Looper.getMainLooper()).postDelayed(()->{
            try{tone.stopTone();tone.release();}catch(Exception ignored){}
            if(done!=null)done.run();
        },1050L);
    }
}
