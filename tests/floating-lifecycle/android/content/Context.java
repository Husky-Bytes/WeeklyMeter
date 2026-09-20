package android.content;
import android.app.AppOpsManager;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.content.res.Resources;
import android.os.PowerManager;
import android.view.WindowManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
public class Context {
    public static final int MODE_PRIVATE=0,RECEIVER_NOT_EXPORTED=4;
    public static final WindowManager WINDOW=new WindowManager();
    public static final KeyguardManager KEYGUARD=new KeyguardManager();
    public static final PowerManager POWER=new PowerManager();
    public static final AppOpsManager APP_OPS=new AppOpsManager();
    public static final NotificationManager NOTIFICATIONS=new NotificationManager();
    public static final List<Intent> starts=new ArrayList<>(),stops=new ArrayList<>();
    public static final Map<String,SharedPreferences> preferences=new HashMap<>();
    private final Map<BroadcastReceiver,IntentFilter> receivers=new LinkedHashMap<>();
    private final Resources resources=new Resources();
    public Context getApplicationContext(){return this;}
    public String getPackageName(){return "dev.yerin.weeklymeter";}
    public Resources getResources(){return resources;}
    public <T>T getSystemService(Class<T> type){
        Object service=type==WindowManager.class?WINDOW:type==KeyguardManager.class?KEYGUARD:type==PowerManager.class?POWER:type==AppOpsManager.class?APP_OPS:type==NotificationManager.class?NOTIFICATIONS:null;
        if(service==null)throw new AssertionError("Unstubbed system service: "+type);
        return type.cast(service);
    }
    public SharedPreferences getSharedPreferences(String name,int mode){return preferences.computeIfAbsent(name,key->new SharedPreferences());}
    public Object startForegroundService(Intent intent){starts.add(intent);return null;}
    public boolean stopService(Intent intent){stops.add(intent);return true;}
    public Intent registerReceiver(BroadcastReceiver receiver,IntentFilter filter){receivers.put(receiver,filter);return null;}
    public Intent registerReceiver(BroadcastReceiver receiver,IntentFilter filter,int flags){return registerReceiver(receiver,filter);}
    public void unregisterReceiver(BroadcastReceiver receiver){receivers.remove(receiver);}
    public void broadcast(String action){for(Map.Entry<BroadcastReceiver,IntentFilter> entry:new ArrayList<>(receivers.entrySet()))if(entry.getValue().actions.contains(action))entry.getKey().onReceive(this,new Intent(action));}
    public int receiverCount(){return receivers.size();}
    public static void reset(){WINDOW.reset();KEYGUARD.locked=false;POWER.interactive=true;APP_OPS.listener=null;starts.clear();stops.clear();preferences.clear();android.provider.Settings.allowed=true;}
}
