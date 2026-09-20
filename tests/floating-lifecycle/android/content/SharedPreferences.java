package android.content;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public final class SharedPreferences {
    private final Map<String,Object> values=new HashMap<>();
    public final List<OnSharedPreferenceChangeListener> listeners=new ArrayList<>();
    public interface OnSharedPreferenceChangeListener { void onSharedPreferenceChanged(SharedPreferences prefs,String key); }
    public float getFloat(String key,float fallback){Object value=values.get(key);return value==null?fallback:(Float)value;}
    public int getInt(String key,int fallback){Object value=values.get(key);return value==null?fallback:(Integer)value;}
    public boolean getBoolean(String key,boolean fallback){Object value=values.get(key);return value==null?fallback:(Boolean)value;}
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener){listeners.add(listener);}
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener){listeners.remove(listener);}
    public Editor edit(){return new Editor();}
    public final class Editor {
        private final Map<String,Object> pending=new HashMap<>();
        public Editor putFloat(String key,float value){pending.put(key,value);return this;}
        public Editor putInt(String key,int value){pending.put(key,value);return this;}
        public Editor putBoolean(String key,boolean value){pending.put(key,value);return this;}
        public void apply(){values.putAll(pending);for(String key:pending.keySet())for(OnSharedPreferenceChangeListener listener:new ArrayList<>(listeners))listener.onSharedPreferenceChanged(SharedPreferences.this,key);}
    }
}
