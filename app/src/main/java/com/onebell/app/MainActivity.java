package com.onebell.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
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
    private static final int REQUEST_AUDIO=901;

    private static final int BG=Color.rgb(7,8,14);
    private static final int CARD=Color.rgb(14,16,25);
    private static final int CARD2=Color.rgb(17,18,29);
    private static final int TEXT=Color.rgb(241,239,249);
    private static final int MUTED=Color.rgb(137,134,158);
    private static final int VIOLET=Color.rgb(139,120,255);
    private static final int VSOFT=Color.rgb(75,60,155);
    private static final int BORDER=Color.rgb(30,31,45);
    private static final int OFF=Color.rgb(86,84,101);

    private LinearLayout list;
    private TextView clock,date;
    private TextView soundNameView;

    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean editorOpen=false;
    private AlarmItem editingAlarm;
    private Calendar editorDate;
    private WheelPicker editorHours,editorMinutes;
    private boolean editorAlways=false;
    private TextView editorDateButton,dateModeChip,alwaysModeChip;

    private final Runnable ticker=new Runnable(){
        @Override public void run(){
            updateClock();
            handler.postDelayed(this,1000L);
        }
    };

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        setVolumeControlStream(AudioManager.STREAM_ALARM);
        showHome();
    }

    @Override protected void onResume(){
        super.onResume();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        if(!editorOpen){
            reconcileSchedules();
            refresh();
        }
    }

    @Override protected void onPause(){
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    @Override public void onBackPressed(){
        if(editorOpen){
            showHome();
            return;
        }
        super.onBackPressed();
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=REQUEST_AUDIO || resultCode!=RESULT_OK || data==null || data.getData()==null) return;

        Uri uri=data.getData();
        try{
            getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }catch(Exception ignored){}

        String name=fileName(uri);
        BellSettings.setSound(this,uri.toString(),name);
        if(soundNameView!=null) soundNameView.setText(name);
        Toast.makeText(this,"Sound selected",Toast.LENGTH_SHORT).show();
    }

    private String fileName(Uri uri){
        String result="Custom audio";
        try(Cursor c=getContentResolver().query(uri,null,null,null,null)){
            if(c!=null && c.moveToFirst()){
                int idx=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if(idx>=0) result=c.getString(idx);
            }
        }catch(Exception ignored){}
        return result;
    }

    private void reconcileSchedules(){
        long now=System.currentTimeMillis();
        for(AlarmItem a:AlarmStore.load(this)){
            if(!a.enabled) continue;

            if(a.isAlways()){
                AlarmScheduler.schedule(this,a);
            }else if(a.time>now){
                AlarmScheduler.schedule(this,a);
            }else{
                AlarmScheduler.cancel(this,a.id);
                AlarmStore.upsert(this,new AlarmItem(a.id,a.time,a.mode,false));
            }
        }
    }

    private void showHome(){
        editorOpen=false;
        editingAlarm=null;
        soundNameView=null;
        editorDateButton=null;
        dateModeChip=null;
        alwaysModeChip=null;
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
        add.setOnClickListener(v->{
            if(permission()) showEditor(null);
        });

        LinearLayout.LayoutParams alp=new LinearLayout.LayoutParams(-1,dp(60));
        alp.setMargins(dp(28),dp(12),dp(28),0);
        col.addView(add,alp);

        return root;
    }

    private void updateClock(){
        if(clock==null||date==null) return;

        Date n=new Date();
        clock.setText(new SimpleDateFormat("HH:mm",Locale.getDefault()).format(n));
        date.setText(new SimpleDateFormat("EEE, d MMM",Locale.getDefault()).format(n));
    }

    private void refresh(){
        updateClock();
        if(list==null) return;

        list.removeAllViews();
        List<AlarmItem> alarms=AlarmStore.load(this);

        if(alarms.isEmpty()){
            LinearLayout e=new LinearLayout(this);
            e.setOrientation(LinearLayout.VERTICAL);
            e.setGravity(Gravity.CENTER);

            TextView ring=t("◯",58,Color.rgb(92,82,148));
            ring.setGravity(Gravity.CENTER);
            e.addView(ring);

            TextView a=t("No alarms yet",17,Color.rgb(198,194,216));
            a.setGravity(Gravity.CENTER);
            e.addView(a);

            TextView b=t("Date or Always. Nothing disappears by itself.",13,MUTED);
            b.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,-2);
            bp.setMargins(0,dp(7),0,0);
            e.addView(b,bp);

            list.addView(e,new LinearLayout.LayoutParams(-1,dp(300)));
            return;
        }

        for(AlarmItem a:alarms){
            list.addView(card(a));
        }
    }

    private View card(AlarmItem a){
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20),dp(12),dp(8),dp(12));
        row.setBackground(shape(a.enabled?CARD:Color.rgb(11,12,18),BORDER,19));

        Calendar c=Calendar.getInstance();
        c.setTimeInMillis(a.time);

        LinearLayout texts=new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView tm=t(
            String.format(Locale.US,"%02d:%02d",c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE)),
            37,
            a.enabled?TEXT:Color.rgb(146,143,160)
        );
        tm.setLetterSpacing(.045f);
        texts.addView(tm);

        String sub;
        if(a.isAlways()){
            sub=a.enabled
                ?"∞   Always   ·   next "+day(a.time)
                :"∞   Always   ·   Off";
        }else if(a.enabled){
            sub="▥   "+dateLabel(a.time);
        }else if(a.time<=System.currentTimeMillis()){
            sub="✓   Finished   ·   "+dateLabel(a.time);
        }else{
            sub="○   Off   ·   "+dateLabel(a.time);
        }

        TextView subView=t(sub,13,a.enabled?MUTED:OFF);
        texts.addView(subView);

        row.addView(texts,new LinearLayout.LayoutParams(0,-2,1));
        row.setOnClickListener(v->showEditor(a));

        TextView toggle=t(a.enabled?"ON":"OFF",11,a.enabled?Color.rgb(203,196,255):Color.rgb(151,148,166));
        toggle.setGravity(Gravity.CENTER);
        toggle.setBackground(shape(
            a.enabled?Color.rgb(42,34,82):Color.rgb(22,23,31),
            a.enabled?Color.rgb(101,84,204):Color.rgb(47,48,60),
            15
        ));
        toggle.setOnClickListener(v->toggleAlarm(a));
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(dp(52),dp(32));
        tp.setMargins(dp(4),0,dp(4),0);
        row.addView(toggle,tp);

        TextView del=t("×",29,Color.rgb(164,160,188));
        del.setGravity(Gravity.CENTER);
        del.setOnClickListener(v->{
            AlarmScheduler.cancel(this,a.id);
            AlarmStore.remove(this,a.id);
            refresh();
        });
        row.addView(del,new LinearLayout.LayoutParams(dp(42),dp(54)));

        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(98));
        p.setMargins(0,0,0,dp(13));
        row.setLayoutParams(p);

        return row;
    }

    private void toggleAlarm(AlarmItem a){
        if(a.enabled){
            AlarmScheduler.cancel(this,a.id);
            AlarmStore.upsert(this,a.withEnabled(false));
            refresh();
            return;
        }

        if(a.isAlways()){
            long next=AlarmScheduler.nextDaily(a.time,System.currentTimeMillis());
            AlarmItem enabled=new AlarmItem(a.id,next,AlarmItem.MODE_ALWAYS,true);
            AlarmStore.upsert(this,enabled);
            AlarmScheduler.schedule(this,enabled);
            refresh();
            return;
        }

        if(a.time>System.currentTimeMillis()){
            AlarmItem enabled=a.withEnabled(true);
            AlarmStore.upsert(this,enabled);
            AlarmScheduler.schedule(this,enabled);
            refresh();
            return;
        }

        showEditor(a);
        Toast.makeText(this,"Choose a new date to reactivate it",Toast.LENGTH_SHORT).show();
    }

    private void showEditor(AlarmItem edit){
        editorOpen=true;
        editingAlarm=edit;
        editorAlways=edit!=null && edit.isAlways();

        editorDate=Calendar.getInstance();
        if(edit!=null){
            editorDate.setTimeInMillis(edit.time);
        }else{
            editorDate.add(Calendar.MINUTE,5);
        }

        setContentView(buildEditor());
    }

    private View buildEditor(){
        FrameLayout root=baseRoot();

        LinearLayout col=new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(22),dp(12),dp(22),dp(18));
        root.addView(col,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView back=t("‹",43,Color.rgb(191,184,225));
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v->showHome());
        top.addView(back,new LinearLayout.LayoutParams(dp(52),dp(56)));

        TextView heading=t(editingAlarm==null?"Add Alarm":"Edit Alarm",20,TEXT);
        heading.setGravity(Gravity.CENTER);
        top.addView(heading,new LinearLayout.LayoutParams(0,dp(56),1));

        top.addView(new View(this),new LinearLayout.LayoutParams(dp(52),dp(56)));
        col.addView(top);

        LinearLayout modes=new LinearLayout(this);
        modes.setGravity(Gravity.CENTER);

        dateModeChip=t("Date",14,TEXT);
        dateModeChip.setGravity(Gravity.CENTER);
        dateModeChip.setOnClickListener(v->{
            editorAlways=false;
            updateModeUi();
        });

        alwaysModeChip=t("Always",14,TEXT);
        alwaysModeChip.setGravity(Gravity.CENTER);
        alwaysModeChip.setOnClickListener(v->{
            editorAlways=true;
            updateModeUi();
        });

        LinearLayout.LayoutParams mp1=new LinearLayout.LayoutParams(0,dp(44),1);
        mp1.setMargins(0,0,dp(5),0);
        LinearLayout.LayoutParams mp2=new LinearLayout.LayoutParams(0,dp(44),1);
        mp2.setMargins(dp(5),0,0,0);
        modes.addView(dateModeChip,mp1);
        modes.addView(alwaysModeChip,mp2);

        LinearLayout.LayoutParams modesP=new LinearLayout.LayoutParams(-1,dp(44));
        modesP.setMargins(dp(44),dp(4),dp(44),dp(8));
        col.addView(modes,modesP);

        editorDateButton=t("",15,Color.rgb(193,186,225));
        editorDateButton.setGravity(Gravity.CENTER);
        editorDateButton.setPadding(dp(16),dp(8),dp(16),dp(8));
        editorDateButton.setOnClickListener(v->{
            if(editorAlways) return;

            DatePickerDialog d=new DatePickerDialog(this,(view,y,m,day)->{
                editorDate.set(Calendar.YEAR,y);
                editorDate.set(Calendar.MONTH,m);
                editorDate.set(Calendar.DAY_OF_MONTH,day);
                updateModeUi();
            },editorDate.get(Calendar.YEAR),editorDate.get(Calendar.MONTH),editorDate.get(Calendar.DAY_OF_MONTH));

            d.getDatePicker().setMinDate(System.currentTimeMillis()-60000L);
            d.show();
        });

        LinearLayout.LayoutParams dbp=new LinearLayout.LayoutParams(-1,dp(50));
        dbp.setMargins(dp(44),0,dp(44),dp(2));
        col.addView(editorDateButton,dbp);

        editorHours=new WheelPicker(this,23,editorDate.get(Calendar.HOUR_OF_DAY));
        editorMinutes=new WheelPicker(this,59,editorDate.get(Calendar.MINUTE));

        LinearLayout wheels=new LinearLayout(this);
        wheels.setGravity(Gravity.CENTER);
        wheels.setOrientation(LinearLayout.HORIZONTAL);
        wheels.addView(editorHours,new LinearLayout.LayoutParams(dp(126),dp(270)));

        TextView colon=t(":",33,Color.rgb(170,158,255));
        colon.setGravity(Gravity.CENTER);
        wheels.addView(colon,new LinearLayout.LayoutParams(dp(45),dp(270)));

        wheels.addView(editorMinutes,new LinearLayout.LayoutParams(dp(126),dp(270)));

        LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,0,1);
        wp.setMargins(0,0,0,dp(4));
        col.addView(wheels,wp);

        LinearLayout soundCard=new LinearLayout(this);
        soundCard.setOrientation(LinearLayout.VERTICAL);
        soundCard.setPadding(dp(20),dp(12),dp(20),dp(10));
        soundCard.setBackground(shape(CARD2,BORDER,20));

        LinearLayout soundRow=new LinearLayout(this);
        soundRow.setGravity(Gravity.CENTER_VERTICAL);
        soundRow.addView(t("♪   Sound",15,TEXT),new LinearLayout.LayoutParams(0,dp(36),1));

        soundNameView=t(BellSettings.soundName(this),14,Color.rgb(174,166,207));
        soundNameView.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        soundNameView.setOnClickListener(v->chooseAudio());
        soundRow.addView(soundNameView,new LinearLayout.LayoutParams(dp(160),dp(36)));
        soundCard.addView(soundRow);

        LinearLayout chooseRow=new LinearLayout(this);
        chooseRow.setGravity(Gravity.CENTER);

        TextView choose=t("Choose audio",13,Color.rgb(171,158,255));
        choose.setGravity(Gravity.CENTER);
        choose.setOnClickListener(v->chooseAudio());
        chooseRow.addView(choose,new LinearLayout.LayoutParams(0,dp(30),1));

        TextView reset=t("Use One Bell",13,MUTED);
        reset.setGravity(Gravity.CENTER);
        reset.setOnClickListener(v->{
            BellSettings.clearSound(this);
            if(soundNameView!=null) soundNameView.setText("One Bell");
        });
        chooseRow.addView(reset,new LinearLayout.LayoutParams(0,dp(30),1));

        soundCard.addView(chooseRow);

        View divider=new View(this);
        divider.setBackgroundColor(Color.rgb(31,32,46));
        LinearLayout.LayoutParams divp=new LinearLayout.LayoutParams(-1,dp(1));
        divp.setMargins(0,dp(1),0,dp(4));
        soundCard.addView(divider,divp);

        soundCard.addView(t("▸   Bell volume",14,TEXT));

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
            public void onProgressChanged(SeekBar s,int p,boolean from){
                pct.setText(p+"%");
            }

            public void onStartTrackingTouch(SeekBar s){}

            public void onStopTrackingTouch(SeekBar s){
                BellSettings.setVolume(MainActivity.this,Math.max(5,s.getProgress())/100f);
            }
        });

        vr.addView(seek,new LinearLayout.LayoutParams(0,dp(38),1));
        vr.addView(pct,new LinearLayout.LayoutParams(dp(54),dp(38)));
        soundCard.addView(vr);

        TextView test=t("Test bell",13,Color.rgb(171,158,255));
        test.setGravity(Gravity.CENTER);
        test.setOnClickListener(v->{
            BellSettings.setVolume(this,Math.max(5,seek.getProgress())/100f);
            BellPlayer.playOnce(this,null);
        });
        soundCard.addView(test,new LinearLayout.LayoutParams(-1,dp(26)));

        col.addView(soundCard,new LinearLayout.LayoutParams(-1,dp(176)));

        LinearLayout actions=new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(0,dp(12),0,0);

        Button cancel=button("Cancel",false);
        cancel.setOnClickListener(v->showHome());

        Button set=button(editingAlarm==null?"Set":"Save",true);
        set.setOnClickListener(v->saveEditor(seek));

        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(56),1);
        cp.setMargins(0,0,dp(8),0);

        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(56),1);
        sp.setMargins(dp(8),0,0,0);

        actions.addView(cancel,cp);
        actions.addView(set,sp);
        col.addView(actions,new LinearLayout.LayoutParams(-1,dp(72)));

        updateModeUi();
        return root;
    }

    private void saveEditor(SeekBar seek){
        BellSettings.setVolume(this,Math.max(5,seek.getProgress())/100f);

        long now=System.currentTimeMillis();
        long when;

        if(editorAlways){
            Calendar source=Calendar.getInstance();
            source.set(Calendar.HOUR_OF_DAY,editorHours.getValue());
            source.set(Calendar.MINUTE,editorMinutes.getValue());
            source.set(Calendar.SECOND,0);
            source.set(Calendar.MILLISECOND,0);
            when=AlarmScheduler.nextDaily(source.getTimeInMillis(),now);
        }else{
            Calendar chosen=(Calendar)editorDate.clone();
            chosen.set(Calendar.HOUR_OF_DAY,editorHours.getValue());
            chosen.set(Calendar.MINUTE,editorMinutes.getValue());
            chosen.set(Calendar.SECOND,0);
            chosen.set(Calendar.MILLISECOND,0);
            when=chosen.getTimeInMillis();

            if(when<=now+1000L){
                Toast.makeText(this,"Choose a future date and time",Toast.LENGTH_LONG).show();
                return;
            }
        }

        int id=editingAlarm==null?AlarmStore.nextId(this):editingAlarm.id;
        if(editingAlarm!=null) AlarmScheduler.cancel(this,editingAlarm.id);

        int mode=editorAlways?AlarmItem.MODE_ALWAYS:AlarmItem.MODE_DATE;
        AlarmItem item=new AlarmItem(id,when,mode,true);
        AlarmStore.upsert(this,item);

        if(!AlarmScheduler.schedule(this,item)){
            AlarmStore.upsert(this,item.withEnabled(false));
            Toast.makeText(this,"Android blocked exact alarms",Toast.LENGTH_LONG).show();
            return;
        }

        showHome();
    }

    private void updateModeUi(){
        if(dateModeChip==null || alwaysModeChip==null || editorDateButton==null) return;

        styleModeChip(dateModeChip,!editorAlways);
        styleModeChip(alwaysModeChip,editorAlways);

        if(editorAlways){
            editorDateButton.setText("Every day");
            editorDateButton.setTextColor(Color.rgb(170,164,195));
            editorDateButton.setBackground(shape(Color.rgb(12,13,20),Color.rgb(28,29,39),18));
        }else{
            editorDateButton.setText(formatEditorDate());
            editorDateButton.setTextColor(Color.rgb(193,186,225));
            editorDateButton.setBackground(shape(CARD2,BORDER,18));
        }
    }

    private void styleModeChip(TextView chip,boolean selected){
        chip.setTextColor(selected?TEXT:Color.rgb(150,146,170));
        chip.setBackground(shape(
            selected?Color.rgb(43,35,83):Color.rgb(13,14,21),
            selected?Color.rgb(104,86,207):Color.rgb(31,32,43),
            18
        ));
    }

    private String formatEditorDate(){
        Calendar today=Calendar.getInstance();
        Calendar chosen=(Calendar)editorDate.clone();

        String prefix;
        if(sameDay(today,chosen)){
            prefix="Today";
        }else{
            today.add(Calendar.DAY_OF_YEAR,1);
            prefix=sameDay(today,chosen)?"Tomorrow":new SimpleDateFormat("EEE",Locale.getDefault()).format(chosen.getTime());
        }

        return prefix+"   ·   "+new SimpleDateFormat("d MMM yyyy",Locale.getDefault()).format(chosen.getTime());
    }

    private String dateLabel(long ms){
        return new SimpleDateFormat("EEE, d MMM yyyy",Locale.getDefault()).format(new Date(ms));
    }

    private String day(long ms){
        Calendar n=Calendar.getInstance();
        Calendar x=Calendar.getInstance();
        x.setTimeInMillis(ms);

        if(sameDay(n,x)) return "Today";

        n.add(Calendar.DAY_OF_YEAR,1);
        if(sameDay(n,x)) return "Tomorrow";

        return new SimpleDateFormat("EEE d MMM",Locale.getDefault()).format(new Date(ms));
    }

    private boolean sameDay(Calendar a,Calendar b){
        return a.get(Calendar.YEAR)==b.get(Calendar.YEAR)
            && a.get(Calendar.DAY_OF_YEAR)==b.get(Calendar.DAY_OF_YEAR);
    }

    private void chooseAudio(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i,REQUEST_AUDIO);
    }

    private FrameLayout baseRoot(){
        FrameLayout root=new FrameLayout(this);
        root.setBackgroundColor(BG);
        root.addView(new GlowBackground(this),new FrameLayout.LayoutParams(-1,-1));
        return root;
    }

    private boolean permission(){
        if(AlarmScheduler.canExact(this)) return true;

        new AlertDialog.Builder(this,AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Exact alarms")
            .setMessage("One Bell needs Android's exact-alarm access so the chime lands on the exact minute.")
            .setNegativeButton("Not now",null)
            .setPositiveButton("Open settings",(d,w)->{
                try{
                    startActivity(AlarmScheduler.permissionIntent(this));
                }catch(Exception e){
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                }
            })
            .show();

        return false;
    }

    private void settings(){
        int vol=AlarmReceiver.volume(this);

        String status=
            "Device alarm volume   "+vol+"%\n"+
            "One Bell volume   "+Math.round(BellSettings.volume(this)*100)+"%\n"+
            "Sound   "+BellSettings.soundName(this)+"\n\n"+
            "Alarms stay in the list until you delete them.";

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

        GlowBackground(Activity c){
            super(c);
        }

        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            c.drawColor(BG);

            float w=getWidth();
            float h=getHeight();

            p.setShader(new RadialGradient(
                w*.82f,
                h*.16f,
                Math.max(w,h)*.33f,
                new int[]{
                    Color.argb(65,92,76,180),
                    Color.argb(20,42,35,86),
                    Color.TRANSPARENT
                },
                new float[]{0f,.38f,1f},
                Shader.TileMode.CLAMP
            ));
            c.drawCircle(w*.82f,h*.16f,Math.max(w,h)*.33f,p);

            p.setShader(new RadialGradient(
                w*.50f,
                h*.91f,
                Math.max(w,h)*.30f,
                new int[]{
                    Color.argb(42,94,75,200),
                    Color.TRANSPARENT
                },
                null,
                Shader.TileMode.CLAMP
            ));
            c.drawCircle(w*.50f,h*.91f,Math.max(w,h)*.30f,p);
            p.setShader(null);
        }
    }
}
