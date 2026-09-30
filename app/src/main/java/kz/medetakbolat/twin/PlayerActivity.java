package kz.medetakbolat.twin;

import android.app.PictureInPictureParams;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;

@UnstableApi
public class PlayerActivity extends AppCompatActivity {
    private ListenableFuture<MediaController> future;
    private MediaController controller;
    private String startUri,currentUri="";
    private FrameLayout mediaLayer;
    private PlayerView playerView;
    private ImageView artwork;
    private LinearLayout topBar,centerBar,bottomBar;
    private TextView title,current,total,play,speed,repeat,feedback,fit;
    private SeekBar seek;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean draggingSeek=false,controlsVisible=true,inPip=false,sleepAtEnd=false;
    private float videoScale=1f,panX=0f,panY=0f;
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    private final Runnable hideControls=()->setControls(false);
    private final Runnable ticker=new Runnable(){
        @Override public void run(){
            if(controller!=null){
                long p=controller.getCurrentPosition(),d=controller.getDuration();
                if(d>0&&!draggingSeek)seek.setProgress((int)Math.min(1000,p*1000/d));
                current.setText(Ui.time(p));total.setText(Ui.time(Math.max(0,d)));
                play.setText(controller.isPlaying()?"Ⅱ":"▶");
                if(!currentUri.isEmpty())Store.saveProgress(PlayerActivity.this,currentUri,p,d);
            }
            handler.postDelayed(this,650);
        }
    };

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        startUri=getIntent().getStringExtra("uri");
        buildUi();
        connect();
    }

    private void buildUi(){
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(android.graphics.Color.BLACK);

        mediaLayer=new FrameLayout(this);mediaLayer.setBackgroundColor(android.graphics.Color.BLACK);
        root.addView(mediaLayer,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));

        playerView=new PlayerView(this);playerView.setUseController(false);playerView.setBackgroundColor(android.graphics.Color.BLACK);playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        mediaLayer.addView(playerView,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));

        artwork=new ImageView(this);artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);artwork.setImageResource(R.drawable.cover_placeholder);artwork.setBackgroundColor(android.graphics.Color.BLACK);
        mediaLayer.addView(artwork,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));

        topBar=Ui.row(this);topBar.setPadding(Ui.dp(this,6),Ui.dp(this,8),Ui.dp(this,6),Ui.dp(this,8));topBar.setBackgroundColor(android.graphics.Color.argb(92,0,0,0));
        TextView back=Ui.icon(this,"‹");back.setTextSize(31);back.setOnClickListener(v->finish());topBar.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
        title=Ui.text(this,"",15,Ui.TEXT,true);title.setMaxLines(1);title.setPadding(Ui.dp(this,4),0,Ui.dp(this,6),0);topBar.addView(title,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        fit=Ui.icon(this,"Fit");fit.setTextSize(12);fit.setOnClickListener(v->zoomSheet());topBar.addView(fit,new LinearLayout.LayoutParams(Ui.dp(this,52),Ui.dp(this,48)));
        TextView more=Ui.icon(this,"⋮");more.setOnClickListener(v->moreSheet());topBar.addView(more,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
        root.addView(topBar,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,66),Gravity.TOP));

        centerBar=Ui.row(this);centerBar.setGravity(Gravity.CENTER);
        TextView prev=Ui.icon(this,"‹");prev.setTextSize(34);play=Ui.icon(this,"▶");play.setTextSize(36);TextView next=Ui.icon(this,"›");next.setTextSize(34);
        prev.setOnClickListener(v->{showControls();switchMedia(-1);});
        play.setOnClickListener(v->{showControls();togglePlay();});
        next.setOnClickListener(v->{showControls();switchMedia(1);});
        centerBar.addView(prev,new LinearLayout.LayoutParams(Ui.dp(this,76),Ui.dp(this,76)));
        centerBar.addView(play,new LinearLayout.LayoutParams(Ui.dp(this,96),Ui.dp(this,84)));
        centerBar.addView(next,new LinearLayout.LayoutParams(Ui.dp(this,76),Ui.dp(this,76)));
        root.addView(centerBar,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,Ui.dp(this,88),Gravity.CENTER));

        bottomBar=new LinearLayout(this);bottomBar.setOrientation(LinearLayout.VERTICAL);bottomBar.setPadding(Ui.dp(this,12),Ui.dp(this,6),Ui.dp(this,12),Ui.dp(this,10));bottomBar.setBackgroundColor(android.graphics.Color.argb(100,0,0,0));
        LinearLayout timeline=Ui.row(this);
        current=Ui.text(this,"0:00",11,Ui.TEXT,false);total=Ui.text(this,"0:00",11,Ui.TEXT,false);seek=new SeekBar(this);seek.setMax(1000);
        seek.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));seek.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        timeline.addView(current,new LinearLayout.LayoutParams(Ui.dp(this,54),Ui.dp(this,38)));timeline.addView(seek,new LinearLayout.LayoutParams(0,Ui.dp(this,38),1));timeline.addView(total,new LinearLayout.LayoutParams(Ui.dp(this,62),Ui.dp(this,38)));bottomBar.addView(timeline);

        LinearLayout tools=Ui.row(this);
        speed=Ui.text(this,"1.0×",13,Ui.TEXT,true);speed.setGravity(Gravity.CENTER);speed.setOnClickListener(v->{showControls();speedSheet();});
        repeat=Ui.text(this,"↻",13,Ui.MUTED,true);repeat.setGravity(Gravity.CENTER);repeat.setOnClickListener(v->{showControls();cycleRepeat();});
        TextView book=Ui.text(this,"Bookmark",13,Ui.TEXT,true);book.setGravity(Gravity.CENTER);book.setOnClickListener(v->{showControls();bookmark();});
        TextView queue=Ui.text(this,"Queue",13,Ui.TEXT,true);queue.setGravity(Gravity.CENTER);queue.setOnClickListener(v->{showControls();queueSheet();});
        tools.addView(speed,new LinearLayout.LayoutParams(0,Ui.dp(this,40),1));tools.addView(repeat,new LinearLayout.LayoutParams(0,Ui.dp(this,40),1));tools.addView(book,new LinearLayout.LayoutParams(0,Ui.dp(this,40),1));tools.addView(queue,new LinearLayout.LayoutParams(0,Ui.dp(this,40),1));bottomBar.addView(tools);
        root.addView(bottomBar,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,98),Gravity.BOTTOM));

        feedback=Ui.text(this,"",15,Ui.TEXT,true);feedback.setGravity(Gravity.CENTER);feedback.setPadding(Ui.dp(this,14),Ui.dp(this,8),Ui.dp(this,14),Ui.dp(this,8));feedback.setBackground(Ui.round(android.graphics.Color.argb(155,0,0,0),18,this));feedback.setVisibility(View.GONE);
        FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,Ui.dp(this,44),Gravity.CENTER_HORIZONTAL|Gravity.TOP);fp.topMargin=Ui.dp(this,82);root.addView(feedback,fp);

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean from){if(from&&controller!=null&&controller.getDuration()>0)current.setText(Ui.time(controller.getDuration()*p/1000));}
            public void onStartTrackingTouch(SeekBar s){draggingSeek=true;showControls();}
            public void onStopTrackingTouch(SeekBar s){draggingSeek=false;if(controller!=null&&controller.getDuration()>0)controller.seekTo(controller.getDuration()*s.getProgress()/1000);scheduleHide();}
        });

        setContentView(root);
        setupGestures();
        showControls();
    }

    private void connect(){
        SessionToken token=new SessionToken(this,new ComponentName(this,PlayerService.class));
        future=new MediaController.Builder(this,token).buildAsync();
        future.addListener(()->{
            try{
                controller=future.get();playerView.setPlayer(controller);controller.setRepeatMode(Store.repeatMode(this));updateRepeatUi();
                controller.addListener(new Player.Listener(){
                    @Override public void onMediaItemTransition(MediaItem item,int reason){
                        if(sleepAtEnd&&reason==Player.MEDIA_ITEM_TRANSITION_REASON_AUTO){controller.pause();sleepAtEnd=false;}
                        updateCurrent();
                    }
                    @Override public void onIsPlayingChanged(boolean playing){play.setText(playing?"Ⅱ":"▶");configurePip();if(playing)scheduleHide();else showControls();}
                });
                prepareIfNeeded();handler.post(ticker);
            }catch(Exception e){Toast.makeText(this,"Player could not start",Toast.LENGTH_LONG).show();}
        },ContextCompat.getMainExecutor(this));
    }

    private void prepareIfNeeded(){
        if(controller==null)return;
        if(startUri==null||startUri.isEmpty()){
            if(controller.getCurrentMediaItem()!=null){updateCurrent();return;}
            Store.QueueDef q=Store.currentQueue(this);if(q.items.isEmpty())return;startUri=q.items.get(0);
        }
        String currentId=controller.getCurrentMediaItem()==null?"":controller.getCurrentMediaItem().mediaId;
        if(startUri.equals(currentId)){updateCurrent();return;}
        Store.QueueDef q=Store.currentQueue(this);ArrayList<String> uris=new ArrayList<>(q.items);
        if(!uris.contains(startUri))uris.add(0,startUri);
        int idx=Math.max(0,uris.indexOf(startUri));long pos=Store.progress(this,startUri)[0];
        controller.setMediaItems(Playback.items(this,uris,idx),idx,pos);controller.prepare();controller.play();updateCurrent();
    }

    private void updateCurrent(){
        if(controller==null||controller.getCurrentMediaItem()==null)return;
        currentUri=controller.getCurrentMediaItem().mediaId;Store.setNowPlaying(this,currentUri);Store.addRecent(this,currentUri);
        MediaEntry e=MediaRepository.resolve(this,currentUri);if(e==null)return;
        title.setText(e.title);boolean video=e.isVideo();artwork.setVisibility(video?View.GONE:View.VISIBLE);fit.setVisibility(video?View.VISIBLE:View.GONE);
        if(!video)Thumb.load(this,artwork,e);
        float sp=Store.speed(this,currentUri);controller.setPlaybackSpeed(sp);speed.setText(speedText(sp)+"×");updateRepeatUi();
        resetTransform();mediaLayer.setTranslationX(0);mediaLayer.setAlpha(1f);
        if(video){configurePip();scheduleHide();}else showControls();
    }

    private void setupGestures(){
        scaleDetector=new ScaleGestureDetector(this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector d){
                if(!isVideo())return false;
                videoScale=Math.max(1f,Math.min(3f,videoScale*d.getScaleFactor()));applyTransform();flash(Math.round(videoScale*100)+"%");return true;
            }
        });
        gestureDetector=new GestureDetector(this,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(MotionEvent e){return true;}
            @Override public boolean onSingleTapConfirmed(MotionEvent e){
                if(controlsVisible)setControls(false);else showControls();return true;
            }
            @Override public boolean onDoubleTap(MotionEvent e){
                float x=e.getX(),w=Math.max(1,mediaLayer.getWidth());
                if(x<w*.40f)seekBy(-10000);else if(x>w*.60f)seekBy(10000);else togglePlay();
                showControls();return true;
            }
            @Override public boolean onFling(MotionEvent e1,MotionEvent e2,float vx,float vy){
                if(e1==null||e2==null||videoScale>1.01f)return false;
                float dx=e2.getX()-e1.getX(),dy=e2.getY()-e1.getY();
                if(Math.abs(dx)>Ui.dp(PlayerActivity.this,70)&&Math.abs(dx)>Math.abs(dy)*1.35f&&Math.abs(vx)>350){
                    switchMedia(dx<0?1:-1);return true;
                }
                return false;
            }
            @Override public boolean onScroll(MotionEvent e1,MotionEvent e2,float dx,float dy){
                if(e1==null||scaleDetector.isInProgress())return false;
                if(videoScale>1.01f){panX-=dx;panY-=dy;applyTransform();return true;}
                if(!isVideo()||Math.abs(dx)>Math.abs(dy))return false;
                if(e1.getX()<mediaLayer.getWidth()/2f)brightness(-dy/Math.max(1f,mediaLayer.getHeight()));
                else volume(-dy/Math.max(1f,mediaLayer.getHeight()));
                return true;
            }
        });
        View.OnTouchListener touch=(v,e)->{scaleDetector.onTouchEvent(e);gestureDetector.onTouchEvent(e);return true;};
        playerView.setOnTouchListener(touch);artwork.setOnTouchListener(touch);
    }

    private void switchMedia(int direction){
        if(controller==null)return;
        boolean can=direction>0?controller.hasNextMediaItem():controller.hasPreviousMediaItem();
        if(!can){flash(direction>0?"End of queue":"Start of queue");return;}
        float width=Math.max(mediaLayer.getWidth(),getResources().getDisplayMetrics().widthPixels);
        float out=direction>0?-width:width;
        mediaLayer.animate().translationX(out).alpha(.45f).setDuration(115).withEndAction(()->{
            if(direction>0)controller.seekToNextMediaItem();else controller.seekToPreviousMediaItem();
            mediaLayer.setTranslationX(-out);mediaLayer.setAlpha(.45f);
            mediaLayer.animate().translationX(0).alpha(1f).setDuration(155).start();
        }).start();
    }

    private void togglePlay(){if(controller==null)return;if(controller.isPlaying())controller.pause();else controller.play();}
    private void seekBy(long ms){if(controller==null)return;long p=Math.max(0,controller.getCurrentPosition()+ms);controller.seekTo(p);flash((ms>0?"+":"")+Math.round(ms/1000f)+"s");}
    private boolean isVideo(){MediaEntry e=MediaRepository.resolve(this,currentUri);return e!=null&&e.isVideo();}

    private void showControls(){setControls(true);scheduleHide();}
    private void setControls(boolean visible){
        controlsVisible=visible;handler.removeCallbacks(hideControls);
        View[] views={topBar,centerBar,bottomBar};
        for(View v:views){
            if(visible){v.setVisibility(View.VISIBLE);v.animate().alpha(1f).setDuration(150).start();}
            else v.animate().alpha(0f).setDuration(150).withEndAction(()->{if(!controlsVisible)v.setVisibility(View.INVISIBLE);}).start();
        }
        if(isVideo())setSystemChrome(!visible);
    }
    private void scheduleHide(){handler.removeCallbacks(hideControls);if(controller!=null&&controller.isPlaying()&&isVideo())handler.postDelayed(hideControls,2400);}
    private void setSystemChrome(boolean hide){
        getWindow().getDecorView().setSystemUiVisibility(hide?(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY):View.SYSTEM_UI_FLAG_VISIBLE);
    }

    private void brightness(float delta){
        WindowManager.LayoutParams lp=getWindow().getAttributes();float b=lp.screenBrightness<0?.5f:lp.screenBrightness;b=Math.max(.05f,Math.min(1f,b+delta));lp.screenBrightness=b;getWindow().setAttributes(lp);flash("Brightness  "+Math.round(b*100)+"%");
    }
    private void volume(float delta){
        AudioManager am=(AudioManager)getSystemService(Context.AUDIO_SERVICE);int max=am.getStreamMaxVolume(AudioManager.STREAM_MUSIC),cur=am.getStreamVolume(AudioManager.STREAM_MUSIC);int n=Math.max(0,Math.min(max,cur+Math.round(delta*max*1.55f)));am.setStreamVolume(AudioManager.STREAM_MUSIC,n,0);flash("Volume  "+Math.round(n*100f/max)+"%");
    }
    private void flash(String text){
        feedback.animate().cancel();feedback.setText(text);feedback.setVisibility(View.VISIBLE);feedback.setAlpha(1f);
        feedback.animate().alpha(0f).setStartDelay(650).setDuration(220).withEndAction(()->feedback.setVisibility(View.GONE)).start();
    }

    private void applyTransform(){
        playerView.setScaleX(videoScale);playerView.setScaleY(videoScale);
        float mx=playerView.getWidth()*(videoScale-1)/2f,my=playerView.getHeight()*(videoScale-1)/2f;
        panX=Math.max(-mx,Math.min(mx,panX));panY=Math.max(-my,Math.min(my,panY));
        playerView.setTranslationX(panX);playerView.setTranslationY(panY);
    }
    private void resetTransform(){videoScale=1f;panX=panY=0;applyTransform();fit.setText(playerView.getResizeMode()==AspectRatioFrameLayout.RESIZE_MODE_FIT?"Fit":"Fill");}

    private void cycleRepeat(){
        if(controller==null)return;
        int mode=controller.getRepeatMode();
        int next=mode==Player.REPEAT_MODE_OFF?Player.REPEAT_MODE_ONE:(mode==Player.REPEAT_MODE_ONE?Player.REPEAT_MODE_ALL:Player.REPEAT_MODE_OFF);
        controller.setRepeatMode(next);
        Store.setRepeatMode(this,next);
        updateRepeatUi();
        flash(next==Player.REPEAT_MODE_ONE?"Repeat one":(next==Player.REPEAT_MODE_ALL?"Repeat playlist / queue":"Repeat off"));
    }

    private void updateRepeatUi(){
        if(repeat==null)return;
        int mode=controller==null?Store.repeatMode(this):controller.getRepeatMode();
        repeat.setText(mode==Player.REPEAT_MODE_ONE?"↻ 1":(mode==Player.REPEAT_MODE_ALL?"↻ All":"↻"));
        repeat.setTextColor(mode==Player.REPEAT_MODE_OFF?Ui.MUTED:Ui.ACCENT);
    }

    private String speedText(float s){return Math.abs(s-Math.round(s))<.001?String.format("%.1f",s):String.format("%.2f",s).replaceAll("0$","");}
    private void speedSheet(){
        String[] labels={"0.5×","0.75×","1.0×","1.25×","1.5×","1.75×","2.0×","Custom…"};
        Sheets.choices(this,"Playback speed",labels,w->{if(w==7){Sheets.number(this,"Custom speed","0.25 – 3.0",speedText(Store.speed(this,currentUri)),x->{try{setSpeed(Math.max(.25f,Math.min(3f,Float.parseFloat(x))));}catch(Exception ignored){}});return;}float[]v={.5f,.75f,1f,1.25f,1.5f,1.75f,2f};setSpeed(v[w]);});
    }
    private void setSpeed(float s){if(controller!=null)controller.setPlaybackSpeed(s);if(!currentUri.isEmpty())Store.setSpeed(this,currentUri,s);speed.setText(speedText(s)+"×");}
    private void bookmark(){if(controller==null||currentUri.isEmpty())return;Store.addBookmark(this,currentUri,controller.getCurrentPosition());flash("Bookmark  "+Ui.time(controller.getCurrentPosition()));}
    private void bookmarksSheet(){
        ArrayList<Long>b=Store.bookmarks(this,currentUri);if(b.isEmpty()){Toast.makeText(this,"No bookmarks yet",Toast.LENGTH_SHORT).show();return;}
        String[] labels=new String[b.size()];for(int i=0;i<b.size();i++)labels[i]=Ui.time(b.get(i));
        Sheets.choices(this,"Bookmarks",labels,w->{if(controller!=null)controller.seekTo(b.get(w));});
    }
    private void queueSheet(){
        if(controller==null)return;int n=controller.getMediaItemCount();String[]labels=new String[n];
        for(int i=0;i<n;i++){MediaItem mi=controller.getMediaItemAt(i);CharSequence t=mi.mediaMetadata.title;labels[i]=(i==controller.getCurrentMediaItemIndex()?"▶  ":"")+(t==null?"Media":t.toString());}
        Sheets.choices(this,"Queue",labels,w->controller.seekToDefaultPosition(w));
    }
    private void zoomSheet(){
        Sheets.choices(this,"Video size",new String[]{"Fit","Fill / crop","Stretch","Reset zoom"},w->{
            if(w==0){playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);fit.setText("Fit");resetTransform();}
            else if(w==1){playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);fit.setText("Fill");resetTransform();}
            else if(w==2){playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);fit.setText("Fill");resetTransform();}
            else resetTransform();showControls();
        });
    }
    private void moreSheet(){
        ArrayList<String> opts=new ArrayList<>();opts.add("Bookmarks");opts.add("Sleep timer");if(isVideo()){opts.add("Fit / Fill / Crop");if(Build.VERSION.SDK_INT>=26)opts.add("Picture in picture");}opts.add("Media info");
        Sheets.choices(this,title.getText().toString(),opts.toArray(new String[0]),w->{
            String x=opts.get(w);if("Bookmarks".equals(x))bookmarksSheet();else if("Sleep timer".equals(x))sleepSheet();else if("Fit / Fill / Crop".equals(x))zoomSheet();else if("Picture in picture".equals(x))enterPip();else mediaInfo();
        });
    }
    private void sleepSheet(){
        Sheets.choices(this,"Sleep timer",new String[]{"15 minutes","30 minutes","60 minutes","End of current media","Off"},w->{
            handler.removeCallbacksAndMessages("sleep");sleepAtEnd=false;
            if(w<3){long[]m={15,30,60};Runnable r=()->{if(controller!=null)controller.pause();};handler.postAtTime(r,"sleep",android.os.SystemClock.uptimeMillis()+m[w]*60000L);Toast.makeText(this,"Sleep timer set",Toast.LENGTH_SHORT).show();}
            else if(w==3){sleepAtEnd=true;Toast.makeText(this,"Stops at end",Toast.LENGTH_SHORT).show();}
        });
    }
    private void mediaInfo(){
        MediaEntry e=MediaRepository.resolve(this,currentUri);if(e!=null)Sheets.info(this,e.title,(e.isVideo()?"Video":"Audio")+"\n"+e.durationText()+"\n"+e.folder+"\n\n"+e.uri);
    }

    private PictureInPictureParams pipParams(boolean auto){
        Rect r=new Rect();playerView.getGlobalVisibleRect(r);
        int w=16,h=9;
        try{
            androidx.media3.common.VideoSize vs=controller==null?androidx.media3.common.VideoSize.UNKNOWN:controller.getVideoSize();
            if(vs!=null&&vs.width>0&&vs.height>0){w=vs.width;h=vs.height;}
        }catch(Exception ignored){}
        float ratio=w/(float)Math.max(1,h);
        if(ratio<0.42f){w=9;h=16;}else if(ratio>2.39f){w=21;h=9;}
        PictureInPictureParams.Builder b=new PictureInPictureParams.Builder().setAspectRatio(new Rational(w,h)).setSourceRectHint(r);
        if(Build.VERSION.SDK_INT>=31)b.setAutoEnterEnabled(auto);
        return b.build();
    }

    private void configurePip(){
        if(Build.VERSION.SDK_INT<26||!isVideo())return;
        try{setPictureInPictureParams(pipParams(Build.VERSION.SDK_INT>=31&&controller!=null&&controller.isPlaying()));}catch(Exception ignored){}
    }

    private void enterPip(){
        if(Build.VERSION.SDK_INT<26||!isVideo()||controller==null||!controller.isPlaying()||isInPictureInPictureMode())return;
        try{
            configurePip();
            if(Build.VERSION.SDK_INT<31){inPip=enterPictureInPictureMode(pipParams(false));}
        }catch(Exception ignored){}
    }

    @Override public void onUserLeaveHint(){
        super.onUserLeaveHint();
        if(Build.VERSION.SDK_INT>=26&&isVideo()&&controller!=null&&controller.isPlaying()&&!isFinishing()){
            if(Build.VERSION.SDK_INT<31)enterPip();
            else configurePip();
        }
    }

    @Override public void onPictureInPictureModeChanged(boolean in,Configuration cfg){
        super.onPictureInPictureModeChanged(in,cfg);inPip=in;if(in)setControls(false);else showControls();
    }

    @Override protected void onStop(){
        if(controller!=null&&!currentUri.isEmpty())Store.saveProgress(this,currentUri,controller.getCurrentPosition(),controller.getDuration());
        super.onStop();
    }

    @Override protected void onDestroy(){
        boolean closedPip=inPip&&isFinishing();
        handler.removeCallbacks(ticker);handler.removeCallbacks(hideControls);
        if(controller!=null&&!currentUri.isEmpty())Store.saveProgress(this,currentUri,controller.getCurrentPosition(),controller.getDuration());
        if(closedPip&&controller!=null)controller.pause();
        if(future!=null)MediaController.releaseFuture(future);
        super.onDestroy();
    }
}
