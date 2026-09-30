package com.onebell.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        long now=System.currentTimeMillis();
        for(AlarmItem a:AlarmStore.load(c)) if(a.time>now) AlarmScheduler.schedule(c,a);
        AlarmStore.removeExpired(c,now);
    }
}
