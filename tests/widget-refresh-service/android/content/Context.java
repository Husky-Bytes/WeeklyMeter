package android.content;
import java.util.*;
public class Context {
 public static final int MODE_PRIVATE=0;
 public static final android.app.job.JobScheduler JOBS=new android.app.job.JobScheduler();
 public static final android.app.NotificationManager NOTIFICATIONS=new android.app.NotificationManager();
 public static final List<String> EVENTS=Collections.synchronizedList(new ArrayList<>());
 public static final List<Intent> starts=new ArrayList<>(),stops=new ArrayList<>();
 public static boolean blockStart;
 public static java.util.function.Consumer<Intent> dispatch,stopDispatch;
 public static final StylePrefs STYLE=new StylePrefs(),FLOATING_STYLE=new StylePrefs();
 public Context getApplicationContext(){return this;}
 public <T>T getSystemService(Class<T> type){if(type==android.app.job.JobScheduler.class)return type.cast(JOBS);if(type==android.app.NotificationManager.class)return type.cast(NOTIFICATIONS);throw new AssertionError(type);}
 public SharedPreferences getSharedPreferences(String name,int mode){if("widget_style".equals(name))return STYLE;if("floating_style".equals(name))return FLOATING_STYLE;throw new AssertionError(name);}
 public ComponentName startForegroundService(Intent intent){if(blockStart)throw new IllegalStateException("Synthetic start blocked");starts.add(intent);EVENTS.add("startForegroundService");if(dispatch!=null)dispatch.accept(intent);return new ComponentName(this,intent.target);}
 public boolean stopService(Intent intent){stops.add(intent);if(stopDispatch!=null)stopDispatch.accept(intent);return true;}
 public static final class StylePrefs implements SharedPreferences {
  public final Map<String,Object> data=new HashMap<>();
  public boolean getBoolean(String key,boolean fallback){Object v=data.get(key);return v==null?fallback:(Boolean)v;}
  public int getInt(String key,int fallback){Object v=data.get(key);return v==null?fallback:(Integer)v;}
 }
}
