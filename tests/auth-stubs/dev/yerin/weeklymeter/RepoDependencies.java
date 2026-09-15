package dev.yerin.weeklymeter;

import android.content.Context;
import java.util.*;

/** In-memory storage/scheduler doubles. Tests exercise real Repo and Api code. */
final class Vault {
    static Map<String,Object> state=new LinkedHashMap<>();
    static int writeFailuresRemaining,writeAttempts;
    Vault(Context context){}
    Map<String,Object> read(){return copy(state);}
    void write(Map<String,Object> value)throws java.io.IOException{
        writeAttempts++;
        if(writeFailuresRemaining>0){writeFailuresRemaining--;throw new java.io.IOException("Fixture storage failure");}
        state=copy(value);
    }
    void clear(){state.clear();}
    private static Map<String,Object> copy(Map<String,Object> value){return new LinkedHashMap<>(Json.object(Json.parse(Json.encode(value))));}
}
final class Store {
    static final Preferences values=new Preferences();
    static Runnable onSave;
    static Preferences prefs(Context context){return values;}
    static boolean connected(Context context){return values.getBoolean("connected",false);}
    static void save(Context context,List<Usage> list){values.data.put("saved_usage",list);if(onSave!=null)onSave.run();}
    static void error(Context context,String message){values.data.put("error",message);}
    static final class Preferences {
        final Map<String,Object> data=new LinkedHashMap<>();
        long getLong(String key,long fallback){Object value=data.get(key);return value instanceof Number?((Number)value).longValue():fallback;}
        boolean getBoolean(String key,boolean fallback){Object value=data.get(key);return value instanceof Boolean?(Boolean)value:fallback;}
        String getString(String key,String fallback){Object value=data.get(key);return value instanceof String?(String)value:fallback;}
        Editor edit(){return new Editor(this);}
    }
    static final class Editor {
        final Preferences prefs;
        Editor(Preferences prefs){this.prefs=prefs;}
        Editor putBoolean(String key,boolean value){prefs.data.put(key,value);return this;}
        Editor putString(String key,String value){prefs.data.put(key,value);return this;}
        Editor putLong(String key,long value){prefs.data.put(key,value);return this;}
        Editor remove(String key){prefs.data.remove(key);return this;}
        Editor clear(){prefs.data.clear();return this;}
        void apply(){}
    }
}
final class Scheduler {static void cancel(Context context){}}
