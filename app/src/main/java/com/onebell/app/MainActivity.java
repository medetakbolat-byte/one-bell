package com.onebell.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int BG=Color.rgb(7,8,14);
    private static final int CARD=Color.rgb(14,16,25);
    private static final int CARD2=Color.rgb(17,18,29);
    private static final int TEXT=Color.rgb(241,239,249);
    private static final int MUTED=Color.rgb(137,134,158);
    private static final int VIOLET=Color.rgb(139,120,255);
    private static final int VSOFT=Color.rgb(75,60,155);
    private static final int BORDER=Color.rgb(30,31,45);

    private LinearLayout list;
    private TextView clock,date;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean editorOpen=false;

    private final Runnable ticker=new Runnable(){
        @Override public void run(){
            updateClock();
            handler.postDelayed(this,1000L);
        }
    };

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        Window w=getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(BG);
        setVolumeControlStream(AudioManager.STREAM_ALARM);
        showHome();
    }

    @Override protected void onResume(){
        super.onResume();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        if(!editorOpen){
            refresh();
            if(AlarmScheduler.canExact(this)){
                for(AlarmItem a:AlarmStore.load(this)){
                    if(a.time>System.currentTimeMillis()) AlarmScheduler.schedule(this,a);
                }
            }
        }
    }

    @Override protected void onPause(){
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    @Override public void onBackPressed(){
        if(editorOpen){ showHome(); return; }
        super.onBackPressed();
    }

    private void showHome(){
        editorOpen=false;
        setContentView(buildHome());
        refresh();
    }

    private View buildHome(){
        FrameLayout root=baseRoot();
        LinearLayout col=new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(24),dp(16),dp(24),dp(18));
        root.addView(col,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=t("One Bell",29,TEXT);
        title.setLetterSpacing(.12f);
        top.addView(title,new LinearLayout.LayoutParams(0,dp(56),1));

        TextView gear=t("⚙",25,Color.rgb(185,180,210));
        gear.setGravity(Gravity.CENTER);
        gear.setOnClickListener(v->settings());
        top.addView(gear,new LinearLayout.LayoutParams(dp(52),dp(52)));
        col.addView(top);

        View accent=new View(this);
        accent.setBackgroundColor(VIOLET);
        LinearLayout.LayoutParams al=new LinearLayout.LayoutParams(dp(22),dp(1));
        al.setMargins(0,0,0,dp(16));
        col.addView(accent,al);

        clock=t("--:--",27,TEXT);
        clock.setLetterSpacing(.13f);
        col.addView(clock);

        date=t("",14,MUTED);
        LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(-2,-2);
        dlp.setMargins(0,dp(4),0,dp(24));
        col.addView(date,dlp);

        ScrollView scroll=new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list,new ScrollView.LayoutParams(-1,-2));
        col.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        Button add=button("＋   Add Alarm",true);
        add.setOnClickListener(v->{ if(permission()) showEditor(null); });
        LinearLayout.LayoutParams alp=new LinearLayout.LayoutParams(-1,dp(60));
        alp.setMargins(dp(28),dp(12),dp(28),0);
        col.addView(add,alp);
        return root;
    }

    private void updateClock(){
        if(clock==null||date==null)return;
        Date n=new Date();
        clock.setText(new SimpleDateFormat("HH:mm",Locale.getDefault()).format(n));
        date.setText(new SimpleDateFormat("EEE, d MMM",Locale.getDefault()).format(n));
    }

    private void refresh(){
        updateClock();
        if(list==null)return;
        AlarmStore.removeExpired(this,System.currentTimeMillis()-60000L);
        list.removeAllViews();
        List<AlarmItem> alarms=AlarmStore.load(this);

        if(alarms.isEmpty()){
            LinearLayout e=new LinearLayout(this);
            e.setOrientation(LinearLayout.VERTICAL);
            e.setGravity(Gravity.CENTER);
            TextView ring=t("◯",58,Color.rgb(92,82,148));
            ring.setGravity(Gravity.CENTER);
            e.addView(ring);
            TextView a=t("No moments waiting",17,Color.rgb(198,194,216));
            a.setGravity(Gravity.CENTER);
            e.addView(a);
            TextView b=t("Set a time. It rings once.",13,MUTED);
            b.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,-2);
            bp.setMargins(0,dp(7),0,0);
            e.addView(b,bp);
            list.addView(e,new LinearLayout.LayoutParams(-1,dp(300)));
            return;
        }

        for(AlarmItem a:alarms) list.addView(card(a));
    }

    private View card(AlarmItem a){
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20),dp(12),dp(10),dp(12));
        row.setBackground(shape(CARD,BORDER,19));

        Calendar c=Calendar.getInstance();
        c.setTimeInMillis(a.time);

        LinearLayout texts=new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView tm=t(String.format(Locale.US,"%02d:%02d",c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE)),37,TEXT);
        tm.setLetterSpacing(.045f);
        texts.addView(tm);
        TextView sub=t("▥   One Bell   ·   "+day(a.time),13,MUTED);
        texts.addView(sub);
        row.addView(texts,new LinearLayout.LayoutParams(0,-2,1));
        row.setOnClickListener(v->showEditor(a));

        TextView del=t("×",29,Color.rgb(164,160,188));
        del.setGravity(Gravity.CENTER);
        del.setOnClickListener(v->{
            AlarmScheduler.cancel(this,a.id);
            AlarmStore.remove(this,a.id);
            refresh();
        });
        row.addView(del,new LinearLayout.LayoutParams(dp(50),dp(54)));

        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(98));
        p.setMargins(0,0,0,dp(13));
        row.setLayoutParams(p);
        return row;
    }

    private View buildEditor(AlarmItem edit){
        FrameLayout root=baseRoot();
        LinearLayout col=new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(22),dp(14),dp(22),dp(20));
        root.addView(col,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=t("‹",43,Color.rgb(191,184,225));
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v->showHome());
        top.addView(back,new LinearLayout.LayoutParams(dp(52),dp(58)));

        TextView heading=t(edit==null?"Add Alarm":"Edit Alarm",20,TEXT);
        heading.setGravity(Gravity.CENTER);
        top.addView(heading,new LinearLayout.LayoutParams(0,dp(58),1));
        top.addView(new View(this),new LinearLayout.LayoutParams(dp(52),dp(58)));
        col.addView(top);

        Calendar c=Calendar.getInstance();
        if(edit!=null)c.setTimeInMillis(edit.time);
        else c.add(Calendar.MINUTE,5);

        WheelPicker hours=new WheelPicker(this,23,c.get(Calendar.HOUR_OF_DAY));
        WheelPicker mins=new WheelPicker(this,59,c.get(Calendar.MINUTE));

        LinearLayout wheels=new LinearLayout(this);
        wheels.setGravity(Gravity.CENTER);
        wheels.setOrientation(LinearLayout.HORIZONTAL);
        wheels.addView(hours,new LinearLayout.LayoutParams(dp(126),dp(330)));

        TextView colon=t(":",33,Color.rgb(170,158,255));
        colon.setGravity(Gravity.CENTER);
        wheels.addView(colon,new LinearLayout.LayoutParams(dp(45),dp(330)));
        wheels.addView(mins,new LinearLayout.LayoutParams(dp(126),dp(330)));

        LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,0,1);
        wp.setMargins(0,dp(8),0,dp(8));
        col.addView(wheels,wp);

        LinearLayout soundCard=new LinearLayout(this);
        soundCard.setOrientation(LinearLayout.VERTICAL);
        soundCard.setPadding(dp(20),dp(16),dp(20),dp(14));
        soundCard.setBackground(shape(CARD2,BORDER,20));

        LinearLayout soundRow=new LinearLayout(this);
        soundRow.setGravity(Gravity.CENTER_VERTICAL);
        soundRow.addView(t("♪   Sound",15,TEXT),new LinearLayout.LayoutParams(0,dp(36),1));
        soundRow.addView(t("One Bell",14,Color.rgb(174,166,207)));
        soundCard.addView(soundRow);

        View divider=new View(this);
        divider.setBackgroundColor(Color.rgb(31,32,46));
        LinearLayout.LayoutParams divp=new LinearLayout.LayoutParams(-1,dp(1));
        divp.setMargins(0,dp(2),0,dp(8));
        soundCard.addView(divider,divp);

        soundCard.addView(t("▸   Bell volume",15,TEXT));

        LinearLayout vr=new LinearLayout(this);
        vr.setGravity(Gravity.CENTER_VERTICAL);
        SeekBar seek=new SeekBar(this);
        seek.setMax(100);
        seek.setProgress(Math.round(BellSettings.volume(this)*100));
        seek.getProgressDrawable().setTint(VIOLET);
        seek.getThumb().setTint(Color.rgb(184,174,255));
        TextView pct=t(seek.getProgress()+"%",14,Color.rgb(177,170,207));
        pct.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean from){ pct.setText(p+"%"); }
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){
                BellSettings.setVolume(MainActivity.this,Math.max(5,s.getProgress())/100f);
            }
        });
        vr.addView(seek,new LinearLayout.LayoutParams(0,dp(45),1));
        vr.addView(pct,new LinearLayout.LayoutParams(dp(54),dp(45)));
        soundCard.addView(vr);

        TextView test=t("Test bell",13,Color.rgb(171,158,255));
        test.setGravity(Gravity.CENTER);
        test.setPadding(0,dp(4),0,dp(2));
        test.setOnClickListener(v->{
            BellSettings.setVolume(this,Math.max(5,seek.getProgress())/100f);
            BellPlayer.playOnce(this,null);
        });
        soundCard.addView(test);
        col.addView(soundCard,new LinearLayout.LayoutParams(-1,dp(170)));

        LinearLayout actions=new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(0,dp(18),0,0);

        Button cancel=button("Cancel",false);
        cancel.setOnClickListener(v->showHome());
        Button set=button(edit==null?"Set":"Save",true);
        set.setOnClickListener(v->{
            BellSettings.setVolume(this,Math.max(5,seek.getProgress())/100f);
            long when=next(hours.getValue(),mins.getValue());
            int id=edit==null?AlarmStore.nextId(this):edit.id;
            if(edit!=null)AlarmScheduler.cancel(this,edit.id);
            AlarmItem item=new AlarmItem(id,when);
            AlarmStore.upsert(this,item);
            if(!AlarmScheduler.schedule(this,item)){
                AlarmStore.remove(this,id);
                Toast.makeText(this,"Android blocked exact alarms",Toast.LENGTH_LONG).show();
            }
            showHome();
        });

        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(58),1);
        cp.setMargins(0,0,dp(8),0);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(58),1);
        sp.setMargins(dp(8),0,0,0);
        actions.addView(cancel,cp);
        actions.addView(set,sp);
        col.addView(actions,new LinearLayout.LayoutParams(-1,dp(82)));
        return root;
    }

    private void showEditor(AlarmItem edit){
        editorOpen=true;
        setContentView(buildEditor(edit));
    }

    private FrameLayout baseRoot(){
        FrameLayout root=new FrameLayout(this);
        root.setBackgroundColor(BG);
        root.addView(new GlowBackground(this),new FrameLayout.LayoutParams(-1,-1));
        return root;
    }

    private String day(long ms){
        Calendar n=Calendar.getInstance(),x=Calendar.getInstance();
        x.setTimeInMillis(ms);
        if(same(n,x))return "Today";
        n.add(Calendar.DAY_OF_YEAR,1);
        if(same(n,x))return "Tomorrow";
        return new SimpleDateFormat("EEE d MMM",Locale.getDefault()).format(new Date(ms));
    }

    private boolean same(Calendar a,Calendar b){
        return a.get(Calendar.YEAR)==b.get(Calendar.YEAR)
            && a.get(Calendar.DAY_OF_YEAR)==b.get(Calendar.DAY_OF_YEAR);
    }

    private long next(int h,int m){
        Calendar c=Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY,h);
        c.set(Calendar.MINUTE,m);
        c.set(Calendar.SECOND,0);
        c.set(Calendar.MILLISECOND,0);
        if(c.getTimeInMillis()<=System.currentTimeMillis()+1000) c.add(Calendar.DAY_OF_YEAR,1);
        return c.getTimeInMillis();
    }

    private boolean permission(){
        if(AlarmScheduler.canExact(this))return true;
        new AlertDialog.Builder(this,AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Exact alarms")
            .setMessage("One Bell needs Android's exact-alarm access so the chime lands on the exact minute.")
            .setNegativeButton("Not now",null)
            .setPositiveButton("Open settings",(d,w)->{
                try{startActivity(AlarmScheduler.permissionIntent(this));}
                catch(Exception e){startActivity(new Intent(Settings.ACTION_SETTINGS));}
            }).show();
        return false;
    }

    private void settings(){
        int vol=AlarmReceiver.volume(this);
        String status="Device alarm volume   "+vol+"%\nOne Bell volume   "
            +Math.round(BellSettings.volume(this)*100)+"%\n\nSilent/Vibrate mode: supported via the alarm audio stream.";
        new AlertDialog.Builder(this,AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("One Bell")
            .setMessage(status)
            .setNegativeButton("Close",null)
            .setNeutralButton("Sound settings",(x,w)->startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS)))
            .setPositiveButton("Test Bell",(x,w)->BellPlayer.playOnce(this,null))
            .show();
    }

    private Button button(String s,boolean primary){
        Button b=new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(17);
        b.setTextColor(TEXT);
        b.setTypeface(Typeface.create("sans",Typeface.NORMAL));
        b.setStateListAnimator(null);
        b.setBackground(shape(primary?VSOFT:CARD,primary?VIOLET:BORDER,29));
        return b;
    }

    private TextView t(String s,float size,int color){
        TextView v=new TextView(this);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setFontFeatureSettings("tnum");
        return v;
    }

    private GradientDrawable shape(int fill,int stroke,int radius){
        GradientDrawable g=new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(1),stroke);
        return g;
    }

    private int dp(int n){
        return Math.round(n*getResources().getDisplayMetrics().density);
    }

    private static final class GlowBackground extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        GlowBackground(Activity c){super(c);}

        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            c.drawColor(BG);
            float w=getWidth(),h=getHeight();

            p.setShader(new RadialGradient(
                w*.82f,h*.16f,Math.max(w,h)*.33f,
                new int[]{Color.argb(65,92,76,180),Color.argb(20,42,35,86),Color.TRANSPARENT},
                new float[]{0f,.38f,1f},Shader.TileMode.CLAMP));
            c.drawCircle(w*.82f,h*.16f,Math.max(w,h)*.33f,p);

            p.setShader(new RadialGradient(
                w*.50f,h*.91f,Math.max(w,h)*.30f,
                new int[]{Color.argb(42,94,75,200),Color.TRANSPARENT},
                null,Shader.TileMode.CLAMP));
            c.drawCircle(w*.50f,h*.91f,Math.max(w,h)*.30f,p);
            p.setShader(null);
        }
    }
}
