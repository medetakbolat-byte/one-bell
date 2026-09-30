package kz.medetakbolat.twin;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.util.Size;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public final class Artwork {
    private Artwork(){}

    public static byte[] bytes(Context c,MediaEntry e){
        if(e==null)return null;
        Bitmap b=null;
        try{
            String custom=Store.mediaCover(c,e.uri);
            if(custom!=null&&!custom.isEmpty()){
                try(InputStream in=c.getContentResolver().openInputStream(Uri.parse(custom))){if(in!=null)b=BitmapFactory.decodeStream(in);}
            }
            if(b==null&&e.isVideo()&&Build.VERSION.SDK_INT>=29){
                try{b=c.getContentResolver().loadThumbnail(Uri.parse(e.uri),new Size(320,220),null);}catch(Exception ignored){}
            }
            if(b==null){
                MediaMetadataRetriever r=new MediaMetadataRetriever();r.setDataSource(c,Uri.parse(e.uri));
                if(e.isVideo())b=r.getFrameAtTime(Math.max(1_000_000L,e.duration*1000L/10L),MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                else{byte[] art=r.getEmbeddedPicture();if(art!=null)b=BitmapFactory.decodeByteArray(art,0,art.length);}
                r.release();
            }
            if(b==null)return null;
            int max=320;
            if(b.getWidth()>max||b.getHeight()>max){
                float scale=Math.min(max/(float)b.getWidth(),max/(float)b.getHeight());
                b=Bitmap.createScaledBitmap(b,Math.max(1,Math.round(b.getWidth()*scale)),Math.max(1,Math.round(b.getHeight()*scale)),true);
            }
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.JPEG,82,out);
            return out.toByteArray();
        }catch(Exception ignored){return null;}
    }
}
