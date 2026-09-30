package kz.medetakbolat.twin;

import android.Manifest;
import android.app.AlertDialog;
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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_STORAGE=44;
    private FrameLayout content;
    private TextView navHome, navLibrary, navQueues;
    private ArrayList<MediaEntry> allMedia=new ArrayList<>();
    private String libraryFilter="all";
    private String folderFilter="";
    private String searchText="";
    private String sortMode="new";

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        Store.ensureDefaults(this);
        buildShell();
        if(getIntent()!=null && Intent.ACTION_VIEW.equals(getIntent().getAction()) && getIntent().getData()!=null){
            Uri u=getIntent().getData();
            try{ getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION); }catch(Exception ignored){}
            openSingle(u.toString());
            return;
        }
        if(needsPermission()) requestStorage();
        else showHome();
    }

    @Override protected void onResume(){
        super.onResume();
        if(content!=null && !needsPermission()) refreshMedia(null);
    }

    private boolean needsPermission(){
        return android.os.Build.VERSION.SDK_INT>=23 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED;
    }

    private void requestStorage(){
        ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},REQ_STORAGE);
        showPermissionHint();
    }

    @Override public void onRequestPermissionsResult(int requestCode,@NonNull String[] permissions,@NonNull int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        showHome();
    }

    private void showPermissionHint(){
        content.removeAllViews();
        LinearLayout box=Ui.card(this);
        Ui.margins(box,18,24,18,0);
        box.addView(Ui.text(this,"Your media stays on your phone",20,Ui.TEXT,true));
        TextView t=Ui.text(this,"Twin needs media access only to show and play your local audio and video. You can still open individual files without it.",14,Ui.MUTED,false);
        t.setPadding(0,Ui.dp(this,8),0,Ui.dp(this,12)); box.addView(t);
        TextView b=Ui.button(this,"Allow media access"); b.setOnClickListener(v->requestStorage()); box.addView(b);
        content.addView(box);
    }

    private void buildShell(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Ui.BG);
        content=new FrameLayout(this);
        root.addView(content,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        LinearLayout nav=Ui.row(this); nav.setPadding(Ui.dp(this,12),Ui.dp(this,7),Ui.dp(this,12),Ui.dp(this,9)); nav.setBackgroundColor(Ui.SURFACE);
        navHome=navItem("⌂\nHome"); navLibrary=navItem("▣\nLibrary"); navQueues=navItem("☷\nQueues");
        nav.addView(navHome,new LinearLayout.LayoutParams(0,Ui.dp(this,58),1));
        nav.addView(navLibrary,new LinearLayout.LayoutParams(0,Ui.dp(this,58),1));
        nav.addView(navQueues,new LinearLayout.LayoutParams(0,Ui.dp(this,58),1));
        navHome.setOnClickListener(v->showHome());
        navLibrary.setOnClickListener(v->showLibrary("all",""));
        navQueues.setOnClickListener(v->showQueues());
        root.addView(nav);
        setContentView(root);
    }

    private TextView navItem(String s){
        TextView t=Ui.text(this,s,12,Ui.MUTED,true); t.setGravity(Gravity.CENTER); t.setClickable(true); return t;
    }

    private void selectNav(int n){
        navHome.setTextColor(n==0?Ui.ACCENT:Ui.MUTED);
        navLibrary.setTextColor(n==1?Ui.ACCENT:Ui.MUTED);
        navQueues.setTextColor(n==2?Ui.ACCENT:Ui.MUTED);
    }

    private void refreshMedia(Runnable after){
        Executors.newSingleThreadExecutor().execute(()->{
            ArrayList<MediaEntry> m=needsPermission()?new ArrayList<>():MediaRepository.scan(this);
            runOnUiThread(()->{ allMedia=m; if(after!=null)after.run(); });
        });
    }

    private void showHome(){
        selectNav(0);
        refreshMedia(this::renderHome);
    }

    private void renderHome(){
        ScrollView sv=new ScrollView(this);
        LinearLayout page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(Ui.dp(this,18),Ui.dp(this,12),Ui.dp(this,18),Ui.dp(this,24));
        sv.addView(page);

        LinearLayout top=Ui.row(this);
        TextView title=Ui.text(this,"Twin",28,Ui.TEXT,true);
        top.addView(title,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        TextView search=Ui.button(this,"⌕"); search.setOnClickListener(v->askSearch()); top.addView(search,new LinearLayout.LayoutParams(Ui.dp(this,52),Ui.dp(this,44)));
        page.addView(top);

        page.addView(Ui.section(this,"Continue"));
        MediaEntry cont=findContinue();
        if(cont!=null){
            long[] pr=Store.progress(this,cont.uri);
            LinearLayout card=mediaCard(cont,Ui.time(pr[0])+" / "+Ui.time(pr[1]>0?pr[1]:cont.duration));
            card.setOnClickListener(v->openSingle(cont.uri));
            page.addView(card);
        }else{
            LinearLayout empty=Ui.card(this);
            empty.addView(Ui.text(this,"Nothing unfinished yet",15,Ui.MUTED,false));
            page.addView(empty);
        }

        LinearLayout ch=Ui.row(this); TextView ct=Ui.section(this,"Collections"); ch.addView(ct,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        TextView plus=Ui.button(this,"＋"); plus.setOnClickListener(v->createCollection()); ch.addView(plus,new LinearLayout.LayoutParams(Ui.dp(this,52),Ui.dp(this,42))); page.addView(ch);

        HorizontalScrollView hsv=new HorizontalScrollView(this); hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout strip=Ui.row(this);
        for(Store.CollectionDef c:Store.getCollections(this)){
            LinearLayout card=collectionCard(c); card.setOnClickListener(v->openCollection(c.id));
            strip.addView(card,new LinearLayout.LayoutParams(Ui.dp(this,148),Ui.dp(this,120)));
            Ui.margins(card,0,0,10,0);
        }
        hsv.addView(strip); page.addView(hsv);

        page.addView(Ui.section(this,"Recent"));
        ArrayList<String> recent=Store.getRecent(this);
        int shown=0;
        for(String u:recent){
            MediaEntry e=find(u); if(e==null)e=MediaRepository.resolve(this,u); if(e==null)continue;
            final MediaEntry item=e;
            LinearLayout row=compactRow(item);
            row.setOnClickListener(v->openSingle(item.uri));
            row.setOnLongClickListener(v->{mediaMenu(item); return true;});
            page.addView(row); Ui.margins(row,0,0,0,7);
            if(++shown>=6)break;
        }
        if(shown==0)page.addView(Ui.text(this,"Your recently opened media will appear here.",14,Ui.MUTED,false));

        content.removeAllViews(); content.addView(sv);
    }

    private MediaEntry findContinue(){
        for(String u:Store.getRecent(this)){
            MediaEntry e=find(u); if(e==null)e=MediaRepository.resolve(this,u);
            long[] p=Store.progress(this,u); long d=p[1]>0?p[1]:(e==null?0:e.duration);
            if(e!=null && p[0]>5000 && (d<=0 || p[0]<d-8000)) return e;
        }
        return null;
    }

    private LinearLayout collectionCard(Store.CollectionDef c){
        LinearLayout card=Ui.card(this);
        TextView cover=Ui.text(this,"▶",32,Ui.ACCENT,true); cover.setGravity(Gravity.CENTER); cover.setBackgroundResource(com.google.android.material.R.drawable.mtrl_popupmenu_background);
        cover.setBackground(Ui.round(Ui.SURFACE2,14,this));
        if(c.cover!=null&&!c.cover.isEmpty()){
            android.widget.ImageView iv=new android.widget.ImageView(this); iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            try{iv.setImageURI(Uri.parse(c.cover));}catch(Exception ignored){}
            card.addView(iv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,66)));
        }else card.addView(cover,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,66)));
        TextView name=Ui.text(this,c.name,15,Ui.TEXT,true); name.setPadding(0,Ui.dp(this,7),0,0); card.addView(name);
        card.addView(Ui.text(this,c.playlists.size()+" playlists",12,Ui.MUTED,false));
        return card;
    }

    private LinearLayout mediaCard(MediaEntry e,String sub){
        LinearLayout card=Ui.card(this);
        LinearLayout row=Ui.row(this);
        TextView art=Ui.text(this,e.isVideo()?"▶":"♪",26,Ui.ACCENT,true); art.setGravity(Gravity.CENTER); art.setBackground(Ui.round(Ui.SURFACE2,14,this));
        row.addView(art,new LinearLayout.LayoutParams(Ui.dp(this,68),Ui.dp(this,68)));
        LinearLayout txt=new LinearLayout(this); txt.setOrientation(LinearLayout.VERTICAL); txt.setPadding(Ui.dp(this,12),0,0,0);
        txt.addView(Ui.text(this,e.title,16,Ui.TEXT,true)); txt.addView(Ui.text(this,sub,13,Ui.MUTED,false));
        row.addView(txt,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        TextView p=Ui.button(this,"▶"); row.addView(p,new LinearLayout.LayoutParams(Ui.dp(this,50),Ui.dp(this,46)));
        card.addView(row); return card;
    }

    private LinearLayout compactRow(MediaEntry e){
        LinearLayout row=Ui.row(this); row.setPadding(Ui.dp(this,10),Ui.dp(this,10),Ui.dp(this,8),Ui.dp(this,10)); row.setBackground(Ui.round(Ui.SURFACE,14,this));
        TextView art=Ui.text(this,e.isVideo()?"▶":"♪",20,Ui.ACCENT,true); art.setGravity(Gravity.CENTER); art.setBackground(Ui.round(Ui.SURFACE2,10,this));
        row.addView(art,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
        LinearLayout txt=new LinearLayout(this); txt.setOrientation(LinearLayout.VERTICAL); txt.setPadding(Ui.dp(this,11),0,0,0);
        txt.addView(Ui.text(this,e.title,15,Ui.TEXT,true));
        long[] pr=Store.progress(this,e.uri);
        String meta=(e.isVideo()?"Video":"Audio")+" · "+e.durationText();
        if(pr[0]>0)meta=Ui.time(pr[0])+" / "+Ui.time(pr[1]>0?pr[1]:e.duration);
        txt.addView(Ui.text(this,meta,12,Ui.MUTED,false));
        row.addView(txt,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        return row;
    }

    private void showLibrary(String filter,String folder){
        selectNav(1); libraryFilter=filter; folderFilter=folder;
        refreshMedia(this::renderLibrary);
    }

    private void renderLibrary(){
        LinearLayout page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(Ui.dp(this,16),Ui.dp(this,10),Ui.dp(this,16),0);
        LinearLayout top=Ui.row(this);
        top.addView(Ui.text(this,"Library",27,Ui.TEXT,true),new LinearLayout.LayoutParams(0,Ui.dp(this,50),1));
        TextView search=Ui.button(this,"⌕"); search.setOnClickListener(v->askSearch()); top.addView(search,new LinearLayout.LayoutParams(Ui.dp(this,50),Ui.dp(this,44)));
        TextView sort=Ui.button(this,"⇅"); sort.setOnClickListener(v->sortDialog()); top.addView(sort,new LinearLayout.LayoutParams(Ui.dp(this,50),Ui.dp(this,44)));
        page.addView(top);

        LinearLayout chips=Ui.row(this);
        for(String[] x:new String[][]{{"all","All"},{"audio","Audio"},{"video","Video"},{"folders","Folders"}}){
            TextView b=Ui.button(this,x[1]); if(libraryFilter.equals(x[0])) b.setTextColor(Ui.ACCENT);
            b.setOnClickListener(v->{libraryFilter=x[0]; folderFilter=""; renderLibrary();});
            chips.addView(b,new LinearLayout.LayoutParams(0,Ui.dp(this,42),1)); Ui.margins(b,2,0,2,8);
        }
        page.addView(chips);

        if(!folderFilter.isEmpty()){
            TextView back=Ui.button(this,"‹  "+folderFilter); back.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
            back.setOnClickListener(v->{libraryFilter="folders"; folderFilter=""; renderLibrary();}); page.addView(back);
        }

        RecyclerView rv=new RecyclerView(this); rv.setLayoutManager(new LinearLayoutManager(this));
        if("folders".equals(libraryFilter)&&folderFilter.isEmpty()) rv.setAdapter(new FolderAdapter(folderNames()));
        else rv.setAdapter(new MediaAdapter(filteredMedia()));
        page.addView(rv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        content.removeAllViews(); content.addView(page);
    }

    private ArrayList<String> folderNames(){
        Set<String>s=new HashSet<>(); for(MediaEntry e:allMedia)s.add(e.folder);
        ArrayList<String> out=new ArrayList<>(s); Collections.sort(out,String.CASE_INSENSITIVE_ORDER); return out;
    }

    private ArrayList<MediaEntry> filteredMedia(){
        ArrayList<MediaEntry> out=new ArrayList<>();
        for(MediaEntry e:allMedia){
            if("audio".equals(libraryFilter)&&e.isVideo())continue;
            if("video".equals(libraryFilter)&&!e.isVideo())continue;
            if(!folderFilter.isEmpty()&&!folderFilter.equals(e.folder))continue;
            if(!searchText.isEmpty()&&!e.title.toLowerCase().contains(searchText.toLowerCase()))continue;
            out.add(e);
        }
        if("name".equals(sortMode)) out.sort((a,b)->a.title.compareToIgnoreCase(b.title));
        else if("duration".equals(sortMode)) out.sort((a,b)->Long.compare(b.duration,a.duration));
        else if("played".equals(sortMode)) out.sort((a,b)->Long.compare(Store.progress(this,b.uri)[2],Store.progress(this,a.uri)[2]));
        else out.sort((a,b)->Long.compare(b.addedAt,a.addedAt));
        return out;
    }

    private void sortDialog(){
        String[] labels={"Recently added","Name","Duration","Recently played"};
        String[] vals={"new","name","duration","played"};
        int checked=0; for(int i=0;i<vals.length;i++)if(vals[i].equals(sortMode))checked=i;
        new AlertDialog.Builder(this).setTitle("Sort by").setSingleChoiceItems(labels,checked,(d,w)->{sortMode=vals[w];d.dismiss();renderLibrary();}).show();
    }

    private void askSearch(){
        EditText e=new EditText(this); e.setHint("Search media…"); e.setText(searchText); e.setTextColor(Ui.TEXT); e.setHintTextColor(Ui.MUTED);
        new AlertDialog.Builder(this).setTitle("Search").setView(e)
                .setPositiveButton("Search",(d,w)->{searchText=e.getText().toString().trim();showLibrary("all","");})
                .setNeutralButton("Clear",(d,w)->{searchText="";showLibrary("all","");}).show();
    }

    private void showQueues(){
        selectNav(2);
        ScrollView sv=new ScrollView(this); LinearLayout page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(Ui.dp(this,18),Ui.dp(this,12),Ui.dp(this,18),Ui.dp(this,24)); sv.addView(page);
        LinearLayout top=Ui.row(this); top.addView(Ui.text(this,"Queues",27,Ui.TEXT,true),new LinearLayout.LayoutParams(0,Ui.dp(this,52),1));
        TextView add=Ui.button(this,"＋"); add.setOnClickListener(v->createQueue()); top.addView(add,new LinearLayout.LayoutParams(Ui.dp(this,52),Ui.dp(this,44))); page.addView(top);
        String active=Store.currentQueueId(this);
        for(Store.QueueDef q:Store.getQueues(this)){
            LinearLayout card=Ui.card(this);
            TextView n=Ui.text(this,(q.id.equals(active)?"●  ":"")+q.name,17,q.id.equals(active)?Ui.ACCENT:Ui.TEXT,true); card.addView(n);
            card.addView(Ui.text(this,q.items.size()+" items",13,Ui.MUTED,false));
            card.setOnClickListener(v->openQueue(q.id));
            card.setOnLongClickListener(v->{queueMenu(q);return true;});
            page.addView(card); Ui.margins(card,0,0,0,10);
        }
        content.removeAllViews(); content.addView(sv);
    }

    private void createQueue(){
        EditText e=new EditText(this); e.setHint("Queue name");
        new AlertDialog.Builder(this).setTitle("New queue").setView(e).setPositiveButton("Create",(d,w)->{
            String n=e.getText().toString().trim(); if(n.isEmpty())n="Queue";
            ArrayList<Store.QueueDef> qs=Store.getQueues(this); Store.QueueDef q=new Store.QueueDef();q.name=n;qs.add(q);Store.saveQueues(this,qs);showQueues();
        }).setNegativeButton("Cancel",null).show();
    }

    private void queueMenu(Store.QueueDef q){
        String[] a=q.id.equals("current")?new String[]{"Make active","Clear"}:new String[]{"Make active","Rename","Clear","Delete"};
        new AlertDialog.Builder(this).setTitle(q.name).setItems(a,(d,w)->{
            String x=a[w];
            if("Make active".equals(x)){Store.setCurrentQueue(this,q.id);showQueues();}
            else if("Clear".equals(x)){ArrayList<Store.QueueDef>qs=Store.getQueues(this);for(Store.QueueDef z:qs)if(z.id.equals(q.id))z.items.clear();Store.saveQueues(this,qs);showQueues();}
            else if("Delete".equals(x)){ArrayList<Store.QueueDef>qs=Store.getQueues(this);qs.removeIf(z->z.id.equals(q.id));Store.saveQueues(this,qs);showQueues();}
            else if("Rename".equals(x))renameQueue(q);
        }).show();
    }

    private void renameQueue(Store.QueueDef q){
        EditText e=new EditText(this);e.setText(q.name);
        new AlertDialog.Builder(this).setTitle("Rename queue").setView(e).setPositiveButton("Save",(d,w)->{
            ArrayList<Store.QueueDef>qs=Store.getQueues(this);for(Store.QueueDef z:qs)if(z.id.equals(q.id))z.name=e.getText().toString().trim();Store.saveQueues(this,qs);showQueues();
        }).show();
    }

    private void createCollection(){
        EditText e=new EditText(this);e.setHint("Collection name");
        new AlertDialog.Builder(this).setTitle("New collection").setView(e).setPositiveButton("Create",(d,w)->{
            String n=e.getText().toString().trim();if(n.isEmpty())n="Collection";
            ArrayList<Store.CollectionDef>cs=Store.getCollections(this);Store.CollectionDef c=new Store.CollectionDef();c.name=n;cs.add(c);Store.saveCollections(this,cs);renderHome();
        }).show();
    }

    private void openCollection(String id){
        Intent i=new Intent(this,OrganizeActivity.class);i.putExtra("mode","collection");i.putExtra("collection",id);startActivity(i);
    }

    private void openQueue(String id){
        Intent i=new Intent(this,OrganizeActivity.class);i.putExtra("mode","queue");i.putExtra("queue",id);startActivity(i);
    }

    private void openSingle(String uri){
        Store.replaceCurrentQueue(this,Collections.singletonList(uri)); Store.setCurrentQueue(this,"current"); Store.addRecent(this,uri);
        Intent i=new Intent(this,PlayerActivity.class); i.putExtra("uri",uri); startActivity(i);
    }

    private MediaEntry find(String uri){for(MediaEntry e:allMedia)if(e.uri.equals(uri))return e;return null;}

    private void mediaMenu(MediaEntry e){
        String[] items={"Play","Play next","Add to queue","Add to playlist","Details"};
        new AlertDialog.Builder(this).setTitle(e.title).setItems(items,(d,w)->{
            if(w==0)openSingle(e.uri);
            else if(w==1){Store.addToQueue(this,Store.currentQueueId(this),e.uri,true);Toast.makeText(this,"Playing next",Toast.LENGTH_SHORT).show();}
            else if(w==2)addToQueueDialog(e.uri);
            else if(w==3)addToPlaylistDialog(e.uri);
            else new AlertDialog.Builder(this).setTitle(e.title).setMessage((e.isVideo()?"Video":"Audio")+"\n"+e.durationText()+"\n"+e.folder+"\n\n"+e.uri).setPositiveButton("OK",null).show();
        }).show();
    }

    private void addToQueueDialog(String uri){
        ArrayList<Store.QueueDef>qs=Store.getQueues(this);String[] names=new String[qs.size()];for(int i=0;i<qs.size();i++)names[i]=qs.get(i).name;
        new AlertDialog.Builder(this).setTitle("Add to queue").setItems(names,(d,w)->{Store.addToQueue(this,qs.get(w).id,uri,false);Toast.makeText(this,"Added to "+names[w],Toast.LENGTH_SHORT).show();}).show();
    }

    private void addToPlaylistDialog(String uri){
        ArrayList<Store.CollectionDef>cs=Store.getCollections(this);ArrayList<String> labels=new ArrayList<>(), ids=new ArrayList<>(), cids=new ArrayList<>();
        for(Store.CollectionDef c:cs)for(Store.Playlist p:c.playlists){labels.add(c.name+" · "+p.name);ids.add(p.id);cids.add(c.id);}
        new AlertDialog.Builder(this).setTitle("Add to playlist").setItems(labels.toArray(new String[0]),(d,w)->{
            ArrayList<Store.CollectionDef>fresh=Store.getCollections(this);
            for(Store.CollectionDef c:fresh)if(c.id.equals(cids.get(w)))for(Store.Playlist p:c.playlists)if(p.id.equals(ids.get(w))){
                boolean exists=false;for(Store.Item it:p.items)if(it.uri.equals(uri))exists=true;if(!exists)p.items.add(new Store.Item(uri));
            }
            Store.saveCollections(this,fresh);Toast.makeText(this,"Added to "+labels.get(w),Toast.LENGTH_SHORT).show();
        }).show();
    }

    class MediaVH extends RecyclerView.ViewHolder{
        LinearLayout box; TextView title,sub,icon;
        MediaVH(View v){super(v);box=(LinearLayout)v;icon=(TextView)box.getChildAt(0);LinearLayout t=(LinearLayout)box.getChildAt(1);title=(TextView)t.getChildAt(0);sub=(TextView)t.getChildAt(1);}
    }

    class MediaAdapter extends RecyclerView.Adapter<MediaVH>{
        final ArrayList<MediaEntry> data;
        MediaAdapter(ArrayList<MediaEntry>d){data=d;}
        @NonNull public MediaVH onCreateViewHolder(@NonNull ViewGroup p,int v){return new MediaVH(compactRow(new MediaEntry("","",0,0,"audio","")));}
        public void onBindViewHolder(@NonNull MediaVH h,int pos){
            MediaEntry e=data.get(pos);h.icon.setText(e.isVideo()?"▶":"♪");h.title.setText(e.title);
            long[]pr=Store.progress(MainActivity.this,e.uri);h.sub.setText(pr[0]>0?Ui.time(pr[0])+" / "+Ui.time(pr[1]>0?pr[1]:e.duration):(e.isVideo()?"Video":"Audio")+" · "+e.durationText());
            h.box.setOnClickListener(v->openSingle(e.uri));h.box.setOnLongClickListener(v->{mediaMenu(e);return true;});
            RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.setMargins(0,0,0,Ui.dp(MainActivity.this,7));h.box.setLayoutParams(lp);
        }
        public int getItemCount(){return data.size();}
    }

    class FolderAdapter extends RecyclerView.Adapter<MediaVH>{
        final ArrayList<String>data;FolderAdapter(ArrayList<String>d){data=d;}
        @NonNull public MediaVH onCreateViewHolder(@NonNull ViewGroup p,int v){return new MediaVH(compactRow(new MediaEntry("","",0,0,"audio","")));}
        public void onBindViewHolder(@NonNull MediaVH h,int pos){
            String f=data.get(pos);int count=0;for(MediaEntry e:allMedia)if(f.equals(e.folder))count++;
            h.icon.setText("▣");h.title.setText(f);h.sub.setText(count+" media");h.box.setOnClickListener(v->{libraryFilter="all";folderFilter=f;renderLibrary();});
            RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.setMargins(0,0,0,Ui.dp(MainActivity.this,7));h.box.setLayoutParams(lp);
        }
        public int getItemCount(){return data.size();}
    }
}
