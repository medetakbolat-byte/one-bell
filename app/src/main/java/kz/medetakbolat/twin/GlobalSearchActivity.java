package kz.medetakbolat.twin;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.concurrent.Executors;

public class GlobalSearchActivity extends AppCompatActivity {
    private final ArrayList<Result> results=new ArrayList<>();
    private ArrayList<MediaEntry> media=new ArrayList<>();
    private ResultAdapter adapter;

    static class Result{
        String type,title,sub,uri,collectionId,playlistId;
        MediaEntry media;
    }

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Ui.BG);
        LinearLayout top=Ui.row(this);top.setPadding(Ui.dp(this,8),Ui.dp(this,7),Ui.dp(this,12),Ui.dp(this,5));
        TextView back=Ui.icon(this,"‹");back.setTextSize(30);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
        EditText search=new EditText(this);search.setHint("Search media, playlists, collections");search.setHintTextColor(Ui.MUTED);search.setTextColor(Ui.TEXT);search.setTextSize(16);search.setSingleLine(true);search.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));top.addView(search,new LinearLayout.LayoutParams(0,Ui.dp(this,52),1));root.addView(top);

        RecyclerView rv=new RecyclerView(this);rv.setLayoutManager(new LinearLayoutManager(this));adapter=new ResultAdapter();rv.setAdapter(adapter);root.addView(rv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));setContentView(root);

        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){rebuild(s.toString());}public void afterTextChanged(Editable e){}});
        Executors.newSingleThreadExecutor().execute(()->{media=MediaRepository.scan(this);runOnUiThread(()->{rebuild("");search.requestFocus();});});
    }

    private void rebuild(String q){
        results.clear();String needle=q==null?"":q.trim().toLowerCase();
        if(needle.isEmpty()){adapter.notifyDataSetChanged();return;}
        for(MediaEntry e:media)if(e.matches(needle)){Result r=new Result();r.type="media";r.title=e.title;r.sub=(e.isVideo()?"Video":"Audio")+" · "+e.durationText();r.uri=e.uri;r.media=e;results.add(r);}
        for(Store.Playlist p:Store.allPlaylists(this))if(p.name.toLowerCase().contains(needle)){Result r=new Result();r.type="playlist";r.title=p.name;r.sub=Store.collectionNameForPlaylist(this,p.id)+" · "+p.items.size()+" items";r.collectionId=Store.collectionIdForPlaylist(this,p.id);r.playlistId=p.id;results.add(r);}
        for(Store.CollectionDef c:Store.visibleCollections(this))if(c.name.toLowerCase().contains(needle)){Result r=new Result();r.type="collection";r.title=c.name;r.sub=c.playlists.size()+" playlists";r.collectionId=c.id;results.add(r);}
        adapter.notifyDataSetChanged();
    }

    private void open(Result r){
        if("media".equals(r.type)){
            ArrayList<String> uris=new ArrayList<>();for(Result x:results)if("media".equals(x.type))uris.add(x.uri);if(!uris.contains(r.uri))uris.add(0,r.uri);
            Store.setPlaybackFallbackCover(this,"");Store.setCurrentQueue(this,"current");Store.replaceQueue(this,"current",uris);Store.addRecent(this,r.uri);
            Intent i=new Intent(this,PlayerActivity.class);i.putExtra("uri",r.uri);startActivity(i);
        }else{
            Intent i=new Intent(this,OrganizeActivity.class);i.putExtra("mode","collection".equals(r.type)?"collection":"playlist");i.putExtra("collection",r.collectionId);if(r.playlistId!=null)i.putExtra("playlist",r.playlistId);startActivity(i);
        }
    }

    class VH extends RecyclerView.ViewHolder{
        LinearLayout row;ImageView art;TextView title,sub;
        VH(View v){super(v);row=(LinearLayout)v;art=(ImageView)row.getChildAt(0);LinearLayout t=(LinearLayout)row.getChildAt(1);title=(TextView)t.getChildAt(0);sub=(TextView)t.getChildAt(1);}
    }
    class ResultAdapter extends RecyclerView.Adapter<VH>{
        @NonNull public VH onCreateViewHolder(@NonNull ViewGroup p,int v){
            LinearLayout row=Ui.row(GlobalSearchActivity.this);row.setPadding(Ui.dp(GlobalSearchActivity.this,16),Ui.dp(GlobalSearchActivity.this,8),Ui.dp(GlobalSearchActivity.this,12),Ui.dp(GlobalSearchActivity.this,8));row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
            ImageView iv=new ImageView(GlobalSearchActivity.this);iv.setScaleType(ImageView.ScaleType.CENTER_CROP);iv.setClipToOutline(true);iv.setBackground(Ui.round(Ui.SURFACE2,10,GlobalSearchActivity.this));row.addView(iv,new LinearLayout.LayoutParams(Ui.dp(GlobalSearchActivity.this,62),Ui.dp(GlobalSearchActivity.this,56)));
            LinearLayout t=new LinearLayout(GlobalSearchActivity.this);t.setOrientation(LinearLayout.VERTICAL);t.setPadding(Ui.dp(GlobalSearchActivity.this,12),0,0,0);TextView n=Ui.text(GlobalSearchActivity.this,"",15,Ui.TEXT,true);n.setMaxLines(2);t.addView(n);t.addView(Ui.text(GlobalSearchActivity.this,"",12,Ui.MUTED,false));row.addView(t,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));return new VH(row);
        }
        public void onBindViewHolder(@NonNull VH h,int pos){
            Result r=results.get(pos);h.title.setText(r.title);h.sub.setText(r.sub);
            if(r.media!=null)Thumb.load(GlobalSearchActivity.this,h.art,r.media);
            else if("playlist".equals(r.type)){Store.Playlist p=Store.findPlaylist(GlobalSearchActivity.this,r.playlistId);if(p!=null&&p.cover!=null&&!p.cover.isEmpty())try{h.art.setImageURI(android.net.Uri.parse(p.cover));}catch(Exception e){h.art.setImageResource(R.drawable.cover_placeholder);}else h.art.setImageResource(R.drawable.cover_placeholder);}
            else h.art.setImageResource(R.drawable.cover_placeholder);
            h.row.setOnClickListener(v->open(r));
        }
        public int getItemCount(){return results.size();}
    }
}
