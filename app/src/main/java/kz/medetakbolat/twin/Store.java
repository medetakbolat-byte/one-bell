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
    private static final int DATA_VERSION=3;
    public static final String ROOT_COLLECTION="__root__";

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
                boolean empty=true;
                for(CollectionDef cd:cs)for(Playlist pl:cd.playlists)if(!pl.items.isEmpty())empty=false;
                if(empty)p(c).edit().putString("collections","[]").apply();
            }
        }

        ArrayList<QueueDef> qs=parseQueues(c);
        if(qs.isEmpty()){
            QueueDef q=new QueueDef();q.id="current";q.name="Current";qs.add(q);saveQueuesRaw(c,qs);
        }
        boolean hasCurrent=false;for(QueueDef q:qs)if("current".equals(q.id))hasCurrent=true;
        if(!hasCurrent){
            QueueDef q=new QueueDef();q.id="current";q.name="Current";qs.add(0,q);saveQueuesRaw(c,qs);
        }

        ArrayList<CollectionDef> cs=parseCollections(c);
        boolean hasRoot=false;for(CollectionDef x:cs)if(ROOT_COLLECTION.equals(x.id))hasRoot=true;
        if(!hasRoot){
            CollectionDef root=new CollectionDef();root.id=ROOT_COLLECTION;root.name="Playlists";root.cover="";
            cs.add(0,root);saveCollectionsRaw(c,cs);
        }

        p(c).edit().putString("current_queue",p(c).getString("current_queue","current"))
                .putInt("data_version",DATA_VERSION).apply();
    }

    private static ArrayList<CollectionDef> parseCollections(Context c){
        ArrayList<CollectionDef> out=new ArrayList<>();
        try{
            JSONArray a=new JSONArray(p(c).getString("collections","[]"));
            for(int i=0;i<a.length();i++){
                JSONObject o=a.getJSONObject(i);CollectionDef cd=new CollectionDef();
                cd.id=o.optString("id",cd.id);cd.name=o.optString("name","Collection");cd.cover=o.optString("cover","");
                JSONArray ps=o.optJSONArray("playlists");
                if(ps!=null)for(int j=0;j<ps.length();j++)cd.playlists.add(parsePlaylist(ps.getJSONObject(j)));
                out.add(cd);
            }
        }catch(Exception ignored){}
        return out;
    }

    private static Playlist parsePlaylist(JSONObject o){
        Playlist pl=new Playlist();pl.id=o.optString("id",pl.id);pl.name=o.optString("name","Playlist");pl.cover=o.optString("cover","");
        JSONArray ss=o.optJSONArray("sections");if(ss!=null)for(int i=0;i<ss.length();i++){String s=ss.optString(i);if(!s.isEmpty())pl.sections.add(s);}
        JSONArray is=o.optJSONArray("items");if(is!=null)for(int i=0;i<is.length();i++){
            Object raw=is.opt(i);
            if(raw instanceof JSONObject){JSONObject x=(JSONObject)raw;String uri=x.optString("uri","");if(!uri.isEmpty())pl.items.add(new Item(uri,x.optString("section","")));}
            else if(raw!=null){String uri=String.valueOf(raw);if(!uri.isEmpty())pl.items.add(new Item(uri));}
        }
        return pl;
    }

    private static ArrayList<QueueDef> parseQueues(Context c){
        ArrayList<QueueDef> out=new ArrayList<>();
        try{
            JSONArray a=new JSONArray(p(c).getString("queues","[]"));
            for(int i=0;i<a.length();i++){
                JSONObject o=a.getJSONObject(i);QueueDef q=new QueueDef();
                q.id=o.optString("id",q.id);q.name=o.optString("name","Queue");
                JSONArray is=o.optJSONArray("items");if(is!=null)for(int j=0;j<is.length();j++){String u=is.optString(j);if(!u.isEmpty())q.items.add(u);}
                out.add(q);
            }
        }catch(Exception ignored){}
        return out;
    }

    private static void saveCollectionsRaw(Context c,List<CollectionDef> cs){
        JSONArray a=new JSONArray();
        try{
            for(CollectionDef cd:cs){
                JSONObject o=new JSONObject();o.put("id",cd.id);o.put("name",cd.name);o.put("cover",cd.cover);
                JSONArray ps=new JSONArray();
                for(Playlist pl:cd.playlists){
                    JSONObject po=new JSONObject();po.put("id",pl.id);po.put("name",pl.name);po.put("cover",pl.cover);
                    JSONArray ss=new JSONArray();for(String s:pl.sections)ss.put(s);po.put("sections",ss);
                    JSONArray items=new JSONArray();for(Item it:pl.items){JSONObject io=new JSONObject();io.put("uri",it.uri);io.put("section",it.section);items.put(io);}
                    po.put("items",items);ps.put(po);
                }
                o.put("playlists",ps);a.put(o);
            }
        }catch(Exception ignored){}
        p(c).edit().putString("collections",a.toString()).apply();
    }

    private static void saveQueuesRaw(Context c,List<QueueDef> qs){
        JSONArray a=new JSONArray();
        try{
            for(QueueDef q:qs){
                JSONObject o=new JSONObject();o.put("id",q.id);o.put("name",q.name);
                JSONArray is=new JSONArray();for(String s:q.items)is.put(s);o.put("items",is);a.put(o);
            }
        }catch(Exception ignored){}
        p(c).edit().putString("queues",a.toString()).apply();
    }

    public static ArrayList<CollectionDef> getCollections(Context c){ensure(c);return parseCollections(c);}
    public static ArrayList<CollectionDef> visibleCollections(Context c){
        ArrayList<CollectionDef> out=new ArrayList<>();
        for(CollectionDef cd:getCollections(c))if(!ROOT_COLLECTION.equals(cd.id))out.add(cd);
        return out;
    }
    public static void saveCollections(Context c,List<CollectionDef> cs){saveCollectionsRaw(c,cs);}

    public static ArrayList<Playlist> allPlaylists(Context c){
        ArrayList<Playlist> out=new ArrayList<>();
        for(CollectionDef cd:getCollections(c))out.addAll(cd.playlists);
        return out;
    }
    public static Playlist findPlaylist(Context c,String playlistId){
        if(playlistId==null)return null;
        for(CollectionDef cd:getCollections(c))for(Playlist p:cd.playlists)if(playlistId.equals(p.id))return p;
        return null;
    }
    public static String collectionIdForPlaylist(Context c,String playlistId){
        for(CollectionDef cd:getCollections(c))for(Playlist p:cd.playlists)if(p.id.equals(playlistId))return cd.id;
        return ROOT_COLLECTION;
    }
    public static String collectionNameForPlaylist(Context c,String playlistId){
        for(CollectionDef cd:getCollections(c))for(Playlist p:cd.playlists)if(p.id.equals(playlistId))return ROOT_COLLECTION.equals(cd.id)?"No collection":cd.name;
        return "No collection";
    }
    public static Playlist createStandalonePlaylist(Context c,String name){
        ArrayList<CollectionDef> cs=getCollections(c);CollectionDef root=null;
        for(CollectionDef cd:cs)if(ROOT_COLLECTION.equals(cd.id))root=cd;
        if(root==null){root=new CollectionDef();root.id=ROOT_COLLECTION;root.name="Playlists";cs.add(0,root);}
        Playlist p=new Playlist();p.name=(name==null||name.trim().isEmpty())?"Playlist":name.trim();root.playlists.add(p);saveCollections(c,cs);return p;
    }
    public static CollectionDef createCollection(Context c,String name){
        ArrayList<CollectionDef> cs=getCollections(c);CollectionDef cd=new CollectionDef();cd.name=name;cs.add(cd);saveCollections(c,cs);return cd;
    }
    public static void movePlaylist(Context c,String playlistId,String targetCollectionId){
        ArrayList<CollectionDef> cs=getCollections(c);Playlist found=null;
        for(CollectionDef cd:cs){
            for(int i=0;i<cd.playlists.size();i++){
                if(cd.playlists.get(i).id.equals(playlistId)){found=cd.playlists.remove(i);break;}
            }
            if(found!=null)break;
        }
        if(found==null)return;
        String target=(targetCollectionId==null||targetCollectionId.isEmpty())?ROOT_COLLECTION:targetCollectionId;
        CollectionDef dest=null;for(CollectionDef cd:cs)if(cd.id.equals(target))dest=cd;
        if(dest==null){for(CollectionDef cd:cs)if(ROOT_COLLECTION.equals(cd.id))dest=cd;}
        if(dest==null){dest=new CollectionDef();dest.id=ROOT_COLLECTION;dest.name="Playlists";cs.add(0,dest);}
        dest.playlists.add(found);saveCollections(c,cs);
    }
    public static void deleteCollectionKeepPlaylists(Context c,String collectionId){
        if(collectionId==null||ROOT_COLLECTION.equals(collectionId))return;
        ArrayList<CollectionDef> cs=getCollections(c);CollectionDef root=null,target=null;
        for(CollectionDef cd:cs){if(ROOT_COLLECTION.equals(cd.id))root=cd;if(collectionId.equals(cd.id))target=cd;}
        if(target==null)return;if(root==null){root=new CollectionDef();root.id=ROOT_COLLECTION;root.name="Playlists";cs.add(0,root);}
        root.playlists.addAll(target.playlists);cs.remove(target);saveCollections(c,cs);
    }
    public static boolean playlistContains(Context c,String playlistId,String uri){
        Playlist p=findPlaylist(c,playlistId);if(p==null)return false;for(Item i:p.items)if(i.uri.equals(uri))return true;return false;
    }
    public static ArrayList<String> playlistUris(Context c,String playlistId){
        ArrayList<String> out=new ArrayList<>();Playlist p=findPlaylist(c,playlistId);if(p!=null)for(Item i:p.items)out.add(i.uri);return out;
    }
    public static String playlistCover(Context c,String playlistId){Playlist p=findPlaylist(c,playlistId);return p==null?"":p.cover;}

    public static ArrayList<QueueDef> getQueues(Context c){ensure(c);return parseQueues(c);}
    public static void saveQueues(Context c,List<QueueDef> qs){saveQueuesRaw(c,qs);}
    public static QueueDef queue(Context c,String id){
        ArrayList<QueueDef> qs=getQueues(c);for(QueueDef q:qs)if(q.id.equals(id))return q;
        return qs.isEmpty()?new QueueDef():qs.get(0);
    }
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

    public static void setPlaybackFallbackCover(Context c,String uri){p(c).edit().putString("playback_fallback_cover",uri==null?"":uri).apply();}
    public static String playbackFallbackCover(Context c){return p(c).getString("playback_fallback_cover","");}

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
    public static void setRepeatMode(Context c,int mode){p(c).edit().putInt("repeat_mode",mode).apply();}
    public static int repeatMode(Context c){return p(c).getInt("repeat_mode",0);}
    public static void setLastMainPage(Context c,int page){p(c).edit().putInt("last_main_page",page).apply();}
    public static int lastMainPage(Context c){return p(c).getInt("last_main_page",0);}
}
