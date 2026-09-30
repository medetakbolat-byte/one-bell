package kz.medetakbolat.twin;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.Executors;

public class MediaPickerActivity extends AppCompatActivity {
    private ArrayList<MediaEntry> all=new ArrayList<>(),shown=new ArrayList<>();
    private final HashSet<String> selected=new HashSet<>();
    private PickerAdapter adapter;
    private TextView add;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Ui.BG);
        LinearLayout top=Ui.row(this);top.setPadding(Ui.dp(this,8),Ui.dp(this,8),Ui.dp(this,12),Ui.dp(this,5));
        TextView back=Ui.icon(this,"‹");back.setTextSize(30);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
        TextView title=Ui.text(this,"Add media",20,Ui.TEXT,true);top.addView(title,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        add=Ui.text(this,"Add",14,Ui.MUTED,true);add.setGravity(Gravity.CENTER);add.setOnClickListener(v->finishWithSelection());top.addView(add,new LinearLayout.LayoutParams(Ui.dp(this,64),Ui.dp(this,48)));root.addView(top);

        EditText search=new EditText(this);search.setHint("Search");search.setHintTextColor(Ui.MUTED);search.setTextColor(Ui.TEXT);search.setSingleLine(true);search.setTextSize(15);search.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,48));sp.setMargins(Ui.dp(this,14),0,Ui.dp(this,14),Ui.dp(this,6));root.addView(search,sp);

        RecyclerView rv=new RecyclerView(this);rv.setLayoutManager(new GridLayoutManager(this,3));adapter=new PickerAdapter();rv.setAdapter(adapter);root.addView(rv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));setContentView(root);

        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){filter(s.toString());}public void afterTextChanged(Editable e){}});
        Executors.newSingleThreadExecutor().execute(()->{all=MediaRepository.scan(this);runOnUiThread(()->{shown=new ArrayList<>(all);adapter.notifyDataSetChanged();});});
    }

    private void filter(String q){
        shown.clear();String n=q==null?"":q.trim().toLowerCase();for(MediaEntry e:all)if(e.matches(n))shown.add(e);adapter.notifyDataSetChanged();
    }
    private void toggle(MediaEntry e){if(selected.contains(e.uri))selected.remove(e.uri);else selected.add(e.uri);add.setText(selected.isEmpty()?"Add":"Add "+selected.size());add.setTextColor(selected.isEmpty()?Ui.MUTED:Ui.ACCENT);adapter.notifyDataSetChanged();}
    private void finishWithSelection(){if(selected.isEmpty())return;Intent data=new Intent();data.putStringArrayListExtra("uris",new ArrayList<>(selected));setResult(RESULT_OK,data);finish();}

    class VH extends RecyclerView.ViewHolder{FrameLayout frame;ImageView art;TextView check,title;VH(View v){super(v);LinearLayout outer=(LinearLayout)v;frame=(FrameLayout)outer.getChildAt(0);art=(ImageView)frame.getChildAt(0);check=(TextView)frame.getChildAt(1);title=(TextView)outer.getChildAt(1);}}
    class PickerAdapter extends RecyclerView.Adapter<VH>{
        @NonNull public VH onCreateViewHolder(@NonNull ViewGroup p,int v){
            LinearLayout outer=new LinearLayout(MediaPickerActivity.this);outer.setOrientation(LinearLayout.VERTICAL);outer.setPadding(Ui.dp(MediaPickerActivity.this,3),Ui.dp(MediaPickerActivity.this,3),Ui.dp(MediaPickerActivity.this,3),Ui.dp(MediaPickerActivity.this,9));
            FrameLayout frame=new FrameLayout(MediaPickerActivity.this);ImageView iv=new ImageView(MediaPickerActivity.this);iv.setScaleType(ImageView.ScaleType.CENTER_CROP);frame.addView(iv,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(MediaPickerActivity.this,108)));
            TextView check=Ui.text(MediaPickerActivity.this,"✓",20,Ui.TEXT,true);check.setGravity(Gravity.CENTER);check.setBackground(Ui.round(android.graphics.Color.argb(190,45,150,225),18,MediaPickerActivity.this));FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(Ui.dp(MediaPickerActivity.this,34),Ui.dp(MediaPickerActivity.this,34),Gravity.TOP|Gravity.RIGHT);cp.setMargins(0,Ui.dp(MediaPickerActivity.this,6),Ui.dp(MediaPickerActivity.this,6),0);frame.addView(check,cp);outer.addView(frame,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(MediaPickerActivity.this,108)));
            TextView title=Ui.text(MediaPickerActivity.this,"",11,Ui.TEXT,true);title.setMaxLines(1);title.setPadding(0,Ui.dp(MediaPickerActivity.this,5),0,0);outer.addView(title);return new VH(outer);
        }
        public void onBindViewHolder(@NonNull VH h,int pos){MediaEntry e=shown.get(pos);Thumb.load(MediaPickerActivity.this,h.art,e);h.title.setText(e.title);h.check.setVisibility(selected.contains(e.uri)?View.VISIBLE:View.GONE);h.itemView.setOnClickListener(v->toggle(e));}
        public int getItemCount(){return shown.size();}
    }
}
