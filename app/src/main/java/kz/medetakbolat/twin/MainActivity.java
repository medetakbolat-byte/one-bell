package kz.medetakbolat.twin;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
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

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_STORAGE=44,REQ_OPEN=701,REQ_COVER=702;
    private FrameLayout content;
    private LinearLayout mini;
    private ImageView miniThumb;
    private TextView miniTitle,miniPlay;
    private TextView navHome,navLibrary,navQueues;
    private ArrayList<MediaEntry> allMedia=new ArrayList<>();
    private String libraryFilter="all",folderFilter="",searchText="",sortMode="new";
    private ListenableFuture<MediaController> controllerFuture;
    private MediaController controller;
    private String pendingCoverUri="";

    @Override public void onCreate(Bundle b){
        super.onCreate(b);Store.ensure(this);buildShell();connectMini();
        if(getIntent()!=null&&Intent.ACTION_VIEW.equals(getIntent().getAction())&&getIntent().getData()!=null){
            Uri u=getIntent().getData();persist(u);Store.addRecent(this,u.toString());
            launchPlayer(u.toString(),true);return;
        }
        if(needsPermission())requestStorage();else showHome();
    }

    private boolean needsPermission(){return android.os.Build.VERSION.SDK_INT>=23&&ContextCompat.checkSelfPermission(this,Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED;}
    private void requestStorage(){ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},REQ_STORAGE);showPermissionHint();}
    @Override public void onRequestPermissionsResult(int r,@NonNull String[]p,@NonNull int[]g){super.onRequestPermissionsResult(r,p,g);showHome();}

    private void buildShell(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Ui.BG);
        content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        mini=Ui.row(this);mini.setPadding(Ui.dp(this,10),Ui.dp(this,6),Ui.dp(this,6),Ui.dp(this,6));mini.setBackgroundColor(Ui.SURFACE);mini.setVisibility(View.GONE);
        miniThumb=new ImageView(this);miniThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);mini.addView(miniThumb,new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,44)));
        miniTitle=Ui.text(this,"",14,Ui.TEXT,true);miniTitle.setPadding(Ui.dp(this,10),0,Ui.dp(this,6),0);mini.addView(miniTitle,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        TextView prev=Ui.icon(this,"‹‹");miniPlay=Ui.icon(this,"▶");TextView next=Ui.icon(this,"››");TextView stop=Ui.icon(this,"×");
        mini.addView(prev,new LinearLayout.LayoutParams(Ui.dp(this,42),Ui.dp(this,48)));mini.addView(miniPlay,new LinearLayout.LayoutParams(Ui.dp(this,42),Ui.dp(this,48)));mini.addView(next,new LinearLayout.LayoutParams(Ui.dp(this,42),Ui.dp(this,48)));mini.addView(stop,new LinearLayout.LayoutParams(Ui.dp(this,42),Ui.dp(this,48)));
        mini.setOnClickListener(v->startActivity(new Intent(this,PlayerActivity.class)));
        prev.setOnClickListener(v->{if(controller!=null&&controller.hasPreviousMediaItem())controller.seekToPreviousMediaItem();});
        next.setOnClickListener(v->{if(controller!=null&&controller.hasNextMediaItem())controller.seekToNextMediaItem();});
        miniPlay.setOnClickListener(v->{if(controller!=null){if(controller.isPlaying())controller.pause();else controller.play();}});
        stop.setOnClickListener(v->{Playback.stop(this);mini.setVisibility(View.GONE);});
        root.addView(mini,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,58)));

        LinearLayout nav=Ui.row(this);nav.setPadding(Ui.dp(this,8),Ui.dp(this,3),Ui.dp(this,8),Ui.dp(this,5));nav.setBackgroundColor(Ui.BG);
        navHome=navItem("⌂\nHome");navLibrary=navItem("▦\nLibrary");navQueues=navItem("☷\nQueues");
        nav.addView(navHome,new LinearLayout.LayoutParams(0,Ui.dp(this,54),1));nav.addView(navLibrary,new LinearLayout.LayoutParams(0,Ui.dp(this,54),1));nav.addView(navQueues,new LinearLayout.LayoutParams(0,Ui.dp(this,54),1));
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
                controller=controllerFuture.get();controller.addListener(new Player.Listener(){
                    @Override public void onMediaItemTransition(MediaItem item,int reason){updateMini();}
                    @Override public void onIsPlayingChanged(boolean b){updateMini();}
                    @Override public void onMediaMetadataChanged(androidx.media3.common.MediaMetadata m){updateMini();}
                });updateMini();
            }catch(Exception ignored){}
        },ContextCompat.getMainExecutor(this));
    }

    private void updateMini(){
        if(controller==null||controller.getCurrentMediaItem()==null){mini.setVisibility(View.GONE);return;}
        MediaItem mi=controller.getCurrentMediaItem();String uri=mi.mediaId;MediaEntry e=MediaRepository.resolve(this,uri);
        if(e==null){mini.setVisibility(View.GONE);return;}mini.setVisibility(View.VISIBLE);miniTitle.setText(e.title);miniPlay.setText(controller.isPlaying()?"Ⅱ":"▶");Thumb.load(this,miniThumb,e);
    }

    private void showPermissionHint(){
        content.removeAllViews();LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(Ui.dp(this,24),Ui.dp(this,36),Ui.dp(this,24),0);
        page.addView(Ui.text(this,"Twin",30,Ui.TEXT,true));TextView p=Ui.text(this,"Allow local media access so Twin can show your audio and video. Nothing is uploaded anywhere.",15,Ui.MUTED,false);p.setPadding(0,Ui.dp(this,12),0,Ui.dp(this,18));page.addView(p);
        TextView b=Ui.pill(this,"Allow media access");b.setOnClickListener(v->requestStorage());page.addView(b,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,Ui.dp(this,44)));content.addView(page);
    }

    private void refresh(Runnable after){
        Executors.newSingleThreadExecutor().execute(()->{ArrayList<MediaEntry>m=needsPermission()?new ArrayList<>():MediaRepository.scan(this);runOnUiThread(()->{allMedia=m;if(after!=null)after.run();});});
    }

    private void showHome(){selectNav(0);refresh(this::renderHome);}
    private void renderHome(){
        ScrollView sv=new ScrollView(this);LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(Ui.dp(this,18),Ui.dp(this,12),Ui.dp(this,18),Ui.dp(this,30));sv.addView(page);
        LinearLayout top=Ui.row(this);top.addView(Ui.text(this,"Twin",28,Ui.TEXT,true),new LinearLayout.LayoutParams(0,Ui.dp(this,50),1));TextView search=Ui.icon(this,"⌕");search.setOnClickListener(v->askSearch());top.addView(search,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));page.addView(top);

        MediaEntry cont=findContinue();if(cont!=null){page.addView(Ui.section(this,"Continue"));page.addView(bigMedia(cont));}

        LinearLayout ch=Ui.row(this);ch.addView(Ui.section(this,"Collections"),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));TextView add=Ui.icon(this,"＋");add.setOnClickListener(v->createCollection());ch.addView(add,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));page.addView(ch);
        ArrayList<Store.CollectionDef>cs=Store.getCollections(this);
        if(cs.isEmpty()){
            LinearLayout empty=Ui.row(this);TextView e=Ui.text(this,"No collections yet",15,Ui.MUTED,false);empty.addView(e,new LinearLayout.LayoutParams(0,Ui.dp(this,54),1));TextView n=Ui.pill(this,"Create");n.setOnClickListener(v->createCollection());empty.addView(n);page.addView(empty);
        }else{
            HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);LinearLayout strip=Ui.row(this);
            for(Store.CollectionDef c:cs){LinearLayout cell=collectionCell(c);cell.setOnClickListener(v->openCollection(c.id));strip.addView(cell,new LinearLayout.LayoutParams(Ui.dp(this,142),Ui.dp(this,148)));}
            hs.addView(strip);page.addView(hs);
        }

        page.addView(Ui.section(this,"Recent"));int shown=0;
        for(String u:Store.getRecent(this)){MediaEntry e=find(u);if(e==null)e=MediaRepository.resolve(this,u);if(e==null)continue;LinearLayout row=mediaListRow(e);final MediaEntry item=e;row.setOnClickListener(v->launchPlayer(item.uri,false));row.setOnLongClickListener(v->{mediaMenu(item);return true;});page.addView(row);page.addView(Ui.hairline(this));if(++shown>=7)break;}
        if(shown==0)page.addView(Ui.text(this,"Open something. It will appear here.",14,Ui.MUTED,false));
        content.removeAllViews();content.addView(sv);
    }

    private MediaEntry findContinue(){for(String u:Store.getRecent(this)){MediaEntry e=find(u);if(e==null)e=MediaRepository.resolve(this,u);long[]p=Store.progress(this,u);long d=p[1]>0?p[1]:(e==null?0:e.duration);if(e!=null&&p[0]>5000&&(d<=0||p[0]<d-8000))return e;}return null;}
    private MediaEntry find(String u){for(MediaEntry e:allMedia)if(e.uri.equals(u))return e;return null;}

    private LinearLayout bigMedia(MediaEntry e){
        LinearLayout outer=new LinearLayout(this);outer.setOrientation(LinearLayout.VERTICAL);ImageView iv=new ImageView(this);iv.setScaleType(ImageView.ScaleType.CENTER_CROP);Thumb.load(this,iv,e);outer.addView(iv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,176)));
        LinearLayout line=Ui.row(this);line.setPadding(0,Ui.dp(this,8),0,Ui.dp(this,5));LinearLayout txt=new LinearLayout(this);txt.setOrientation(LinearLayout.VERTICAL);txt.addView(Ui.text(this,e.title,17,Ui.TEXT,true));long[]p=Store.progress(this,e.uri);txt.addView(Ui.text(this,Ui.time(p[0])+" / "+Ui.time(p[1]>0?p[1]:e.duration),13,Ui.MUTED,false));line.addView(txt,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));TextView play=Ui.icon(this,"▶");line.addView(play,new LinearLayout.LayoutParams(Ui.dp(this,50),Ui.dp(this,50)));outer.addView(line);
        outer.setOnClickListener(v->launchPlayer(e.uri,false));return outer;
    }

    private LinearLayout collectionCell(Store.CollectionDef c){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(0,0,Ui.dp(this,10),0);ImageView iv=new ImageView(this);iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if(c.cover!=null&&!c.cover.isEmpty())try{iv.setImageURI(Uri.parse(c.cover));}catch(Exception ignored){iv.setImageResource(R.drawable.cover_placeholder);}else iv.setImageResource(R.drawable.cover_placeholder);
        box.addView(iv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,105)));TextView n=Ui.text(this,c.name,14,Ui.TEXT,true);n.setPadding(0,Ui.dp(this,6),0,0);box.addView(n);box.addView(Ui.text(this,c.playlists.size()+" playlists",11,Ui.MUTED,false));return box;
    }

    private LinearLayout mediaListRow(MediaEntry e){
        LinearLayout row=Ui.row(this);row.setPadding(0,Ui.dp(this,8),0,Ui.dp(this,8));ImageView iv=new ImageView(this);iv.setScaleType(ImageView.ScaleType.CENTER_CROP);Thumb.load(this,iv,e);row.addView(iv,new LinearLayout.LayoutParams(Ui.dp(this,64),Ui.dp(this,48)));
        LinearLayout txt=new LinearLayout(this);txt.setOrientation(LinearLayout.VERTICAL);txt.setPadding(Ui.dp(this,12),0,0,0);txt.addView(Ui.text(this,e.title,15,Ui.TEXT,true));long[]p=Store.progress(this,e.uri);String sub=p[0]>0?Ui.time(p[0])+" / "+Ui.time(p[1]>0?p[1]:e.duration):(e.isVideo()?"Video":"Audio")+" · "+e.durationText();txt.addView(Ui.text(this,sub,12,Ui.MUTED,false));row.addView(txt,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));TextView more=Ui.icon(this,"⋮");more.setOnClickListener(v->mediaMenu(e));row.addView(more,new LinearLayout.LayoutParams(Ui.dp(this,42),Ui.dp(this,48)));return row;
    }

    private void showLibrary(String filter,String folder){selectNav(1);libraryFilter=filter;folderFilter=folder;refresh(this::renderLibrary);}
    private void renderLibrary(){
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(Ui.dp(this,14),Ui.dp(this,8),Ui.dp(this,14),0);
        LinearLayout top=Ui.row(this);top.addView(Ui.text(this,folderFilter.isEmpty()?"Library":folderFilter,27,Ui.TEXT,true),new LinearLayout.LayoutParams(0,Ui.dp(this,50),1));
        TextView open=Ui.icon(this,"＋");open.setOnClickListener(v->openFile());top.addView(open,new LinearLayout.LayoutParams(Ui.dp(this,46),Ui.dp(this,46)));
        TextView search=Ui.icon(this,"⌕");search.setOnClickListener(v->askSearch());top.addView(search,new LinearLayout.LayoutParams(Ui.dp(this,46),Ui.dp(this,46)));
        TextView layout=Ui.icon(this,Store.libraryGrid(this)?"☰":"▦");layout.setOnClickListener(v->{Store.setLibraryGrid(this,!Store.libraryGrid(this));renderLibrary();});top.addView(layout,new LinearLayout.LayoutParams(Ui.dp(this,46),Ui.dp(this,46)));
        TextView sort=Ui.icon(this,"⇅");sort.setOnClickListener(v->sortSheet());top.addView(sort,new LinearLayout.LayoutParams(Ui.dp(this,46),Ui.dp(this,46)));page.addView(top);

        if(folderFilter.isEmpty()){
            HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);LinearLayout chips=Ui.row(this);
            for(String[]x:new String[][]{{"all","All"},{"audio","Audio"},{"video","Video"},{"folders","Folders"}}){TextView b=Ui.pill(this,x[1]);if(libraryFilter.equals(x[0]))b.setTextColor(Ui.ACCENT);b.setOnClickListener(v->{libraryFilter=x[0];renderLibrary();});chips.addView(b);LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)b.getLayoutParams();lp.setMargins(0,0,Ui.dp(this,7),Ui.dp(this,8));b.setLayoutParams(lp);}hs.addView(chips);page.addView(hs);
        }else{TextView b=Ui.text(this,"‹  All folders",14,Ui.MUTED,true);b.setPadding(0,Ui.dp(this,4),0,Ui.dp(this,10));b.setOnClickListener(v->{folderFilter="";libraryFilter="folders";renderLibrary();});page.addView(b);}

        RecyclerView rv=new RecyclerView(this);if("folders".equals(libraryFilter)&&folderFilter.isEmpty()){rv.setLayoutManager(new LinearLayoutManager(this));rv.setAdapter(new FolderAdapter(folderNames()));}
        else{boolean grid=Store.libraryGrid(this);rv.setLayoutManager(grid?new GridLayoutManager(this,3):new LinearLayoutManager(this));rv.setAdapter(new MediaAdapter(filteredMedia(),grid));}
        page.addView(rv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));content.removeAllViews();content.addView(page);
    }

    private ArrayList<String> folderNames(){Set<String>s=new HashSet<>();for(MediaEntry e:allMedia)s.add(e.folder);ArrayList<String>o=new ArrayList<>(s);Collections.sort(o,String.CASE_INSENSITIVE_ORDER);return o;}
    private ArrayList<MediaEntry> filteredMedia(){ArrayList<MediaEntry>o=new ArrayList<>();for(MediaEntry e:allMedia){if("audio".equals(libraryFilter)&&e.isVideo())continue;if("video".equals(libraryFilter)&&!e.isVideo())continue;if(!folderFilter.isEmpty()&&!folderFilter.equals(e.folder))continue;if(!searchText.isEmpty()&&!e.title.toLowerCase().contains(searchText.toLowerCase()))continue;o.add(e);}if("name".equals(sortMode))o.sort((a,b)->a.title.compareToIgnoreCase(b.title));else if("duration".equals(sortMode))o.sort((a,b)->Long.compare(b.duration,a.duration));else if("played".equals(sortMode))o.sort((a,b)->Long.compare(Store.progress(this,b.uri)[2],Store.progress(this,a.uri)[2]));else o.sort((a,b)->Long.compare(b.addedAt,a.addedAt));return o;}

    private void sortSheet(){showSheet("Sort by",new String[]{"Recently added","Name","Duration","Recently played"},w->{sortMode=new String[]{"new","name","duration","played"}[w];renderLibrary();});}
    private void askSearch(){EditText e=new EditText(this);e.setHint("Search media");e.setText(searchText);e.setTextColor(Ui.TEXT);e.setHintTextColor(Ui.MUTED);new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Search").setView(e).setPositiveButton("Search",(d,w)->{searchText=e.getText().toString().trim();showLibrary("all","");}).setNeutralButton("Clear",(d,w)->{searchText="";showLibrary("all","");}).show();}

    private void showQueues(){
        selectNav(2);ScrollView sv=new ScrollView(this);LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(Ui.dp(this,18),Ui.dp(this,12),Ui.dp(this,18),Ui.dp(this,28));sv.addView(page);
        LinearLayout top=Ui.row(this);top.addView(Ui.text(this,"Queues",27,Ui.TEXT,true),new LinearLayout.LayoutParams(0,Ui.dp(this,52),1));TextView add=Ui.icon(this,"＋");add.setOnClickListener(v->createQueue());top.addView(add,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));page.addView(top);
        boolean any=false;String active=Store.currentQueueId(this);
        for(Store.QueueDef q:Store.getQueues(this)){if("current".equals(q.id)&&q.items.isEmpty())continue;any=true;LinearLayout r=Ui.row(this);r.setPadding(0,Ui.dp(this,11),0,Ui.dp(this,11));LinearLayout t=new LinearLayout(this);t.setOrientation(LinearLayout.VERTICAL);t.addView(Ui.text(this,(q.id.equals(active)?"●  ":"")+("current".equals(q.id)?"Now playing":q.name),16,q.id.equals(active)?Ui.ACCENT:Ui.TEXT,true));t.addView(Ui.text(this,q.items.size()+" items",12,Ui.MUTED,false));r.addView(t,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));TextView more=Ui.icon(this,"⋮");more.setOnClickListener(v->queueMenu(q));r.addView(more,new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,48)));r.setOnClickListener(v->openQueue(q.id));page.addView(r);page.addView(Ui.hairline(this));}
        if(!any)page.addView(Ui.text(this,"No saved queues yet.",14,Ui.MUTED,false));content.removeAllViews();content.addView(sv);
    }

    private void createCollection(){EditText e=new EditText(this);e.setHint("Collection name");new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("New collection").setView(e).setPositiveButton("Create",(d,w)->{String n=e.getText().toString().trim();if(n.isEmpty())return;ArrayList<Store.CollectionDef>cs=Store.getCollections(this);Store.CollectionDef c=new Store.CollectionDef();c.name=n;cs.add(c);Store.saveCollections(this,cs);renderHome();}).setNegativeButton("Cancel",null).show();}
    private void createQueue(){EditText e=new EditText(this);e.setHint("Queue name");new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("New queue").setView(e).setPositiveButton("Create",(d,w)->{String n=e.getText().toString().trim();if(n.isEmpty())return;ArrayList<Store.QueueDef>qs=Store.getQueues(this);Store.QueueDef q=new Store.QueueDef();q.name=n;qs.add(q);Store.saveQueues(this,qs);showQueues();}).show();}
    private void queueMenu(Store.QueueDef q){ArrayList<String>o=new ArrayList<>();o.add("Make active");if(!"current".equals(q.id))o.add("Rename");o.add("Clear");if(!"current".equals(q.id))o.add("Delete");showSheet(q.name,o.toArray(new String[0]),w->{String x=o.get(w);if("Make active".equals(x)){Store.setCurrentQueue(this,q.id);Playback.syncActiveQueue(this);}else if("Rename".equals(x))renameQueue(q);else if("Clear".equals(x)){Store.replaceQueue(this,q.id,new ArrayList<>());if(q.id.equals(Store.currentQueueId(this)))Playback.syncActiveQueue(this);}else if("Delete".equals(x)){ArrayList<Store.QueueDef>qs=Store.getQueues(this);qs.removeIf(z->z.id.equals(q.id));Store.saveQueues(this,qs);if(q.id.equals(Store.currentQueueId(this)))Store.setCurrentQueue(this,"current");}showQueues();});}
    private void renameQueue(Store.QueueDef q){EditText e=new EditText(this);e.setText(q.name);new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Rename queue").setView(e).setPositiveButton("Save",(d,w)->{ArrayList<Store.QueueDef>qs=Store.getQueues(this);for(Store.QueueDef z:qs)if(z.id.equals(q.id))z.name=e.getText().toString().trim();Store.saveQueues(this,qs);showQueues();}).show();}

    private void mediaMenu(MediaEntry e){showSheet(e.title,new String[]{"Play","Play next","Add to queue","Add to playlist","Rename in Twin","Set cover","Details"},w->{if(w==0)launchPlayer(e.uri,true);else if(w==1){Playback.addNext(this,e.uri);Toast.makeText(this,"Playing next",Toast.LENGTH_SHORT).show();}else if(w==2)addQueueSheet(e.uri);else if(w==3)addPlaylistSheet(e.uri);else if(w==4)renameMedia(e);else if(w==5){pendingCoverUri=e.uri;pickCover();}else showDetails(e);});}
    private void renameMedia(MediaEntry e){EditText x=new EditText(this);x.setText(e.title);new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Rename in Twin").setView(x).setPositiveButton("Save",(d,w)->{String n=x.getText().toString().trim();if(!n.isEmpty()){Store.setAlias(this,e.uri,n);refresh(()->{if(libraryFilter!=null)renderLibrary();});}}).setNeutralButton("Use original",(d,w)->{Store.setAlias(this,e.uri,"");refresh(this::renderLibrary);}).show();}
    private void showDetails(MediaEntry e){new androidx.appcompat.app.AlertDialog.Builder(this).setTitle(e.title).setMessage((e.isVideo()?"Video":"Audio")+"\n"+e.durationText()+"\n"+e.folder+"\n\n"+e.uri).setPositiveButton("OK",null).show();}
    private void addQueueSheet(String uri){ArrayList<Store.QueueDef>qs=Store.getQueues(this);ArrayList<String>n=new ArrayList<>();for(Store.QueueDef q:qs)n.add("current".equals(q.id)?"Now playing":q.name);showSheet("Add to queue",n.toArray(new String[0]),w->{Store.addToQueue(this,qs.get(w).id,uri,false);if(qs.get(w).id.equals(Store.currentQueueId(this)))Playback.syncActiveQueue(this);});}
    private void addPlaylistSheet(String uri){ArrayList<Store.CollectionDef>cs=Store.getCollections(this);ArrayList<String>labels=new ArrayList<>(),pids=new ArrayList<>(),cids=new ArrayList<>();for(Store.CollectionDef c:cs)for(Store.Playlist p:c.playlists){labels.add(c.name+" · "+p.name);pids.add(p.id);cids.add(c.id);}if(labels.isEmpty()){Toast.makeText(this,"Create a playlist first",Toast.LENGTH_SHORT).show();return;}showSheet("Add to playlist",labels.toArray(new String[0]),w->{ArrayList<Store.CollectionDef>fresh=Store.getCollections(this);for(Store.CollectionDef c:fresh)if(c.id.equals(cids.get(w)))for(Store.Playlist p:c.playlists)if(p.id.equals(pids.get(w))){boolean ex=false;for(Store.Item it:p.items)if(it.uri.equals(uri))ex=true;if(!ex)p.items.add(new Store.Item(uri));}Store.saveCollections(this,fresh);});}

    private interface SheetChoice{void choose(int index);}
    private void showSheet(String title,String[]opts,SheetChoice action){
        BottomSheetDialog d=new BottomSheetDialog(this);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(Ui.dp(this,20),Ui.dp(this,12),Ui.dp(this,20),Ui.dp(this,22));box.setBackgroundColor(Ui.SURFACE);
        TextView h=Ui.text(this,title,17,Ui.TEXT,true);h.setPadding(0,Ui.dp(this,6),0,Ui.dp(this,8));box.addView(h);
        for(int i=0;i<opts.length;i++){final int idx=i;TextView t=Ui.text(this,opts[i],16,Ui.TEXT,false);t.setPadding(Ui.dp(this,4),Ui.dp(this,13),Ui.dp(this,4),Ui.dp(this,13));t.setOnClickListener(v->{d.dismiss();action.choose(idx);});box.addView(t);}
        d.setContentView(box);d.show();
    }

    private void openCollection(String id){Intent i=new Intent(this,OrganizeActivity.class);i.putExtra("mode","collection");i.putExtra("collection",id);startActivity(i);}
    private void openQueue(String id){Intent i=new Intent(this,OrganizeActivity.class);i.putExtra("mode","queue");i.putExtra("queue",id);startActivity(i);}
    private void launchPlayer(String uri,boolean replaceCurrent){
        if(uri!=null){Store.addRecent(this,uri);if(replaceCurrent){Store.setCurrentQueue(this,"current");Store.replaceQueue(this,"current",Collections.singletonList(uri));}}
        Intent i=new Intent(this,PlayerActivity.class);if(uri!=null)i.putExtra("uri",uri);startActivity(i);
    }
    private void openFile(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"audio/*","video/*"});i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,REQ_OPEN);}
    private void pickCover(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,REQ_COVER);}
    private void persist(Uri u){try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}}
    @Override protected void onActivityResult(int r,int res,Intent data){super.onActivityResult(r,res,data);if(res!=RESULT_OK||data==null||data.getData()==null)return;Uri u=data.getData();persist(u);if(r==REQ_OPEN){Store.addRecent(this,u.toString());launchPlayer(u.toString(),true);}else if(r==REQ_COVER&&!pendingCoverUri.isEmpty()){Store.setMediaCover(this,pendingCoverUri,u.toString());refresh(this::renderLibrary);}}
    @Override protected void onResume(){super.onResume();if(content!=null&&!needsPermission())refresh(null);updateMini();}
    @Override protected void onDestroy(){if(controllerFuture!=null)MediaController.releaseFuture(controllerFuture);super.onDestroy();}

    class MediaVH extends RecyclerView.ViewHolder{ImageView art;TextView title,sub,more;LinearLayout root;MediaVH(View v,boolean grid){super(v);root=(LinearLayout)v;if(grid){art=(ImageView)root.getChildAt(0);title=(TextView)root.getChildAt(1);sub=(TextView)root.getChildAt(2);}else{art=(ImageView)root.getChildAt(0);LinearLayout t=(LinearLayout)root.getChildAt(1);title=(TextView)t.getChildAt(0);sub=(TextView)t.getChildAt(1);more=(TextView)root.getChildAt(2);}}}
    class MediaAdapter extends RecyclerView.Adapter<MediaVH>{
        ArrayList<MediaEntry>d;boolean grid;MediaAdapter(ArrayList<MediaEntry>x,boolean g){d=x;grid=g;}
        @NonNull public MediaVH onCreateViewHolder(@NonNull ViewGroup p,int v){if(grid){LinearLayout b=new LinearLayout(MainActivity.this);b.setOrientation(LinearLayout.VERTICAL);b.setPadding(Ui.dp(MainActivity.this,3),Ui.dp(MainActivity.this,3),Ui.dp(MainActivity.this,3),Ui.dp(MainActivity.this,10));ImageView iv=new ImageView(MainActivity.this);iv.setScaleType(ImageView.ScaleType.CENTER_CROP);b.addView(iv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(MainActivity.this,104)));b.addView(Ui.text(MainActivity.this,"",12,Ui.TEXT,true));b.addView(Ui.text(MainActivity.this,"",10,Ui.MUTED,false));return new MediaVH(b,true);}return new MediaVH(mediaListRow(new MediaEntry("","",0,0,"audio","")),false);}
        public void onBindViewHolder(@NonNull MediaVH h,int pos){MediaEntry e=d.get(pos);Thumb.load(MainActivity.this,h.art,e);h.title.setText(e.title);h.sub.setText(e.durationText());h.root.setOnClickListener(v->launchPlayer(e.uri,true));h.root.setOnLongClickListener(v->{mediaMenu(e);return true;});if(!grid&&h.more!=null)h.more.setOnClickListener(v->mediaMenu(e));}
        public int getItemCount(){return d.size();}
    }
    class FolderAdapter extends RecyclerView.Adapter<MediaVH>{
        ArrayList<String>d;FolderAdapter(ArrayList<String>x){d=x;}
        @NonNull public MediaVH onCreateViewHolder(@NonNull ViewGroup p,int v){return new MediaVH(mediaListRow(new MediaEntry("","",0,0,"audio","")),false);}
        public void onBindViewHolder(@NonNull MediaVH h,int pos){String f=d.get(pos);int c=0;for(MediaEntry e:allMedia)if(f.equals(e.folder))c++;h.art.setImageResource(R.drawable.cover_placeholder);h.title.setText(f);h.sub.setText(c+" media");h.more.setVisibility(View.GONE);h.root.setOnClickListener(v->{folderFilter=f;libraryFilter="all";renderLibrary();});}
        public int getItemCount(){return d.size();}
    }
}
