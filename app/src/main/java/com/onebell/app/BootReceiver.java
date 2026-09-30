package com.onebell.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        long now=System.currentTimeMillis();
        for(AlarmItem a:AlarmStore.load(c)){
            if(!a.enabled) continue;
            if(a.isAlways()){
                AlarmScheduler.schedule(c,a);
            }else if(a.time>now){
                AlarmScheduler.schedule(c,a);
            }else{
                AlarmStore.upsert(c,new AlarmItem(a.id,a.time,a.mode,false));
            }
        }
    }
}
