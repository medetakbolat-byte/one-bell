package kz.medetakbolat.twin;

import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;

@UnstableApi
public class PlayerActivity extends AppCompatActivity {
    private ListenableFuture<MediaController> future;
    private MediaController controller;
    private String startUri,currentUri="";
    private PlayerView playerView;
    private ImageView artwork;
    private LinearLayout topBar,centerBar,bottomPanel;
    private TextView title,current,total,play,speed,feedback,fit;
    private SeekBar seek;
    private Handler h=new Handler(Looper.getMainLooper());
    private boolean dragging=false,controlsVisible=true,sleepAtEnd=false,inPip=false;
    private float videoScale=1f,panX=0,panY=0;
    private ScaleGestureDetector scaler;
    private GestureDetector gestures;

    private final Runnable hideControls=()->setControls(false);
    private final Runnable ticker=new Runnable(){public void run(){if(controller!=null){long p=controller.getCurrentPosition(),d=controller.getDuration();if(!dragging&&d>0)seek.setProgress((int)Math.min(1000,p*1000/d));current.setText(Ui.time(p));total.setText(Ui.time(Math.max(0,d)));play.setText(controller.isPlaying()?"Ⅱ":"▶");if(!currentUri.isEmpty())Store.saveProgress(PlayerActivity.this,currentUri,p,d);}h.postDelayed(this,600);}};

    @Override public void onCreate(Bundle b){
        super.onCreate(b);startUri=getIntent().getStringExtra("uri");buildUi();connect();
    }

    private void buildUi(){
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(android.graphics.Color.BLACK);

        playerView=new PlayerView(this);playerView.setUseController(false);playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);playerView.setBackgroundColor(android.graphics.Color.BLACK);
        root.addView(playerView,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));

        artwork=new ImageView(this);artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);artwork.setImageResource(R.drawable.cover_placeholder);root.addView(artwork,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));

        topBar=Ui.row(this);topBar.setPadding(Ui.dp(this,8),Ui.dp(this,8),Ui.dp(this,8),Ui.dp(this,8));topBar.setBackgroundColor(android.graphics.Color.argb(80,0,0,0));
        TextView back=Ui.icon(this,"‹");back.setOnClickListener(v->finish());topBar.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
        title=Ui.text(this,"",16,Ui.TEXT,true);title.setMaxLines(1);title.setPadding(Ui.dp(this,6),0,Ui.dp(this,6),0);topBar.addView(title,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        fit=Ui.icon(this,"Fit");fit.setTextSize(12);fit.setOnClickListener(v->zoomSheet());topBar.addView(fit,new LinearLayout.LayoutParams(Ui.dp(this,52),Ui.dp(this,48)));
        TextView more=Ui.icon(this,"⋮");more.setOnClickListener(v->moreSheet());topBar.addView(more,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
        FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,64),Gravity.TOP);root.addView(topBar,tp);

        centerBar=Ui.row(this);centerBar.setGravity(Gravity.CENTER);centerBar.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        TextView prev=Ui.icon(this,"‹|");TextView back10=Ui.icon(this,"−10");play=Ui.icon(this,"▶");play.setTextSize(31);TextView fwd10=Ui.icon(this,"+10");TextView next=Ui.icon(this,"|›");
        prev.setOnClickListener(v->{showControls();if(controller!=null&&controller.hasPreviousMediaItem())controller.seekToPreviousMediaItem();});
        next.setOnClickListener(v->{showControls();if(controller!=null&&controller.hasNextMediaItem())controller.seekToNextMediaItem();});
        back10.setOnClickListener(v->{showControls();seekBy(-10000);});fwd10.setOnClickListener(v->{showControls();seekBy(10000);});play.setOnClickListener(v->{showControls();togglePlay();});
        centerBar.addView(prev,new LinearLayout.LayoutParams(Ui.dp(this,56),Ui.dp(this,64)));centerBar.addView(back10,new LinearLayout.LayoutParams(Ui.dp(this,64),Ui.dp(this,64)));centerBar.addView(play,new LinearLayout.LayoutParams(Ui.dp(this,82),Ui.dp(this,76)));centerBar.addView(fwd10,new LinearLayout.LayoutParams(Ui.dp(this,64),Ui.dp(this,64)));centerBar.addView(next,new LinearLayout.LayoutParams(Ui.dp(this,56),Ui.dp(this,64)));
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,Ui.dp(this,80),Gravity.CENTER);root.addView(centerBar,cp);

        bottomPanel=new LinearLayout(this);bottomPanel.setOrientation(LinearLayout.VERTICAL);bottomPanel.setPadding(Ui.dp(this,14),Ui.dp(this,8),Ui.dp(this,14),Ui.dp(this,10));bottomPanel.setBackgroundColor(android.graphics.Color.argb(95,0,0,0));
        LinearLayout timeline=Ui.row(this);current=Ui.text(this,"0:00",11,Ui.TEXT,false);total=Ui.text(this,"0:00",11,Ui.TEXT,false);seek=new SeekBar(this);seek.setMax(1000);seek.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));seek.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));timeline.addView(current,new LinearLayout.LayoutParams(Ui.dp(this,54),Ui.dp(this,38)));timeline.addView(seek,new LinearLayout.LayoutParams(0,Ui.dp(this,38),1));timeline.addView(total,new LinearLayout.LayoutParams(Ui.dp(this,62),Ui.dp(this,38)));bottomPanel.addView(timeline);

        LinearLayout tools=Ui.row(this);speed=Ui.text(this,"1.0×",13,Ui.TEXT,true);speed.setGravity(Gravity.CENTER);speed.setOnClickListener(v->{showControls();speedSheet();});TextView book=Ui.text(this,"Bookmark",13,Ui.TEXT,true);book.setGravity(Gravity.CENTER);book.setOnClickListener(v->{showControls();bookmark();});TextView queue=Ui.text(this,"Queue",13,Ui.TEXT,true);queue.setGravity(Gravity.CENTER);queue.setOnClickListener(v->{showControls();queueSheet();});tools.addView(speed,new LinearLayout.LayoutParams(0,Ui.dp(this,40),1));tools.addView(book,new LinearLayout.LayoutParams(0,Ui.dp(this,40),1));tools.addView(queue,new LinearLayout.LayoutParams(0,Ui.dp(this,40),1));bottomPanel.addView(tools);
        FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,98),Gravity.BOTTOM);root.addView(bottomPanel,bp);

        feedback=Ui.text(this,"",16,Ui.TEXT,true);feedback.setGravity(Gravity.CENTER);feedback.setPadding(Ui.dp(this,14),Ui.dp(this,8),Ui.dp(this,14),Ui.dp(this,8));feedback.setBackground(Ui.round(android.graphics.Color.argb(135,0,0,0),18,this));feedback.setVisibility(View.GONE);FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,Ui.dp(this,44),Gravity.CENTER_HORIZONTAL|Gravity.TOP);fp.topMargin=Ui.dp(this,78);root.addView(feedback,fp);

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean from){if(from&&controller!=null&&controller.getDuration()>0)current.setText(Ui.time(controller.getDuration()*p/1000));}public void onStartTrackingTouch(SeekBar s){dragging=true;showControls();}public void onStopTrackingTouch(SeekBar s){dragging=false;if(controller!=null&&controller.getDuration()>0)controller.seekTo(controller.getDuration()*s.getProgress()/1000);scheduleHide();}});

        setContentView(root);setupGestures();showControls();
    }

    private void connect(){
        SessionToken token=new SessionToken(this,new ComponentName(this,PlayerService.class));future=new MediaController.Builder(this,token).buildAsync();future.addListener(()->{
            try{
                controller=future.get();playerView.setPlayer(controller);controller.addListener(new Player.Listener(){
                    @Override public void onMediaItemTransition(MediaItem item,int reason){if(sleepAtEnd&&reason==Player.MEDIA_ITEM_TRANSITION_REASON_AUTO){controller.pause();sleepAtEnd=false;}updateCurrent();}
                    @Override public void onIsPlayingChanged(boolean b){play.setText(b?"Ⅱ":"▶");if(b)scheduleHide();else showControls();}
                });prepareIfNeeded();h.post(ticker);
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
        Store.QueueDef q=Store.currentQueue(this);ArrayList<String>u=new ArrayList<>(q.items);if(!u.contains(startUri))u.add(0,startUri);int idx=Math.max(0,u.indexOf(startUri));long pos=Store.progress(this,startUri)[0];controller.setMediaItems(Playback.items(this,u),idx,pos);controller.prepare();controller.play();updateCurrent();
    }

    private void updateCurrent(){
        if(controller==null||controller.getCurrentMediaItem()==null)return;currentUri=controller.getCurrentMediaItem().mediaId;Store.setNowPlaying(this,currentUri);Store.addRecent(this,currentUri);MediaEntry e=MediaRepository.resolve(this,currentUri);if(e==null)return;
        title.setText(e.title);boolean video=e.isVideo();artwork.setVisibility(video?View.GONE:View.VISIBLE);fit.setVisibility(video?View.VISIBLE:View.GONE);if(!video)Thumb.load(this,artwork,e);float sp=Store.speed(this,currentUri);controller.setPlaybackSpeed(sp);speed.setText(speedText(sp)+"×");resetTransform();if(video)scheduleHide();else showControls();
    }

    private String speedText(float s){return Math.abs(s-Math.round(s))<.001?String.format("%.1f",s):String.format("%.2f",s).replaceAll("0$","");}
    private void togglePlay(){if(controller==null)return;if(controller.isPlaying())controller.pause();else controller.play();}
    private void seekBy(long ms){if(controller==null)return;long p=Math.max(0,controller.getCurrentPosition()+ms);controller.seekTo(p);flash((ms>0?"+":"")+Math.round(ms/1000f)+"s  ·  "+Ui.time(p));}
    private boolean isVideo(){MediaEntry e=MediaRepository.resolve(this,currentUri);return e!=null&&e.isVideo();}

    private void showControls(){setControls(true);scheduleHide();}
    private void setControls(boolean visible){
        controlsVisible=visible;topBar.animate().alpha(visible?1f:0f).setDuration(160).withStartAction(()->{if(visible){topBar.setVisibility(View.VISIBLE);centerBar.setVisibility(View.VISIBLE);bottomPanel.setVisibility(View.VISIBLE);}}).withEndAction(()->{if(!visible){topBar.setVisibility(View.INVISIBLE);centerBar.setVisibility(View.INVISIBLE);bottomPanel.setVisibility(View.INVISIBLE);}}).start();
        centerBar.animate().alpha(visible?1f:0f).setDuration(160).start();bottomPanel.animate().alpha(visible?1f:0f).setDuration(160).start();
        if(isVideo())setSystemChrome(!visible);
    }
    private void scheduleHide(){h.removeCallbacks(hideControls);if(controller!=null&&controller.isPlaying()&&isVideo())h.postDelayed(hideControls,2400);}
    private void setSystemChrome(boolean hide){if(hide)getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);}

    private void setupGestures(){
        scaler=new ScaleGestureDetector(this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector d){if(!isVideo())return false;videoScale=Math.max(1f,Math.min(3f,videoScale*d.getScaleFactor()));applyTransform();flash(Math.round(videoScale*100)+"%");return true;}});
        gestures=new GestureDetector(this,new GestureDetector.SimpleOnGestureListener(){
            public boolean onDown(MotionEvent e){return true;}
            public boolean onSingleTapConfirmed(MotionEvent e){if(isVideo()){if(controlsVisible)setControls(false);else showControls();}else showControls();return true;}
            public boolean onDoubleTap(MotionEvent e){float x=e.getX(),w=playerView.getWidth();if(x<w*.36f)seekBy(-10000);else if(x>w*.64f)seekBy(10000);else togglePlay();showControls();return true;}
            public boolean onScroll(MotionEvent e1,MotionEvent e2,float dx,float dy){if(!isVideo()||scaler.isInProgress())return false;if(videoScale>1.01f){panX-=dx;panY-=dy;applyTransform();return true;}if(Math.abs(dx)>Math.abs(dy)){long jump=(long)(-dx*90);if(Math.abs(jump)>250){controller.seekTo(Math.max(0,controller.getCurrentPosition()+jump));flash((jump>0?"+":"")+Ui.time(Math.abs(jump))+"  ·  "+Ui.time(controller.getCurrentPosition()));}return true;}if(e1.getX()<playerView.getWidth()/2f)brightness(-dy/playerView.getHeight());else volume(-dy/playerView.getHeight());return true;}
        });
        playerView.setOnTouchListener((v,e)->{scaler.onTouchEvent(e);gestures.onTouchEvent(e);return true;});
    }

    private void brightness(float delta){WindowManager.LayoutParams lp=getWindow().getAttributes();float b=lp.screenBrightness<0?.5f:lp.screenBrightness;b=Math.max(.05f,Math.min(1f,b+delta));lp.screenBrightness=b;getWindow().setAttributes(lp);flash("Brightness  "+Math.round(b*100)+"%");}
    private void volume(float delta){AudioManager am=(AudioManager)getSystemService(Context.AUDIO_SERVICE);int max=am.getStreamMaxVolume(AudioManager.STREAM_MUSIC),cur=am.getStreamVolume(AudioManager.STREAM_MUSIC);int n=Math.max(0,Math.min(max,cur+Math.round(delta*max*1.6f)));am.setStreamVolume(AudioManager.STREAM_MUSIC,n,0);flash("Volume  "+Math.round(n*100f/max)+"%");}
    private void flash(String s){feedback.setText(s);feedback.setVisibility(View.VISIBLE);feedback.setAlpha(1f);feedback.animate().alpha(0f).setStartDelay(650).setDuration(250).withEndAction(()->feedback.setVisibility(View.GONE)).start();}

    private void applyTransform(){playerView.setScaleX(videoScale);playerView.setScaleY(videoScale);float mx=playerView.getWidth()*(videoScale-1)/2f,my=playerView.getHeight()*(videoScale-1)/2f;panX=Math.max(-mx,Math.min(mx,panX));panY=Math.max(-my,Math.min(my,panY));playerView.setTranslationX(panX);playerView.setTranslationY(panY);}
    private void resetTransform(){videoScale=1f;panX=panY=0;applyTransform();fit.setText(playerView.getResizeMode()==AspectRatioFrameLayout.RESIZE_MODE_FIT?"Fit":"Fill");}

    private interface Choice{void pick(int i);}
    private void sheet(String title,String[]opts,Choice c){BottomSheetDialog d=new BottomSheetDialog(this);LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.setPadding(Ui.dp(this,20),Ui.dp(this,12),Ui.dp(this,20),Ui.dp(this,24));b.setBackgroundColor(Ui.SURFACE);TextView h=Ui.text(this,title,17,Ui.TEXT,true);h.setPadding(0,Ui.dp(this,6),0,Ui.dp(this,8));b.addView(h);for(int i=0;i<opts.length;i++){final int x=i;TextView t=Ui.text(this,opts[i],16,Ui.TEXT,false);t.setPadding(0,Ui.dp(this,14),0,Ui.dp(this,14));t.setOnClickListener(v->{d.dismiss();c.pick(x);});b.addView(t);}d.setContentView(b);d.show();}

    private void speedSheet(){String[]l={"0.5×","0.75×","1.0×","1.25×","1.5×","1.75×","2.0×","Custom…"};sheet("Playback speed",l,w->{if(w==7){customSpeed();return;}float[]v={.5f,.75f,1f,1.25f,1.5f,1.75f,2f};setSpeed(v[w]);});}
    private void customSpeed(){EditText e=new EditText(this);e.setHint("0.25 – 3.0");e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);new AlertDialog.Builder(this).setTitle("Custom speed").setView(e).setPositiveButton("Set",(d,w)->{try{setSpeed(Math.max(.25f,Math.min(3f,Float.parseFloat(e.getText().toString()))));}catch(Exception ignored){}}).show();}
    private void setSpeed(float s){if(controller!=null)controller.setPlaybackSpeed(s);if(!currentUri.isEmpty())Store.setSpeed(this,currentUri,s);speed.setText(speedText(s)+"×");}
    private void bookmark(){if(controller==null||currentUri.isEmpty())return;Store.addBookmark(this,currentUri,controller.getCurrentPosition());flash("Bookmark  "+Ui.time(controller.getCurrentPosition()));}
    private void bookmarksSheet(){ArrayList<Long>b=Store.bookmarks(this,currentUri);if(b.isEmpty()){Toast.makeText(this,"No bookmarks yet",Toast.LENGTH_SHORT).show();return;}String[]l=new String[b.size()];for(int i=0;i<b.size();i++)l[i]=Ui.time(b.get(i));sheet("Bookmarks",l,w->{if(controller!=null)controller.seekTo(b.get(w));});}
    private void queueSheet(){if(controller==null)return;int n=controller.getMediaItemCount();String[]l=new String[n];for(int i=0;i<n;i++){MediaItem mi=controller.getMediaItemAt(i);CharSequence t=mi.mediaMetadata.title;l[i]=(i==controller.getCurrentMediaItemIndex()?"▶  ":"")+(t==null?"Media":t);}sheet("Queue",l,w->controller.seekToDefaultPosition(w));}

    private void zoomSheet(){sheet("Video size",new String[]{"Fit","Fill / crop","Stretch","Reset zoom"},w->{if(w==0){playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);fit.setText("Fit");resetTransform();}else if(w==1){playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);fit.setText("Fill");resetTransform();}else if(w==2){playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);fit.setText("Fill");resetTransform();}else resetTransform();showControls();});}

    private void moreSheet(){
        ArrayList<String>o=new ArrayList<>();o.add("Bookmarks");o.add("Sleep timer");if(isVideo()){o.add(Store.backgroundVideo(this)?"Disable background video audio":"Play video audio in background");o.add("Fit / Fill / Crop");if(Build.VERSION.SDK_INT>=26)o.add("Picture in picture");}o.add("Media info");
        sheet(title.getText().toString(),o.toArray(new String[0]),w->{String x=o.get(w);if("Bookmarks".equals(x))bookmarksSheet();else if("Sleep timer".equals(x))sleepSheet();else if(x.contains("background video")||x.contains("video audio")){boolean n=!Store.backgroundVideo(this);Store.setBackgroundVideo(this,n);Toast.makeText(this,n?"Video audio will keep playing":"Video pauses when you leave",Toast.LENGTH_SHORT).show();}else if("Fit / Fill / Crop".equals(x))zoomSheet();else if("Picture in picture".equals(x))pip();else mediaInfo();});
    }

    private void sleepSheet(){sheet("Sleep timer",new String[]{"15 minutes","30 minutes","60 minutes","End of current media","Off"},w->{h.removeCallbacksAndMessages("sleep");sleepAtEnd=false;if(w<3){long[]m={15,30,60};Runnable r=()->{if(controller!=null)controller.pause();};h.postAtTime(r,"sleep",android.os.SystemClock.uptimeMillis()+m[w]*60000L);Toast.makeText(this,"Sleep timer set",Toast.LENGTH_SHORT).show();}else if(w==3){sleepAtEnd=true;Toast.makeText(this,"Stops at end",Toast.LENGTH_SHORT).show();}});}
    private void mediaInfo(){MediaEntry e=MediaRepository.resolve(this,currentUri);if(e!=null)new AlertDialog.Builder(this).setTitle(e.title).setMessage((e.isVideo()?"Video":"Audio")+"\n"+e.durationText()+"\n"+e.folder+"\n\n"+e.uri).setPositiveButton("OK",null).show();}
    private void pip(){if(Build.VERSION.SDK_INT>=26&&isVideo())try{inPip=true;enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(new Rational(16,9)).build());}catch(Exception ignored){}}
    @Override public void onPictureInPictureModeChanged(boolean in,android.content.res.Configuration cfg){super.onPictureInPictureModeChanged(in,cfg);inPip=in;if(in)setControls(false);}

    @Override protected void onStop(){
        if(controller!=null&&!currentUri.isEmpty()){Store.saveProgress(this,currentUri,controller.getCurrentPosition(),controller.getDuration());if(isVideo()&&!Store.backgroundVideo(this)&&!inPip&&!isChangingConfigurations())controller.pause();}
        super.onStop();
    }
    @Override protected void onDestroy(){h.removeCallbacks(ticker);h.removeCallbacks(hideControls);if(controller!=null&&!currentUri.isEmpty())Store.saveProgress(this,currentUri,controller.getCurrentPosition(),controller.getDuration());if(future!=null)MediaController.releaseFuture(future);super.onDestroy();}
}
