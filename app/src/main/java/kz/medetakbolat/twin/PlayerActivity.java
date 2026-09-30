package kz.medetakbolat.twin;

import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Rect;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Rational;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@UnstableApi
public class PlayerActivity extends AppCompatActivity {
    private ListenableFuture<MediaController> future;
    private MediaController controller;
    private PlayerView playerView;
    private ImageView artwork;
    private TextView title,subtitle,current,total,playButton,speedButton,zoomLabel;
    private SeekBar seek;
    private Handler handler=new Handler(Looper.getMainLooper());
    private String startUri;
    private String currentUri="";
    private boolean draggingSeek=false;
    private boolean fullscreen=false;
    private boolean sleepAtEnd=false;
    private float videoScale=1f;
    private float panX=0f,panY=0f,lastTouchX,lastTouchY;
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    private final Runnable ticker=new Runnable(){
        @Override public void run(){
            if(controller!=null){
                long p=controller.getCurrentPosition(),d=controller.getDuration();
                if(d>0 && !draggingSeek)seek.setProgress((int)Math.min(1000,p*1000/d));
                current.setText(Ui.time(p));total.setText(Ui.time(Math.max(0,d)));
                if(!currentUri.isEmpty())Store.saveProgress(PlayerActivity.this,currentUri,p,d);
                playButton.setText(controller.isPlaying()?"Ⅱ":"▶");
            }
            handler.postDelayed(this,700);
        }
    };

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        startUri=getIntent().getStringExtra("uri");
        buildUi();
        connect();
        getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true){
            @Override public void handleOnBackPressed(){ if(fullscreen)exitFullscreen(); else finish(); }
        });
    }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Ui.BG);

        LinearLayout top=Ui.row(this);top.setPadding(Ui.dp(this,10),Ui.dp(this,8),Ui.dp(this,10),Ui.dp(this,5));
        TextView back=Ui.button(this,"‹");back.setOnClickListener(v->{if(fullscreen)exitFullscreen();else finish();});top.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,44)));
        TextView spacer=Ui.text(this,"",1,Ui.TEXT,false);top.addView(spacer,new LinearLayout.LayoutParams(0,Ui.dp(this,44),1));
        zoomLabel=Ui.button(this,"Fit");zoomLabel.setOnClickListener(v->zoomMenu());top.addView(zoomLabel,new LinearLayout.LayoutParams(Ui.dp(this,64),Ui.dp(this,44)));
        TextView more=Ui.button(this,"⋮");more.setOnClickListener(v->moreMenu());top.addView(more,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,44)));
        root.addView(top);

        FrameLayout mediaFrame=new FrameLayout(this);mediaFrame.setClipChildren(true);mediaFrame.setBackgroundColor(android.graphics.Color.BLACK);
        playerView=new PlayerView(this);playerView.setUseController(false);playerView.setBackgroundColor(android.graphics.Color.BLACK);playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        mediaFrame.addView(playerView,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));

        artwork=new ImageView(this);artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);artwork.setImageResource(R.drawable.cover_placeholder);artwork.setPadding(Ui.dp(this,44),Ui.dp(this,44),Ui.dp(this,44),Ui.dp(this,44));
        mediaFrame.addView(artwork,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));

        TextView full=Ui.button(this,"⛶");FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,44),Gravity.TOP|Gravity.RIGHT);fp.setMargins(0,Ui.dp(this,8),Ui.dp(this,8),0);mediaFrame.addView(full,fp);full.setOnClickListener(v->toggleFullscreen());
        root.addView(mediaFrame,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setGravity(Gravity.CENTER_HORIZONTAL);info.setPadding(Ui.dp(this,18),Ui.dp(this,10),Ui.dp(this,18),0);
        title=Ui.text(this,"Media",19,Ui.TEXT,true);title.setGravity(Gravity.CENTER);subtitle=Ui.text(this,"",13,Ui.MUTED,false);subtitle.setGravity(Gravity.CENTER);info.addView(title);info.addView(subtitle);root.addView(info);

        LinearLayout timeline=Ui.row(this);timeline.setPadding(Ui.dp(this,18),Ui.dp(this,6),Ui.dp(this,18),0);
        current=Ui.text(this,"0:00",12,Ui.MUTED,false);total=Ui.text(this,"0:00",12,Ui.MUTED,false);seek=new SeekBar(this);seek.setMax(1000);seek.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));seek.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        timeline.addView(current,new LinearLayout.LayoutParams(Ui.dp(this,56),Ui.dp(this,44)));timeline.addView(seek,new LinearLayout.LayoutParams(0,Ui.dp(this,44),1));timeline.addView(total,new LinearLayout.LayoutParams(Ui.dp(this,62),Ui.dp(this,44)));root.addView(timeline);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean user){if(user&&controller!=null&&controller.getDuration()>0)current.setText(Ui.time(controller.getDuration()*p/1000));}public void onStartTrackingTouch(SeekBar s){draggingSeek=true;}public void onStopTrackingTouch(SeekBar s){draggingSeek=false;if(controller!=null&&controller.getDuration()>0)controller.seekTo(controller.getDuration()*s.getProgress()/1000);}});

        LinearLayout transport=Ui.row(this);transport.setGravity(Gravity.CENTER);transport.setPadding(Ui.dp(this,18),0,Ui.dp(this,18),Ui.dp(this,6));
        TextView back10=Ui.button(this,"−10");playButton=Ui.button(this,"▶");TextView fwd10=Ui.button(this,"+10");
        back10.setOnClickListener(v->seekBy(-10000));fwd10.setOnClickListener(v->seekBy(10000));playButton.setOnClickListener(v->togglePlay());
        transport.addView(back10,new LinearLayout.LayoutParams(Ui.dp(this,72),Ui.dp(this,54)));Ui.margins(back10,6,0,14,0);
        transport.addView(playButton,new LinearLayout.LayoutParams(Ui.dp(this,76),Ui.dp(this,60)));
        transport.addView(fwd10,new LinearLayout.LayoutParams(Ui.dp(this,72),Ui.dp(this,54)));Ui.margins(fwd10,14,0,6,0);root.addView(transport);

        LinearLayout tools=Ui.row(this);tools.setPadding(Ui.dp(this,18),0,Ui.dp(this,18),Ui.dp(this,12));
        speedButton=Ui.button(this,"1.0×");TextView bookmark=Ui.button(this,"Bookmark");TextView queue=Ui.button(this,"Queue");
        tools.addView(speedButton,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));tools.addView(bookmark,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));tools.addView(queue,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));Ui.margins(bookmark,8,0,8,0);
        speedButton.setOnClickListener(v->speedMenu());bookmark.setOnClickListener(v->addBookmark());queue.setOnClickListener(v->queueDialog());root.addView(tools);

        setContentView(root);
        setupVideoGestures();
    }

    private void connect(){
        SessionToken token=new SessionToken(this,new ComponentName(this,PlayerService.class));
        future=new MediaController.Builder(this,token).buildAsync();
        future.addListener(()->{
            try{
                controller=future.get();playerView.setPlayer(controller);
                controller.addListener(new Player.Listener(){
                    @Override public void onMediaItemTransition(MediaItem item,int reason){
                        if(sleepAtEnd&&reason==Player.MEDIA_ITEM_TRANSITION_REASON_AUTO){controller.pause();sleepAtEnd=false;}
                        updateCurrent();
                    }
                    @Override public void onIsPlayingChanged(boolean playing){playButton.setText(playing?"Ⅱ":"▶");}
                });
                prepareQueue();
            }catch(Exception e){Toast.makeText(this,"Player could not start",Toast.LENGTH_LONG).show();}
        },ContextCompat.getMainExecutor(this));
    }

    private void prepareQueue(){
        Store.QueueDef q=Store.currentQueue(this);
        ArrayList<String> uris=new ArrayList<>(q.items);
        if(startUri==null||startUri.isEmpty()){
            if(controller.getCurrentMediaItem()!=null)startUri=controller.getCurrentMediaItem().mediaId;
            else if(!uris.isEmpty())startUri=uris.get(0);
        }
        if(startUri==null)return;
        if(!uris.contains(startUri))uris.add(0,startUri);
        ArrayList<MediaItem> items=new ArrayList<>();
        int start=0;
        for(int i=0;i<uris.size();i++){
            String u=uris.get(i);MediaEntry e=MediaRepository.resolve(this,u);
            MediaMetadata md=new MediaMetadata.Builder().setTitle(e==null?"Media":e.title).build();
            items.add(new MediaItem.Builder().setUri(u).setMediaId(u).setMediaMetadata(md).build());if(u.equals(startUri))start=i;
        }
        long saved=Store.progress(this,startUri)[0];
        controller.setMediaItems(items,start,saved);controller.prepare();
        float sp=Store.speed(this,startUri);controller.setPlaybackSpeed(sp);speedButton.setText(trimSpeed(sp)+"×");
        controller.play();updateCurrent();handler.post(ticker);
    }

    private void updateCurrent(){
        if(controller==null||controller.getCurrentMediaItem()==null)return;
        currentUri=controller.getCurrentMediaItem().mediaId;Store.addRecent(this,currentUri);
        MediaEntry e=MediaRepository.resolve(this,currentUri);if(e==null)return;
        title.setText(e.title);subtitle.setText(e.isVideo()?"Video":"Audio");
        artwork.setVisibility(e.isVideo()?View.GONE:View.VISIBLE);playerView.setVisibility(View.VISIBLE);
        zoomLabel.setVisibility(e.isVideo()?View.VISIBLE:View.INVISIBLE);
        float sp=Store.speed(this,currentUri);controller.setPlaybackSpeed(sp);speedButton.setText(trimSpeed(sp)+"×");
        videoScale=1f;panX=panY=0;applyTransform();
    }

    private String trimSpeed(float f){if(Math.abs(f-Math.round(f))<0.001)return String.format("%.1f",f);return String.format("%.2f",f).replaceAll("0$","");}

    private void togglePlay(){if(controller==null)return;if(controller.isPlaying())controller.pause();else controller.play();}
    private void seekBy(long ms){if(controller!=null)controller.seekTo(Math.max(0,controller.getCurrentPosition()+ms));}

    private void speedMenu(){
        String[] labels={"0.5×","0.75×","1.0×","1.25×","1.5×","1.75×","2.0×","Custom…"};
        new AlertDialog.Builder(this).setTitle("Playback speed").setItems(labels,(d,w)->{
            if(w==7){customSpeed();return;}
            float[] vals={.5f,.75f,1f,1.25f,1.5f,1.75f,2f};setSpeed(vals[w]);
        }).show();
    }

    private void customSpeed(){
        EditText e=new EditText(this);e.setHint("0.5 – 3.0");e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        new AlertDialog.Builder(this).setTitle("Custom speed").setView(e).setPositiveButton("Set",(d,w)->{try{float s=Float.parseFloat(e.getText().toString());setSpeed(Math.max(.25f,Math.min(3f,s)));}catch(Exception ignored){}}).show();
    }

    private void setSpeed(float s){if(controller!=null)controller.setPlaybackSpeed(s);if(!currentUri.isEmpty())Store.setSpeed(this,currentUri,s);speedButton.setText(trimSpeed(s)+"×");}

    private void addBookmark(){
        if(controller==null||currentUri.isEmpty())return;Store.addBookmark(this,currentUri,controller.getCurrentPosition());Toast.makeText(this,"Bookmark · "+Ui.time(controller.getCurrentPosition()),Toast.LENGTH_SHORT).show();
    }

    private void bookmarksDialog(){
        ArrayList<Long>b=Store.bookmarks(this,currentUri);if(b.isEmpty()){Toast.makeText(this,"No bookmarks yet",Toast.LENGTH_SHORT).show();return;}
        String[] labels=new String[b.size()];for(int i=0;i<b.size();i++)labels[i]=Ui.time(b.get(i));
        new AlertDialog.Builder(this).setTitle("Bookmarks").setItems(labels,(d,w)->{if(controller!=null)controller.seekTo(b.get(w));}).show();
    }

    private void queueDialog(){
        if(controller==null)return;int n=controller.getMediaItemCount();String[] names=new String[n];
        for(int i=0;i<n;i++){MediaItem mi=controller.getMediaItemAt(i);CharSequence t=mi.mediaMetadata.title;names[i]=(i==controller.getCurrentMediaItemIndex()?"▶  ":"")+((t==null||t.length()==0)?"Media":t.toString());}
        new AlertDialog.Builder(this).setTitle("Up next").setItems(names,(d,w)->controller.seekToDefaultPosition(w))
                .setPositiveButton("Open full queue",(d,w)->{Intent i=new Intent(this,OrganizeActivity.class);i.putExtra("mode","queue");i.putExtra("queue",Store.currentQueueId(this));startActivity(i);}).show();
    }

    private void moreMenu(){
        ArrayList<String> o=new ArrayList<>();o.add("Bookmarks");o.add("Sleep timer");if(isVideo()){o.add("Fit / Fill / Crop");if(Build.VERSION.SDK_INT>=26)o.add("Picture in picture");}o.add("Media info");
        new AlertDialog.Builder(this).setItems(o.toArray(new String[0]),(d,w)->{
            String x=o.get(w);if("Bookmarks".equals(x))bookmarksDialog();else if("Sleep timer".equals(x))sleepMenu();else if("Fit / Fill / Crop".equals(x))zoomMenu();else if("Picture in picture".equals(x))enterPip();else mediaInfo();
        }).show();
    }

    private void sleepMenu(){
        String[] s={"15 minutes","30 minutes","60 minutes","End of current media","Off"};
        new AlertDialog.Builder(this).setTitle("Sleep timer").setItems(s,(d,w)->{
            handler.removeCallbacksAndMessages("sleep");sleepAtEnd=false;
            if(w<3){long[]mins={15,30,60};Runnable r=()->{if(controller!=null)controller.pause();};handler.postAtTime(r,"sleep",android.os.SystemClock.uptimeMillis()+mins[w]*60000L);Toast.makeText(this,"Sleep timer set",Toast.LENGTH_SHORT).show();}
            else if(w==3){sleepAtEnd=true;Toast.makeText(this,"Stops at end",Toast.LENGTH_SHORT).show();}
        }).show();
    }

    private void mediaInfo(){
        MediaEntry e=MediaRepository.resolve(this,currentUri);if(e==null)return;
        new AlertDialog.Builder(this).setTitle(e.title).setMessage((e.isVideo()?"Video":"Audio")+"\n"+e.durationText()+"\n"+e.folder+"\n\n"+e.uri).setPositiveButton("OK",null).show();
    }

    private boolean isVideo(){MediaEntry e=MediaRepository.resolve(this,currentUri);return e!=null&&e.isVideo();}

    private void zoomMenu(){
        if(!isVideo())return;String[] z={"Fit (show all)","Fill / crop","Stretch","Reset pinch zoom"};
        new AlertDialog.Builder(this).setTitle("Video size").setItems(z,(d,w)->{
            if(w==0){playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);zoomLabel.setText("Fit");resetTransform();}
            else if(w==1){playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);zoomLabel.setText("Fill");resetTransform();}
            else if(w==2){playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);zoomLabel.setText("Fill");resetTransform();}
            else resetTransform();
        }).show();
    }

    private void setupVideoGestures(){
        scaleDetector=new ScaleGestureDetector(this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector d){videoScale=Math.max(1f,Math.min(3f,videoScale*d.getScaleFactor()));applyTransform();zoomLabel.setText(Math.round(videoScale*100)+"%");return true;}
        });
        gestureDetector=new GestureDetector(this,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(MotionEvent e){lastTouchX=e.getX();lastTouchY=e.getY();return true;}
            @Override public boolean onDoubleTap(MotionEvent e){
                float x=e.getX(),w=playerView.getWidth();if(x<w*.35f)seekBy(-10000);else if(x>w*.65f)seekBy(10000);else togglePlay();return true;
            }
            @Override public boolean onScroll(MotionEvent e1,MotionEvent e2,float dx,float dy){
                if(scaleDetector.isInProgress())return true;
                if(videoScale>1.01f){panX-=dx;panY-=dy;applyTransform();return true;}
                if(Math.abs(dx)>Math.abs(dy)){seekBy((long)(-dx*85));return true;}
                if(e1!=null&&e1.getX()<playerView.getWidth()/2f)adjustBrightness(-dy/playerView.getHeight());else adjustVolume(-dy/playerView.getHeight());
                return true;
            }
        });
        playerView.setOnTouchListener((v,e)->{scaleDetector.onTouchEvent(e);gestureDetector.onTouchEvent(e);return true;});
    }

    private void adjustBrightness(float delta){
        WindowManager.LayoutParams lp=getWindow().getAttributes();float b=lp.screenBrightness<0?.5f:lp.screenBrightness;b=Math.max(.05f,Math.min(1f,b+delta));lp.screenBrightness=b;getWindow().setAttributes(lp);
    }

    private void adjustVolume(float delta){
        AudioManager am=(AudioManager)getSystemService(Context.AUDIO_SERVICE);int max=am.getStreamMaxVolume(AudioManager.STREAM_MUSIC),cur=am.getStreamVolume(AudioManager.STREAM_MUSIC);int next=Math.max(0,Math.min(max,cur+Math.round(delta*max*1.8f)));am.setStreamVolume(AudioManager.STREAM_MUSIC,next,0);
    }

    private void applyTransform(){playerView.setScaleX(videoScale);playerView.setScaleY(videoScale);float maxX=playerView.getWidth()*(videoScale-1)/2f,maxY=playerView.getHeight()*(videoScale-1)/2f;panX=Math.max(-maxX,Math.min(maxX,panX));panY=Math.max(-maxY,Math.min(maxY,panY));playerView.setTranslationX(panX);playerView.setTranslationY(panY);}
    private void resetTransform(){videoScale=1f;panX=panY=0;applyTransform();zoomLabel.setText(playerView.getResizeMode()==AspectRatioFrameLayout.RESIZE_MODE_FIT?"Fit":"Fill");}

    private void toggleFullscreen(){if(fullscreen)exitFullscreen();else enterFullscreen();}
    private void enterFullscreen(){fullscreen=true;getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);}
    private void exitFullscreen(){fullscreen=false;getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);}

    private void enterPip(){
        if(Build.VERSION.SDK_INT>=26&&isVideo()){try{enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(new Rational(16,9)).build());}catch(Exception ignored){}}
    }

    @Override protected void onStop(){
        if(controller!=null&&!currentUri.isEmpty())Store.saveProgress(this,currentUri,controller.getCurrentPosition(),controller.getDuration());
        super.onStop();
    }

    @Override protected void onDestroy(){
        handler.removeCallbacks(ticker);
        if(controller!=null&&!currentUri.isEmpty())Store.saveProgress(this,currentUri,controller.getCurrentPosition(),controller.getDuration());
        if(future!=null)MediaController.releaseFuture(future);
        super.onDestroy();
    }
}
