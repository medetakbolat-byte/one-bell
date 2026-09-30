package com.onebell.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AlarmStore {
    private static final String PREF="one_bell", KEY="alarms", NEXT="next_id";
    private AlarmStore(){}

    private static SharedPreferences p(Context c){
        return c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
    }

    public static synchronized int nextId(Context c){
        int id=p(c).getInt(NEXT,1000);
        p(c).edit().putInt(NEXT,id+1).apply();
        return id;
    }

    public static synchronized List<AlarmItem> load(Context c){
        ArrayList<AlarmItem> out=new ArrayList<>();
        try{
            JSONArray a=new JSONArray(p(c).getString(KEY,"[]"));
            for(int i=0;i<a.length();i++){
                JSONObject o=a.getJSONObject(i);
                out.add(new AlarmItem(
                    o.getInt("id"),
                    o.getLong("time"),
                    o.optInt("mode",AlarmItem.MODE_DATE),
                    o.has("enabled")?o.optBoolean("enabled",true):true
                ));
            }
        }catch(Exception ignored){}
        Collections.sort(out);
        return out;
    }

    public static synchronized AlarmItem get(Context c,int id){
        for(AlarmItem x:load(c)) if(x.id==id) return x;
        return null;
    }

    public static synchronized void save(Context c,List<AlarmItem> list){
        JSONArray a=new JSONArray();
        try{
            for(AlarmItem x:list){
                JSONObject o=new JSONObject();
                o.put("id",x.id);
                o.put("time",x.time);
                o.put("mode",x.mode);
                o.put("enabled",x.enabled);
                a.put(o);
            }
        }catch(Exception ignored){}
        p(c).edit().putString(KEY,a.toString()).apply();
    }

    public static synchronized void upsert(Context c,AlarmItem x){
        List<AlarmItem> list=load(c);
        boolean found=false;
        for(int i=0;i<list.size();i++){
            if(list.get(i).id==x.id){
                list.set(i,x);
                found=true;
                break;
            }
        }
        if(!found) list.add(x);
        Collections.sort(list);
        save(c,list);
    }

    public static synchronized void remove(Context c,int id){
        List<AlarmItem> list=load(c);
        list.removeIf(x->x.id==id);
        save(c,list);
    }
}
