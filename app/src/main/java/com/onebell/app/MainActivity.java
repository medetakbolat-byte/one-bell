package com.onebell.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int BG=Color.rgb(9,10,16);
    private static final int CARD=Color.rgb(15,17,27);
    private static final int TEXT=Color.rgb(239,238,247);
    private static final int MUTED=Color.rgb(137,136,154);
    private static final int VIOLET=Color.rgb(142,124,255);
    private static final int VSOFT=Color.rgb(81,69,161);

    private LinearLayout list;
    private TextView clock,date;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        setVolumeControlStream(AudioManager.STREAM_ALARM);
        setContentView(build());
    }

    @Override protected void onResume(){
        super.onResume();
        refresh();
        if(AlarmScheduler.canExact(this))
            for(AlarmItem a:AlarmStore.load(this))
                if(a.time>System.currentTimeMillis()) AlarmScheduler.schedule(this,a);
    }

    private View build(){
        FrameLayout root=new FrameLayout(this); root.setBackgroundColor(BG);
        LinearLayout col=new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(22),dp(20),dp(22),dp(18));
        root.addView(col,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=t("One Bell",29,TEXT);
        title.setLetterSpacing(.08f);
        top.addView(title,new LinearLayout.LayoutParams(0,dp(52),1));

        TextView gear=t("⚙",24,Color.rgb(184,180,207));
        gear.setGravity(Gravity.CENTER);
        gear.setOnClickListener(v->settings());
        top.addView(gear,new LinearLayout.LayoutParams(dp(52),dp(52)));
        col.addView(top);

        View line=new View(this); line.setBackgroundColor(VIOLET);
        LinearLayout.LayoutParams lpLine=new LinearLayout.LayoutParams(dp(22),dp(1));
        lpLine.setMargins(0,0,0,dp(15)); col.addView(line,lpLine);

        clock=t("--:--",25,TEXT); clock.setLetterSpacing(.12f); col.addView(clock);
        date=t("",14,MUTED);
        LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(-2,-2);
        dlp.setMargins(0,dp(4),0,dp(22)); col.addView(date,dlp);

        ScrollView scroll=new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list,new ScrollView.LayoutParams(-1,-2));
        col.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        Button add=button("＋  Add Alarm",true);
        add.setOnClickListener(v->{ if(permission()) picker(null); });
        LinearLayout.LayoutParams alp=new LinearLayout.LayoutParams(-1,dp(58));
        alp.setMargins(0,dp(10),0,0); col.addView(add,alp);
        return root;
    }

    private void refresh(){
        Date n=new Date();
        clock.setText(new SimpleDateFormat("HH:mm",Locale.getDefault()).format(n));
        date.setText(new SimpleDateFormat("EEE, d MMM",Locale.getDefault()).format(n));
        AlarmStore.removeExpired(this,System.currentTimeMillis());
        list.removeAllViews();
        List<AlarmItem> alarms=AlarmStore.load(this);

        if(alarms.isEmpty()){
            LinearLayout e=new LinearLayout(this);
            e.setOrientation(LinearLayout.VERTICAL); e.setGravity(Gravity.CENTER);
            TextView ring=t("◯",48,Color.rgb(82,76,121)); ring.setGravity(Gravity.CENTER); e.addView(ring);
            TextView a=t("No moments waiting",17,Color.rgb(191,188,208)); a.setGravity(Gravity.CENTER); e.addView(a);
            TextView b=t("Set a time. It rings once.",13,MUTED); b.setGravity(Gravity.CENTER); e.addView(b);
            list.addView(e,new LinearLayout.LayoutParams(-1,dp(280)));
            return;
        }

        for(AlarmItem a:alarms) list.addView(card(a));
    }

    private View card(AlarmItem a){
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20),dp(12),dp(10),dp(12));
        row.setBackground(shape(CARD,Color.rgb(29,31,44),18));

        Calendar c=Calendar.getInstance(); c.setTimeInMillis(a.time);
        LinearLayout texts=new LinearLayout(this); texts.setOrientation(LinearLayout.VERTICAL);
        TextView tm=t(String.format(Locale.US,"%02d:%02d",
            c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE)),35,TEXT);
        tm.setLetterSpacing(.04f); texts.addView(tm);
        TextView sub=t("◌   One Bell  ·  "+day(a.time),13,MUTED); texts.addView(sub);
        row.addView(texts,new LinearLayout.LayoutParams(0,-2,1));
        row.setOnClickListener(v->picker(a));

        TextView del=t("×",30,Color.rgb(167,164,190)); del.setGravity(Gravity.CENTER);
        del.setOnClickListener(v->{AlarmScheduler.cancel(this,a.id);AlarmStore.remove(this,a.id);refresh();});
        row.addView(del,new LinearLayout.LayoutParams(dp(50),dp(50)));

        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(96));
        p.setMargins(0,0,0,dp(12)); row.setLayoutParams(p);
        return row;
    }

    private String day(long ms){
        Calendar n=Calendar.getInstance(),x=Calendar.getInstance(); x.setTimeInMillis(ms);
        if(same(n,x)) return "Today";
        n.add(Calendar.DAY_OF_YEAR,1);
        if(same(n,x)) return "Tomorrow";
        return new SimpleDateFormat("EEE d MMM",Locale.getDefault()).format(new Date(ms));
    }

    private boolean same(Calendar a,Calendar b){
        return a.get(Calendar.YEAR)==b.get(Calendar.YEAR) &&
               a.get(Calendar.DAY_OF_YEAR)==b.get(Calendar.DAY_OF_YEAR);
    }

    private void picker(AlarmItem edit){
        Calendar c=Calendar.getInstance();
        if(edit!=null)c.setTimeInMillis(edit.time);
        TimePickerDialog d=new TimePickerDialog(this,(v,h,m)->{
            long when=next(h,m);
            int id=edit==null?AlarmStore.nextId(this):edit.id;
            if(edit!=null)AlarmScheduler.cancel(this,edit.id);
            AlarmItem item=new AlarmItem(id,when);
            AlarmStore.upsert(this,item);
            if(!AlarmScheduler.schedule(this,item)){
                AlarmStore.remove(this,id);
                Toast.makeText(this,"Allow exact alarms first",Toast.LENGTH_LONG).show();
            }
            refresh();
        },c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE),true);
        d.setTitle(edit==null?"Add Alarm":"Edit Alarm");
        d.show();
    }

    private long next(int h,int m){
        Calendar c=Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY,h); c.set(Calendar.MINUTE,m);
        c.set(Calendar.SECOND,0); c.set(Calendar.MILLISECOND,0);
        if(c.getTimeInMillis()<=System.currentTimeMillis()+1000) c.add(Calendar.DAY_OF_YEAR,1);
        return c.getTimeInMillis();
    }

    private boolean permission(){
        if(AlarmScheduler.canExact(this)) return true;
        new AlertDialog.Builder(this)
            .setTitle("One Bell needs exact alarms")
            .setMessage("Android requires permission so the bell can fire at the exact minute, even when the app is closed.")
            .setNegativeButton("Not now",null)
            .setPositiveButton("Allow",(d,w)->{
                try{startActivity(AlarmScheduler.permissionIntent(this));}
                catch(Exception e){startActivity(new Intent(Settings.ACTION_SETTINGS));}
            }).show();
        return false;
    }

    private void settings(){
        int vol=AlarmReceiver.volume(this);
        String status="Exact alarms   "+(AlarmScheduler.canExact(this)?"✓":"Needs permission")+
            "\nAlarm volume   "+vol+"%"+
            "\nSilent/Vibrate   alarm stream is used";
        AlertDialog d=new AlertDialog.Builder(this)
            .setTitle("One Bell").setMessage(status)
            .setNegativeButton("Close",null)
            .setNeutralButton("Sound settings",(x,w)->startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS)))
            .setPositiveButton("Test Bell",(x,w)->BellPlayer.playOnce(this,null)).create();
        d.show();
    }

    private Button button(String s,boolean primary){
        Button b=new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(17);
        b.setTextColor(Color.WHITE); b.setTypeface(Typeface.create("sans",Typeface.NORMAL));
        b.setStateListAnimator(null); b.setBackground(shape(primary?VSOFT:CARD,VIOLET,28)); return b;
    }

    private TextView t(String s,float size,int color){
        TextView v=new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(color);
        v.setFontFeatureSettings("tnum"); return v;
    }

    private GradientDrawable shape(int fill,int stroke,int radius){
        GradientDrawable g=new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp(radius));
        g.setStroke(dp(1),stroke); return g;
    }

    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
