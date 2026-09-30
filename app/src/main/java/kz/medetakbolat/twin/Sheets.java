package kz.medetakbolat.twin;

import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

public final class Sheets {
    public interface Choice { void pick(int index); }
    public interface TextDone { void done(String value); }

    private Sheets(){}

    private static LinearLayout base(Context c){
        LinearLayout box=new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(c,20),Ui.dp(c,12),Ui.dp(c,20),Ui.dp(c,24));
        box.setBackgroundColor(Ui.SURFACE);
        return box;
    }

    public static void choices(Context c,String title,String[] items,Choice cb){
        BottomSheetDialog d=new BottomSheetDialog(c);
        LinearLayout box=base(c);
        TextView h=Ui.text(c,title,17,Ui.TEXT,true);
        h.setPadding(0,Ui.dp(c,6),0,Ui.dp(c,8));
        box.addView(h);
        for(int i=0;i<items.length;i++){
            final int index=i;
            TextView t=Ui.text(c,items[i],16,Ui.TEXT,false);
            t.setPadding(Ui.dp(c,2),Ui.dp(c,14),Ui.dp(c,2),Ui.dp(c,14));
            t.setOnClickListener(v->{d.dismiss();cb.pick(index);});
            box.addView(t,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        d.setContentView(box);
        d.show();
    }

    public static void prompt(Context c,String title,String hint,String initial,String positive,TextDone cb){
        BottomSheetDialog d=new BottomSheetDialog(c);
        LinearLayout box=base(c);
        TextView h=Ui.text(c,title,18,Ui.TEXT,true);
        h.setPadding(0,Ui.dp(c,6),0,Ui.dp(c,10));
        box.addView(h);

        EditText e=new EditText(c);
        e.setText(initial==null?"":initial);
        e.setHint(hint==null?"":hint);
        e.setTextColor(Ui.TEXT);
        e.setHintTextColor(Ui.MUTED);
        e.setSingleLine(true);
        e.setTextSize(17);
        e.setSelectAllOnFocus(initial!=null&&!initial.isEmpty());
        e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        box.addView(e,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(c,54)));

        LinearLayout actions=Ui.row(c);
        actions.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        TextView cancel=Ui.text(c,"Cancel",14,Ui.MUTED,true);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(Ui.dp(c,16),Ui.dp(c,12),Ui.dp(c,16),Ui.dp(c,12));
        TextView ok=Ui.text(c,positive==null?"Save":positive,14,Ui.ACCENT,true);
        ok.setGravity(Gravity.CENTER);
        ok.setPadding(Ui.dp(c,16),Ui.dp(c,12),Ui.dp(c,4),Ui.dp(c,12));
        cancel.setOnClickListener(v->d.dismiss());
        ok.setOnClickListener(v->{String value=e.getText().toString().trim();if(!value.isEmpty()){d.dismiss();cb.done(value);}});
        actions.addView(cancel);actions.addView(ok);box.addView(actions);

        d.setContentView(box);d.show();
        e.requestFocus();
        e.postDelayed(()->{
            InputMethodManager imm=(InputMethodManager)c.getSystemService(Context.INPUT_METHOD_SERVICE);
            if(imm!=null)imm.showSoftInput(e,InputMethodManager.SHOW_IMPLICIT);
        },180);
    }

    public static void number(Context c,String title,String hint,String initial,TextDone cb){
        BottomSheetDialog d=new BottomSheetDialog(c);
        LinearLayout box=base(c);
        TextView h=Ui.text(c,title,18,Ui.TEXT,true);h.setPadding(0,Ui.dp(c,6),0,Ui.dp(c,10));box.addView(h);
        EditText e=new EditText(c);e.setText(initial==null?"":initial);e.setHint(hint);e.setTextColor(Ui.TEXT);e.setHintTextColor(Ui.MUTED);
        e.setSingleLine(true);e.setTextSize(17);e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));box.addView(e,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(c,54)));
        TextView ok=Ui.text(c,"Set",15,Ui.ACCENT,true);ok.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);ok.setPadding(0,Ui.dp(c,12),Ui.dp(c,4),Ui.dp(c,12));
        ok.setOnClickListener(v->{String x=e.getText().toString().trim();if(!x.isEmpty()){d.dismiss();cb.done(x);}});box.addView(ok);
        d.setContentView(box);d.show();e.requestFocus();
    }

    public static void confirm(Context c,String title,String message,String action,Runnable yes){
        BottomSheetDialog d=new BottomSheetDialog(c);
        LinearLayout box=base(c);
        TextView h=Ui.text(c,title,18,Ui.TEXT,true);box.addView(h);
        TextView m=Ui.text(c,message,14,Ui.MUTED,false);m.setPadding(0,Ui.dp(c,8),0,Ui.dp(c,14));box.addView(m);
        LinearLayout row=Ui.row(c);row.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        TextView no=Ui.text(c,"Cancel",14,Ui.MUTED,true);no.setPadding(Ui.dp(c,16),Ui.dp(c,12),Ui.dp(c,16),Ui.dp(c,12));no.setOnClickListener(v->d.dismiss());
        TextView go=Ui.text(c,action,14,Color.rgb(255,115,115),true);go.setPadding(Ui.dp(c,16),Ui.dp(c,12),0,Ui.dp(c,12));go.setOnClickListener(v->{d.dismiss();yes.run();});
        row.addView(no);row.addView(go);box.addView(row);
        d.setContentView(box);d.show();
    }

    public static void info(Context c,String title,String message){
        BottomSheetDialog d=new BottomSheetDialog(c);
        LinearLayout box=base(c);
        TextView h=Ui.text(c,title,17,Ui.TEXT,true);box.addView(h);
        TextView m=Ui.text(c,message,14,Ui.MUTED,false);m.setPadding(0,Ui.dp(c,10),0,Ui.dp(c,8));m.setMaxLines(20);box.addView(m);
        d.setContentView(box);d.show();
    }
}
