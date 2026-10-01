package kz.medetakbolat.twin;

import android.Manifest;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
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
    private static final int REQ_STORAGE=44,REQ_OPEN=701,REQ_COVER_MEDIA=702,REQ_COVER_PLAYLIST=703;
    private static final int HOME=0,LIBRARY=1,PLAYLISTS=2,QUEUE=3;

    private FrameLayout content;
    private LinearLayout miniWrap,miniRow;
    private ImageView miniThumb;
    private TextView miniTitle,miniPlay;
    private ProgressBar miniProgress;
    private final TextView[] nav=new TextView[4];

    private ArrayList<MediaEntry> allMedia=new ArrayList<>();
    private String libraryFilter="all",folderFilter="",sortMode="new",playlistFilter="all";
    private final HashSet<String> selected=new HashSet<>();
    private boolean selectionMode=false;
    private int currentPage=HOME;

    private ListenableFuture<MediaController> controllerFuture;
    private MediaController controller;
    private final Handler handler=new Handler(Looper.getMainLooper());

    private String pendingCoverUri="",pendingPlaylistCoverId="";
    private float downX,downY;private long downAt;

    private final Runnable miniTicker=new Runnable(){
        @Override public void run(){
            if(controller!=null&&controller.getDuration()>0)miniProgress.setProgress((int)Math.min(1000,controller.getCurrentPosition()*1000/controller.getDuration()));
            handler.postDelayed(this,700);
        }
    };

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);Store.ensure(this);buildShell();connectMini();
        if(getIntent()!=null&&Intent.ACTION_VIEW.equals(getIntent().getAction())&&getIntent().getData()!=null){
            Uri u=getIntent().getData();persist(u);Store.addRecent(this,u.toString());Store.setPlaybackFallbackCover(this,"");
            openContext(u.toString(),Collections.singletonList(u.toString()));return;
        }
        currentPage=Math.max(0,Math.min(3,Store.lastMainPage(this)));
        if(needsPermission())requestStorage();else showPage(currentPage);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent e){
        if(e.getAction()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();downAt=System.currentTimeMillis();}
        else if(e.getAction()==MotionEvent.ACTION_UP&&!selectionMode){
            float dx=e.getX()-downX,dy=e.getY()-downY;long dt=System.currentTimeMillis()-downAt;
            if(dt<850&&Math.abs(dx)>Ui.dp(this,150)&&Math.abs(dx)>Math.abs(dy)*1.55f){
                int next=currentPage+(dx<0?1:-1);
                if(next>=0&&next<=3){showPage(next);return true;}
            }
        }
        return super.dispatchTouchEvent(e);
    }

    private boolean needsPermission(){return android.os.Build.VERSION.SDK_INT>=23&&ContextCompat.checkSelfPermission(this,Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED;}
    private void requestStorage(){ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},REQ_STORAGE);showPermissionHint();}
    @Override public void onRequestPermissionsResult(int r,@NonNull String[]p,@NonNull int[]g){super.onRequestPermissionsResult(r,p,g);showPage(HOME);}

    private void buildShell(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Ui.BG);
        content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        miniWrap=new LinearLayout(this);miniWrap.setOrientation(LinearLayout.VERTICAL);miniWrap.setBackgroundColor(ColorUtil.alpha(Ui.SURFACE,245));miniWrap.setVisibility(View.GONE);
        miniProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);miniProgress.setMax(1000);
        miniProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));miniProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.HAIR));
        miniWrap.addView(miniProgress,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,2)));

        miniRow=Ui.row(this);miniRow.setPadding(Ui.dp(this,10),Ui.dp(this,5),Ui.dp(this,6),Ui.dp(this,5));
        miniThumb=new ImageView(this);miniThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);miniThumb.setClipToOutline(true);miniThumb.setBackground(Ui.round(Ui.SURFACE2,12,this));
        miniRow.addView(miniThumb,new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,44)));
        miniTitle=Ui.text(this,"",14,Ui.TEXT,true);miniTitle.setSingleLine(true);miniTitle.setPadding(Ui.dp(this,10),0,Ui.dp(this,4),0);miniRow.addView(miniTitle,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        TextView prev=Ui.icon(this,"‹");prev.setTextSize(26);miniPlay=Ui.icon(this,"▶");TextView next=Ui.icon(this,"›");next.setTextSize(26);
        miniRow.addView(prev,new LinearLayout.LayoutParams(Ui.dp(this,42),Ui.dp(this,48)));miniRow.addView(miniPlay,new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,48)));miniRow.addView(next,new LinearLayout.LayoutParams(Ui.dp(this,42),Ui.dp(this,48)));
        miniRow.setOnClickListener(v->startActivity(new Intent(this,PlayerActivity.class)));
        miniRow.setOnLongClickListener(v->{Sheets.choices(this,"Playback",new String[]{"Previous","Open queue","Stop"},w->{if(w==0&&controller!=null&&controller.hasPreviousMediaItem())controller.seekToPreviousMediaItem();else if(w==1)showPage(QUEUE);else if(w==2)Playback.stop(this);});return true;});
        prev.setOnClickListener(v->{if(controller!=null&&controller.hasPreviousMediaItem())controller.seekToPreviousMediaItem();});
        next.setOnClickListener(v->{if(controller!=null&&controller.hasNextMediaItem())controller.seekToNextMediaItem();});
        miniPlay.setOnClickListener(v->{if(controller!=null){if(controller.isPlaying())controller.pause();else controller.play();}});
        miniWrap.addView(miniRow,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,54)));
        root.addView(miniWrap,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,56)));

        LinearLayout bottom=Ui.row(this);bottom.setPadding(Ui.dp(this,4),0,Ui.dp(this,4),Ui.dp(this,4));bottom.setBackgroundColor(Ui.BG);
        nav[0]=navItem("⌂","Home");nav[1]=navItem("▦","Library");nav[2]=navItem("≡","Playlists");nav[3]=navItem("☷","Queue");
        for(int i=0;i<4;i++){final int page=i;bottom.addView(nav[i],new LinearLayout.LayoutParams(0,Ui.dp(this,54),1));nav[i].setOnClickListener(v->showPage(page));}
        root.addView(bottom);setContentView(root);
    }

    private TextView navItem(String icon,String label){TextView t=Ui.text(this,icon+"\n"+label,10,Ui.MUTED,true);t.setGravity(Gravity.CENTER);t.setLines(2);return t;}
    private void selectNav(int page){for(int i=0;i<4;i++)nav[i].setTextColor(i==page?Ui.TEXT:Ui.MUTED);}

    private void connectMini(){
        if(controller!=null)return;showMiniFromStore();
        SessionToken token=new SessionToken(this,new ComponentName(this,PlayerService.class));
        controllerFuture=new MediaController.Builder(this,token).buildAsync();
        controllerFuture.addListener(()->{
            try{
                controller=controllerFuture.get();
                controller.addListener(new Player.Listener(){
                    @Override public void onMediaItemTransition(MediaItem item,int reason){updateMini();}
                    @Override public void onIsPlayingChanged(boolean b){updateMini();}
                    @Override public void onMediaMetadataChanged(androidx.media3.common.MediaMetadata m){updateMini();}
                    @Override public void onPlaybackStateChanged(int state){updateMini();}
                });
                updateMini();handler.removeCallbacks(miniTicker);handler.post(miniTicker);
            }catch(Exception ignored){showMiniFromStore();}
        },ContextCompat.getMainExecutor(this));
    }

    private void showMiniFromStore(){
        String uri=Store.nowPlaying(this);if(uri==null||uri.isEmpty()){miniWrap.setVisibility(View.GONE);return;}
        MediaEntry e=MediaRepository.resolve(this,uri);if(e==null){miniWrap.setVisibility(View.GONE);return;}
        miniWrap.setVisibility(View.VISIBLE);miniTitle.setText(e.title);miniPlay.setText("▶");
        long[]p=Store.progress(this,uri);if(p[1]>0)miniProgress.setProgress((int)Math.min(1000,p[0]*1000/p[1]));
        Thumb.load(this,miniThumb,e,Store.playbackFallbackCover(this));
    }
    private void updateMini(){
        if(controller==null||controller.getCurrentMediaItem()==null){showMiniFromStore();return;}
        String uri=controller.getCurrentMediaItem().mediaId;MediaEntry e=MediaRepository.resolve(this,uri);if(e==null){showMiniFromStore();return;}
        Store.setNowPlaying(this,uri);miniWrap.setVisibility(View.VISIBLE);miniTitle.setText(e.title);miniPlay.setText(controller.isPlaying()?"Ⅱ":"▶");Thumb.load(this,miniThumb,e,Store.playbackFallbackCover(this));
    }

    private void refresh(Runnable after){
        Executors.newSingleThreadExecutor().execute(()->{
            ArrayList<MediaEntry>m=needsPermission()?new ArrayList<>():MediaRepository.scan(this);
            runOnUiThread(()->{allMedia=m;if(after!=null)after.run();});
        });
    }

    private void showPage(int page){
        currentPage=Math.max(0,Math.min(3,page));Store.setLastMainPage(this,currentPage);selectionMode=false;selected.clear();selectNav(currentPage);
        if(currentPage==HOME)showHome();else if(currentPage==LIBRARY)showLibrary(libraryFilter,folderFilter);else if(currentPage==PLAYLISTS)showPlaylists();else showQueues();
    }

    private void showPermissionHint(){
        content.removeAllViews();LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(Ui.dp(this,24),Ui.dp(this,36),Ui.dp(this,24),0);
        page.addView(Ui.text(this,"Twin",24,Ui.TEXT,true));TextView p=Ui.text(this,"Allow local media access so Twin can show your audio and video. Nothing is uploaded anywhere.",15,Ui.MUTED,false);p.setPadding(0,Ui.dp(this,10),0,Ui.dp(this,18));page.addView(p);
        TextView b=Ui.pill(this,"Allow media access");b.setOnClickListener(v->requestStorage());page.addView(b,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,Ui.dp(this,44)));content.addView(page);
    }

    private void showHome(){selectNav(HOME);refresh(this::renderHome);}
    private void renderHome(){
        ScrollView sv=new ScrollView(this);LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(Ui.dp(this,18),Ui.dp(this,6),Ui.dp(this,18),Ui.dp(this,26));sv.addView(page);
        LinearLayout top=Ui.row(this);top.addView(Ui.text(this,"Twin",21,Ui.TEXT,true),new LinearLayout.LayoutParams(0,Ui.dp(this,44),1));TextView search=Ui.icon(this,"⌕");search.setOnClickListener(v->startActivity(new Intent(this,GlobalSearchActivity.class)));top.addView(search,new LinearLayout.LayoutParams(Ui.dp(this,46),Ui.dp(this,44)));page.addView(top);

        MediaEntry cont=findContinue();if(cont!=null){page.addView(Ui.section(this,"Continue"));page.addView(continueMedia(cont));}

        page.addView(Ui.section(this,"Playlists"));
        ArrayList<Store.Playlist> ps=Store.allPlaylists(this);if(ps.isEmpty()){
            TextView create=Ui.text(this,"＋  Create your first playlist",14,Ui.ACCENT,true);create.setPadding(0,Ui.dp(this,8),0,Ui.dp(this,10));create.setOnClickListener(v->createPlaylist());page.addView(create);
        }else{
            int n=Math.min(4,ps.size());for(int i=0;i<n;i++){Store.Playlist p=ps.get(i);LinearLayout row=playlistHomeRow(p);row.setOnClickListener(v->openPlaylist(p.id));row.setOnLongClickListener(v->{playlistMenu(p);return true;});page.addView(row);}
            if(ps.size()>4){TextView all=Ui.text(this,"All playlists  →",13,Ui.ACCENT,true);all.setPadding(0,Ui.dp(this,5),0,Ui.dp(this,8));all.setOnClickListener(v->showPage(PLAYLISTS));page.addView(all);}
        }

        ArrayList<Store.CollectionDef> cs=Store.visibleCollections(this);
        if(!cs.isEmpty()){
            page.addView(Ui.section(this,"Collections"));
            HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);LinearLayout strip=Ui.row(this);
            for(Store.CollectionDef c:cs){LinearLayout cell=collectionCell(c);cell.setOnClickListener(v->openCollection(c.id));strip.addView(cell,new LinearLayout.LayoutParams(Ui.dp(this,142),Ui.dp(this,148)));}
            hs.addView(strip);page.addView(hs);
        }

        page.addView(Ui.section(this,"Recent"));ArrayList<MediaEntry> recent=recentEntries();int shown=0;
        for(MediaEntry e:recent){LinearLayout row=mediaListRow(e,false);row.setOnClickListener(v->openMediaContext(e.uri,recent));row.setOnLongClickListener(v->{mediaMenu(e);return true;});page.addView(row);page.addView(Ui.hairline(this));if(++shown>=7)break;}
        if(shown==0)page.addView(Ui.text(this,"Open something. It will appear here.",14,Ui.MUTED,false));
        content.removeAllViews();content.addView(sv);
    }

    private LinearLayout playlistHomeRow(Store.Playlist p){
        LinearLayout r=Ui.row(this);r.setPadding(0,Ui.dp(this,7),0,Ui.dp(this,7));ImageView art=new ImageView(this);art.setScaleType(ImageView.ScaleType.CENTER_CROP);art.setClipToOutline(true);art.setBackground(Ui.round(Ui.SURFACE2,11,this));
        if(p.cover!=null&&!p.cover.isEmpty())try{art.setImageURI(Uri.parse(p.cover));}catch(Exception ignored){art.setImageResource(R.drawable.cover_placeholder);}else art.setImageResource(R.drawable.cover_placeholder);
        r.addView(art,new LinearLayout.LayoutParams(Ui.dp(this,58),Ui.dp(this,58)));LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);text.setPadding(Ui.dp(this,12),0,0,0);TextView title=Ui.text(this,p.name,15,Ui.TEXT,true);title.setMaxLines(2);text.addView(title);text.addView(Ui.text(this,p.items.size()+" items · "+Store.collectionNameForPlaylist(this,p.id),12,Ui.MUTED,false));r.addView(text,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));return r;
    }

    private MediaEntry findContinue(){for(MediaEntry e:recentEntries()){long[]p=Store.progress(this,e.uri);long d=p[1]>0?p[1]:e.duration;if(p[0]>5000&&(d<=0||p[0]<d-8000))return e;}return null;}
    private FrameLayout continueMedia(MediaEntry e){
        FrameLayout frame=new FrameLayout(this);ImageView iv=new ImageView(this);iv.setScaleType(ImageView.ScaleType.CENTER_CROP);iv.setClipToOutline(true);iv.setBackground(Ui.round(Ui.SURFACE2,16,this));Thumb.load(this,iv,e);
        frame.addView(iv,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,190)));
        LinearLayout overlay=Ui.row(this);overlay.setPadding(Ui.dp(this,12),Ui.dp(this,8),Ui.dp(this,8),Ui.dp(this,8));overlay.setBackgroundColor(android.graphics.Color.argb(140,0,0,0));
        LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);TextView n=Ui.text(this,e.title,16,Ui.TEXT,true);n.setMaxLines(1);text.addView(n);long[]p=Store.progress(this,e.uri);text.addView(Ui.text(this,Ui.time(p[0])+" / "+Ui.time(p[1]>0?p[1]:e.duration),12,Ui.MUTED,false));overlay.addView(text,new LinearLayout.LayoutParams(0,Ui.dp(this,50),1));overlay.addView(Ui.icon(this,"▶"),new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,50)));
        frame.addView(overlay,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,66),Gravity.BOTTOM));frame.setOnClickListener(v->openMediaContext(e.uri,recentEntries()));return frame;
    }

    private LinearLayout collectionCell(Store.CollectionDef c){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(0,0,Ui.dp(this,10),0);ImageView iv=new ImageView(this);iv.setScaleType(ImageView.ScaleType.CENTER_CROP);iv.setClipToOutline(true);iv.setBackground(Ui.round(Ui.SURFACE2,14,this));
        if(c.cover!=null&&!c.cover.isEmpty())try{iv.setImageURI(Uri.parse(c.cover));}catch(Exception ignored){iv.setImageResource(R.drawable.cover_placeholder);}else iv.setImageResource(R.drawable.cover_placeholder);
        box.addView(iv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,106)));TextView n=Ui.text(this,c.name,14,Ui.TEXT,true);n.setPadding(0,Ui.dp(this,6),0,0);n.setMaxLines(1);box.addView(n);box.addView(Ui.text(this,c.playlists.size()+" playlists",11,Ui.MUTED,false));return box;
    }

    private void showLibrary(String filter,String folder){selectNav(LIBRARY);libraryFilter=filter;folderFilter=folder;refresh(this::renderLibrary);}
    private void renderLibrary(){
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(Ui.dp(this,14),Ui.dp(this,5),Ui.dp(this,14),0);

        LinearLayout top=Ui.row(this);String ttl=selectionMode?selected.size()+" selected":(folderFilter.isEmpty()?"Library":folderFilter);
        top.addView(Ui.text(this,ttl,selectionMode?20:25,Ui.TEXT,true),new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        if(selectionMode){
            TextView close=Ui.icon(this,"×");close.setOnClickListener(v->{selectionMode=false;selected.clear();renderLibrary();});top.addView(close,new LinearLayout.LayoutParams(Ui.dp(this,46),Ui.dp(this,46)));
        }else{
            TextView open=Ui.icon(this,"＋");open.setOnClickListener(v->openFile());top.addView(open,new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,46)));
            TextView search=Ui.icon(this,"⌕");search.setOnClickListener(v->startActivity(new Intent(this,GlobalSearchActivity.class)));top.addView(search,new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,46)));
            if(!"audio".equals(libraryFilter)&&!"folders".equals(libraryFilter)){TextView layout=Ui.icon(this,Store.libraryGrid(this)?"☰":"▦");layout.setOnClickListener(v->{Store.setLibraryGrid(this,!Store.libraryGrid(this));renderLibrary();});top.addView(layout,new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,46)));}
            TextView sort=Ui.icon(this,"⇅");sort.setOnClickListener(v->sortSheet());top.addView(sort,new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,46)));
        }
        page.addView(top);

        if(selectionMode)page.addView(selectionActions());
        else if(folderFilter.isEmpty()){
            HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);LinearLayout tabs=Ui.row(this);
            for(String[]x:new String[][]{{"all","All"},{"audio","Audio"},{"video","Video"},{"folders","Folders"}}){
                TextView b=plainTab(x[1],libraryFilter.equals(x[0]));b.setOnClickListener(v->{libraryFilter=x[0];if("audio".equals(x[0])&&"new".equals(sortMode))sortMode="name";renderLibrary();});tabs.addView(b);
            }hs.addView(tabs);page.addView(hs);
        }else{
            TextView b=Ui.text(this,"‹  All folders",14,Ui.MUTED,true);b.setPadding(0,Ui.dp(this,5),0,Ui.dp(this,10));b.setOnClickListener(v->{folderFilter="";libraryFilter="folders";renderLibrary();});page.addView(b);
        }

        RecyclerView rv=new RecyclerView(this);
        if("folders".equals(libraryFilter)&&folderFilter.isEmpty()){rv.setLayoutManager(new LinearLayoutManager(this));rv.setAdapter(new FolderAdapter(folderNames()));}
        else{
            boolean grid=!"audio".equals(libraryFilter)&&Store.libraryGrid(this);if("video".equals(libraryFilter))grid=true;
            ArrayList<MediaEntry> data=filteredMedia();rv.setLayoutManager(grid?new GridLayoutManager(this,3):new LinearLayoutManager(this));rv.setAdapter(new MediaAdapter(data,grid));
        }
        page.addView(rv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));content.removeAllViews();content.addView(page);
    }

    private TextView plainTab(String label,boolean active){
        TextView t=Ui.text(this,label,14,active?Ui.ACCENT:Ui.TEXT,true);t.setGravity(Gravity.CENTER);t.setPadding(Ui.dp(this,12),Ui.dp(this,8),Ui.dp(this,12),Ui.dp(this,10));
        if(active){android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();g.setColor(android.graphics.Color.TRANSPARENT);g.setStroke(Ui.dp(this,1),Ui.ACCENT);g.setCornerRadius(Ui.dp(this,14));t.setBackground(g);}
        return t;
    }

    private LinearLayout selectionActions(){
        LinearLayout row=Ui.row(this);row.setPadding(0,0,0,Ui.dp(this,6));
        String[] labels={"▶","Next","Playlist","Queue","Share","More"};
        for(String x:labels){TextView b=Ui.text(this,x,12,Ui.TEXT,true);b.setGravity(Gravity.CENTER);b.setOnClickListener(v->selectionAction(x));row.addView(b,new LinearLayout.LayoutParams(0,Ui.dp(this,42),1));}
        return row;
    }

    private void selectionAction(String action){
        ArrayList<String> uris=new ArrayList<>(selected);if(uris.isEmpty())return;
        if("▶".equals(action)){Store.setPlaybackFallbackCover(this,"");Store.setCurrentQueue(this,"current");Store.replaceQueue(this,"current",uris);Playback.replaceAndPlay(this,uris,uris.get(0));startActivity(new Intent(this,PlayerActivity.class));}
        else if("Next".equals(action)){for(int i=uris.size()-1;i>=0;i--)Playback.addNext(this,uris.get(i));Toast.makeText(this,"Added next",Toast.LENGTH_SHORT).show();}
        else if("Playlist".equals(action))addManyToPlaylist(uris);
        else if("Queue".equals(action))addManyToQueue(uris);
        else if("Share".equals(action))shareUris(uris);
        else if(uris.size()==1){MediaEntry e=find(uris.get(0));if(e==null)e=MediaRepository.resolve(this,uris.get(0));if(e!=null)mediaMenu(e);}
        else Sheets.info(this,"Selection",uris.size()+" media selected");
    }

    private ArrayList<MediaEntry> filteredMedia(){
        ArrayList<MediaEntry>o=new ArrayList<>();for(MediaEntry e:allMedia){if("audio".equals(libraryFilter)&&e.isVideo())continue;if("video".equals(libraryFilter)&&!e.isVideo())continue;if(!folderFilter.isEmpty()&&!folderFilter.equals(e.folder))continue;o.add(e);}
        if("name".equals(sortMode))o.sort(Natural.MEDIA_ASC);else if("name_desc".equals(sortMode))o.sort(Natural.MEDIA_DESC);else if("duration".equals(sortMode))o.sort((a,b)->Long.compare(b.duration,a.duration));else if("played".equals(sortMode))o.sort((a,b)->Long.compare(Store.progress(this,b.uri)[2],Store.progress(this,a.uri)[2]));else o.sort((a,b)->Long.compare(b.addedAt,a.addedAt));return o;
    }
    private ArrayList<String> folderNames(){Set<String>s=new HashSet<>();for(MediaEntry e:allMedia)s.add(e.folder);ArrayList<String>o=new ArrayList<>(s);o.sort(Natural::compare);return o;}
    private void sortSheet(){Sheets.choices(this,"Sort by",new String[]{"A → Z","Z → A","Recently added","Duration","Recently played"},w->{sortMode=new String[]{"name","name_desc","new","duration","played"}[w];renderLibrary();});}

    private void enterSelection(String uri){selectionMode=true;selected.add(uri);renderLibrary();}
    private void toggleSelection(String uri){if(selected.contains(uri))selected.remove(uri);else selected.add(uri);if(selected.isEmpty())selectionMode=false;renderLibrary();}

    private void showPlaylists(){selectNav(PLAYLISTS);renderPlaylists();}
    private void renderPlaylists(){
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(Ui.dp(this,14),Ui.dp(this,5),Ui.dp(this,14),0);
        LinearLayout top=Ui.row(this);top.addView(Ui.text(this,"Playlists",25,Ui.TEXT,true),new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));TextView plus=Ui.icon(this,"＋");plus.setOnClickListener(v->createPlaylist());top.addView(plus,new LinearLayout.LayoutParams(Ui.dp(this,46),Ui.dp(this,46)));TextView more=Ui.icon(this,"⋮");more.setOnClickListener(v->Sheets.choices(this,"Playlists",new String[]{"New collection","Search"},w->{if(w==0)createCollection();else startActivity(new Intent(this,GlobalSearchActivity.class));}));top.addView(more,new LinearLayout.LayoutParams(Ui.dp(this,46),Ui.dp(this,46)));page.addView(top);

        HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);LinearLayout tabs=Ui.row(this);
        TextView all=plainTab("All","all".equals(playlistFilter));all.setOnClickListener(v->{playlistFilter="all";renderPlaylists();});tabs.addView(all);
        TextView loose=plainTab("Unsorted","root".equals(playlistFilter));loose.setOnClickListener(v->{playlistFilter="root";renderPlaylists();});tabs.addView(loose);
        for(Store.CollectionDef c:Store.visibleCollections(this)){TextView b=plainTab(c.name,c.id.equals(playlistFilter));b.setMaxLines(1);b.setOnClickListener(v->{playlistFilter=c.id;renderPlaylists();});tabs.addView(b);}
        hs.addView(tabs);page.addView(hs);

        RecyclerView rv=new RecyclerView(this);rv.setLayoutManager(new LinearLayoutManager(this));ArrayList<Store.Playlist> data=filteredPlaylists();rv.setAdapter(new PlaylistAdapter(data));page.addView(rv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        if(data.isEmpty()){TextView empty=Ui.text(this,"No playlists here.",14,Ui.MUTED,false);empty.setGravity(Gravity.CENTER);page.addView(empty,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,50)));}
        content.removeAllViews();content.addView(page);
    }

    private ArrayList<Store.Playlist> filteredPlaylists(){
        ArrayList<Store.Playlist> out=new ArrayList<>();
        if("all".equals(playlistFilter))out.addAll(Store.allPlaylists(this));
        else{
            String id="root".equals(playlistFilter)?Store.ROOT_COLLECTION:playlistFilter;
            for(Store.CollectionDef c:Store.getCollections(this))if(c.id.equals(id))out.addAll(c.playlists);
        }
        out.sort((a,b)->Natural.compare(a.name,b.name));return out;
    }

    private void createPlaylist(){Sheets.prompt(this,"New playlist","Playlist name","","Create",name->{Store.Playlist p=Store.createStandalonePlaylist(this,name);playlistFilter="all";renderPlaylists();});}
    private void createCollection(){Sheets.prompt(this,"New collection","Collection name","","Create",name->{Store.createCollection(this,name);renderPlaylists();});}

    private void playlistMenu(Store.Playlist p){
        Sheets.choices(this,p.name,new String[]{"Play","Shuffle","Loop","Move to collection","Set cover","Rename","Delete"},w->{
            if(w==0)playPlaylist(p,false,false);else if(w==1)playPlaylist(p,true,false);else if(w==2)playPlaylist(p,false,true);else if(w==3)movePlaylistSheet(p);else if(w==4){pendingPlaylistCoverId=p.id;pickPlaylistCover();}else if(w==5)renamePlaylist(p);else deletePlaylist(p);
        });
    }
    private void playPlaylist(Store.Playlist p,boolean shuffle,boolean loop){
        ArrayList<String>u=new ArrayList<>();for(Store.Item i:p.items)u.add(i.uri);if(u.isEmpty())return;if(shuffle)Collections.shuffle(u);
        Store.setRepeatMode(this,loop?Player.REPEAT_MODE_ALL:Player.REPEAT_MODE_OFF);Store.setPlaybackFallbackCover(this,p.cover);
        Store.setCurrentQueue(this,"current");Store.replaceQueue(this,"current",u);Playback.replaceAndPlay(this,u,u.get(0));startActivity(new Intent(this,PlayerActivity.class));
    }
    private void movePlaylistSheet(Store.Playlist p){
        ArrayList<Store.CollectionDef> cs=Store.visibleCollections(this);String[] names=new String[cs.size()+1];names[0]="No collection";
        for(int i=0;i<cs.size();i++)names[i+1]=cs.get(i).name;
        Sheets.choices(this,"Move "+p.name,names,w->{Store.movePlaylist(this,p.id,w==0?Store.ROOT_COLLECTION:cs.get(w-1).id);renderPlaylists();});
    }
    private void renamePlaylist(Store.Playlist p){Sheets.prompt(this,"Rename playlist","Playlist name",p.name,"Save",name->{ArrayList<Store.CollectionDef>cs=Store.getCollections(this);for(Store.CollectionDef c:cs)for(Store.Playlist x:c.playlists)if(x.id.equals(p.id))x.name=name;Store.saveCollections(this,cs);renderPlaylists();});}
    private void deletePlaylist(Store.Playlist p){Sheets.confirm(this,"Delete playlist?","Media files stay on your phone.","Delete",()->{ArrayList<Store.CollectionDef>cs=Store.getCollections(this);for(Store.CollectionDef c:cs)c.playlists.removeIf(x->x.id.equals(p.id));Store.saveCollections(this,cs);renderPlaylists();});}

    private void showQueues(){selectNav(QUEUE);renderQueues();}
    private void renderQueues(){
        ScrollView sv=new ScrollView(this);LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(Ui.dp(this,18),Ui.dp(this,7),Ui.dp(this,18),Ui.dp(this,26));sv.addView(page);
        LinearLayout top=Ui.row(this);top.addView(Ui.text(this,"Queue",25,Ui.TEXT,true),new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));TextView add=Ui.icon(this,"＋");add.setOnClickListener(v->createQueue());top.addView(add,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));page.addView(top);
        boolean any=false;String active=Store.currentQueueId(this);
        for(Store.QueueDef q:Store.getQueues(this)){if("current".equals(q.id)&&q.items.isEmpty())continue;any=true;LinearLayout r=Ui.row(this);r.setPadding(0,Ui.dp(this,11),0,Ui.dp(this,11));LinearLayout t=new LinearLayout(this);t.setOrientation(LinearLayout.VERTICAL);t.addView(Ui.text(this,(q.id.equals(active)?"●  ":"")+("current".equals(q.id)?"Now playing":q.name),16,q.id.equals(active)?Ui.ACCENT:Ui.TEXT,true));t.addView(Ui.text(this,q.items.size()+" items",12,Ui.MUTED,false));r.addView(t,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));TextView more=Ui.icon(this,"⋮");more.setOnClickListener(v->queueMenu(q));r.addView(more,new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,48)));r.setOnClickListener(v->openQueue(q.id));page.addView(r);page.addView(Ui.hairline(this));}
        if(!any)page.addView(Ui.text(this,"Your playback queue appears here.",14,Ui.MUTED,false));content.removeAllViews();content.addView(sv);
    }
    private void createQueue(){Sheets.prompt(this,"New queue","Queue name","","Create",name->{ArrayList<Store.QueueDef>qs=Store.getQueues(this);Store.QueueDef q=new Store.QueueDef();q.name=name;qs.add(q);Store.saveQueues(this,qs);renderQueues();});}
    private void queueMenu(Store.QueueDef q){
        ArrayList<String>opts=new ArrayList<>();opts.add("Make active");if(!"current".equals(q.id))opts.add("Rename");opts.add("Clear");if(!"current".equals(q.id))opts.add("Delete");
        Sheets.choices(this,"current".equals(q.id)?"Now playing":q.name,opts.toArray(new String[0]),w->{String x=opts.get(w);
            if("Make active".equals(x)){Store.setCurrentQueue(this,q.id);Playback.syncActiveQueue(this);}
            else if("Rename".equals(x))Sheets.prompt(this,"Rename queue","Queue name",q.name,"Save",name->{ArrayList<Store.QueueDef>qs=Store.getQueues(this);for(Store.QueueDef z:qs)if(z.id.equals(q.id))z.name=name;Store.saveQueues(this,qs);renderQueues();});
            else if("Clear".equals(x))Sheets.confirm(this,"Clear queue?","Media files stay on your phone.","Clear",()->{Store.replaceQueue(this,q.id,new ArrayList<>());if(q.id.equals(Store.currentQueueId(this)))Playback.syncActiveQueue(this);renderQueues();});
            else Sheets.confirm(this,"Delete queue?","Media files stay on your phone.","Delete",()->{ArrayList<Store.QueueDef>qs=Store.getQueues(this);qs.removeIf(z->z.id.equals(q.id));Store.saveQueues(this,qs);if(q.id.equals(Store.currentQueueId(this)))Store.setCurrentQueue(this,"current");renderQueues();});
        });
    }

    private LinearLayout mediaListRow(MediaEntry e,boolean selectedState){
        LinearLayout row=Ui.row(this);row.setPadding(0,Ui.dp(this,7),0,Ui.dp(this,7));if(selectedState)row.setBackground(Ui.round(android.graphics.Color.argb(55,105,190,239),12,this));
        ImageView iv=new ImageView(this);iv.setScaleType(ImageView.ScaleType.CENTER_CROP);iv.setClipToOutline(true);iv.setBackground(Ui.round(Ui.SURFACE2,10,this));Thumb.load(this,iv,e);row.addView(iv,new LinearLayout.LayoutParams(Ui.dp(this,58),Ui.dp(this,58)));
        LinearLayout txt=new LinearLayout(this);txt.setOrientation(LinearLayout.VERTICAL);txt.setPadding(Ui.dp(this,12),0,0,0);TextView name=Ui.text(this,e.title,15,Ui.TEXT,true);name.setMaxLines(3);txt.addView(name);long[]p=Store.progress(this,e.uri);String sub=p[0]>0?Ui.time(p[0])+" / "+Ui.time(p[1]>0?p[1]:e.duration):(e.isVideo()?"Video":"Audio")+" · "+e.durationText();txt.addView(Ui.text(this,sub,12,Ui.MUTED,false));row.addView(txt,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));return row;
    }

    private void mediaMenu(MediaEntry e){
        Sheets.choices(this,e.title,new String[]{"Play","Play next","Add to playlist","Add to queue","Share","Rename in Twin","Set cover","Details"},w->{
            if(w==0){Store.setPlaybackFallbackCover(this,"");openContext(e.uri,Collections.singletonList(e.uri));}
            else if(w==1){Playback.addNext(this,e.uri);Toast.makeText(this,"Playing next",Toast.LENGTH_SHORT).show();}
            else if(w==2)addManyToPlaylist(Collections.singletonList(e.uri));
            else if(w==3)addManyToQueue(Collections.singletonList(e.uri));
            else if(w==4)shareUris(Collections.singletonList(e.uri));
            else if(w==5)Sheets.prompt(this,"Rename in Twin","Display name",e.title,"Save",name->{Store.setAlias(this,e.uri,name);Playback.syncActiveQueue(this);showPage(currentPage);});
            else if(w==6){pendingCoverUri=e.uri;pickMediaCover();}
            else Sheets.info(this,e.title,(e.isVideo()?"Video":"Audio")+"\n"+e.durationText()+"\n"+e.folder+"\n\nOriginal: "+e.originalTitle+"\n"+e.uri);
        });
    }

    private void addManyToPlaylist(List<String> uris){
        ArrayList<Store.Playlist>ps=Store.allPlaylists(this);if(ps.isEmpty()){Toast.makeText(this,"Create a playlist first",Toast.LENGTH_SHORT).show();return;}
        String[] names=new String[ps.size()];for(int i=0;i<ps.size();i++)names[i]=ps.get(i).name+" · "+Store.collectionNameForPlaylist(this,ps.get(i).id);
        Sheets.choices(this,"Add to playlist",names,w->{String id=ps.get(w).id;ArrayList<Store.CollectionDef>cs=Store.getCollections(this);int added=0;for(Store.CollectionDef c:cs)for(Store.Playlist p:c.playlists)if(p.id.equals(id))for(String u:uris){boolean ex=false;for(Store.Item it:p.items)if(it.uri.equals(u))ex=true;if(!ex){p.items.add(new Store.Item(u));added++;}}Store.saveCollections(this,cs);Toast.makeText(this,added+" added",Toast.LENGTH_SHORT).show();});
    }
    private void addManyToQueue(List<String> uris){
        ArrayList<Store.QueueDef>qs=Store.getQueues(this);String[] names=new String[qs.size()];for(int i=0;i<qs.size();i++)names[i]="current".equals(qs.get(i).id)?"Now playing":qs.get(i).name;
        Sheets.choices(this,"Add to queue",names,w->{ArrayList<Store.QueueDef>fresh=Store.getQueues(this);for(Store.QueueDef q:fresh)if(q.id.equals(qs.get(w).id))for(String u:uris)q.items.add(u);Store.saveQueues(this,fresh);if(qs.get(w).id.equals(Store.currentQueueId(this)))Playback.syncActiveQueue(this);});
    }

    private void shareUris(List<String> uris){
        if(uris==null||uris.isEmpty())return;ArrayList<Uri> streams=new ArrayList<>();boolean allAudio=true,allVideo=true;
        for(String s:uris){Uri u=Uri.parse(s);streams.add(u);MediaEntry e=MediaRepository.resolve(this,s);if(e==null||e.isVideo())allAudio=false;if(e==null||!e.isVideo())allVideo=false;}
        Intent send=new Intent(streams.size()==1?Intent.ACTION_SEND:Intent.ACTION_SEND_MULTIPLE);send.setType(allAudio?"audio/*":(allVideo?"video/*":"*/*"));send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if(streams.size()==1)send.putExtra(Intent.EXTRA_STREAM,streams.get(0));else send.putParcelableArrayListExtra(Intent.EXTRA_STREAM,streams);
        ClipData clip=ClipData.newUri(getContentResolver(),"Twin media",streams.get(0));for(int i=1;i<streams.size();i++)clip.addItem(new ClipData.Item(streams.get(i)));send.setClipData(clip);
        startActivity(Intent.createChooser(send,"Share media"));
    }

    private void openMediaContext(String uri,List<MediaEntry> entries){Store.setPlaybackFallbackCover(this,"");openContext(uri,MediaRepository.uris(entries));}
    private void openContext(String uri,List<String> uris){
        if(uri==null)return;ArrayList<String>safe=new ArrayList<>(uris);if(!safe.contains(uri))safe.add(0,uri);Store.setCurrentQueue(this,"current");Store.replaceQueue(this,"current",safe);Store.addRecent(this,uri);
        Intent i=new Intent(this,PlayerActivity.class);i.putExtra("uri",uri);startActivity(i);
    }

    private ArrayList<MediaEntry> recentEntries(){ArrayList<MediaEntry>out=new ArrayList<>();for(String u:Store.getRecent(this)){MediaEntry e=find(u);if(e==null)e=MediaRepository.resolve(this,u);if(e!=null)out.add(e);}return out;}
    private MediaEntry find(String uri){for(MediaEntry e:allMedia)if(e.uri.equals(uri))return e;return null;}

    private void openCollection(String id){Intent i=new Intent(this,OrganizeActivity.class);i.putExtra("mode","collection");i.putExtra("collection",id);startActivity(i);}
    private void openPlaylist(String playlistId){Intent i=new Intent(this,OrganizeActivity.class);i.putExtra("mode","playlist");i.putExtra("collection",Store.collectionIdForPlaylist(this,playlistId));i.putExtra("playlist",playlistId);startActivity(i);}
    private void openQueue(String id){Intent i=new Intent(this,OrganizeActivity.class);i.putExtra("mode","queue");i.putExtra("queue",id);startActivity(i);}

    private void openFile(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"audio/*","video/*"});i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,REQ_OPEN);}
    private void pickMediaCover(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,REQ_COVER_MEDIA);}
    private void pickPlaylistCover(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,REQ_COVER_PLAYLIST);}
    private void persist(Uri u){try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}}

    @Override protected void onActivityResult(int r,int res,Intent data){
        super.onActivityResult(r,res,data);if(res!=RESULT_OK||data==null||data.getData()==null)return;Uri u=data.getData();persist(u);
        if(r==REQ_OPEN){Store.addRecent(this,u.toString());Store.setPlaybackFallbackCover(this,"");openContext(u.toString(),Collections.singletonList(u.toString()));}
        else if(r==REQ_COVER_MEDIA&&!pendingCoverUri.isEmpty()){Store.setMediaCover(this,pendingCoverUri,u.toString());Playback.syncActiveQueue(this);showPage(currentPage);}
        else if(r==REQ_COVER_PLAYLIST&&!pendingPlaylistCoverId.isEmpty()){ArrayList<Store.CollectionDef>cs=Store.getCollections(this);for(Store.CollectionDef c:cs)for(Store.Playlist p:c.playlists)if(p.id.equals(pendingPlaylistCoverId))p.cover=u.toString();Store.saveCollections(this,cs);renderPlaylists();}
    }

    @Override public void onBackPressed(){
        if(selectionMode){selectionMode=false;selected.clear();renderLibrary();return;}
        super.onBackPressed();
    }

    @Override protected void onResume(){super.onResume();showMiniFromStore();if(controller==null)connectMini();else updateMini();if(content!=null&&!needsPermission()&&currentPage==PLAYLISTS)renderPlaylists();}
    @Override protected void onDestroy(){handler.removeCallbacks(miniTicker);if(controllerFuture!=null)MediaController.releaseFuture(controllerFuture);controllerFuture=null;controller=null;super.onDestroy();}

    class MediaVH extends RecyclerView.ViewHolder{
        ImageView art;TextView title,sub;LinearLayout root;
        MediaVH(View v,boolean grid){super(v);root=(LinearLayout)v;if(grid){art=(ImageView)root.getChildAt(0);title=(TextView)root.getChildAt(1);sub=(TextView)root.getChildAt(2);}else{art=(ImageView)root.getChildAt(0);LinearLayout t=(LinearLayout)root.getChildAt(1);title=(TextView)t.getChildAt(0);sub=(TextView)t.getChildAt(1);}}
    }
    class MediaAdapter extends RecyclerView.Adapter<MediaVH>{
        final ArrayList<MediaEntry>data;final boolean grid;MediaAdapter(ArrayList<MediaEntry>d,boolean g){data=d;grid=g;}
        @NonNull public MediaVH onCreateViewHolder(@NonNull ViewGroup p,int v){
            if(grid){
                LinearLayout b=new LinearLayout(MainActivity.this);b.setOrientation(LinearLayout.VERTICAL);b.setPadding(Ui.dp(MainActivity.this,3),Ui.dp(MainActivity.this,3),Ui.dp(MainActivity.this,3),Ui.dp(MainActivity.this,10));b.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
                ImageView iv=new ImageView(MainActivity.this);iv.setScaleType(ImageView.ScaleType.CENTER_CROP);iv.setClipToOutline(true);iv.setBackground(Ui.round(Ui.SURFACE2,14,MainActivity.this));b.addView(iv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(MainActivity.this,106)));
                TextView n=Ui.text(MainActivity.this,"",12,Ui.TEXT,true);n.setMaxLines(2);n.setPadding(0,Ui.dp(MainActivity.this,5),0,0);b.addView(n);b.addView(Ui.text(MainActivity.this,"",10,Ui.MUTED,false));return new MediaVH(b,true);
            }
            return new MediaVH(mediaListRow(new MediaEntry("","",0,0,"audio",""),false),false);
        }
        public void onBindViewHolder(@NonNull MediaVH h,int pos){
            MediaEntry e=data.get(pos);Thumb.load(MainActivity.this,h.art,e);h.title.setText(e.title);h.sub.setText(e.durationText());
            boolean sel=selected.contains(e.uri);h.root.setBackground(sel?Ui.round(android.graphics.Color.argb(55,105,190,239),12,MainActivity.this):null);
            h.root.setOnClickListener(v->{if(selectionMode)toggleSelection(e.uri);else openMediaContext(e.uri,data);});
            h.root.setOnLongClickListener(v->{if(selectionMode)toggleSelection(e.uri);else enterSelection(e.uri);return true;});
        }
        public int getItemCount(){return data.size();}
    }

    class FolderAdapter extends RecyclerView.Adapter<MediaVH>{
        final ArrayList<String>data;FolderAdapter(ArrayList<String>d){data=d;}
        @NonNull public MediaVH onCreateViewHolder(@NonNull ViewGroup p,int v){return new MediaVH(mediaListRow(new MediaEntry("","",0,0,"audio",""),false),false);}
        public void onBindViewHolder(@NonNull MediaVH h,int pos){String f=data.get(pos);int count=0;for(MediaEntry e:allMedia)if(f.equals(e.folder))count++;h.art.setImageResource(R.drawable.cover_placeholder);h.title.setText(f);h.sub.setText(count+" media");h.root.setOnClickListener(v->{folderFilter=f;libraryFilter="all";renderLibrary();});}
        public int getItemCount(){return data.size();}
    }

    class PlaylistVH extends RecyclerView.ViewHolder{LinearLayout row;ImageView art;TextView title,sub;PlaylistVH(View v){super(v);row=(LinearLayout)v;art=(ImageView)row.getChildAt(0);LinearLayout t=(LinearLayout)row.getChildAt(1);title=(TextView)t.getChildAt(0);sub=(TextView)t.getChildAt(1);}}
    class PlaylistAdapter extends RecyclerView.Adapter<PlaylistVH>{
        final ArrayList<Store.Playlist>data;PlaylistAdapter(ArrayList<Store.Playlist>d){data=d;}
        @NonNull public PlaylistVH onCreateViewHolder(@NonNull ViewGroup p,int v){
            LinearLayout r=Ui.row(MainActivity.this);r.setPadding(0,Ui.dp(MainActivity.this,8),0,Ui.dp(MainActivity.this,8));r.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
            ImageView art=new ImageView(MainActivity.this);art.setScaleType(ImageView.ScaleType.CENTER_CROP);art.setClipToOutline(true);art.setBackground(Ui.round(Ui.SURFACE2,12,MainActivity.this));r.addView(art,new LinearLayout.LayoutParams(Ui.dp(MainActivity.this,68),Ui.dp(MainActivity.this,68)));
            LinearLayout t=new LinearLayout(MainActivity.this);t.setOrientation(LinearLayout.VERTICAL);t.setPadding(Ui.dp(MainActivity.this,12),0,0,0);TextView n=Ui.text(MainActivity.this,"",16,Ui.TEXT,true);n.setMaxLines(2);t.addView(n);t.addView(Ui.text(MainActivity.this,"",12,Ui.MUTED,false));r.addView(t,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));return new PlaylistVH(r);
        }
        public void onBindViewHolder(@NonNull PlaylistVH h,int pos){Store.Playlist p=data.get(pos);h.title.setText(p.name);h.sub.setText(p.items.size()+" items · "+Store.collectionNameForPlaylist(MainActivity.this,p.id));if(p.cover!=null&&!p.cover.isEmpty())try{h.art.setImageURI(Uri.parse(p.cover));}catch(Exception e){h.art.setImageResource(R.drawable.cover_placeholder);}else h.art.setImageResource(R.drawable.cover_placeholder);h.row.setOnClickListener(v->openPlaylist(p.id));h.row.setOnLongClickListener(v->{playlistMenu(p);return true;});}
        public int getItemCount(){return data.size();}
    }
}
