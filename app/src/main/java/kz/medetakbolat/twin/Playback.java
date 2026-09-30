package kz.medetakbolat.twin;

import android.content.ComponentName;
import android.content.Context;

import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;

public final class Playback {
    private Playback(){}

    private interface Action{void run(MediaController c);}

    private static void withController(Context ctx,Action action){
        Context app=ctx.getApplicationContext();SessionToken token=new SessionToken(app,new ComponentName(app,PlayerService.class));
        ListenableFuture<MediaController> f=new MediaController.Builder(app,token).buildAsync();
        f.addListener(()->{
            try{MediaController c=f.get();action.run(c);}catch(Exception ignored){}finally{MediaController.releaseFuture(f);}
        }, ContextCompat.getMainExecutor(app));
    }

    public static ArrayList<MediaItem> items(Context c,List<String> uris){
        ArrayList<MediaItem> out=new ArrayList<>();
        for(String u:uris){MediaEntry e=MediaRepository.resolve(c,u);MediaMetadata md=new MediaMetadata.Builder().setTitle(e==null?"Media":e.title).setArtist(e==null?"":(e.isVideo()?"Video":"Audio")).build();out.add(new MediaItem.Builder().setUri(u).setMediaId(u).setMediaMetadata(md).build());}
        return out;
    }

    public static void replaceAndPlay(Context c,List<String> uris,String startUri){
        withController(c,mc->{int idx=Math.max(0,uris.indexOf(startUri));long pos=Store.progress(c,startUri)[0];mc.setMediaItems(items(c,uris),idx,pos);mc.prepare();mc.play();});
    }

    public static void syncActiveQueue(Context c){
        Store.QueueDef q=Store.currentQueue(c);ArrayList<String> uris=new ArrayList<>(q.items);
        withController(c,mc->{boolean play=mc.isPlaying();String now=mc.getCurrentMediaItem()==null?Store.nowPlaying(c):mc.getCurrentMediaItem().mediaId;long pos=mc.getCurrentPosition();int idx=uris.indexOf(now);if(idx<0)idx=0;mc.setMediaItems(items(c,uris),idx,idx<uris.size()&&uris.get(idx).equals(now)?pos:0);mc.prepare();if(play)mc.play();});
    }

    public static void addNext(Context c,String uri){
        String qid=Store.currentQueueId(c);Store.addToQueue(c,qid,uri,true);syncActiveQueue(c);
    }

    public static void stop(Context c){withController(c,mc->{mc.stop();mc.clearMediaItems();Store.setNowPlaying(c,"");});}
}
