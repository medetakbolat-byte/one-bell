package kz.medetakbolat.twin;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.util.LruCache;
import android.util.Size;
import android.widget.ImageView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Thumb {
    private static final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(24*1024*1024){
        @Override protected int sizeOf(String k,Bitmap b){return b.getByteCount();}
    };
    private static final ExecutorService pool=Executors.newFixedThreadPool(3);
    private Thumb(){}

    public static void load(Context c,ImageView iv,MediaEntry e){
        iv.setTag(e.uri);iv.setImageResource(R.drawable.cover_placeholder);
        String custom=Store.mediaCover(c,e.uri);
        if(custom!=null&&!custom.isEmpty()){try{iv.setImageURI(Uri.parse(custom));return;}catch(Exception ignored){}}
        Bitmap hit=cache.get(e.uri);if(hit!=null){iv.setImageBitmap(hit);return;}
        pool.execute(()->{
            Bitmap b=null;
            try{
                Uri u=Uri.parse(e.uri);
                if(Build.VERSION.SDK_INT>=29 && e.isVideo())b=c.getContentResolver().loadThumbnail(u,new Size(420,260),null);
                if(b==null){
                    MediaMetadataRetriever r=new MediaMetadataRetriever();r.setDataSource(c,u);
                    if(e.isVideo()){long us=Math.max(1000000L,(e.duration*1000L)/10L);b=r.getFrameAtTime(us,MediaMetadataRetriever.OPTION_CLOSEST_SYNC);}
                    else{byte[] art=r.getEmbeddedPicture();if(art!=null)b=BitmapFactory.decodeByteArray(art,0,art.length);}
                    r.release();
                }
            }catch(Exception ignored){}
            if(b!=null){cache.put(e.uri,b);Bitmap finalB=b;iv.post(()->{if(e.uri.equals(iv.getTag()))iv.setImageBitmap(finalB);});}
        });
    }
}
