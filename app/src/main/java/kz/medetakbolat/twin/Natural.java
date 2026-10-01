package kz.medetakbolat.twin;

import java.util.Comparator;

public final class Natural {
    private Natural(){}

    public static int compare(String a,String b){
        if(a==null)a="";if(b==null)b="";
        int ia=0,ib=0,na=a.length(),nb=b.length();
        while(ia<na&&ib<nb){
            char ca=Character.toLowerCase(a.charAt(ia)),cb=Character.toLowerCase(b.charAt(ib));
            if(Character.isDigit(ca)&&Character.isDigit(cb)){
                int sa=ia,sb=ib;
                while(ia<na&&Character.isDigit(a.charAt(ia)))ia++;
                while(ib<nb&&Character.isDigit(b.charAt(ib)))ib++;
                String xa=a.substring(sa,ia),xb=b.substring(sb,ib);
                String ta=xa.replaceFirst("^0+(?!$)",""),tb=xb.replaceFirst("^0+(?!$)","");
                if(ta.length()!=tb.length())return Integer.compare(ta.length(),tb.length());
                int c=ta.compareTo(tb);if(c!=0)return c;
                if(xa.length()!=xb.length())return Integer.compare(xa.length(),xb.length());
            }else{
                if(ca!=cb)return Character.compare(ca,cb);
                ia++;ib++;
            }
        }
        return Integer.compare(na-ia,nb-ib);
    }

    public static final Comparator<MediaEntry> MEDIA_ASC=(a,b)->compare(a.title,b.title);
    public static final Comparator<MediaEntry> MEDIA_DESC=(a,b)->compare(b.title,a.title);
}
