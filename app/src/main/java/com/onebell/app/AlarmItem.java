package com.onebell.app;

public final class AlarmItem implements Comparable<AlarmItem> {
    public final int id;
    public final long time;
    public AlarmItem(int id, long time) { this.id = id; this.time = time; }
    @Override public int compareTo(AlarmItem o) { return Long.compare(time, o.time); }
}
