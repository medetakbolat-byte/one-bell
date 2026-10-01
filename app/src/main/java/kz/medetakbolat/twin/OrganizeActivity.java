package kz.medetakbolat.twin;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.Player;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

public class OrganizeActivity extends AppCompatActivity {
    private static final int PICK_COVER=501,PICK_MEDIA=502,PICK_LIBRARY=503;
    private String mode,collectionId,playlistId,queueId,pendingCoverTarget="",pendingKind="all";
    private LinearLayout root;
    private RecyclerView recycler;
    private ArrayList<MediaEntry> library=new ArrayList<>();
    private ItemTouchHelper touchHelper;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);Store.ensure(this);
        mode=getIntent().getStringExtra("mode");collectionId=getIntent().getStringExtra("collection");playlistId=getIntent().getStringExtra("playlist");queueId=getIntent().getStringExtra("queue");
        if(mode==null)mode="collection";
        if("playlist".equals(mode)&&playlistId!=null)collectionId=Store.collectionIdForPlaylist(this,playlistId);
        Executors.newSingleThreadExecutor().execute(()->{library=MediaRepository.scan(this);runOnUiThread(this::render);});
    }
    @Override protected void onResume(){super.onResume();if("playlist".equals(mode)&&playlistId!=null)collectionId=Store.collectionIdForPlaylist(this,playlistId);if(root!=null)render();}

    private void render(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Ui.BG);
        LinearLayout top=Ui.row(this);top.setPadding(Ui.dp(this,8),Ui.dp(this,7),Ui.dp(this,8),Ui.dp(this,4));
        TextView back=Ui.icon(this,"‹");back.setTextSize(30);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
        TextView title=Ui.text(this,currentTitle(),22,Ui.TEXT,true);title.setPadding(Ui.dp(this,4),0,0,0);title.setMaxLines(2);top.addView(title,new LinearLayout.LayoutParams(0,Ui.dp(this,52),1));
        TextView more=Ui.icon(this,"⋮");more.setOnClickListener(v->showMore());top.addView(more,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));root.addView(top);
        if("collection".equals(mode))renderCollection();else if("playlist".equals(mode))renderPlaylist();else renderQueue();
        setContentView(root);
    }

    private String currentTitle(){
        if("collection".equals(mode)){Store.CollectionDef c=findCollection();return c==null?"Collection":c.name;}
        if("playlist".equals(mode)){Store.Playlist p=findPlaylist();return p==null?"Playlist":p.name;}
        Store.QueueDef q=findQueue();return q==null?"Queue":("current".equals(q.id)?"Now playing":q.name);
    }
    private Store.CollectionDef findCollection(){for(Store.CollectionDef c:Store.getCollections(this))if(c.id.equals(collectionId))return c;return null;}
    private Store.Playlist findPlaylist(){return Store.findPlaylist(this,playlistId);}
    private Store.QueueDef findQueue(){for(Store.QueueDef q:Store.getQueues(this))if(q.id.equals(queueId))return q;return null;}

    private ImageView cover(String uri){
        ImageView iv=new ImageView(this);iv.setScaleType(ImageView.ScaleType.CENTER_CROP);iv.setClipToOutline(true);iv.setBackground(Ui.round(Ui.SURFACE2,14,this));iv.setImageResource(R.drawable.cover_placeholder);
        if(uri!=null&&!uri.isEmpty())try{iv.setImageURI(Uri.parse(uri));}catch(Exception ignored){}return iv;
    }

    private void renderCollection(){
        Store.CollectionDef c=findCollection();if(c==null||Store.ROOT_COLLECTION.equals(c.id)){finish();return;}
        ImageView hero=cover(c.cover);hero.setOnClickListener(v->pickCover("collection"));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,168));hp.setMargins(Ui.dp(this,16),0,Ui.dp(this,16),Ui.dp(this,8));root.addView(hero,hp);
        LinearLayout line=Ui.row(this);line.setPadding(Ui.dp(this,16),0,Ui.dp(this,10),Ui.dp(this,8));LinearLayout t=new LinearLayout(this);t.setOrientation(LinearLayout.VERTICAL);t.addView(Ui.text(this,c.name,20,Ui.TEXT,true));t.addView(Ui.text(this,c.playlists.size()+" playlists",12,Ui.MUTED,false));line.addView(t,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));TextView add=Ui.pill(this,"＋ Playlist");add.setOnClickListener(v->newPlaylistInCollection());line.addView(add);root.addView(line);

        recycler=new RecyclerView(this);recycler.setLayoutManager(new LinearLayoutManager(this));PlaylistAdapter a=new PlaylistAdapter(c.playlists);recycler.setAdapter(a);root.addView(recycler,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));attachPlaylistDrag(a);
    }

    private void renderPlaylist(){
        Store.Playlist p=findPlaylist();if(p==null){finish();return;}
        LinearLayout head=Ui.row(this);head.setPadding(Ui.dp(this,16),Ui.dp(this,2),Ui.dp(this,16),Ui.dp(this,10));ImageView iv=cover(p.cover);iv.setOnClickListener(v->pickCover("playlist"));head.addView(iv,new LinearLayout.LayoutParams(Ui.dp(this,86),Ui.dp(this,86)));
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(Ui.dp(this,14),0,0,0);TextView nm=Ui.text(this,p.name,20,Ui.TEXT,true);nm.setMaxLines(2);info.addView(nm);info.addView(Ui.text(this,p.items.size()+" items · "+Store.collectionNameForPlaylist(this,p.id),12,Ui.MUTED,false));head.addView(info,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));root.addView(head);

        LinearLayout actions=Ui.row(this);actions.setPadding(Ui.dp(this,16),0,Ui.dp(this,16),Ui.dp(this,8));TextView play=Ui.pill(this,"▶ Play");TextView sh=Ui.pill(this,"Shuffle");TextView loop=Ui.pill(this,"↻ Loop");
        play.setOnClickListener(v->playPlaylist(false,false));sh.setOnClickListener(v->playPlaylist(true,false));loop.setOnClickListener(v->playPlaylist(false,true));
        actions.addView(play,new LinearLayout.LayoutParams(0,Ui.dp(this,42),1));actions.addView(sh,new LinearLayout.LayoutParams(0,Ui.dp(this,42),1));actions.addView(loop,new LinearLayout.LayoutParams(0,Ui.dp(this,42),1));((LinearLayout.LayoutParams)sh.getLayoutParams()).setMargins(Ui.dp(this,6),0,Ui.dp(this,6),0);root.addView(actions);

        recycler=new RecyclerView(this);recycler.setLayoutManager(new LinearLayoutManager(this));ItemAdapter a=new ItemAdapter(p.items,false,p.cover);recycler.setAdapter(a);root.addView(recycler,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));attachItemDrag(a,false);

        TextView add=Ui.text(this,"＋  Add audio / video",15,Ui.ACCENT,true);add.setGravity(Gravity.CENTER);add.setPadding(0,Ui.dp(this,10),0,Ui.dp(this,12));add.setOnClickListener(v->addMedia());root.addView(add,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,54)));
    }

    private void renderQueue(){
        Store.QueueDef q=findQueue();if(q==null){finish();return;}
        LinearLayout status=Ui.row(this);status.setPadding(Ui.dp(this,16),Ui.dp(this,4),Ui.dp(this,16),Ui.dp(this,10));TextView s=Ui.text(this,q.id.equals(Store.currentQueueId(this))?"● Active queue":q.items.size()+" items",14,q.id.equals(Store.currentQueueId(this))?Ui.ACCENT:Ui.MUTED,true);status.addView(s,new LinearLayout.LayoutParams(0,Ui.dp(this,42),1));if(!q.id.equals(Store.currentQueueId(this))){TextView a=Ui.pill(this,"Make active");a.setOnClickListener(v->{Store.setCurrentQueue(this,q.id);Playback.syncActiveQueue(this);render();});status.addView(a);}root.addView(status);
        if(!q.items.isEmpty()){
            LinearLayout qActions=Ui.row(this);qActions.setPadding(Ui.dp(this,16),0,Ui.dp(this,16),Ui.dp(this,8));TextView p=Ui.pill(this,"▶ Play");TextView loop=Ui.pill(this,"↻ Loop");p.setOnClickListener(v->playQueue(false));loop.setOnClickListener(v->playQueue(true));qActions.addView(p,new LinearLayout.LayoutParams(0,Ui.dp(this,42),1));qActions.addView(loop,new LinearLayout.LayoutParams(0,Ui.dp(this,42),1));((LinearLayout.LayoutParams)loop.getLayoutParams()).setMargins(Ui.dp(this,8),0,0,0);root.addView(qActions);
        }
        ArrayList<Store.Item>w=new ArrayList<>();for(String u:q.items)w.add(new Store.Item(u));recycler=new RecyclerView(this);recycler.setLayoutManager(new LinearLayoutManager(this));ItemAdapter a=new ItemAdapter(w,true,"");recycler.setAdapter(a);root.addView(recycler,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));attachItemDrag(a,true);
        TextView add=Ui.text(this,"＋  Add media",15,Ui.ACCENT,true);add.setGravity(Gravity.CENTER);add.setOnClickListener(v->addMedia());root.addView(add,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,54)));
    }

    private void attachPlaylistDrag(PlaylistAdapter a){
        touchHelper=new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP|ItemTouchHelper.DOWN,0){
            public boolean onMove(@NonNull RecyclerView rv,@NonNull RecyclerView.ViewHolder f,@NonNull RecyclerView.ViewHolder t){int x=f.getBindingAdapterPosition(),y=t.getBindingAdapterPosition();if(x<0||y<0)return false;Collections.swap(a.data,x,y);a.notifyItemMoved(x,y);savePlaylistOrder(a.data);return true;}
            public void onSwiped(@NonNull RecyclerView.ViewHolder v,int d){}public boolean isLongPressDragEnabled(){return false;}
        });touchHelper.attachToRecyclerView(recycler);a.drag=vh->touchHelper.startDrag(vh);
    }
    private void attachItemDrag(ItemAdapter a,boolean queue){
        touchHelper=new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP|ItemTouchHelper.DOWN,0){
            public boolean onMove(@NonNull RecyclerView rv,@NonNull RecyclerView.ViewHolder f,@NonNull RecyclerView.ViewHolder t){int x=f.getBindingAdapterPosition(),y=t.getBindingAdapterPosition();if(x<0||y<0)return false;if(!queue){String dest=a.data.get(y).section;a.data.get(x).section=dest;}Collections.swap(a.data,x,y);a.notifyItemMoved(x,y);if(queue)saveQueue(a.data);else saveItems(a.data);return true;}
            public void onSwiped(@NonNull RecyclerView.ViewHolder v,int d){}public boolean isLongPressDragEnabled(){return false;}
        });touchHelper.attachToRecyclerView(recycler);a.drag=vh->touchHelper.startDrag(vh);
    }

    private void savePlaylistOrder(ArrayList<Store.Playlist> order){ArrayList<Store.CollectionDef>cs=Store.getCollections(this);for(Store.CollectionDef c:cs)if(c.id.equals(collectionId)){c.playlists.clear();c.playlists.addAll(order);}Store.saveCollections(this,cs);}
    private void saveItems(ArrayList<Store.Item>items){ArrayList<Store.CollectionDef>cs=Store.getCollections(this);for(Store.CollectionDef c:cs)for(Store.Playlist p:c.playlists)if(p.id.equals(playlistId)){p.items.clear();p.items.addAll(items);}Store.saveCollections(this,cs);}
    private void saveQueue(ArrayList<Store.Item>items){ArrayList<String>u=new ArrayList<>();for(Store.Item i:items)u.add(i.uri);Store.replaceQueue(this,queueId,u);if(queueId.equals(Store.currentQueueId(this)))Playback.syncActiveQueue(this);}

    private void playPlaylist(boolean shuffle,boolean loop){
        Store.Playlist p=findPlaylist();if(p==null||p.items.isEmpty())return;ArrayList<String>u=new ArrayList<>();for(Store.Item i:p.items)u.add(i.uri);if(shuffle)Collections.shuffle(u);
        Store.setRepeatMode(this,loop?Player.REPEAT_MODE_ALL:Player.REPEAT_MODE_OFF);Store.setPlaybackFallbackCover(this,p.cover);Store.setCurrentQueue(this,"current");Store.replaceQueue(this,"current",u);Playback.replaceAndPlay(this,u,u.get(0));startActivity(new Intent(this,PlayerActivity.class));
    }
    private void playQueue(boolean loop){Store.QueueDef q=findQueue();if(q==null||q.items.isEmpty())return;Store.setPlaybackFallbackCover(this,"");Store.setRepeatMode(this,loop?Player.REPEAT_MODE_ALL:Player.REPEAT_MODE_OFF);Store.setCurrentQueue(this,q.id);Playback.replaceAndPlay(this,q.items,q.items.get(0));startActivity(new Intent(this,PlayerActivity.class));}
    private void playItem(Store.Item i,boolean queue){
        if(queue){Store.setPlaybackFallbackCover(this,"");Store.setCurrentQueue(this,queueId);Store.QueueDef q=findQueue();if(q!=null)Playback.replaceAndPlay(this,q.items,i.uri);}
        else{Store.Playlist p=findPlaylist();ArrayList<String>u=new ArrayList<>();for(Store.Item x:p.items)u.add(x.uri);Store.setPlaybackFallbackCover(this,p.cover);Store.setCurrentQueue(this,"current");Store.replaceQueue(this,"current",u);Playback.replaceAndPlay(this,u,i.uri);}
        Store.addRecent(this,i.uri);startActivity(new Intent(this,PlayerActivity.class));
    }

    private void newPlaylistInCollection(){Sheets.prompt(this,"New playlist","Playlist name","","Create",n->{ArrayList<Store.CollectionDef>cs=Store.getCollections(this);for(Store.CollectionDef c:cs)if(c.id.equals(collectionId)){Store.Playlist p=new Store.Playlist();p.name=n;c.playlists.add(p);}Store.saveCollections(this,cs);render();});}

    private void addMedia(){
        if("playlist".equals(mode)){
            Sheets.choices(this,"Add to playlist",new String[]{"Audio from Library","Video from Library","All media","Open file…"},w->{if(w==3)openFiles();else openLibraryPicker(w==0?"audio":(w==1?"video":"all"));});
        }else Sheets.choices(this,"Add to queue",new String[]{"Choose from Library","Open file…"},w->{if(w==0)openLibraryPicker("all");else openFiles();});
    }
    private void openLibraryPicker(String kind){
        Intent i=new Intent(this,MediaPickerActivity.class);i.putExtra("kind",kind);
        if("playlist".equals(mode))i.putStringArrayListExtra("exclude",Store.playlistUris(this,playlistId));
        startActivityForResult(i,PICK_LIBRARY);
    }
    private void openFiles(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"audio/*","video/*"});i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PICK_MEDIA);}
    private void addUris(List<String>u){
        if(u==null||u.isEmpty())return;
        if("playlist".equals(mode)){ArrayList<Store.CollectionDef>cs=Store.getCollections(this);for(Store.CollectionDef c:cs)for(Store.Playlist p:c.playlists)if(p.id.equals(playlistId))for(String s:u){boolean ex=false;for(Store.Item it:p.items)if(it.uri.equals(s))ex=true;if(!ex)p.items.add(new Store.Item(s));}Store.saveCollections(this,cs);}
        else if("queue".equals(mode)){ArrayList<Store.QueueDef>qs=Store.getQueues(this);for(Store.QueueDef q:qs)if(q.id.equals(queueId))q.items.addAll(u);Store.saveQueues(this,qs);if(queueId.equals(Store.currentQueueId(this)))Playback.syncActiveQueue(this);}
        for(String s:u)Store.addRecent(this,s);render();
    }

    private void showMore(){
        if("collection".equals(mode))Sheets.choices(this,currentTitle(),new String[]{"Rename","Set cover","New playlist","Delete collection"},w->{if(w==0)rename();else if(w==1)pickCover("collection");else if(w==2)newPlaylistInCollection();else deleteCollection();});
        else if("playlist".equals(mode))Sheets.choices(this,currentTitle(),new String[]{"Rename","Set cover","Move to collection","Add section","Manage sections","Add media","Delete playlist"},w->{if(w==0)rename();else if(w==1)pickCover("playlist");else if(w==2)moveCurrentPlaylist();else if(w==3)addSection();else if(w==4)manageSections();else if(w==5)addMedia();else deletePlaylist();});
        else{ArrayList<String>o=new ArrayList<>();if(!"current".equals(queueId))o.add("Rename");o.add("Clear queue");o.add("Add media");if(!"current".equals(queueId))o.add("Delete queue");Sheets.choices(this,currentTitle(),o.toArray(new String[0]),w->{String x=o.get(w);if("Rename".equals(x))rename();else if("Clear queue".equals(x)){Store.replaceQueue(this,queueId,new ArrayList<>());if(queueId.equals(Store.currentQueueId(this)))Playback.syncActiveQueue(this);render();}else if("Add media".equals(x))addMedia();else deleteQueue();});}
    }

    private void moveCurrentPlaylist(){
        Store.Playlist p=findPlaylist();if(p==null)return;ArrayList<Store.CollectionDef>cs=Store.visibleCollections(this);String[]names=new String[cs.size()+1];names[0]="No collection";for(int i=0;i<cs.size();i++)names[i+1]=cs.get(i).name;
        Sheets.choices(this,"Move "+p.name,names,w->{Store.movePlaylist(this,p.id,w==0?Store.ROOT_COLLECTION:cs.get(w-1).id);collectionId=Store.collectionIdForPlaylist(this,p.id);render();});
    }

    private void rename(){Sheets.prompt(this,"Rename","Name",currentTitle(),"Save",n->{if("collection".equals(mode)){ArrayList<Store.CollectionDef>cs=Store.getCollections(this);for(Store.CollectionDef x:cs)if(x.id.equals(collectionId))x.name=n;Store.saveCollections(this,cs);}else if("playlist".equals(mode)){ArrayList<Store.CollectionDef>cs=Store.getCollections(this);for(Store.CollectionDef x:cs)for(Store.Playlist p:x.playlists)if(p.id.equals(playlistId))p.name=n;Store.saveCollections(this,cs);}else{ArrayList<Store.QueueDef>qs=Store.getQueues(this);for(Store.QueueDef q:qs)if(q.id.equals(queueId))q.name=n;Store.saveQueues(this,qs);}render();});}
    private void deleteCollection(){Sheets.confirm(this,"Delete collection?","The playlists stay. Only the grouping is removed.","Delete",()->{Store.deleteCollectionKeepPlaylists(this,collectionId);finish();});}
    private void deletePlaylist(){Sheets.confirm(this,"Delete playlist?","Media files stay on your phone.","Delete",()->{ArrayList<Store.CollectionDef>cs=Store.getCollections(this);for(Store.CollectionDef c:cs)c.playlists.removeIf(p->p.id.equals(playlistId));Store.saveCollections(this,cs);finish();});}
    private void deleteQueue(){Sheets.confirm(this,"Delete queue?","Media files stay on your phone.","Delete",()->{ArrayList<Store.QueueDef>qs=Store.getQueues(this);qs.removeIf(q->q.id.equals(queueId));Store.saveQueues(this,qs);if(queueId.equals(Store.currentQueueId(this))){Store.setCurrentQueue(this,"current");Playback.syncActiveQueue(this);}finish();});}

    private void addSection(){Sheets.prompt(this,"Add section","Section name","","Add",n->{ArrayList<Store.CollectionDef>cs=Store.getCollections(this);for(Store.CollectionDef c:cs)for(Store.Playlist p:c.playlists)if(p.id.equals(playlistId)&&!p.sections.contains(n))p.sections.add(n);Store.saveCollections(this,cs);render();});}
    private void manageSections(){
        Store.Playlist p=findPlaylist();if(p==null||p.sections.isEmpty()){Toast.makeText(this,"No sections yet",Toast.LENGTH_SHORT).show();return;}
        Sheets.choices(this,"Sections",p.sections.toArray(new String[0]),i->{String old=p.sections.get(i);Sheets.choices(this,old,new String[]{"Rename","Remove section"},w->{if(w==0)Sheets.prompt(this,"Rename section","Section name",old,"Save",n->{ArrayList<Store.CollectionDef>cs=Store.getCollections(this);for(Store.CollectionDef c:cs)for(Store.Playlist pl:c.playlists)if(pl.id.equals(playlistId)){int ix=pl.sections.indexOf(old);if(ix>=0)pl.sections.set(ix,n);for(Store.Item it:pl.items)if(old.equals(it.section))it.section=n;}Store.saveCollections(this,cs);render();});else Sheets.confirm(this,"Remove section?","Media stays in the playlist and moves to the unsectioned area.","Remove",()->{ArrayList<Store.CollectionDef>cs=Store.getCollections(this);for(Store.CollectionDef c:cs)for(Store.Playlist pl:c.playlists)if(pl.id.equals(playlistId)){pl.sections.remove(old);for(Store.Item it:pl.items)if(old.equals(it.section))it.section="";}Store.saveCollections(this,cs);render();});});});
    }

    private void pickCover(String target){pendingCoverTarget=target;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PICK_COVER);}
    private void persist(Uri u){try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}}

    @Override protected void onActivityResult(int r,int res,Intent data){
        super.onActivityResult(r,res,data);if(res!=RESULT_OK||data==null)return;
        if(r==PICK_COVER&&data.getData()!=null){
            Uri u=data.getData();persist(u);if("collection".equals(pendingCoverTarget)){ArrayList<Store.CollectionDef>cs=Store.getCollections(this);for(Store.CollectionDef c:cs)if(c.id.equals(collectionId))c.cover=u.toString();Store.saveCollections(this,cs);}
            else{ArrayList<Store.CollectionDef>cs=Store.getCollections(this);for(Store.CollectionDef c:cs)for(Store.Playlist p:c.playlists)if(p.id.equals(playlistId))p.cover=u.toString();Store.saveCollections(this,cs);}render();
        }else if(r==PICK_MEDIA){
            ArrayList<String>u=new ArrayList<>();if(data.getData()!=null){persist(data.getData());u.add(data.getData().toString());}ClipData clips=data.getClipData();if(clips!=null)for(int i=0;i<clips.getItemCount();i++){Uri x=clips.getItemAt(i).getUri();persist(x);u.add(x.toString());}addUris(u);
        }else if(r==PICK_LIBRARY){ArrayList<String>u=data.getStringArrayListExtra("uris");addUris(u==null?new ArrayList<>():u);}
    }

    private void playlistEntryMenu(Store.Playlist p){
        Sheets.choices(this,p.name,new String[]{"Open","Play","Move to collection","Rename","Delete"},w->{if(w==0){Intent i=new Intent(this,OrganizeActivity.class);i.putExtra("mode","playlist");i.putExtra("collection",Store.collectionIdForPlaylist(this,p.id));i.putExtra("playlist",p.id);startActivity(i);}else if(w==1){playlistId=p.id;playPlaylist(false,false);}else if(w==2){playlistId=p.id;moveCurrentPlaylist();}else if(w==3)Sheets.prompt(this,"Rename playlist","Playlist name",p.name,"Save",n->{ArrayList<Store.CollectionDef>cs=Store.getCollections(this);for(Store.CollectionDef c:cs)for(Store.Playlist z:c.playlists)if(z.id.equals(p.id))z.name=n;Store.saveCollections(this,cs);render();});else Sheets.confirm(this,"Delete playlist?","Media files stay on your phone.","Delete",()->{ArrayList<Store.CollectionDef>cs=Store.getCollections(this);for(Store.CollectionDef c:cs)c.playlists.removeIf(z->z.id.equals(p.id));Store.saveCollections(this,cs);render();});});
    }

    private void itemMenu(Store.Item it,boolean queue){
        ArrayList<String>o=new ArrayList<>();o.add("Play");o.add("Play next");if(!queue)o.add("Move to section");o.add("Share");o.add("Remove");
        Sheets.choices(this,"Media",o.toArray(new String[0]),w->{String x=o.get(w);if("Play".equals(x))playItem(it,queue);else if("Play next".equals(x)){Playback.addNext(this,it.uri);Toast.makeText(this,"Playing next",Toast.LENGTH_SHORT).show();}else if("Move to section".equals(x))chooseSection(it);else if("Share".equals(x))share(it.uri);else removeItem(it,queue);});
    }
    private void share(String uri){Intent send=new Intent(Intent.ACTION_SEND);MediaEntry e=MediaRepository.resolve(this,uri);send.setType(e!=null&&e.isVideo()?"video/*":"audio/*");send.putExtra(Intent.EXTRA_STREAM,Uri.parse(uri));send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);send.setClipData(ClipData.newUri(getContentResolver(),"Twin media",Uri.parse(uri)));startActivity(Intent.createChooser(send,"Share media"));}
    private void chooseSection(Store.Item item){Store.Playlist p=findPlaylist();ArrayList<String>s=new ArrayList<>();s.add("No section");s.addAll(p.sections);Sheets.choices(this,"Move to section",s.toArray(new String[0]),w->{ArrayList<Store.CollectionDef>cs=Store.getCollections(this);for(Store.CollectionDef c:cs)for(Store.Playlist pl:c.playlists)if(pl.id.equals(playlistId))for(Store.Item it:pl.items)if(it.uri.equals(item.uri)&&it.section.equals(item.section)){it.section=w==0?"":s.get(w);break;}Store.saveCollections(this,cs);render();});}
    private void removeItem(Store.Item it,boolean queue){if(queue){ArrayList<Store.QueueDef>qs=Store.getQueues(this);for(Store.QueueDef q:qs)if(q.id.equals(queueId))q.items.remove(it.uri);Store.saveQueues(this,qs);if(queueId.equals(Store.currentQueueId(this)))Playback.syncActiveQueue(this);}else{ArrayList<Store.CollectionDef>cs=Store.getCollections(this);for(Store.CollectionDef c:cs)for(Store.Playlist p:c.playlists)if(p.id.equals(playlistId))for(int i=0;i<p.items.size();i++)if(p.items.get(i).uri.equals(it.uri)&&p.items.get(i).section.equals(it.section)){p.items.remove(i);break;}Store.saveCollections(this,cs);}render();}

    interface DragStart{void start(RecyclerView.ViewHolder h);}
    class PlaylistVH extends RecyclerView.ViewHolder{ImageView art;TextView title,sub,drag,more;LinearLayout row;PlaylistVH(View v){super(v);row=(LinearLayout)v;art=(ImageView)row.getChildAt(0);LinearLayout t=(LinearLayout)row.getChildAt(1);title=(TextView)t.getChildAt(0);sub=(TextView)t.getChildAt(1);LinearLayout tools=(LinearLayout)row.getChildAt(2);drag=(TextView)tools.getChildAt(0);more=(TextView)tools.getChildAt(1);}}
    class PlaylistAdapter extends RecyclerView.Adapter<PlaylistVH>{
        ArrayList<Store.Playlist>data;DragStart drag;PlaylistAdapter(ArrayList<Store.Playlist>d){data=new ArrayList<>(d);}
        @NonNull public PlaylistVH onCreateViewHolder(@NonNull ViewGroup p,int v){return new PlaylistVH(playlistRow());}
        public void onBindViewHolder(@NonNull PlaylistVH h,int pos){Store.Playlist p=data.get(pos);h.title.setText(p.name);h.sub.setText(p.items.size()+" items");if(p.cover!=null&&!p.cover.isEmpty())try{h.art.setImageURI(Uri.parse(p.cover));}catch(Exception e){h.art.setImageResource(R.drawable.cover_placeholder);}else h.art.setImageResource(R.drawable.cover_placeholder);h.row.setOnClickListener(v->{Intent i=new Intent(OrganizeActivity.this,OrganizeActivity.class);i.putExtra("mode","playlist");i.putExtra("collection",Store.collectionIdForPlaylist(OrganizeActivity.this,p.id));i.putExtra("playlist",p.id);startActivity(i);});h.drag.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_DOWN&&drag!=null)drag.start(h);return true;});h.more.setOnClickListener(v->playlistEntryMenu(p));}
        public int getItemCount(){return data.size();}
    }
    private LinearLayout playlistRow(){
        LinearLayout r=Ui.row(this);r.setPadding(Ui.dp(this,16),Ui.dp(this,8),Ui.dp(this,8),Ui.dp(this,8));r.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        ImageView iv=new ImageView(this);iv.setScaleType(ImageView.ScaleType.CENTER_CROP);iv.setClipToOutline(true);iv.setBackground(Ui.round(Ui.SURFACE2,10,this));r.addView(iv,new LinearLayout.LayoutParams(Ui.dp(this,68),Ui.dp(this,58)));
        LinearLayout t=new LinearLayout(this);t.setOrientation(LinearLayout.VERTICAL);t.setPadding(Ui.dp(this,12),0,Ui.dp(this,8),0);TextView n=Ui.text(this,"",15,Ui.TEXT,true);n.setMaxLines(3);t.addView(n);t.addView(Ui.text(this,"",12,Ui.MUTED,false));r.addView(t,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        LinearLayout tools=Ui.row(this);tools.setGravity(Gravity.CENTER);TextView drag=Ui.icon(this,"≡"),more=Ui.icon(this,"⋮");tools.addView(drag,new LinearLayout.LayoutParams(Ui.dp(this,42),Ui.dp(this,52)));tools.addView(more,new LinearLayout.LayoutParams(Ui.dp(this,42),Ui.dp(this,52)));r.addView(tools,new LinearLayout.LayoutParams(Ui.dp(this,84),Ui.dp(this,58)));return r;
    }

    class ItemVH extends RecyclerView.ViewHolder{LinearLayout outer,row;TextView section,title,sub,drag,more;ImageView art;ItemVH(View v){super(v);outer=(LinearLayout)v;section=(TextView)outer.getChildAt(0);row=(LinearLayout)outer.getChildAt(1);art=(ImageView)row.getChildAt(0);LinearLayout t=(LinearLayout)row.getChildAt(1);title=(TextView)t.getChildAt(0);sub=(TextView)t.getChildAt(1);LinearLayout tools=(LinearLayout)row.getChildAt(2);drag=(TextView)tools.getChildAt(0);more=(TextView)tools.getChildAt(1);}}
    class ItemAdapter extends RecyclerView.Adapter<ItemVH>{
        ArrayList<Store.Item>data;boolean queue;String fallbackCover;DragStart drag;
        ItemAdapter(ArrayList<Store.Item>d,boolean q,String cover){data=new ArrayList<>();for(Store.Item i:d)data.add(new Store.Item(i.uri,i.section));queue=q;fallbackCover=cover==null?"":cover;}
        @NonNull public ItemVH onCreateViewHolder(@NonNull ViewGroup p,int v){return new ItemVH(itemRow());}
        public void onBindViewHolder(@NonNull ItemVH h,int pos){Store.Item it=data.get(pos);MediaEntry e=MediaRepository.resolve(OrganizeActivity.this,it.uri);String prev=pos==0?"":data.get(pos-1).section;boolean show=!queue&&!it.section.isEmpty()&&(pos==0||!it.section.equals(prev));h.section.setVisibility(show?View.VISIBLE:View.GONE);h.section.setText(it.section);if(e!=null)Thumb.load(OrganizeActivity.this,h.art,e,fallbackCover);else h.art.setImageResource(R.drawable.cover_placeholder);h.title.setText(e==null?"Missing media":e.title);h.title.setMaxLines(3);h.sub.setText(e==null?it.uri:(e.isVideo()?"Video · ":"Audio · ")+e.durationText());h.row.setOnClickListener(v->playItem(it,queue));h.row.setOnLongClickListener(v->{itemMenu(it,queue);return true;});h.more.setOnClickListener(v->itemMenu(it,queue));h.drag.setOnTouchListener((v,ev)->{if(ev.getAction()==MotionEvent.ACTION_DOWN&&drag!=null)drag.start(h);return true;});}
        public int getItemCount(){return data.size();}
    }
    private LinearLayout itemRow(){
        LinearLayout o=new LinearLayout(this);o.setOrientation(LinearLayout.VERTICAL);o.setPadding(Ui.dp(this,16),0,Ui.dp(this,8),0);o.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView sec=Ui.text(this,"",13,Ui.MUTED,true);sec.setPadding(0,Ui.dp(this,10),0,Ui.dp(this,4));o.addView(sec);
        LinearLayout r=Ui.row(this);r.setPadding(0,Ui.dp(this,7),0,Ui.dp(this,7));ImageView iv=new ImageView(this);iv.setScaleType(ImageView.ScaleType.CENTER_CROP);iv.setClipToOutline(true);iv.setBackground(Ui.round(Ui.SURFACE2,10,this));r.addView(iv,new LinearLayout.LayoutParams(Ui.dp(this,62),Ui.dp(this,56)));
        LinearLayout t=new LinearLayout(this);t.setOrientation(LinearLayout.VERTICAL);t.setPadding(Ui.dp(this,12),0,Ui.dp(this,8),0);TextView n=Ui.text(this,"",14,Ui.TEXT,true);n.setMaxLines(3);t.addView(n);t.addView(Ui.text(this,"",12,Ui.MUTED,false));r.addView(t,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        LinearLayout tools=Ui.row(this);tools.setGravity(Gravity.CENTER);tools.addView(Ui.icon(this,"≡"),new LinearLayout.LayoutParams(Ui.dp(this,42),Ui.dp(this,50)));tools.addView(Ui.icon(this,"⋮"),new LinearLayout.LayoutParams(Ui.dp(this,42),Ui.dp(this,50)));r.addView(tools,new LinearLayout.LayoutParams(Ui.dp(this,84),Ui.dp(this,56)));o.addView(r);o.addView(Ui.hairline(this));return o;
    }
}
