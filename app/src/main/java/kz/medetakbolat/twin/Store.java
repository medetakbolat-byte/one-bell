package kz.medetakbolat.twin;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Store {
    private static final String PREF="twin_store";
    private static final int DATA_VERSION=2;

    public static class Item {
        public String uri,section="";
        public Item(String u){uri=u;}
        public Item(String u,String s){uri=u;section=s==null?"":s;}
    }
    public static class Playlist {
        public String id=UUID.randomUUID().toString(),name="Playlist",cover="";
        public ArrayList<Item> items=new ArrayList<>();
        public ArrayList<String> sections=new ArrayList<>();
    }
    public static class CollectionDef {
        public String id=UUID.randomUUID().toString(),name="Collection",cover="";
        public ArrayList<Playlist> playlists=new ArrayList<>();
    }
    public static class QueueDef {
        public String id=UUID.randomUUID().toString(),name="Queue";
        public ArrayList<String> items=new ArrayList<>();
    }

    private static SharedPreferences p(Context c){return c.getSharedPreferences(PREF,Context.MODE_PRIVATE);}

    public static synchronized void ensure(Context c){
        int v=p(c).getInt("data_version",0);
        if(v<2){
            ArrayList<CollectionDef> cs=parseCollections(c);
            boolean looksLikeDemo=cs.size()==2 && "Teaching".equals(cs.get(0).name) && "Personal".equals(cs.get(1).name);
            if(looksLikeDemo){
                boolean empty=true;for(CollectionDef cd:cs)for(Playlist pl:cd.playlists)if(!pl.items.isEmpty())empty=false;
                if(empty)p(c).edit().putString("collections","[]").apply();
            }
            ArrayList<QueueDef> qs=parseQueues(c);
            if(qs.isEmpty()){QueueDef q=new QueueDef();q.id="current";q.name="Current";qs.add(q);saveQueues(c,qs);}
            boolean has=false;for(QueueDef q:qs)if("current".equals(q.id))has=true;
            if(!has){QueueDef q=new QueueDef();q.id="current";q.name="Current";qs.add(0,q);saveQueues(c,qs);}
            p(c).edit().putString("current_queue","current").putInt("data_version",DATA_VERSION).apply();
        }
    }

    private static ArrayList<CollectionDef> parseCollections(Context c){
        ArrayList<CollectionDef> out=new ArrayList<>();
        try{
            JSONArray a=new JSONArray(p(c).getString("collections","[]"));
            for(int i=0;i<a.length();i++){
                JSONObject o=a.getJSONObject(i);CollectionDef cd=new CollectionDef();
                cd.id=o.optString("id",cd.id);cd.name=o.optString("name","Collection");cd.cover=o.optString("cover","");
                JSONArray ps=o.optJSONArray("playlists");if(ps!=null)for(int j=0;j<ps.length();j++)cd.playlists.add(parsePlaylist(ps.getJSONObject(j)));
                out.add(cd);
            }
        }catch(Exception ignored){}
        return out;
    }

    private static Playlist parsePlaylist(JSONObject o){
        Playlist pl=new Playlist();pl.id=o.optString("id",pl.id);pl.name=o.optString("name","Playlist");pl.cover=o.optString("cover","");
        JSONArray ss=o.optJSONArray("sections");if(ss!=null)for(int i=0;i<ss.length();i++)pl.sections.add(ss.optString(i));
        JSONArray is=o.optJSONArray("items");if(is!=null)for(int i=0;i<is.length();i++){
            Object raw=is.opt(i);
            if(raw instanceof JSONObject){JSONObject x=(JSONObject)raw;pl.items.add(new Item(x.optString("uri",""),x.optString("section","")));}
            else if(raw!=null)pl.items.add(new Item(String.valueOf(raw)));
        }return pl;
    }

    private static ArrayList<QueueDef> parseQueues(Context c){
        ArrayList<QueueDef> out=new ArrayList<>();
        try{
            JSONArray a=new JSONArray(p(c).getString("queues","[]"));
            for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);QueueDef q=new QueueDef();q.id=o.optString("id",q.id);q.name=o.optString("name","Queue");JSONArray is=o.optJSONArray("items");if(is!=null)for(int j=0;j<is.length();j++)q.items.add(is.optString(j));out.add(q);}
        }catch(Exception ignored){}return out;
    }

    public static ArrayList<CollectionDef> getCollections(Context c){ensure(c);return parseCollections(c);}
    public static void saveCollections(Context c,List<CollectionDef> cs){
        JSONArray a=new JSONArray();try{
            for(CollectionDef cd:cs){JSONObject o=new JSONObject();o.put("id",cd.id);o.put("name",cd.name);o.put("cover",cd.cover);JSONArray ps=new JSONArray();
                for(Playlist pl:cd.playlists){JSONObject po=new JSONObject();po.put("id",pl.id);po.put("name",pl.name);po.put("cover",pl.cover);JSONArray ss=new JSONArray();for(String s:pl.sections)ss.put(s);po.put("sections",ss);JSONArray items=new JSONArray();
                    for(Item it:pl.items){JSONObject io=new JSONObject();io.put("uri",it.uri);io.put("section",it.section);items.put(io);}po.put("items",items);ps.put(po);}
                o.put("playlists",ps);a.put(o);}
        }catch(Exception ignored){}p(c).edit().putString("collections",a.toString()).apply();
    }

    public static ArrayList<QueueDef> getQueues(Context c){ensure(c);return parseQueues(c);}
    public static void saveQueues(Context c,List<QueueDef> qs){
        JSONArray a=new JSONArray();try{for(QueueDef q:qs){JSONObject o=new JSONObject();o.put("id",q.id);o.put("name",q.name);JSONArray is=new JSONArray();for(String s:q.items)is.put(s);o.put("items",is);a.put(o);}}catch(Exception ignored){}
        p(c).edit().putString("queues",a.toString()).apply();
    }
    public static QueueDef queue(Context c,String id){for(QueueDef q:getQueues(c))if(q.id.equals(id))return q;return getQueues(c).get(0);}
    public static String currentQueueId(Context c){ensure(c);return p(c).getString("current_queue","current");}
    public static void setCurrentQueue(Context c,String id){p(c).edit().putString("current_queue",id).apply();}
    public static QueueDef currentQueue(Context c){return queue(c,currentQueueId(c));}
    public static void replaceQueue(Context c,String id,List<String> uris){
        ArrayList<QueueDef> qs=getQueues(c);QueueDef target=null;for(QueueDef q:qs)if(q.id.equals(id))target=q;
        if(target==null){target=new QueueDef();target.id=id;target.name="Current";qs.add(0,target);}
        target.items.clear();target.items.addAll(uris);saveQueues(c,qs);
    }
    public static void replaceCurrentQueue(Context c,List<String> uris){replaceQueue(c,currentQueueId(c),uris);}
    public static void addToQueue(Context c,String id,String uri,boolean next){
        ArrayList<QueueDef> qs=getQueues(c);for(QueueDef q:qs)if(q.id.equals(id)){
            if(next){String now=nowPlaying(c);int i=q.items.indexOf(now);q.items.add(i>=0?i+1:0,uri);}else q.items.add(uri);
        }saveQueues(c,qs);
    }

    public static void setNowPlaying(Context c,String uri){p(c).edit().putString("now_playing",uri==null?"":uri).apply();}
    public static String nowPlaying(Context c){return p(c).getString("now_playing","");}

    public static void addRecent(Context c,String uri){
        if(uri==null||uri.isEmpty())return;ArrayList<String> r=getRecent(c);r.remove(uri);r.add(0,uri);while(r.size()>120)r.remove(r.size()-1);
        JSONArray a=new JSONArray();for(String s:r)a.put(s);p(c).edit().putString("recent",a.toString()).apply();
    }
    public static ArrayList<String> getRecent(Context c){
        ArrayList<String> out=new ArrayList<>();try{JSONArray a=new JSONArray(p(c).getString("recent","[]"));for(int i=0;i<a.length();i++){String s=a.optString(i);if(!s.isEmpty())out.add(s);}}catch(Exception ignored){}return out;
    }

    public static void setAlias(Context c,String uri,String name){putMapString(c,"aliases",uri,name);}
    public static String alias(Context c,String uri){return getMapString(c,"aliases",uri,"");}
    public static void setMediaCover(Context c,String uri,String cover){putMapString(c,"media_covers",uri,cover);}
    public static String mediaCover(Context c,String uri){return getMapString(c,"media_covers",uri,"");}
    private static void putMapString(Context c,String key,String k,String v){try{JSONObject o=new JSONObject(p(c).getString(key,"{}"));if(v==null||v.isEmpty())o.remove(k);else o.put(k,v);p(c).edit().putString(key,o.toString()).apply();}catch(Exception ignored){}}
    private static String getMapString(Context c,String key,String k,String def){try{return new JSONObject(p(c).getString(key,"{}")).optString(k,def);}catch(Exception e){return def;}}

    public static void saveProgress(Context c,String uri,long pos,long dur){
        if(uri==null)return;try{JSONObject o=new JSONObject(p(c).getString("progress","{}"));JSONObject v=new JSONObject();v.put("p",Math.max(0,pos));v.put("d",Math.max(0,dur));v.put("t",System.currentTimeMillis());o.put(uri,v);p(c).edit().putString("progress",o.toString()).apply();}catch(Exception ignored){}
    }
    public static long[] progress(Context c,String uri){try{JSONObject o=new JSONObject(p(c).getString("progress","{}")).optJSONObject(uri);if(o!=null)return new long[]{o.optLong("p"),o.optLong("d"),o.optLong("t")};}catch(Exception ignored){}return new long[]{0,0,0};}

    public static void addBookmark(Context c,String uri,long pos){try{JSONObject all=new JSONObject(p(c).getString("bookmarks","{}"));JSONArray a=all.optJSONArray(uri);if(a==null)a=new JSONArray();a.put(pos);all.put(uri,a);p(c).edit().putString("bookmarks",all.toString()).apply();}catch(Exception ignored){}}
    public static ArrayList<Long> bookmarks(Context c,String uri){ArrayList<Long>out=new ArrayList<>();try{JSONArray a=new JSONObject(p(c).getString("bookmarks","{}")).optJSONArray(uri);if(a!=null)for(int i=0;i<a.length();i++)out.add(a.optLong(i));}catch(Exception ignored){}return out;}

    public static void setSpeed(Context c,String uri,float speed){try{JSONObject o=new JSONObject(p(c).getString("speeds","{}"));o.put(uri,(double)speed);p(c).edit().putString("speeds",o.toString()).apply();}catch(Exception ignored){}}
    public static float speed(Context c,String uri){try{return(float)new JSONObject(p(c).getString("speeds","{}")).optDouble(uri,1.0);}catch(Exception e){return 1f;}}

    public static void setBackgroundVideo(Context c,boolean b){p(c).edit().putBoolean("background_video",b).apply();}
    public static boolean backgroundVideo(Context c){return p(c).getBoolean("background_video",false);}
    public static void setLibraryGrid(Context c,boolean b){p(c).edit().putBoolean("library_grid",b).apply();}
    public static boolean libraryGrid(Context c){return p(c).getBoolean("library_grid",true);}
}
