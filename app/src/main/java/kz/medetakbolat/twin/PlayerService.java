package kz.medetakbolat.twin;

import android.app.PendingIntent;
import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

public class PlayerService extends MediaSessionService {
    private ExoPlayer player;
    private MediaSession session;

    @Override public void onCreate(){
        super.onCreate();
        player=new ExoPlayer.Builder(this).build();
        player.setHandleAudioBecomingNoisy(true);
        Intent open=new Intent(this,PlayerActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,7,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        session=new MediaSession.Builder(this,player).setSessionActivity(pi).build();
    }

    @Nullable @Override public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo){
        return session;
    }

    @Override public void onDestroy(){
        if(session!=null)session.release();
        if(player!=null)player.release();
        super.onDestroy();
    }
}
