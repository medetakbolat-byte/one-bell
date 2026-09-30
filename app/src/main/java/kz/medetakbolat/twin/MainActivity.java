package kz.medetakbolat.twin;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_STORAGE=44,REQ_OPEN=701,REQ_COVER=702;
    private FrameLayout content;
    private LinearLayout miniWrap,miniRow;
    private ImageView miniThumb;
    private TextView miniTitle,miniPlay;
    private ProgressBar miniProgress;
    private TextView navHome,navLibrary,navQueues;
    private ArrayList<MediaEntry> allMedia=new ArrayList<>();
    private String libraryFilter="all",folderFilter="",sortMode="new",pendingCoverUri="";
    private ListenableFuture<MediaController> controllerFuture;
    private MediaController controller;
    private final Handler handler=new Handler(Looper.getMainLooper());

    private final Runnable miniTicker=new Runnable(){
        @Override public void run(){
            if(controller!=null&&controller.getDuration()>0){
                miniProgress.setProgress((int)Math.min(1000,controller.getCurrentPosition()*1000/controller.getDuration()));
            }
            handler.postDelayed(this,700);
        }
    };

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);Store.ensure(this);buildShell();connectMini();
        if(getIntent()!=null&&Intent.ACTION_VIEW.equals(getIntent().getAction())&&getIntent().getData()!=null){
            Uri u=getIntent().getData();persist(u);Store.addRecent(this,u.toString());
            openContext(u.toString(),Collections.singletonList(u.toString()));return;
        }
        if(needsPermission())requestStorage();else showHome();
    }

    private boolean needsPermission(){return android.os.Build.VERSION.SDK_INT>=23&&ContextCompat.checkSelfPermission(this,Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED;}
    private void requestStorage(){ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},REQ_STORAGE);showPermissionHint();}
    @Override public void onRequestPermissionsResult(int r,@NonNull String[]p,@NonNull int[]g){super.onRequestPermissionsResult(r,p,g);showHome();}

    private void buildShell(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Ui.BG);
        content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        miniWrap=new LinearLayout(this);miniWrap.setOrientation(LinearLayout.VERTICAL);miniWrap.setBackgroundColor(Ui.SURFACE);miniWrap.setVisibility(View.GONE);
        miniProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);miniProgress.setMax(1000);miniProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));miniProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.HAIR));
        miniWrap.addView(miniProgress,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,2)));

        miniRow=Ui.row(this);miniRow.setPadding(Ui.dp(this,10),Ui.dp(this,5),Ui.dp(this,4),Ui.dp(this,5));
        miniThumb=new ImageView(this);miniThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);miniRow.addView(miniThumb,new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,44)));
        miniTitle=Ui.text(this,"",14,Ui.TEXT,true);miniTitle.setMaxLines(1);miniTitle.setPadding(Ui.dp(this,10),0,Ui.dp(this,6),0);miniRow.addView(miniTitle,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        TextView prev=Ui.icon(this,"‹");prev.setTextSize(26);miniPlay=Ui.icon(this,"▶");TextView next=Ui.icon(this,"›");next.setTextSize(26);TextView stop=Ui.icon(this,"×");
        miniRow.addView(prev,new LinearLayout.LayoutParams(Ui.dp(this,40),Ui.dp(this,48)));miniRow.addView(miniPlay,new LinearLayout.LayoutParams(Ui.dp(this,42),Ui.dp(this,48)));miniRow.addView(next,new LinearLayout.LayoutParams(Ui.dp(this,40),Ui.dp(this,48)));miniRow.addView(stop,new LinearLayout.LayoutParams(Ui.dp(this,38),Ui.dp(this,48)));
        miniRow.setOnClickListener(v->startActivity(new Intent(this,PlayerActivity.class)));
        prev.setOnClickListener(v->{if(controller!=null&&controller.hasPreviousMediaItem())controller.seekToPreviousMediaItem();});
        next.setOnClickListener(v->{if(controller!=null&&controller.hasNextMediaItem())controller.seekToNextMediaItem();});
        miniPlay.setOnClickListener(v->{if(controller!=null){if(controller.isPlaying())controller.pause();else controller.play();}});
        stop.setOnClickListener(v->{Playback.stop(this);miniWrap.setVisibility(View.GONE);});
        miniWrap.addView(miniRow,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,54)));
        root.addView(miniWrap,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,56)));

        LinearLayout nav=Ui.row(this);nav.setPadding(Ui.dp(this,8),Ui.dp(this,1),Ui.dp(this,8),Ui.dp(this,4));nav.setBackgroundColor(Ui.BG);
        navHome=navItem("⌂\nHome");navLibrary=navItem("▦\nLibrary");navQueues=navItem("☷\nQueues");
        nav.addView(navHome,new LinearLayout.LayoutParams(0,Ui.dp(this,52),1));nav.addView(navLibrary,new LinearLayout.LayoutParams(0,Ui.dp(this,52),1));nav.addView(navQueues,new LinearLayout.LayoutParams(0,Ui.dp(this,52),1));
        navHome.setOnClickListener(v->showHome());navLibrary.setOnClickListener(v->showLibrary("all",""));navQueues.setOnClickListener(v->showQueues());
        root.addView(nav);setContentView(root);
    }

    private TextView navItem(String s){TextView t=Ui.text(this,s,11,Ui.MUTED,true);t.setGravity(Gravity.CENTER);return t;}
    private void selectNav(int n){navHome.setTextColor(n==0?Ui.TEXT:Ui.MUTED);navLibrary.setTextColor(n==1?Ui.TEXT:Ui.MUTED);navQueues.setTextColor(n==2?Ui.TEXT:Ui.MUTED);}

    private void connectMini(){
        SessionToken token=new SessionToken(this,new ComponentName(this,PlayerService.class));
        controllerFuture=new MediaController.Builder(this,token).buildAsync();
        controllerFuture.addListener(()->{
            try{
                controller=controllerFuture.get();
                controller.addListener(new Player.Listener(){
                    @Override public void onMediaItemTransition(MediaItem item,int reason){updateMini();}
                    @Override public void onIsPlayingChanged(boolean b){updateMini();}
                    @Override public void onMediaMetadataChanged(androidx.media3.common.MediaMetadata m){updateMini();}
                });
                updateMini();handler.post(miniTicker);
            }catch(Exception ignored){}
        },ContextCompat.getMainExecutor(this));
    }

    private void updateMini(){
        if(controller==null||controller.getCurrentMediaItem()==null){miniWrap.setVisibility(View.GONE);return;}
        String uri=controller.getCurrentMediaItem().mediaId;MediaEntry e=MediaRepository.resolve(this,uri);
        if(e==null){miniWrap.setVisibility(View.GONE);return;}
        miniWrap.setVisibility(View.VISIBLE);miniTitle.setText(e.title);miniPlay.setText(controller.isPlaying()?"Ⅱ":"▶");Thumb.load(this,miniThumb,e);
    }

    private void refresh(Runnable after){
        Executors.newSingleThreadExecutor().execute(()->{
            ArrayList<MediaEntry>m=needsPermission()?new ArrayList<>():MediaRepository.scan(this);
            runOnUiThread(()->{allMedia=m;if(after!=null)after.run();});
        });
    }

    private void showPermissionHint(){
        content.removeAllViews();LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(Ui.dp(this,24),Ui.dp(this,36),Ui.dp(this,24),0);
        page.addView(Ui.text(this,"Twin",24,Ui.TEXT,true));
        TextView p=Ui.text(this,"Allow local media access so Twin can show your audio and video. Nothing is uploaded anywhere.",15,Ui.MUTED,false);p.setPadding(0,Ui.dp(this,10),0,Ui.dp(this,18));page.addView(p);
        TextView b=Ui.pill(this,"Allow media access");b.setOnClickListener(v->requestStorage());page.addView(b,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,Ui.dp(this,44)));content.addView(page);
    }

    private void showHome(){selectNav(0);refresh(this::renderHome);}
    private void renderHome(){
        ScrollView sv=new ScrollView(this);LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(Ui.dp(this,18),Ui.dp(this,8),Ui.dp(this,18),Ui.dp(this,26));sv.addView(page);
        LinearLayout top=Ui.row(this);top.addView(Ui.text(this,"Twin",22,Ui.TEXT,true),new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));TextView search=Ui.icon(this,"⌕");search.setOnClickListener(v->startActivity(new Intent(this,GlobalSearchActivity.class)));top.addView(search,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,46)));page.addView(top);

        MediaEntry cont=findContinue();
        if(cont!=null){page.addView(Ui.section(this,"Continue"));page.addView(continueMedia(cont));}

        LinearLayout ch=Ui.row(this);ch.addView(Ui.section(this,"Collections"),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));TextView add=Ui.icon(this,"＋");add.setOnClickListener(v->createCollection());ch.addView(add,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));page.addView(ch);
        ArrayList<Store.CollectionDef>cs=Store.getCollections(this);
        if(cs.isEmpty()){
            LinearLayout empty=Ui.row(this);empty.addView(Ui.text(this,"No collections yet",14,Ui.MUTED,false),new LinearLayout.LayoutParams(0,Ui.dp(this,50),1));TextView create=Ui.text(this,"Create",14,Ui.ACCENT,true);create.setGravity(Gravity.CENTER);create.setOnClickListener(v->createCollection());empty.addView(create,new LinearLayout.LayoutParams(Ui.dp(this,70),Ui.dp(this,50)));page.addView(empty);
        }else{
            HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);LinearLayout strip=Ui.row(this);
            for(Store.CollectionDef c:cs){LinearLayout cell=collectionCell(c);cell.setOnClickListener(v->openCollection(c.id));strip.addView(cell,new LinearLayout.LayoutParams(Ui.dp(this,142),Ui.dp(this,150)));}
            hs.addView(strip);page.addView(hs);
        }

        page.addView(Ui.section(this,"Recent"));
        ArrayList<MediaEntry> recent=recentEntries();int shown=0;
        for(MediaEntry e:recent){LinearLayout row=mediaListRow(e);row.setOnClickListener(v->openMediaContext(e.uri,recent));row.setOnLongClickListener(v->{mediaMenu(e);return true;});page.addView(row);page.addView(Ui.hairline(this));if(++shown>=8)break;}
        if(shown==0)page.addView(Ui.text(this,"Open something. It will appear here.",14,Ui.MUTED,false));
        content.removeAllViews();content.addView(sv);
    }

    private MediaEntry findContinue(){
        for(MediaEntry e:recentEntries()){long[]p=Store.progress(this,e.uri);long d=p[1]>0?p[1]:e.duration;if(p[0]>5000&&(d<=0||p[0]<d-8000))return e;}return null;
    }

    private FrameLayout continueMedia(MediaEntry e){
        FrameLayout frame=new FrameLayout(this);ImageView iv=new ImageView(this);iv.setScaleType(ImageView.ScaleType.CENTER_CROP);Thumb.load(this,iv,e);frame.addView(iv,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,196)));
        LinearLayout overlay=Ui.row(this);overlay.setPadding(Ui.dp(this,12),Ui.dp(this,8),Ui.dp(this,8),Ui.dp(this,8));overlay.setBackgroundColor(android.graphics.Color.argb(145,0,0,0));
        LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);TextView n=Ui.text(this,e.title,16,Ui.TEXT,true);n.setMaxLines(1);text.addView(n);long[]p=Store.progress(this,e.uri);text.addView(Ui.text(this,Ui.time(p[0])+" / "+Ui.time(p[1]>0?p[1]:e.duration),12,Ui.MUTED,false));overlay.addView(text,new LinearLayout.LayoutParams(0,Ui.dp(this,50),1));TextView play=Ui.icon(this,"▶");overlay.addView(play,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,50)));
        FrameLayout.LayoutParams op=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,66),Gravity.BOTTOM);frame.addView(overlay,op);
        frame.setOnClickListener(v->openMediaContext(e.uri,recentEntries()));return frame;
    }

    private LinearLayout collectionCell(Store.CollectionDef c){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(0,0,Ui.dp(this,10),0);ImageView iv=new ImageView(this);iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if(c.cover!=null&&!c.cover.isEmpty())try{iv.setImageURI(Uri.parse(c.cover));}catch(Exception ignored){iv.setImageResource(R.drawable.cover_placeholder);}else iv.setImageResource(R.drawable.cover_placeholder);
        box.addView(iv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,108)));TextView n=Ui.text(this,c.name,14,Ui.TEXT,true);n.setPadding(0,Ui.dp(this,6),0,0);box.addView(n);box.addView(Ui.text(this,c.playlists.size()+" playlists",11,Ui.MUTED,false));return box;
    }

    private LinearLayout mediaListRow(MediaEntry e){
        LinearLayout row=Ui.row(this);row.setPadding(0,Ui.dp(this,8),0,Ui.dp(this,8));ImageView iv=new ImageView(this);iv.setScaleType(ImageView.ScaleType.CENTER_CROP);Thumb.load(this,iv,e);row.addView(iv,new LinearLayout.LayoutParams(Ui.dp(this,68),Ui.dp(this,50)));
        LinearLayout txt=new LinearLayout(this);txt.setOrientation(LinearLayout.VERTICAL);txt.setPadding(Ui.dp(this,12),0,0,0);TextView name=Ui.text(this,e.title,15,Ui.TEXT,true);name.setMaxLines(1);txt.addView(name);
        long[]p=Store.progress(this,e.uri);String sub=p[0]>0?Ui.time(p[0])+" / "+Ui.time(p[1]>0?p[1]:e.duration):(e.isVideo()?"Video":"Audio")+" · "+e.durationText();txt.addView(Ui.text(this,sub,12,Ui.MUTED,false));row.addView(txt,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        TextView more=Ui.icon(this,"⋮");more.setOnClickListener(v->mediaMenu(e));row.addView(more,new LinearLayout.LayoutParams(Ui.dp(this,42),Ui.dp(this,48)));return row;
    }

    private void showLibrary(String filter,String folder){selectNav(1);libraryFilter=filter;folderFilter=folder;refresh(this::renderLibrary);}
    private void renderLibrary(){
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(Ui.dp(this,14),Ui.dp(this,6),Ui.dp(this,14),0);
        LinearLayout top=Ui.row(this);top.addView(Ui.text(this,folderFilter.isEmpty()?"Library":folderFilter,24,Ui.TEXT,true),new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        TextView open=Ui.icon(this,"＋");open.setOnClickListener(v->openFile());top.addView(open,new LinearLayout.LayoutParams(Ui.dp(this,46),Ui.dp(this,46)));
        TextView search=Ui.icon(this,"⌕");search.setOnClickListener(v->startActivity(new Intent(this,GlobalSearchActivity.class)));top.addView(search,new LinearLayout.LayoutParams(Ui.dp(this,46),Ui.dp(this,46)));
        TextView layout=Ui.icon(this,Store.libraryGrid(this)?"☰":"▦");layout.setOnClickListener(v->{Store.setLibraryGrid(this,!Store.libraryGrid(this));renderLibrary();});top.addView(layout,new LinearLayout.LayoutParams(Ui.dp(this,46),Ui.dp(this,46)));
        TextView sort=Ui.icon(this,"⇅");sort.setOnClickListener(v->sortSheet());top.addView(sort,new LinearLayout.LayoutParams(Ui.dp(this,46),Ui.dp(this,46)));page.addView(top);

        if(folderFilter.isEmpty()){
            HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);LinearLayout chips=Ui.row(this);
            for(String[]x:new String[][]{{"all","All"},{"audio","Audio"},{"video","Video"},{"folders","Folders"}}){TextView b=Ui.pill(this,x[1]);if(libraryFilter.equals(x[0]))b.setTextColor(Ui.ACCENT);b.setOnClickListener(v->{libraryFilter=x[0];renderLibrary();});chips.addView(b);LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)b.getLayoutParams();lp.setMargins(0,0,Ui.dp(this,7),Ui.dp(this,8));b.setLayoutParams(lp);}hs.addView(chips);page.addView(hs);
        }else{TextView b=Ui.text(this,"‹  All folders",14,Ui.MUTED,true);b.setPadding(0,Ui.dp(this,4),0,Ui.dp(this,10));b.setOnClickListener(v->{folderFilter="";libraryFilter="folders";renderLibrary();});page.addView(b);}

        RecyclerView rv=new RecyclerView(this);
        if("folders".equals(libraryFilter)&&folderFilter.isEmpty()){rv.setLayoutManager(new LinearLayoutManager(this));rv.setAdapter(new FolderAdapter(folderNames()));}
        else{boolean grid=Store.libraryGrid(this);ArrayList<MediaEntry> data=filteredMedia();rv.setLayoutManager(grid?new GridLayoutManager(this,3):new LinearLayoutManager(this));rv.setAdapter(new MediaAdapter(data,grid));}
        page.addView(rv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));content.removeAllViews();content.addView(page);
    }

    private ArrayList<MediaEntry> filteredMedia(){
        ArrayList<MediaEntry>o=new ArrayList<>();for(MediaEntry e:allMedia){if("audio".equals(libraryFilter)&&e.isVideo())continue;if("video".equals(libraryFilter)&&!e.isVideo())continue;if(!folderFilter.isEmpty()&&!folderFilter.equals(e.folder))continue;o.add(e);}
        if("name".equals(sortMode))o.sort((a,b)->a.title.compareToIgnoreCase(b.title));else if("duration".equals(sortMode))o.sort((a,b)->Long.compare(b.duration,a.duration));else if("played".equals(sortMode))o.sort((a,b)->Long.compare(Store.progress(this,b.uri)[2],Store.progress(this,a.uri)[2]));else o.sort((a,b)->Long.compare(b.addedAt,a.addedAt));return o;
    }
    private ArrayList<String> folderNames(){Set<String>s=new HashSet<>();for(MediaEntry e:allMedia)s.add(e.folder);ArrayList<String>o=new ArrayList<>(s);Collections.sort(o,String.CASE_INSENSITIVE_ORDER);return o;}
    private void sortSheet(){Sheets.choices(this,"Sort by",new String[]{"Recently added","Name","Duration","Recently played"},w->{sortMode=new String[]{"new","name","duration","played"}[w];renderLibrary();});}

    private void showQueues(){
        selectNav(2);ScrollView sv=new ScrollView(this);LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(Ui.dp(this,18),Ui.dp(this,8),Ui.dp(this,18),Ui.dp(this,26));sv.addView(page);
        LinearLayout top=Ui.row(this);top.addView(Ui.text(this,"Queues",24,Ui.TEXT,true),new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));TextView add=Ui.icon(this,"＋");add.setOnClickListener(v->createQueue());top.addView(add,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));page.addView(top);
        boolean any=false;String active=Store.currentQueueId(this);
        for(Store.QueueDef q:Store.getQueues(this)){if("current".equals(q.id)&&q.items.isEmpty())continue;any=true;LinearLayout r=Ui.row(this);r.setPadding(0,Ui.dp(this,11),0,Ui.dp(this,11));LinearLayout t=new LinearLayout(this);t.setOrientation(LinearLayout.VERTICAL);t.addView(Ui.text(this,(q.id.equals(active)?"●  ":"")+("current".equals(q.id)?"Now playing":q.name),16,q.id.equals(active)?Ui.ACCENT:Ui.TEXT,true));t.addView(Ui.text(this,q.items.size()+" items",12,Ui.MUTED,false));r.addView(t,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));TextView more=Ui.icon(this,"⋮");more.setOnClickListener(v->queueMenu(q));r.addView(more,new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,48)));r.setOnClickListener(v->openQueue(q.id));page.addView(r);page.addView(Ui.hairline(this));}
        if(!any)page.addView(Ui.text(this,"No saved queues yet.",14,Ui.MUTED,false));content.removeAllViews();content.addView(sv);
    }

    private void createCollection(){Sheets.prompt(this,"New collection","Collection name","","Create",name->{ArrayList<Store.CollectionDef>cs=Store.getCollections(this);Store.CollectionDef c=new Store.CollectionDef();c.name=name;cs.add(c);Store.saveCollections(this,cs);renderHome();});}
    private void createQueue(){Sheets.prompt(this,"New queue","Queue name","","Create",name->{ArrayList<Store.QueueDef>qs=Store.getQueues(this);Store.QueueDef q=new Store.QueueDef();q.name=name;qs.add(q);Store.saveQueues(this,qs);showQueues();});}
    private void queueMenu(Store.QueueDef q){
        ArrayList<String>opts=new ArrayList<>();opts.add("Make active");if(!"current".equals(q.id))opts.add("Rename");opts.add("Clear");if(!"current".equals(q.id))opts.add("Delete");
        Sheets.choices(this,"current".equals(q.id)?"Now playing":q.name,opts.toArray(new String[0]),w->{String x=opts.get(w);
            if("Make active".equals(x)){Store.setCurrentQueue(this,q.id);Playback.syncActiveQueue(this);}
            else if("Rename".equals(x))Sheets.prompt(this,"Rename queue","Queue name",q.name,"Save",name->{ArrayList<Store.QueueDef>qs=Store.getQueues(this);for(Store.QueueDef z:qs)if(z.id.equals(q.id))z.name=name;Store.saveQueues(this,qs);showQueues();});
            else if("Clear".equals(x))Sheets.confirm(this,"Clear queue?","Media files stay on your phone.","Clear",()->{Store.replaceQueue(this,q.id,new ArrayList<>());if(q.id.equals(Store.currentQueueId(this)))Playback.syncActiveQueue(this);showQueues();});
            else Sheets.confirm(this,"Delete queue?","Media files stay on your phone.","Delete",()->{ArrayList<Store.QueueDef>qs=Store.getQueues(this);qs.removeIf(z->z.id.equals(q.id));Store.saveQueues(this,qs);if(q.id.equals(Store.currentQueueId(this)))Store.setCurrentQueue(this,"current");showQueues();});
        });
    }

    private void mediaMenu(MediaEntry e){
        Sheets.choices(this,e.title,new String[]{"Play","Play next","Add to queue","Add to playlist","Rename in Twin","Set cover","Details"},w->{
            if(w==0)openContext(e.uri,Collections.singletonList(e.uri));
            else if(w==1){Playback.addNext(this,e.uri);Toast.makeText(this,"Playing next",Toast.LENGTH_SHORT).show();}
            else if(w==2)addQueueSheet(e.uri);
            else if(w==3)addPlaylistSheet(e.uri);
            else if(w==4)Sheets.prompt(this,"Rename in Twin","Display name",e.title,"Save",name->{Store.setAlias(this,e.uri,name);Playback.syncActiveQueue(this);refresh(this::renderLibrary);});
            else if(w==5){pendingCoverUri=e.uri;pickCover();}
            else Sheets.info(this,e.title,(e.isVideo()?"Video":"Audio")+"\n"+e.durationText()+"\n"+e.folder+"\n\nOriginal: "+e.originalTitle+"\n"+e.uri);
        });
    }

    private void addQueueSheet(String uri){
        ArrayList<Store.QueueDef>qs=Store.getQueues(this);ArrayList<String>names=new ArrayList<>();for(Store.QueueDef q:qs)names.add("current".equals(q.id)?"Now playing":q.name);
        Sheets.choices(this,"Add to queue",names.toArray(new String[0]),w->{Store.addToQueue(this,qs.get(w).id,uri,false);if(qs.get(w).id.equals(Store.currentQueueId(this)))Playback.syncActiveQueue(this);});
    }
    private void addPlaylistSheet(String uri){
        ArrayList<Store.CollectionDef>cs=Store.getCollections(this);ArrayList<String>labels=new ArrayList<>(),pids=new ArrayList<>(),cids=new ArrayList<>();
        for(Store.CollectionDef c:cs)for(Store.Playlist p:c.playlists){labels.add(c.name+" · "+p.name);pids.add(p.id);cids.add(c.id);}
        if(labels.isEmpty()){Toast.makeText(this,"Create a playlist first",Toast.LENGTH_SHORT).show();return;}
        Sheets.choices(this,"Add to playlist",labels.toArray(new String[0]),w->{ArrayList<Store.CollectionDef>fresh=Store.getCollections(this);for(Store.CollectionDef c:fresh)if(c.id.equals(cids.get(w)))for(Store.Playlist p:c.playlists)if(p.id.equals(pids.get(w))){boolean exists=false;for(Store.Item it:p.items)if(it.uri.equals(uri))exists=true;if(!exists)p.items.add(new Store.Item(uri));}Store.saveCollections(this,fresh);});
    }

    private void openMediaContext(String uri,List<MediaEntry> entries){openContext(uri,MediaRepository.uris(entries));}
    private void openContext(String uri,List<String> uris){
        if(uri==null)return;ArrayList<String>safe=new ArrayList<>(uris);if(!safe.contains(uri))safe.add(0,uri);
        Store.setCurrentQueue(this,"current");Store.replaceQueue(this,"current",safe);Store.addRecent(this,uri);
        Intent i=new Intent(this,PlayerActivity.class);i.putExtra("uri",uri);startActivity(i);
    }

    private ArrayList<MediaEntry> recentEntries(){
        ArrayList<MediaEntry>out=new ArrayList<>();for(String u:Store.getRecent(this)){MediaEntry e=find(u);if(e==null)e=MediaRepository.resolve(this,u);if(e!=null)out.add(e);}return out;
    }
    private MediaEntry find(String uri){for(MediaEntry e:allMedia)if(e.uri.equals(uri))return e;return null;}

    private void openCollection(String id){Intent i=new Intent(this,OrganizeActivity.class);i.putExtra("mode","collection");i.putExtra("collection",id);startActivity(i);}
    private void openQueue(String id){Intent i=new Intent(this,OrganizeActivity.class);i.putExtra("mode","queue");i.putExtra("queue",id);startActivity(i);}

    private void openFile(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"audio/*","video/*"});i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,REQ_OPEN);}
    private void pickCover(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,REQ_COVER);}
    private void persist(Uri u){try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}}
    @Override protected void onActivityResult(int r,int res,Intent data){
        super.onActivityResult(r,res,data);if(res!=RESULT_OK||data==null||data.getData()==null)return;Uri u=data.getData();persist(u);
        if(r==REQ_OPEN){Store.addRecent(this,u.toString());openContext(u.toString(),Collections.singletonList(u.toString()));}
        else if(r==REQ_COVER&&!pendingCoverUri.isEmpty()){Store.setMediaCover(this,pendingCoverUri,u.toString());Playback.syncActiveQueue(this);refresh(this::renderLibrary);}
    }

    @Override protected void onResume(){super.onResume();if(content!=null&&!needsPermission())refresh(null);updateMini();}
    @Override protected void onDestroy(){handler.removeCallbacks(miniTicker);if(controllerFuture!=null)MediaController.releaseFuture(controllerFuture);super.onDestroy();}

    class MediaVH extends RecyclerView.ViewHolder{
        ImageView art;TextView title,sub,more;LinearLayout root;
        MediaVH(View v,boolean grid){super(v);root=(LinearLayout)v;if(grid){art=(ImageView)root.getChildAt(0);title=(TextView)root.getChildAt(1);sub=(TextView)root.getChildAt(2);}else{art=(ImageView)root.getChildAt(0);LinearLayout t=(LinearLayout)root.getChildAt(1);title=(TextView)t.getChildAt(0);sub=(TextView)t.getChildAt(1);more=(TextView)root.getChildAt(2);}}
    }
    class MediaAdapter extends RecyclerView.Adapter<MediaVH>{
        final ArrayList<MediaEntry>data;final boolean grid;
        MediaAdapter(ArrayList<MediaEntry>d,boolean g){data=d;grid=g;}
        @NonNull public MediaVH onCreateViewHolder(@NonNull ViewGroup p,int v){
            if(grid){LinearLayout b=new LinearLayout(MainActivity.this);b.setOrientation(LinearLayout.VERTICAL);b.setPadding(Ui.dp(MainActivity.this,3),Ui.dp(MainActivity.this,3),Ui.dp(MainActivity.this,3),Ui.dp(MainActivity.this,10));ImageView iv=new ImageView(MainActivity.this);iv.setScaleType(ImageView.ScaleType.CENTER_CROP);b.addView(iv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(MainActivity.this,106)));TextView n=Ui.text(MainActivity.this,"",12,Ui.TEXT,true);n.setMaxLines(1);b.addView(n);b.addView(Ui.text(MainActivity.this,"",10,Ui.MUTED,false));return new MediaVH(b,true);}
            return new MediaVH(mediaListRow(new MediaEntry("","",0,0,"audio","")),false);
        }
        public void onBindViewHolder(@NonNull MediaVH h,int pos){MediaEntry e=data.get(pos);Thumb.load(MainActivity.this,h.art,e);h.title.setText(e.title);h.sub.setText(e.durationText());h.root.setOnClickListener(v->openMediaContext(e.uri,data));h.root.setOnLongClickListener(v->{mediaMenu(e);return true;});if(!grid&&h.more!=null)h.more.setOnClickListener(v->mediaMenu(e));}
        public int getItemCount(){return data.size();}
    }

    class FolderAdapter extends RecyclerView.Adapter<MediaVH>{
        final ArrayList<String>data;FolderAdapter(ArrayList<String>d){data=d;}
        @NonNull public MediaVH onCreateViewHolder(@NonNull ViewGroup p,int v){return new MediaVH(mediaListRow(new MediaEntry("","",0,0,"audio","")),false);}
        public void onBindViewHolder(@NonNull MediaVH h,int pos){String f=data.get(pos);int count=0;for(MediaEntry e:allMedia)if(f.equals(e.folder))count++;h.art.setImageResource(R.drawable.cover_placeholder);h.title.setText(f);h.sub.setText(count+" media");h.more.setVisibility(View.GONE);h.root.setOnClickListener(v->{folderFilter=f;libraryFilter="all";renderLibrary();});}
        public int getItemCount(){return data.size();}
    }
}
