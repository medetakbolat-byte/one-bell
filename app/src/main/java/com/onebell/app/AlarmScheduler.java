package com.onebell.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.util.Calendar;

public final class AlarmScheduler {
    public static final String EXTRA_ID="alarm_id";
    private AlarmScheduler(){}

    public static boolean canExact(Context c){
        if(Build.VERSION.SDK_INT<Build.VERSION_CODES.S) return true;
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        return am!=null && am.canScheduleExactAlarms();
    }

    public static Intent permissionIntent(Context c){
        Intent i=new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
        i.setData(Uri.parse("package:"+c.getPackageName()));
        return i;
    }

    private static PendingIntent fire(Context c,int id){
        Intent i=new Intent(c,AlarmReceiver.class);
        i.setAction("com.onebell.app.FIRE_"+id);
        i.putExtra(EXTRA_ID,id);
        return PendingIntent.getBroadcast(c,id,i,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent show(Context c,int id){
        Intent i=new Intent(c,MainActivity.class);
        i.setAction("com.onebell.app.SHOW_"+id);
        return PendingIntent.getActivity(c,id+1000000,i,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }

    public static long nextDaily(long source,long now){
        Calendar src=Calendar.getInstance();
        src.setTimeInMillis(source);

        Calendar next=Calendar.getInstance();
        next.setTimeInMillis(now);
        next.set(Calendar.HOUR_OF_DAY,src.get(Calendar.HOUR_OF_DAY));
        next.set(Calendar.MINUTE,src.get(Calendar.MINUTE));
        next.set(Calendar.SECOND,0);
        next.set(Calendar.MILLISECOND,0);
        if(next.getTimeInMillis()<=now) next.add(Calendar.DAY_OF_YEAR,1);
        return next.getTimeInMillis();
    }

    public static boolean schedule(Context c,AlarmItem original){
        if(original==null) return false;
        if(!original.enabled){
            cancel(c,original.id);
            return true;
        }

        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(am==null || !canExact(c)) return false;

        AlarmItem a=original;
        long now=System.currentTimeMillis();

        if(a.isAlways()){
            long next=nextDaily(a.time,now);
            if(next!=a.time){
                a=new AlarmItem(a.id,next,a.mode,true);
                AlarmStore.upsert(c,a);
            }
        }else if(a.time<=now){
            return false;
        }

        am.setAlarmClock(new AlarmManager.AlarmClockInfo(a.time,show(c,a.id)),fire(c,a.id));
        return true;
    }

    public static void cancel(Context c,int id){
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(am!=null) am.cancel(fire(c,id));
    }
}
