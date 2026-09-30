package kz.medetakbolat.twin;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.DefaultMediaNotificationProvider;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlayerService extends MediaSessionService {
    private ExoPlayer player;
    private MediaSession session;
    private Handler handler;
    private ExecutorService artExecutor;

    private final Runnable saver=new Runnable(){
        @Override public void run(){
            if(player!=null&&player.getCurrentMediaItem()!=null){
                String id=player.getCurrentMediaItem().mediaId;
                Store.setNowPlaying(PlayerService.this,id);
                Store.saveProgress(PlayerService.this,id,player.getCurrentPosition(),player.getDuration());
            }
            if(handler!=null)handler.postDelayed(this,5000);
        }
    };

    @Override public void onCreate(){
        super.onCreate();
        player=new ExoPlayer.Builder(this).build();
        player.setHandleAudioBecomingNoisy(true);

        Intent open=new Intent(this,PlayerActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,17,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        session=new MediaSession.Builder(this,player).setSessionActivity(pi).build();

        DefaultMediaNotificationProvider provider=new DefaultMediaNotificationProvider.Builder(this)
                .setChannelId("twin_playback")
                .setChannelName(R.string.playback_channel)
                .setNotificationId(2207)
                .build();
        provider.setSmallIcon(R.drawable.ic_notification);
        setMediaNotificationProvider(provider);

        handler=new Handler(Looper.getMainLooper());
        artExecutor=Executors.newSingleThreadExecutor();

        player.addListener(new Player.Listener(){
            @Override public void onMediaItemTransition(MediaItem item,int reason){
                if(item!=null){
                    Store.setNowPlaying(PlayerService.this,item.mediaId);
                    Store.addRecent(PlayerService.this,item.mediaId);
                    enrichArtwork(item.mediaId);
                }
            }
        });
        handler.post(saver);
    }

    private void enrichArtwork(String mediaId){
        artExecutor.execute(()->{
            MediaEntry e=MediaRepository.resolve(PlayerService.this,mediaId);
            byte[] art=Artwork.bytes(PlayerService.this,e);
            handler.post(()->{
                if(player==null||player.getCurrentMediaItem()==null||!mediaId.equals(player.getCurrentMediaItem().mediaId))return;
                MediaItem old=player.getCurrentMediaItem();
                MediaMetadata.Builder mb=old.mediaMetadata.buildUpon();
                if(art!=null)mb.setArtworkData(art,MediaMetadata.PICTURE_TYPE_FRONT_COVER);
                if(e!=null){
                    mb.setTitle(e.title).setDisplayTitle(e.title).setArtist("Twin · "+(e.isVideo()?"Video":"Audio"));
                }
                player.replaceMediaItem(player.getCurrentMediaItemIndex(),old.buildUpon().setMediaMetadata(mb.build()).build());
            });
        });
    }

    @Nullable @Override public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo){return session;}

    @Override public void onDestroy(){
        if(handler!=null)handler.removeCallbacks(saver);
        if(artExecutor!=null)artExecutor.shutdownNow();
        if(player!=null&&player.getCurrentMediaItem()!=null)Store.saveProgress(this,player.getCurrentMediaItem().mediaId,player.getCurrentPosition(),player.getDuration());
        if(session!=null)session.release();if(player!=null)player.release();
        super.onDestroy();
    }
}
