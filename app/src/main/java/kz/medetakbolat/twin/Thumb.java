package kz.medetakbolat.twin;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.util.LruCache;
import android.util.Size;
import android.widget.ImageView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Thumb {
    private static final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(28*1024*1024){
        @Override protected int sizeOf(String k,Bitmap b){return b.getByteCount();}
    };
    private static final ExecutorService pool=Executors.newFixedThreadPool(3);
    private Thumb(){}

    public static void load(Context c,ImageView iv,MediaEntry e){load(c,iv,e,"");}

    public static void load(Context c,ImageView iv,MediaEntry e,String fallbackCover){
        if(e==null){iv.setImageResource(R.drawable.cover_placeholder);return;}
        String custom=Store.mediaCover(c,e.uri);
        String explicit=(custom!=null&&!custom.isEmpty())?custom:(fallbackCover==null?"":fallbackCover);
        String key=e.uri+"|"+explicit;
        iv.setTag(key);
        iv.setImageResource(R.drawable.cover_placeholder);

        if(!explicit.isEmpty()){
            try{iv.setImageURI(Uri.parse(explicit));return;}catch(Exception ignored){}
        }

        Bitmap hit=cache.get(key);if(hit!=null){iv.setImageBitmap(hit);return;}
        pool.execute(()->{
            Bitmap b=null;
            try{
                Uri u=Uri.parse(e.uri);
                if(Build.VERSION.SDK_INT>=29&&e.isVideo()){
                    try{b=c.getContentResolver().loadThumbnail(u,new Size(420,260),null);}catch(Exception ignored){}
                }
                if(b==null){
                    MediaMetadataRetriever r=new MediaMetadataRetriever();r.setDataSource(c,u);
                    if(e.isVideo()){
                        long us=Math.max(1000000L,(e.duration*1000L)/10L);
                        b=r.getFrameAtTime(us,MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    }else{
                        byte[] art=r.getEmbeddedPicture();
                        if(art!=null)b=BitmapFactory.decodeByteArray(art,0,art.length);
                    }
                    r.release();
                }
            }catch(Exception ignored){}
            if(b==null&&!e.isVideo())b=audioFallback(e.title);
            if(b!=null){
                b=scaled(b,480);
                cache.put(key,b);
                Bitmap finalB=b;
                iv.post(()->{if(key.equals(iv.getTag()))iv.setImageBitmap(finalB);});
            }
        });
    }

    private static Bitmap scaled(Bitmap b,int max){
        if(b.getWidth()<=max&&b.getHeight()<=max)return b;
        float s=Math.min(max/(float)b.getWidth(),max/(float)b.getHeight());
        return Bitmap.createScaledBitmap(b,Math.max(1,Math.round(b.getWidth()*s)),Math.max(1,Math.round(b.getHeight()*s)),true);
    }

    private static Bitmap audioFallback(String title){
        int size=360;Bitmap b=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);
        c.drawColor(Color.rgb(18,24,31));
        Paint line=new Paint(Paint.ANTI_ALIAS_FLAG);line.setStrokeWidth(18);line.setStrokeCap(Paint.Cap.ROUND);
        line.setColor(Color.argb(145,83,195,245));c.drawLine(58,95,272,265,line);
        line.setColor(Color.argb(125,141,98,255));c.drawLine(115,72,302,233,line);

        String token=cleanToken(title);
        Paint text=new Paint(Paint.ANTI_ALIAS_FLAG);text.setColor(Color.rgb(238,243,247));text.setTextAlign(Paint.Align.CENTER);text.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        text.setTextSize(token.length()>7?38:48);
        Rect r=new Rect();text.getTextBounds(token,0,token.length(),r);
        c.drawText(token,size/2f,size*.80f-r.exactCenterY(),text);
        return b;
    }

    private static String cleanToken(String s){
        if(s==null||s.trim().isEmpty())return "AUDIO";
        String x=s.replaceAll("\\.[A-Za-z0-9]{2,5}$","").replace('_',' ').trim();
        String[] parts=x.split("\\s+");
        if(parts.length>0&&parts[0].length()<=10)return parts[0].toUpperCase();
        return x.length()<=10?x.toUpperCase():x.substring(0,10).toUpperCase();
    }
}
