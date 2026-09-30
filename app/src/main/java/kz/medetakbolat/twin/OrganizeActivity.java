package kz.medetakbolat.twin;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

public class OrganizeActivity extends AppCompatActivity {
    private static final int PICK_COVER=501;
    private static final int PICK_MEDIA=502;

    private String mode;
    private String collectionId;
    private String playlistId;
    private String queueId;
    private LinearLayout root;
    private RecyclerView recycler;
    private ArrayList<MediaEntry> library=new ArrayList<>();
    private String pendingCoverTarget="";

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        mode=getIntent().getStringExtra("mode");
        collectionId=getIntent().getStringExtra("collection");
        playlistId=getIntent().getStringExtra("playlist");
        queueId=getIntent().getStringExtra("queue");
        if(mode==null)mode="collection";
        Executors.newSingleThreadExecutor().execute(()->{
            library=MediaRepository.scan(this);
            runOnUiThread(this::render);
        });
    }

    @Override protected void onResume(){super.onResume(); if(root!=null)render();}

    private void render(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Ui.BG);
        LinearLayout top=Ui.row(this);top.setPadding(Ui.dp(this,12),Ui.dp(this,10),Ui.dp(this,12),Ui.dp(this,6));
        TextView back=Ui.button(this,"‹");back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,44)));
        TextView title=Ui.text(this,currentTitle(),22,Ui.TEXT,true);title.setPadding(Ui.dp(this,10),0,0,0);top.addView(title,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        TextView more=Ui.button(this,"⋮");more.setOnClickListener(v->showMore());top.addView(more,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,44)));
        root.addView(top);

        if("collection".equals(mode)) renderCollection();
        else if("playlist".equals(mode)) renderPlaylist();
        else renderQueue();
        setContentView(root);
    }

    private String currentTitle(){
        if("collection".equals(mode)){Store.CollectionDef c=findCollection();return c==null?"Collection":c.name;}
        if("playlist".equals(mode)){Store.Playlist p=findPlaylist();return p==null?"Playlist":p.name;}
        Store.QueueDef q=findQueue();return q==null?"Queue":q.name;
    }

    private Store.CollectionDef findCollection(){
        for(Store.CollectionDef c:Store.getCollections(this))if(c.id.equals(collectionId))return c;return null;
    }
    private Store.Playlist findPlaylist(){
        for(Store.CollectionDef c:Store.getCollections(this))if(c.id.equals(collectionId))
            for(Store.Playlist p:c.playlists)if(p.id.equals(playlistId))return p;
        return null;
    }
    private Store.QueueDef findQueue(){
        for(Store.QueueDef q:Store.getQueues(this))if(q.id.equals(queueId))return q;return null;
    }

    private ImageView coverView(String uri){
        ImageView iv=new ImageView(this);iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if(uri!=null&&!uri.isEmpty())try{iv.setImageURI(Uri.parse(uri));}catch(Exception ignored){}
        else iv.setImageResource(kz.medetakbolat.twin.R.drawable.cover_placeholder);
        return iv;
    }

    private void renderCollection(){
        Store.CollectionDef c=findCollection();if(c==null){finish();return;}
        ImageView cover=coverView(c.cover);cover.setOnClickListener(v->pickCover("collection"));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,155));cp.setMargins(Ui.dp(this,18),Ui.dp(this,6),Ui.dp(this,18),Ui.dp(this,10));root.addView(cover,cp);

        LinearLayout info=Ui.row(this);info.setPadding(Ui.dp(this,18),0,Ui.dp(this,18),Ui.dp(this,8));
        LinearLayout it=new LinearLayout(this);it.setOrientation(LinearLayout.VERTICAL);
        it.addView(Ui.text(this,c.name,21,Ui.TEXT,true));it.addView(Ui.text(this,c.playlists.size()+" playlists",13,Ui.MUTED,false));info.addView(it,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        TextView add=Ui.button(this,"＋ New playlist");add.setOnClickListener(v->newPlaylist());info.addView(add);root.addView(info);

        recycler=new RecyclerView(this);recycler.setLayoutManager(new LinearLayoutManager(this));
        PlaylistAdapter adapter=new PlaylistAdapter(c.playlists);recycler.setAdapter(adapter);
        root.addView(recycler,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        attachDrag(adapter,()->savePlaylistOrder(adapter.data));
    }

    private void savePlaylistOrder(ArrayList<Store.Playlist> order){
        ArrayList<Store.CollectionDef> cs=Store.getCollections(this);
        for(Store.CollectionDef c:cs)if(c.id.equals(collectionId)){c.playlists.clear();c.playlists.addAll(order);}
        Store.saveCollections(this,cs);
    }

    private void newPlaylist(){
        EditText e=new EditText(this);e.setHint("Playlist name");
        new AlertDialog.Builder(this).setTitle("New playlist").setView(e).setPositiveButton("Create",(d,w)->{
            String n=e.getText().toString().trim();if(n.isEmpty())n="Playlist";
            ArrayList<Store.CollectionDef>cs=Store.getCollections(this);
            for(Store.CollectionDef c:cs)if(c.id.equals(collectionId)){Store.Playlist p=new Store.Playlist();p.name=n;c.playlists.add(p);}
            Store.saveCollections(this,cs);render();
        }).setNegativeButton("Cancel",null).show();
    }

    private void renderPlaylist(){
        Store.Playlist p=findPlaylist();if(p==null){finish();return;}
        LinearLayout head=Ui.row(this);head.setPadding(Ui.dp(this,18),Ui.dp(this,6),Ui.dp(this,18),Ui.dp(this,8));
        ImageView cover=coverView(p.cover);cover.setOnClickListener(v->pickCover("playlist"));head.addView(cover,new LinearLayout.LayoutParams(Ui.dp(this,76),Ui.dp(this,76)));
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(Ui.dp(this,14),0,0,0);
        info.addView(Ui.text(this,p.name,20,Ui.TEXT,true));info.addView(Ui.text(this,p.items.size()+" items",13,Ui.MUTED,false));head.addView(info,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));root.addView(head);

        LinearLayout actions=Ui.row(this);actions.setPadding(Ui.dp(this,18),0,Ui.dp(this,18),Ui.dp(this,8));
        TextView play=Ui.button(this,"▶  Play all");play.setOnClickListener(v->playPlaylist(false));actions.addView(play,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));
        TextView shuffle=Ui.button(this,"⤨  Shuffle");shuffle.setOnClickListener(v->playPlaylist(true));actions.addView(shuffle,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));Ui.margins(shuffle,8,0,0,0);root.addView(actions);

        recycler=new RecyclerView(this);recycler.setLayoutManager(new LinearLayoutManager(this));
        ItemAdapter adapter=new ItemAdapter(p.items,false);recycler.setAdapter(adapter);
        root.addView(recycler,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        attachDrag(adapter,()->savePlaylistItems(adapter.data));

        TextView add=Ui.button(this,"＋  Add media");add.setOnClickListener(v->addMediaDialog());LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,52));ap.setMargins(Ui.dp(this,18),Ui.dp(this,7),Ui.dp(this,18),Ui.dp(this,12));root.addView(add,ap);
    }

    private void renderQueue(){
        Store.QueueDef q=findQueue();if(q==null){finish();return;}
        LinearLayout info=Ui.row(this);info.setPadding(Ui.dp(this,18),Ui.dp(this,5),Ui.dp(this,18),Ui.dp(this,10));
        TextView state=Ui.text(this,q.id.equals(Store.currentQueueId(this))?"● Current queue":q.items.size()+" items",15,q.id.equals(Store.currentQueueId(this))?Ui.ACCENT:Ui.MUTED,true);
        info.addView(state,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        TextView active=Ui.button(this,"Make active");active.setVisibility(q.id.equals(Store.currentQueueId(this))?View.GONE:View.VISIBLE);active.setOnClickListener(v->{Store.setCurrentQueue(this,q.id);render();});info.addView(active);root.addView(info);

        if(!q.items.isEmpty()){
            TextView play=Ui.button(this,"▶  Play queue");play.setOnClickListener(v->playQueue());LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,48));pp.setMargins(Ui.dp(this,18),0,Ui.dp(this,18),Ui.dp(this,8));root.addView(play,pp);
        }
        ArrayList<Store.Item> wraps=new ArrayList<>();for(String u:q.items)wraps.add(new Store.Item(u));
        recycler=new RecyclerView(this);recycler.setLayoutManager(new LinearLayoutManager(this));
        ItemAdapter adapter=new ItemAdapter(wraps,true);recycler.setAdapter(adapter);
        root.addView(recycler,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        attachDrag(adapter,()->saveQueueItems(adapter.data));

        TextView add=Ui.button(this,"＋  Add media");add.setOnClickListener(v->addMediaDialog());LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,52));ap.setMargins(Ui.dp(this,18),Ui.dp(this,7),Ui.dp(this,18),Ui.dp(this,12));root.addView(add,ap);
    }

    private void attachDrag(RecyclerView.Adapter<?> adapter,Runnable save){
        ItemTouchHelper helper=new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP|ItemTouchHelper.DOWN,0){
            @Override public boolean onMove(@NonNull RecyclerView rv,@NonNull RecyclerView.ViewHolder from,@NonNull RecyclerView.ViewHolder to){
                int a=from.getBindingAdapterPosition(),b=to.getBindingAdapterPosition();
                if(adapter instanceof PlaylistAdapter)Collections.swap(((PlaylistAdapter)adapter).data,a,b);
                else if(adapter instanceof ItemAdapter)Collections.swap(((ItemAdapter)adapter).data,a,b);
                adapter.notifyItemMoved(a,b);save.run();return true;
            }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder v,int dir){}
            @Override public boolean isLongPressDragEnabled(){return true;}
        });helper.attachToRecyclerView(recycler);
    }

    private void savePlaylistItems(ArrayList<Store.Item> items){
        ArrayList<Store.CollectionDef>cs=Store.getCollections(this);
        for(Store.CollectionDef c:cs)if(c.id.equals(collectionId))for(Store.Playlist p:c.playlists)if(p.id.equals(playlistId)){p.items.clear();p.items.addAll(items);}
        Store.saveCollections(this,cs);
    }

    private void saveQueueItems(ArrayList<Store.Item> items){
        ArrayList<Store.QueueDef>qs=Store.getQueues(this);for(Store.QueueDef q:qs)if(q.id.equals(queueId)){q.items.clear();for(Store.Item it:items)q.items.add(it.uri);}Store.saveQueues(this,qs);
    }

    private void playPlaylist(boolean shuffle){
        Store.Playlist p=findPlaylist();if(p==null||p.items.isEmpty())return;
        ArrayList<String> uris=new ArrayList<>();for(Store.Item it:p.items)uris.add(it.uri);if(shuffle)Collections.shuffle(uris);
        Store.setCurrentQueue(this,"current");Store.replaceCurrentQueue(this,uris);openPlayer(uris.get(0));
    }

    private void playQueue(){
        Store.QueueDef q=findQueue();if(q==null||q.items.isEmpty())return;Store.setCurrentQueue(this,q.id);openPlayer(q.items.get(0));
    }

    private void openPlayer(String uri){
        Store.addRecent(this,uri);Intent i=new Intent(this,PlayerActivity.class);i.putExtra("uri",uri);startActivity(i);
    }

    private void addMediaDialog(){
        final ArrayList<MediaEntry> choices=new ArrayList<>();
        for(int i=0;i<library.size()&&i<120;i++)choices.add(library.get(i));
        String[] names=new String[choices.size()];boolean[] checked=new boolean[choices.size()];
        for(int i=0;i<choices.size();i++)names[i]=choices.get(i).title+"   "+choices.get(i).durationText();
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle("Add media")
                .setMultiChoiceItems(names,checked,(d,w,is)->checked[w]=is)
                .setPositiveButton("Add",null)
                .setNeutralButton("Open file…",null)
                .setNegativeButton("Cancel",null).create();
        dlg.setOnShowListener(x->{
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                ArrayList<String> uris=new ArrayList<>();for(int i=0;i<checked.length;i++)if(checked[i])uris.add(choices.get(i).uri);
                addUris(uris);dlg.dismiss();
            });
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{dlg.dismiss();openFilePicker();});
        });dlg.show();
    }

    private void openFilePicker(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"audio/*","video/*"});i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PICK_MEDIA);
    }

    private void addUris(List<String> uris){
        if(uris==null||uris.isEmpty())return;
        if("playlist".equals(mode)){
            ArrayList<Store.CollectionDef>cs=Store.getCollections(this);
            for(Store.CollectionDef c:cs)if(c.id.equals(collectionId))for(Store.Playlist p:c.playlists)if(p.id.equals(playlistId)){
                for(String u:uris){boolean exists=false;for(Store.Item it:p.items)if(it.uri.equals(u))exists=true;if(!exists)p.items.add(new Store.Item(u));}
            }
            Store.saveCollections(this,cs);
        }else if("queue".equals(mode)){
            ArrayList<Store.QueueDef>qs=Store.getQueues(this);for(Store.QueueDef q:qs)if(q.id.equals(queueId))q.items.addAll(uris);Store.saveQueues(this,qs);
        }
        for(String u:uris)Store.addRecent(this,u);
        Toast.makeText(this,"Added "+uris.size(),Toast.LENGTH_SHORT).show();render();
    }

    private void pickCover(String target){
        pendingCoverTarget=target;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PICK_COVER);
    }

    @Override protected void onActivityResult(int req,int res,Intent data){
        super.onActivityResult(req,res,data);if(res!=RESULT_OK||data==null)return;
        if(req==PICK_COVER&&data.getData()!=null){
            Uri u=data.getData();try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
            if("collection".equals(pendingCoverTarget)){
                ArrayList<Store.CollectionDef>cs=Store.getCollections(this);for(Store.CollectionDef c:cs)if(c.id.equals(collectionId))c.cover=u.toString();Store.saveCollections(this,cs);
            }else{
                ArrayList<Store.CollectionDef>cs=Store.getCollections(this);for(Store.CollectionDef c:cs)if(c.id.equals(collectionId))for(Store.Playlist p:c.playlists)if(p.id.equals(playlistId))p.cover=u.toString();Store.saveCollections(this,cs);
            }render();
        }else if(req==PICK_MEDIA){
            ArrayList<String> uris=new ArrayList<>();
            if(data.getData()!=null){persist(data.getData());uris.add(data.getData().toString());}
            ClipData clips=data.getClipData();if(clips!=null)for(int i=0;i<clips.getItemCount();i++){Uri u=clips.getItemAt(i).getUri();persist(u);uris.add(u.toString());}
            addUris(uris);
        }
    }

    private void persist(Uri u){try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}}

    private void showMore(){
        if("collection".equals(mode)){
            new AlertDialog.Builder(this).setItems(new String[]{"Rename","Set cover","New playlist"},(d,w)->{
                if(w==0)renameCurrent();else if(w==1)pickCover("collection");else newPlaylist();
            }).show();
        }else if("playlist".equals(mode)){
            new AlertDialog.Builder(this).setItems(new String[]{"Rename","Set cover","Add section","Add media"},(d,w)->{
                if(w==0)renameCurrent();else if(w==1)pickCover("playlist");else if(w==2)addSection();else addMediaDialog();
            }).show();
        }else{
            new AlertDialog.Builder(this).setItems(new String[]{"Rename","Clear queue","Add media"},(d,w)->{
                if(w==0)renameCurrent();else if(w==1)clearQueue();else addMediaDialog();
            }).show();
        }
    }

    private void renameCurrent(){
        EditText e=new EditText(this);e.setText(currentTitle());
        new AlertDialog.Builder(this).setTitle("Rename").setView(e).setPositiveButton("Save",(d,w)->{
            String n=e.getText().toString().trim();if(n.isEmpty())return;
            if("collection".equals(mode)){ArrayList<Store.CollectionDef>cs=Store.getCollections(this);for(Store.CollectionDef c:cs)if(c.id.equals(collectionId))c.name=n;Store.saveCollections(this,cs);}
            else if("playlist".equals(mode)){ArrayList<Store.CollectionDef>cs=Store.getCollections(this);for(Store.CollectionDef c:cs)if(c.id.equals(collectionId))for(Store.Playlist p:c.playlists)if(p.id.equals(playlistId))p.name=n;Store.saveCollections(this,cs);}
            else{ArrayList<Store.QueueDef>qs=Store.getQueues(this);for(Store.QueueDef q:qs)if(q.id.equals(queueId))q.name=n;Store.saveQueues(this,qs);}render();
        }).show();
    }

    private void clearQueue(){ArrayList<Store.QueueDef>qs=Store.getQueues(this);for(Store.QueueDef q:qs)if(q.id.equals(queueId))q.items.clear();Store.saveQueues(this,qs);render();}

    private void addSection(){
        EditText e=new EditText(this);e.setHint("Section name");
        new AlertDialog.Builder(this).setTitle("Add section").setView(e).setPositiveButton("Add",(d,w)->{
            String n=e.getText().toString().trim();if(n.isEmpty())return;ArrayList<Store.CollectionDef>cs=Store.getCollections(this);
            for(Store.CollectionDef c:cs)if(c.id.equals(collectionId))for(Store.Playlist p:c.playlists)if(p.id.equals(playlistId)&&!p.sections.contains(n))p.sections.add(n);
            Store.saveCollections(this,cs);Toast.makeText(this,"Section added. Long-press media to move it.",Toast.LENGTH_LONG).show();
        }).show();
    }

    private void itemMenu(Store.Item item,boolean queue){
        ArrayList<String> opts=new ArrayList<>();opts.add("Play");opts.add("Play next");if(!queue)opts.add("Move to section");opts.add("Remove");
        new AlertDialog.Builder(this).setItems(opts.toArray(new String[0]),(d,w)->{
            String x=opts.get(w);
            if("Play".equals(x)){
                if(queue){Store.setCurrentQueue(this,queueId);}else{Store.setCurrentQueue(this,"current");ArrayList<String>u=new ArrayList<>();for(Store.Item it:findPlaylist().items)u.add(it.uri);Store.replaceCurrentQueue(this,u);}
                openPlayer(item.uri);
            }else if("Play next".equals(x)){Store.addToQueue(this,Store.currentQueueId(this),item.uri,true);Toast.makeText(this,"Playing next",Toast.LENGTH_SHORT).show();}
            else if("Move to section".equals(x))chooseSection(item);
            else removeItem(item,queue);
        }).show();
    }

    private void chooseSection(Store.Item item){
        Store.Playlist p=findPlaylist();if(p==null)return;ArrayList<String> ss=new ArrayList<>();ss.add("No section");ss.addAll(p.sections);
        new AlertDialog.Builder(this).setTitle("Move to section").setItems(ss.toArray(new String[0]),(d,w)->{
            ArrayList<Store.CollectionDef>cs=Store.getCollections(this);for(Store.CollectionDef c:cs)if(c.id.equals(collectionId))for(Store.Playlist pl:c.playlists)if(pl.id.equals(playlistId))for(Store.Item it:pl.items)if(it.uri.equals(item.uri)&&it.section.equals(item.section)){it.section=w==0?"":ss.get(w);break;}
            Store.saveCollections(this,cs);render();
        }).show();
    }

    private void removeItem(Store.Item item,boolean queue){
        if(queue){ArrayList<Store.QueueDef>qs=Store.getQueues(this);for(Store.QueueDef q:qs)if(q.id.equals(queueId))q.items.remove(item.uri);Store.saveQueues(this,qs);}
        else{ArrayList<Store.CollectionDef>cs=Store.getCollections(this);for(Store.CollectionDef c:cs)if(c.id.equals(collectionId))for(Store.Playlist p:c.playlists)if(p.id.equals(playlistId)){for(int i=0;i<p.items.size();i++)if(p.items.get(i).uri.equals(item.uri)&&p.items.get(i).section.equals(item.section)){p.items.remove(i);break;}}Store.saveCollections(this,cs);}
        render();
    }

    class PlaylistVH extends RecyclerView.ViewHolder{LinearLayout box;TextView icon,title,sub;PlaylistVH(View v){super(v);box=(LinearLayout)v;icon=(TextView)box.getChildAt(0);LinearLayout t=(LinearLayout)box.getChildAt(1);title=(TextView)t.getChildAt(0);sub=(TextView)t.getChildAt(1);}}
    class PlaylistAdapter extends RecyclerView.Adapter<PlaylistVH>{
        ArrayList<Store.Playlist>data;PlaylistAdapter(ArrayList<Store.Playlist>d){data=new ArrayList<>(d);}
        @NonNull public PlaylistVH onCreateViewHolder(@NonNull ViewGroup p,int v){return new PlaylistVH(playlistRow());}
        public void onBindViewHolder(@NonNull PlaylistVH h,int pos){Store.Playlist p=data.get(pos);h.icon.setText("▶");h.title.setText(p.name);h.sub.setText(p.items.size()+" items");h.box.setOnClickListener(v->{Intent i=new Intent(OrganizeActivity.this,OrganizeActivity.class);i.putExtra("mode","playlist");i.putExtra("collection",collectionId);i.putExtra("playlist",p.id);startActivity(i);});}
        public int getItemCount(){return data.size();}
    }

    private LinearLayout playlistRow(){
        LinearLayout row=Ui.row(this);row.setPadding(Ui.dp(this,18),Ui.dp(this,9),Ui.dp(this,18),Ui.dp(this,9));
        TextView icon=Ui.text(this,"▶",20,Ui.ACCENT,true);icon.setGravity(Gravity.CENTER);icon.setBackground(Ui.round(Ui.SURFACE2,12,this));row.addView(icon,new LinearLayout.LayoutParams(Ui.dp(this,52),Ui.dp(this,52)));
        LinearLayout txt=new LinearLayout(this);txt.setOrientation(LinearLayout.VERTICAL);txt.setPadding(Ui.dp(this,12),0,0,0);txt.addView(Ui.text(this,"",15,Ui.TEXT,true));txt.addView(Ui.text(this,"",12,Ui.MUTED,false));row.addView(txt,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));return row;
    }

    class ItemVH extends RecyclerView.ViewHolder{LinearLayout outer,row;TextView section,icon,title,sub;ItemVH(View v){super(v);outer=(LinearLayout)v;section=(TextView)outer.getChildAt(0);row=(LinearLayout)outer.getChildAt(1);icon=(TextView)row.getChildAt(0);LinearLayout t=(LinearLayout)row.getChildAt(1);title=(TextView)t.getChildAt(0);sub=(TextView)t.getChildAt(1);}}
    class ItemAdapter extends RecyclerView.Adapter<ItemVH>{
        ArrayList<Store.Item>data;boolean queue;ItemAdapter(ArrayList<Store.Item>d,boolean q){data=new ArrayList<>();for(Store.Item it:d)data.add(new Store.Item(it.uri,it.section));queue=q;}
        @NonNull public ItemVH onCreateViewHolder(@NonNull ViewGroup p,int v){return new ItemVH(itemRow());}
        public void onBindViewHolder(@NonNull ItemVH h,int pos){
            Store.Item it=data.get(pos);MediaEntry e=MediaRepository.resolve(OrganizeActivity.this,it.uri);
            String prev=pos==0?"":data.get(pos-1).section;
            h.section.setVisibility(!queue&&!it.section.isEmpty()&&(pos==0||!it.section.equals(prev))?View.VISIBLE:View.GONE);h.section.setText(it.section);
            h.icon.setText(e!=null&&e.isVideo()?"▶":"♪");h.title.setText(e==null?"Missing media":e.title);h.sub.setText(e==null?it.uri:e.durationText()+"  ·  hold & drag");
            h.row.setOnClickListener(v->{if(queue)Store.setCurrentQueue(OrganizeActivity.this,queueId);else{Store.setCurrentQueue(OrganizeActivity.this,"current");ArrayList<String>u=new ArrayList<>();for(Store.Item x:data)u.add(x.uri);Store.replaceCurrentQueue(OrganizeActivity.this,u);}openPlayer(it.uri);});
            h.row.setOnLongClickListener(v->{itemMenu(it,queue);return true;});
        }
        public int getItemCount(){return data.size();}
    }

    private LinearLayout itemRow(){
        LinearLayout outer=new LinearLayout(this);outer.setOrientation(LinearLayout.VERTICAL);outer.setPadding(Ui.dp(this,18),0,Ui.dp(this,18),0);
        TextView sec=Ui.text(this,"",14,Ui.MUTED,true);sec.setPadding(0,Ui.dp(this,8),0,Ui.dp(this,4));outer.addView(sec);
        LinearLayout row=Ui.row(this);row.setPadding(Ui.dp(this,10),Ui.dp(this,9),Ui.dp(this,10),Ui.dp(this,9));row.setBackground(Ui.round(Ui.SURFACE,14,this));
        TextView icon=Ui.text(this,"♪",18,Ui.ACCENT,true);icon.setGravity(Gravity.CENTER);icon.setBackground(Ui.round(Ui.SURFACE2,10,this));row.addView(icon,new LinearLayout.LayoutParams(Ui.dp(this,46),Ui.dp(this,46)));
        LinearLayout txt=new LinearLayout(this);txt.setOrientation(LinearLayout.VERTICAL);txt.setPadding(Ui.dp(this,11),0,0,0);txt.addView(Ui.text(this,"",14,Ui.TEXT,true));txt.addView(Ui.text(this,"",12,Ui.MUTED,false));row.addView(txt,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));outer.addView(row);
        return outer;
    }
}
