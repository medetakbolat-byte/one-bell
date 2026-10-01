package kz.medetakbolat.twin;

import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;

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
        f.addListener(()->{try{MediaController c=f.get();action.run(c);}catch(Exception ignored){}finally{MediaController.releaseFuture(f);}},ContextCompat.getMainExecutor(app));
    }

    public static MediaItem item(Context c,String uri,boolean withArtwork){
        MediaEntry e=MediaRepository.resolve(c,uri);
        MediaMetadata.Builder mb=new MediaMetadata.Builder().setTitle(e==null?"Media":e.title).setDisplayTitle(e==null?"Media":e.title).setArtist(e==null?"Twin":("Twin · "+(e.isVideo()?"Video":"Audio")));
        String custom=Store.mediaCover(c,uri);String fallback=(e!=null&&!e.isVideo())?Store.playbackFallbackCover(c):"";
        String art=(custom!=null&&!custom.isEmpty())?custom:fallback;if(art!=null&&!art.isEmpty())mb.setArtworkUri(Uri.parse(art));
        if(withArtwork){byte[] bytes=Artwork.bytes(c,e);if(bytes!=null)mb.setArtworkData(bytes,MediaMetadata.PICTURE_TYPE_FRONT_COVER);}
        return new MediaItem.Builder().setUri(uri).setMediaId(uri).setMediaMetadata(mb.build()).build();
    }

    public static ArrayList<MediaItem> items(Context c,List<String> uris,int focus){ArrayList<MediaItem>out=new ArrayList<>();for(int i=0;i<uris.size();i++)out.add(item(c,uris.get(i),Math.abs(i-focus)<=1));return out;}

    public static void replaceAndPlay(Context c,List<String> uris,String startUri){
        if(uris==null||uris.isEmpty()||startUri==null)return;ArrayList<String>safe=new ArrayList<>(uris);int idx=safe.indexOf(startUri);if(idx<0){safe.add(0,startUri);idx=0;}final int start=idx;final long pos=Store.progress(c,startUri)[0];
        withController(c,mc->{mc.setMediaItems(items(c,safe,start),start,pos);mc.setRepeatMode(Store.repeatMode(c));mc.prepare();mc.play();});
    }

    public static void syncActiveQueue(Context c){
        Store.QueueDef q=Store.currentQueue(c);ArrayList<String>uris=new ArrayList<>(q.items);
        withController(c,mc->{boolean wasPlaying=mc.isPlaying();String now=mc.getCurrentMediaItem()==null?Store.nowPlaying(c):mc.getCurrentMediaItem().mediaId;long pos=mc.getCurrentPosition();if(uris.isEmpty()){mc.clearMediaItems();return;}int idx=uris.indexOf(now);if(idx<0)idx=0;mc.setMediaItems(items(c,uris,idx),idx,idx<uris.size()&&uris.get(idx).equals(now)?pos:0);mc.setRepeatMode(Store.repeatMode(c));mc.prepare();if(wasPlaying)mc.play();});
    }

    public static void addNext(Context c,String uri){Store.addToQueue(c,Store.currentQueueId(c),uri,true);syncActiveQueue(c);}
    public static void stop(Context c){withController(c,mc->{mc.stop();mc.clearMediaItems();Store.setNowPlaying(c,"");Store.setPlaybackFallbackCover(c,"");});}
}
