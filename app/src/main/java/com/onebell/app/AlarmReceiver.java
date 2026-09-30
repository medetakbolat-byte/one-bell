package com.onebell.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.PowerManager;

public final class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        int id=i.getIntExtra(AlarmScheduler.EXTRA_ID,-1);
        if(id>=0) AlarmStore.remove(c,id);

        PendingResult pending=goAsync();
        PowerManager pm=(PowerManager)c.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl=pm==null?null:
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"OneBell:Ring");
        if(wl!=null) wl.acquire(10000L);

        BellPlayer.playOnce(c,()->{
            if(wl!=null && wl.isHeld()) wl.release();
            pending.finish();
        });
    }

    public static int volume(Context c){
        AudioManager a=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);
        if(a==null) return 0;
        int max=a.getStreamMaxVolume(AudioManager.STREAM_ALARM);
        return max==0?0:Math.round(100f*a.getStreamVolume(AudioManager.STREAM_ALARM)/max);
    }
}
