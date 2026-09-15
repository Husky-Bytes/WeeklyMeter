package android.content;
public class Context {
    public static final int MODE_PRIVATE=0;
    public static final android.app.job.JobScheduler JOBS=new android.app.job.JobScheduler();
    public static final StylePrefs STYLE=new StylePrefs();
    public static final java.util.List<Intent> starts=new java.util.ArrayList<>(),stops=new java.util.ArrayList<>();
    public static boolean blockStart;
    public <T> T getSystemService(Class<T> type){return type.cast(JOBS);}
    public Context getApplicationContext(){return this;}
    public SharedPreferences getSharedPreferences(String name,int mode){return STYLE;}
    public ComponentName startForegroundService(Intent intent){if(blockStart)throw new IllegalStateException("Synthetic blocked start");starts.add(intent);return new ComponentName(this,intent.target);}
    public boolean stopService(Intent intent){stops.add(intent);return true;}
    public static final class StylePrefs implements SharedPreferences {
        public final java.util.Map<String,Object> data=new java.util.HashMap<>();
        public boolean getBoolean(String key,boolean fallback){Object value=data.get(key);return value instanceof Boolean?(Boolean)value:fallback;}
        public int getInt(String key,int fallback){Object value=data.get(key);return value instanceof Number?((Number)value).intValue():fallback;}
    }
}
