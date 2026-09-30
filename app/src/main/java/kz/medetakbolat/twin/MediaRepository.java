package kz.medetakbolat.twin;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.OpenableColumns;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public final class MediaRepository {
    private MediaRepository(){}

    public static ArrayList<MediaEntry> scan(Context c) {
        ArrayList<MediaEntry> out=new ArrayList<>();
        query(c, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, false, out);
        query(c, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, out);
        Collections.sort(out, (a,b)->Long.compare(b.addedAt,a.addedAt));
        return out;
    }

    private static void query(Context c, Uri base, boolean video, ArrayList<MediaEntry> out) {
        ArrayList<String> p=new ArrayList<>();
        p.add(MediaStore.MediaColumns._ID);
        p.add(MediaStore.MediaColumns.DISPLAY_NAME);
        p.add(MediaStore.MediaColumns.DURATION);
        p.add(MediaStore.MediaColumns.DATE_ADDED);
        if(Build.VERSION.SDK_INT>=29)p.add(MediaStore.MediaColumns.RELATIVE_PATH);
        try(Cursor cur=c.getContentResolver().query(base,p.toArray(new String[0]),null,null,null)){
            if(cur==null)return;
            int id=cur.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
            int name=cur.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
            int dur=cur.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION);
            int date=cur.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED);
            int folder=Build.VERSION.SDK_INT>=29?cur.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH):-1;
            while(cur.moveToNext()){
                long row=cur.getLong(id);
                String n=cur.getString(name);
                long d=cur.isNull(dur)?0:cur.getLong(dur);
                long at=cur.isNull(date)?0:cur.getLong(date)*1000L;
                String f=folder>=0&&!cur.isNull(folder)?cur.getString(folder):"";
                Uri u=ContentUris.withAppendedId(base,row);
                out.add(new MediaEntry(u.toString(),n,d,at,video?"video":"audio",cleanFolder(f)));
            }
        }catch(Exception ignored){}
    }

    private static String cleanFolder(String f){
        if(f==null||f.isEmpty())return "Device";
        String s=f;
        while(s.endsWith("/"))s=s.substring(0,s.length()-1);
        int i=s.lastIndexOf('/'); return i>=0?s.substring(i+1):s;
    }

    public static MediaEntry resolve(Context c, String uriString) {
        if(uriString==null)return null;
        for(MediaEntry e:scan(c))if(uriString.equals(e.uri))return e;
        Uri uri=Uri.parse(uriString);
        String title="Media";
        try(Cursor cur=c.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(cur!=null&&cur.moveToFirst()){ int i=cur.getColumnIndex(OpenableColumns.DISPLAY_NAME); if(i>=0)title=cur.getString(i); }
        }catch(Exception ignored){}
        long dur=0; String type="audio";
        try{
            String mime=c.getContentResolver().getType(uri); if(mime!=null&&mime.startsWith("video/"))type="video";
            MediaMetadataRetriever r=new MediaMetadataRetriever(); r.setDataSource(c,uri);
            String ds=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION); if(ds!=null)dur=Long.parseLong(ds);
            String hasVideo=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO); if("yes".equalsIgnoreCase(hasVideo))type="video";
            r.release();
        }catch(Exception ignored){}
        return new MediaEntry(uriString,title,dur,System.currentTimeMillis(),type,"Opened files");
    }

    public static ArrayList<MediaEntry> resolveMany(Context c, java.util.List<String> uris) {
        ArrayList<MediaEntry> all=scan(c), out=new ArrayList<>();
        java.util.HashMap<String,MediaEntry> map=new java.util.HashMap<>();
        for(MediaEntry e:all)map.put(e.uri,e);
        for(String s:uris){ MediaEntry e=map.get(s); if(e==null)e=resolve(c,s); if(e!=null)out.add(e); }
        return out;
    }
}
