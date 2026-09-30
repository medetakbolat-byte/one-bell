package kz.medetakbolat.twin;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Store {
    private static final String PREF = "twin_store";

    public static class Item {
        public String uri;
        public String section = "";
        public Item(String uri) { this.uri = uri; }
        public Item(String uri, String section) { this.uri=uri; this.section=section==null?"":section; }
    }

    public static class Playlist {
        public String id = UUID.randomUUID().toString();
        public String name = "Playlist";
        public String cover = "";
        public ArrayList<Item> items = new ArrayList<>();
        public ArrayList<String> sections = new ArrayList<>();
    }

    public static class CollectionDef {
        public String id = UUID.randomUUID().toString();
        public String name = "Collection";
        public String cover = "";
        public ArrayList<Playlist> playlists = new ArrayList<>();
    }

    public static class QueueDef {
        public String id = UUID.randomUUID().toString();
        public String name = "Queue";
        public ArrayList<String> items = new ArrayList<>();
    }

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static void ensureDefaults(Context c) {
        if (p(c).getBoolean("seeded", false)) return;
        ArrayList<CollectionDef> cs = new ArrayList<>();
        CollectionDef teaching = new CollectionDef();
        teaching.name = "Teaching";
        for (String n : new String[]{"C2 English","Oral English","Translation","Grammar"}) {
            Playlist pl = new Playlist(); pl.name = n; teaching.playlists.add(pl);
        }
        cs.add(teaching);
        CollectionDef personal = new CollectionDef(); personal.name = "Personal"; cs.add(personal);
        saveCollections(c, cs);

        ArrayList<QueueDef> qs = new ArrayList<>();
        QueueDef current = new QueueDef(); current.name = "Current"; current.id = "current";
        qs.add(current);
        saveQueues(c, qs);
        p(c).edit().putString("current_queue","current").putBoolean("seeded",true).apply();
    }

    public static ArrayList<CollectionDef> getCollections(Context c) {
        ensureDefaults(c);
        ArrayList<CollectionDef> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p(c).getString("collections","[]"));
            for (int i=0;i<a.length();i++) {
                JSONObject o=a.getJSONObject(i);
                CollectionDef cd=new CollectionDef();
                cd.id=o.optString("id",cd.id); cd.name=o.optString("name","Collection"); cd.cover=o.optString("cover","");
                JSONArray ps=o.optJSONArray("playlists");
                if(ps!=null) for(int j=0;j<ps.length();j++) cd.playlists.add(parsePlaylist(ps.getJSONObject(j)));
                out.add(cd);
            }
        } catch(Exception ignored) {}
        return out;
    }

    private static Playlist parsePlaylist(JSONObject o) {
        Playlist pl=new Playlist();
        pl.id=o.optString("id",pl.id); pl.name=o.optString("name","Playlist"); pl.cover=o.optString("cover","");
        JSONArray ss=o.optJSONArray("sections");
        if(ss!=null) for(int i=0;i<ss.length();i++) pl.sections.add(ss.optString(i));
        JSONArray is=o.optJSONArray("items");
        if(is!=null) for(int i=0;i<is.length();i++) {
            Object raw=is.opt(i);
            if(raw instanceof JSONObject) {
                JSONObject io=(JSONObject)raw;
                pl.items.add(new Item(io.optString("uri",""),io.optString("section","")));
            } else if(raw!=null) pl.items.add(new Item(String.valueOf(raw)));
        }
        return pl;
    }

    public static void saveCollections(Context c, List<CollectionDef> cs) {
        JSONArray a=new JSONArray();
        try {
            for(CollectionDef cd:cs) {
                JSONObject o=new JSONObject();
                o.put("id",cd.id); o.put("name",cd.name); o.put("cover",cd.cover);
                JSONArray ps=new JSONArray();
                for(Playlist pl:cd.playlists) {
                    JSONObject po=new JSONObject();
                    po.put("id",pl.id); po.put("name",pl.name); po.put("cover",pl.cover);
                    JSONArray ss=new JSONArray(); for(String s:pl.sections) ss.put(s); po.put("sections",ss);
                    JSONArray items=new JSONArray();
                    for(Item it:pl.items) {
                        JSONObject io=new JSONObject(); io.put("uri",it.uri); io.put("section",it.section); items.put(io);
                    }
                    po.put("items",items); ps.put(po);
                }
                o.put("playlists",ps); a.put(o);
            }
        } catch(Exception ignored) {}
        p(c).edit().putString("collections",a.toString()).apply();
    }

    public static ArrayList<QueueDef> getQueues(Context c) {
        ensureDefaults(c);
        ArrayList<QueueDef> out=new ArrayList<>();
        try {
            JSONArray a=new JSONArray(p(c).getString("queues","[]"));
            for(int i=0;i<a.length();i++) {
                JSONObject o=a.getJSONObject(i);
                QueueDef q=new QueueDef(); q.id=o.optString("id",q.id); q.name=o.optString("name","Queue");
                JSONArray is=o.optJSONArray("items"); if(is!=null) for(int j=0;j<is.length();j++) q.items.add(is.optString(j));
                out.add(q);
            }
        } catch(Exception ignored) {}
        boolean has=false; for(QueueDef q:out) if("current".equals(q.id)) has=true;
        if(!has){ QueueDef q=new QueueDef(); q.id="current"; q.name="Current"; out.add(0,q); saveQueues(c,out); }
        return out;
    }

    public static void saveQueues(Context c, List<QueueDef> qs) {
        JSONArray a=new JSONArray();
        try {
            for(QueueDef q:qs) {
                JSONObject o=new JSONObject(); o.put("id",q.id); o.put("name",q.name);
                JSONArray is=new JSONArray(); for(String s:q.items) is.put(s); o.put("items",is); a.put(o);
            }
        } catch(Exception ignored) {}
        p(c).edit().putString("queues",a.toString()).apply();
    }

    public static QueueDef queue(Context c, String id) {
        for(QueueDef q:getQueues(c)) if(q.id.equals(id)) return q;
        return getQueues(c).get(0);
    }

    public static String currentQueueId(Context c) {
        return p(c).getString("current_queue","current");
    }

    public static QueueDef currentQueue(Context c) { return queue(c,currentQueueId(c)); }

    public static void setCurrentQueue(Context c, String id) {
        p(c).edit().putString("current_queue",id).apply();
    }

    public static void replaceCurrentQueue(Context c, List<String> uris) {
        ArrayList<QueueDef> qs=getQueues(c);
        QueueDef q=null;
        String id=currentQueueId(c);
        for(QueueDef x:qs) if(x.id.equals(id)) q=x;
        if(q==null){ q=qs.get(0); setCurrentQueue(c,q.id); }
        q.items.clear(); q.items.addAll(uris); saveQueues(c,qs);
    }

    public static void addToQueue(Context c, String queueId, String uri, boolean next) {
        ArrayList<QueueDef> qs=getQueues(c);
        for(QueueDef q:qs) if(q.id.equals(queueId)) {
            if(next) q.items.add(0,uri); else q.items.add(uri);
        }
        saveQueues(c,qs);
    }

    public static void addRecent(Context c, String uri) {
        if(uri==null||uri.isEmpty()) return;
        ArrayList<String> r=getRecent(c); r.remove(uri); r.add(0,uri);
        while(r.size()>60) r.remove(r.size()-1);
        JSONArray a=new JSONArray(); for(String s:r)a.put(s);
        p(c).edit().putString("recent",a.toString()).apply();
    }

    public static ArrayList<String> getRecent(Context c) {
        ArrayList<String> out=new ArrayList<>();
        try { JSONArray a=new JSONArray(p(c).getString("recent","[]")); for(int i=0;i<a.length();i++)out.add(a.optString(i)); } catch(Exception ignored){}
        return out;
    }

    public static void saveProgress(Context c, String uri, long pos, long dur) {
        if(uri==null) return;
        try {
            JSONObject o=new JSONObject(p(c).getString("progress","{}"));
            JSONObject v=new JSONObject(); v.put("p",Math.max(0,pos)); v.put("d",Math.max(0,dur)); v.put("t",System.currentTimeMillis());
            o.put(uri,v); p(c).edit().putString("progress",o.toString()).apply();
        } catch(Exception ignored){}
    }

    public static long[] progress(Context c, String uri) {
        try {
            JSONObject o=new JSONObject(p(c).getString("progress","{}")).optJSONObject(uri);
            if(o!=null) return new long[]{o.optLong("p",0),o.optLong("d",0),o.optLong("t",0)};
        } catch(Exception ignored){}
        return new long[]{0,0,0};
    }

    public static void addBookmark(Context c, String uri, long pos) {
        try {
            JSONObject all=new JSONObject(p(c).getString("bookmarks","{}"));
            JSONArray a=all.optJSONArray(uri); if(a==null)a=new JSONArray(); a.put(pos); all.put(uri,a);
            p(c).edit().putString("bookmarks",all.toString()).apply();
        } catch(Exception ignored){}
    }

    public static ArrayList<Long> bookmarks(Context c, String uri) {
        ArrayList<Long> out=new ArrayList<>();
        try { JSONArray a=new JSONObject(p(c).getString("bookmarks","{}")).optJSONArray(uri); if(a!=null)for(int i=0;i<a.length();i++)out.add(a.optLong(i)); }catch(Exception ignored){}
        return out;
    }

    public static void setSpeed(Context c, String uri, float speed) {
        try { JSONObject o=new JSONObject(p(c).getString("speeds","{}")); o.put(uri,(double)speed); p(c).edit().putString("speeds",o.toString()).apply(); }catch(Exception ignored){}
    }

    public static float speed(Context c, String uri) {
        try { return (float)new JSONObject(p(c).getString("speeds","{}")).optDouble(uri,1.0); }catch(Exception e){return 1f;}
    }
}
