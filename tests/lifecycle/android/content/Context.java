package android.content;
public class Context {
    public static final int MODE_PRIVATE=0;
    public static final android.app.job.JobScheduler JOBS=new android.app.job.JobScheduler();
    public static final StylePrefs STYLE=new StylePrefs();
    public static final StylePrefs FLOATING_STYLE=new StylePrefs();
    public static final StylePrefs DIAGNOSTICS=new StylePrefs();
    public static boolean failDiagnostics;
    public static final java.util.List<Intent> starts=new java.util.ArrayList<>(),stops=new java.util.ArrayList<>();
    public static boolean blockStart;
    public <T> T getSystemService(Class<T> type){return type.cast(JOBS);}
    public Context getApplicationContext(){return this;}
    public SharedPreferences getSharedPreferences(String name,int mode){
        if("auto_refresh_status".equals(name)){if(failDiagnostics)throw new IllegalStateException("Synthetic preference failure");return DIAGNOSTICS;}
        if("widget_style".equals(name))return STYLE;
        if("floating_style".equals(name))return FLOATING_STYLE;
        throw new AssertionError("Unexpected preference name: "+name);
    }
    public ComponentName startForegroundService(Intent intent){if(blockStart)throw new IllegalStateException("Synthetic blocked start");starts.add(intent);return new ComponentName(this,intent.target);}
    public boolean stopService(Intent intent){stops.add(intent);return true;}
    public static final class StylePrefs implements SharedPreferences {
        public final java.util.Map<String,Object> data=new java.util.HashMap<>();
        public boolean getBoolean(String key,boolean fallback){Object value=data.get(key);return value instanceof Boolean?(Boolean)value:fallback;}
        public int getInt(String key,int fallback){Object value=data.get(key);return value instanceof Number?((Number)value).intValue():fallback;}
        public long getLong(String key,long fallback){Object value=data.get(key);return value instanceof Number?((Number)value).longValue():fallback;}
        public String getString(String key,String fallback){Object value=data.get(key);return value instanceof String?(String)value:fallback;}
        public SharedPreferences.Editor edit(){return new SharedPreferences.Editor(){
            private final java.util.Map<String,Object> pending=new java.util.HashMap<>();
            public SharedPreferences.Editor putBoolean(String key,boolean value){pending.put(key,value);return this;}
            public SharedPreferences.Editor putInt(String key,int value){pending.put(key,value);return this;}
            public SharedPreferences.Editor putLong(String key,long value){pending.put(key,value);return this;}
            public SharedPreferences.Editor putString(String key,String value){pending.put(key,value);return this;}
            public void apply(){data.putAll(pending);}
        };}
    }
}
