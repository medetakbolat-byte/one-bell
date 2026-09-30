package kz.medetakbolat.twin;

public class MediaEntry {
    public String uri;
    public String title;
    public long duration;
    public long addedAt;
    public String type;
    public String folder;

    public MediaEntry(String uri, String title, long duration, long addedAt, String type, String folder) {
        this.uri = uri;
        this.title = title == null ? "Untitled" : title;
        this.duration = duration;
        this.addedAt = addedAt;
        this.type = type == null ? "audio" : type;
        this.folder = folder == null ? "" : folder;
    }

    public boolean isVideo() { return "video".equals(type); }

    public String durationText() {
        long s = Math.max(0, duration / 1000);
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%d:%02d", m, sec);
    }
}
