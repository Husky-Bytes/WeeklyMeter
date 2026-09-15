package dev.yerin.weeklymeter;
import android.content.Context;
import java.util.*;
final class Store {
    static final Prefs values=new Prefs();
    static boolean connected(Context c){return values.getBoolean("connected",false);}
    static Prefs prefs(Context c){return values;}
    static void error(Context c,String text){values.data.put("error",text);}
    static final class Prefs {
        final Map<String,Object> data=new HashMap<>();
        boolean getBoolean(String key,boolean fallback){Object v=data.get(key);return v instanceof Boolean?(Boolean)v:fallback;}
        int getInt(String key,int fallback){Object v=data.get(key);return v instanceof Number?((Number)v).intValue():fallback;}
        long getLong(String key,long fallback){Object v=data.get(key);return v instanceof Number?((Number)v).longValue():fallback;}
        Prefs edit(){return this;}
        Prefs putLong(String key,long value){data.put(key,value);return this;}
        Prefs remove(String key){data.remove(key);return this;}
        void apply(){}
    }
}
