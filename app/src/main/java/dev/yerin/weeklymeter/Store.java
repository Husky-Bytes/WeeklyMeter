package dev.yerin.weeklymeter;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;

/** Non-secret, local widget display cache; credentials belong exclusively to Vault. */
final class Store {
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("display",Context.MODE_PRIVATE);}
    static boolean connected(Context c){return prefs(c).getBoolean("connected",false);}
    static List<Usage> meters(Context c){
        List<Usage> list=new ArrayList<>();
        try{for(Object item:Json.array(Json.parse(prefs(c).getString("meters","[]"))))list.add(Usage.fromMap(Json.object(item)));}catch(Exception ignored){list.clear();}
        return list;
    }
    static Usage selected(Context c){String id=prefs(c).getString("selected","codex");for(Usage u:meters(c))if(u.id.equals(id))return u;return null;}
    static void save(Context c,List<Usage> list){
        List<Object> encoded=new ArrayList<>();for(Usage u:list)encoded.add(u.toMap());
        // Do not silently switch to a different bucket if a user's chosen bucket disappears.
        prefs(c).edit().putString("meters",Json.encode(encoded)).putString("error","").putLong("backoff",0).apply();
    }
    static String error(Context c){return Messages.localize(prefs(c).getString("error",""),Texts.locale(c));}
    static void error(Context c,String msg){prefs(c).edit().putString("error",msg).apply();}
    private Store(){}
}
