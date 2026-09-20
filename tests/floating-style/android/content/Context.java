package android.content;

import java.util.*;

/** Small, strict preference fake: wrong types throw just as Android's implementation does. */
public class Context {
    public static final int MODE_PRIVATE=0;
    public final Map<String,MemoryPreferences> files=new HashMap<>();
    public SharedPreferences getSharedPreferences(String name,int mode){
        if(mode!=MODE_PRIVATE)throw new AssertionError("Preferences must remain private");
        MemoryPreferences p=files.get(name);if(p==null){p=new MemoryPreferences();files.put(name,p);}return p;
    }
    public static final class MemoryPreferences implements SharedPreferences {
        public final Map<String,Object> values=new HashMap<>();
        public int writes;
        public boolean failRead;
        private Object read(String key,Object fallback){if(failRead)throw new IllegalStateException("Preference read failure");return values.containsKey(key)?values.get(key):fallback;}
        public String getString(String key,String fallback){return (String)read(key,fallback);}
        public int getInt(String key,int fallback){return (Integer)read(key,fallback);}
        public boolean getBoolean(String key,boolean fallback){return (Boolean)read(key,fallback);}
        public Editor edit(){return new Editor(){
            private final Map<String,Object> pending=new HashMap<>();
            public Editor putString(String key,String value){pending.put(key,value);return this;}
            public Editor putInt(String key,int value){pending.put(key,value);return this;}
            public Editor putBoolean(String key,boolean value){pending.put(key,value);return this;}
            public void apply(){values.putAll(pending);writes++;}
        };}
    }
}
