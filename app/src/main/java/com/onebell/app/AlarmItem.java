package com.onebell.app;

public final class AlarmItem implements Comparable<AlarmItem> {
    public static final int MODE_DATE=0;
    public static final int MODE_ALWAYS=1;

    public final int id;
    public final long time;
    public final int mode;
    public final boolean enabled;

    public AlarmItem(int id,long time){
        this(id,time,MODE_DATE,true);
    }

    public AlarmItem(int id,long time,int mode,boolean enabled){
        this.id=id;
        this.time=time;
        this.mode=mode;
        this.enabled=enabled;
    }

    public boolean isAlways(){ return mode==MODE_ALWAYS; }

    public AlarmItem withEnabled(boolean value){
        return new AlarmItem(id,time,mode,value);
    }

    public AlarmItem withTime(long value){
        return new AlarmItem(id,value,mode,enabled);
    }

    @Override public int compareTo(AlarmItem o){
        if(enabled!=o.enabled) return enabled?-1:1;
        return Long.compare(time,o.time);
    }
}
