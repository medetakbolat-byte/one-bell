package kz.medetakbolat.twin;

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
import java.util.HashMap;
import java.util.HashSet;

public final class MediaRepository {
    private MediaRepository(){}

    public static ArrayList<MediaEntry> scan(Context c){
        ArrayList<MediaEntry> out=new ArrayList<>();HashSet<String>seen=new HashSet<>();
        query(c,MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,false,out,seen);
        query(c,MediaStore.Video.Media.EXTERNAL_CONTENT_URI,true,out,seen);
        for(String u:Store.getRecent(c))if(!seen.contains(u)){MediaEntry e=resolveDirect(c,u);if(e!=null){out.add(e);seen.add(u);}}
        Collections.sort(out,(a,b)->Long.compare(b.addedAt,a.addedAt));return out;
    }

    private static void query(Context c,Uri base,boolean video,ArrayList<MediaEntry> out,HashSet<String>seen){
        ArrayList<String> p=new ArrayList<>();p.add(MediaStore.MediaColumns._ID);p.add(MediaStore.MediaColumns.DISPLAY_NAME);p.add(MediaStore.MediaColumns.DURATION);p.add(MediaStore.MediaColumns.DATE_ADDED);if(Build.VERSION.SDK_INT>=29)p.add(MediaStore.MediaColumns.RELATIVE_PATH);
        try(Cursor cur=c.getContentResolver().query(base,p.toArray(new String[0]),null,null,null)){if(cur==null)return;
            int id=cur.getColumnIndexOrThrow(MediaStore.MediaColumns._ID),name=cur.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME),dur=cur.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION),date=cur.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED),folder=Build.VERSION.SDK_INT>=29?cur.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH):-1;
            while(cur.moveToNext()){Uri u=ContentUris.withAppendedId(base,cur.getLong(id));String us=u.toString(),n=cur.getString(name),alias=Store.alias(c,us);if(!alias.isEmpty())n=alias;long d=cur.isNull(dur)?0:cur.getLong(dur),at=cur.isNull(date)?0:cur.getLong(date)*1000L;String f=folder>=0&&!cur.isNull(folder)?cur.getString(folder):"";out.add(new MediaEntry(us,n,d,at,video?"video":"audio",cleanFolder(f)));seen.add(us);}
        }catch(Exception ignored){}
    }

    private static String cleanFolder(String f){if(f==null||f.isEmpty())return "Device";String s=f;while(s.endsWith("/"))s=s.substring(0,s.length()-1);int i=s.lastIndexOf('/');return i>=0?s.substring(i+1):s;}

    public static MediaEntry resolve(Context c,String uriString){
        if(uriString==null||uriString.isEmpty())return null;
        for(MediaEntry e:scanShallow(c))if(uriString.equals(e.uri))return e;
        return resolveDirect(c,uriString);
    }

    private static ArrayList<MediaEntry> scanShallow(Context c){
        ArrayList<MediaEntry> out=new ArrayList<>();HashSet<String>s=new HashSet<>();query(c,MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,false,out,s);query(c,MediaStore.Video.Media.EXTERNAL_CONTENT_URI,true,out,s);return out;
    }

    private static MediaEntry resolveDirect(Context c,String uriString){
        try{
            Uri uri=Uri.parse(uriString);String title="Media";long size=0;
            try(Cursor cur=c.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(cur!=null&&cur.moveToFirst()){int i=cur.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(i>=0&&!cur.isNull(i))title=cur.getString(i);}}
            String alias=Store.alias(c,uriString);if(!alias.isEmpty())title=alias;
            long dur=0;String type="audio";
            String mime=c.getContentResolver().getType(uri);if(mime!=null&&mime.startsWith("video/"))type="video";
            MediaMetadataRetriever r=new MediaMetadataRetriever();r.setDataSource(c,uri);String ds=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);if(ds!=null)dur=Long.parseLong(ds);String hv=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO);if("yes".equalsIgnoreCase(hv))type="video";r.release();
            return new MediaEntry(uriString,title,dur,System.currentTimeMillis(),type,"Opened");
        }catch(Exception e){return null;}
    }
}
