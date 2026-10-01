package kz.medetakbolat.twin;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
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
import java.util.HashSet;
import java.util.concurrent.Executors;

public class MediaPickerActivity extends AppCompatActivity {
    private ArrayList<MediaEntry> all=new ArrayList<>(),shown=new ArrayList<>();
    private final HashSet<String> selected=new HashSet<>(),excluded=new HashSet<>();
    private PickerAdapter adapter;
    private TextView add;
    private String kind="all";

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        ArrayList<String> ex=getIntent().getStringArrayListExtra("exclude");if(ex!=null)excluded.addAll(ex);
        String k=getIntent().getStringExtra("kind");if(k!=null)kind=k;

        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Ui.BG);
        LinearLayout top=Ui.row(this);top.setPadding(Ui.dp(this,8),Ui.dp(this,7),Ui.dp(this,12),Ui.dp(this,4));
        TextView back=Ui.icon(this,"‹");back.setTextSize(30);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
        String titleText="audio".equals(kind)?"Add audio":("video".equals(kind)?"Add video":"Add media");
        TextView title=Ui.text(this,titleText,20,Ui.TEXT,true);top.addView(title,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        add=Ui.text(this,"Add",14,Ui.MUTED,true);add.setGravity(Gravity.CENTER);add.setOnClickListener(v->finishWithSelection());top.addView(add,new LinearLayout.LayoutParams(Ui.dp(this,76),Ui.dp(this,48)));root.addView(top);

        EditText search=new EditText(this);search.setHint("Search library");search.setHintTextColor(Ui.MUTED);search.setTextColor(Ui.TEXT);search.setSingleLine(true);search.setTextSize(15);search.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,48));sp.setMargins(Ui.dp(this,14),0,Ui.dp(this,14),Ui.dp(this,6));root.addView(search,sp);

        TextView note=Ui.text(this,excluded.isEmpty()?"":"Already-added media is hidden",12,Ui.MUTED,false);note.setPadding(Ui.dp(this,14),0,0,Ui.dp(this,4));if(!excluded.isEmpty())root.addView(note);

        RecyclerView rv=new RecyclerView(this);rv.setLayoutManager(new LinearLayoutManager(this));adapter=new PickerAdapter();rv.setAdapter(adapter);root.addView(rv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));setContentView(root);

        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){filter(s.toString());}public void afterTextChanged(Editable e){}});
        Executors.newSingleThreadExecutor().execute(()->{
            ArrayList<MediaEntry> scan=MediaRepository.scan(this);
            ArrayList<MediaEntry> clean=new ArrayList<>();
            for(MediaEntry e:scan){
                if(excluded.contains(e.uri))continue;
                if("audio".equals(kind)&&e.isVideo())continue;
                if("video".equals(kind)&&!e.isVideo())continue;
                clean.add(e);
            }
            clean.sort(Natural.MEDIA_ASC);
            all=clean;runOnUiThread(()->{shown=new ArrayList<>(all);adapter.notifyDataSetChanged();});
        });
    }

    private void filter(String q){
        shown.clear();String n=q==null?"":q.trim().toLowerCase();for(MediaEntry e:all)if(e.matches(n))shown.add(e);adapter.notifyDataSetChanged();
    }
    private void toggle(MediaEntry e){if(selected.contains(e.uri))selected.remove(e.uri);else selected.add(e.uri);add.setText(selected.isEmpty()?"Add":"Add "+selected.size());add.setTextColor(selected.isEmpty()?Ui.MUTED:Ui.ACCENT);adapter.notifyDataSetChanged();}
    private void finishWithSelection(){if(selected.isEmpty())return;Intent data=new Intent();data.putStringArrayListExtra("uris",new ArrayList<>(selected));setResult(RESULT_OK,data);finish();}

    class VH extends RecyclerView.ViewHolder{LinearLayout row;ImageView art;TextView title,sub,check;VH(View v){super(v);row=(LinearLayout)v;art=(ImageView)row.getChildAt(0);LinearLayout t=(LinearLayout)row.getChildAt(1);title=(TextView)t.getChildAt(0);sub=(TextView)t.getChildAt(1);check=(TextView)row.getChildAt(2);}}
    class PickerAdapter extends RecyclerView.Adapter<VH>{
        @NonNull public VH onCreateViewHolder(@NonNull ViewGroup p,int v){
            LinearLayout row=Ui.row(MediaPickerActivity.this);row.setPadding(Ui.dp(MediaPickerActivity.this,14),Ui.dp(MediaPickerActivity.this,7),Ui.dp(MediaPickerActivity.this,12),Ui.dp(MediaPickerActivity.this,7));row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
            ImageView iv=new ImageView(MediaPickerActivity.this);iv.setScaleType(ImageView.ScaleType.CENTER_CROP);iv.setClipToOutline(true);iv.setBackground(Ui.round(Ui.SURFACE2,10,MediaPickerActivity.this));row.addView(iv,new LinearLayout.LayoutParams(Ui.dp(MediaPickerActivity.this,60),Ui.dp(MediaPickerActivity.this,60)));
            LinearLayout text=new LinearLayout(MediaPickerActivity.this);text.setOrientation(LinearLayout.VERTICAL);text.setPadding(Ui.dp(MediaPickerActivity.this,12),0,Ui.dp(MediaPickerActivity.this,8),0);TextView n=Ui.text(MediaPickerActivity.this,"",14,Ui.TEXT,true);n.setMaxLines(3);text.addView(n);text.addView(Ui.text(MediaPickerActivity.this,"",12,Ui.MUTED,false));row.addView(text,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            TextView check=Ui.text(MediaPickerActivity.this,"✓",18,Ui.TEXT,true);check.setGravity(Gravity.CENTER);check.setBackground(Ui.round(Ui.ACCENT,18,MediaPickerActivity.this));row.addView(check,new LinearLayout.LayoutParams(Ui.dp(MediaPickerActivity.this,34),Ui.dp(MediaPickerActivity.this,34)));return new VH(row);
        }
        public void onBindViewHolder(@NonNull VH h,int pos){MediaEntry e=shown.get(pos);Thumb.load(MediaPickerActivity.this,h.art,e);h.title.setText(e.title);h.sub.setText((e.isVideo()?"Video":"Audio")+" · "+e.durationText());h.check.setVisibility(selected.contains(e.uri)?View.VISIBLE:View.INVISIBLE);h.row.setBackground(selected.contains(e.uri)?Ui.round(android.graphics.Color.argb(45,105,190,239),12,MediaPickerActivity.this):null);h.itemView.setOnClickListener(v->toggle(e));}
        public int getItemCount(){return shown.size();}
    }
}
