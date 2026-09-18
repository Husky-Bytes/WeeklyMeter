package android.content;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
public class Context {
 public static final int MODE_PRIVATE=0;
 private final Map<String,SharedPreferences> prefs=new ConcurrentHashMap<>();
 public String getPackageName(){return "dev.yerin.weeklymeter";}
 public SharedPreferences getSharedPreferences(String name,int mode){return prefs.computeIfAbsent(name,k->new MemoryPreferences());}
 static final class MemoryPreferences implements SharedPreferences {
  private final Map<String,Object> values=new HashMap<>();
  public synchronized String getString(String key,String fallback){return (String)values.getOrDefault(key,fallback);}
  public synchronized boolean getBoolean(String key,boolean fallback){return (Boolean)values.getOrDefault(key,fallback);}
  public synchronized int getInt(String key,int fallback){return (Integer)values.getOrDefault(key,fallback);}
  public synchronized long getLong(String key,long fallback){return (Long)values.getOrDefault(key,fallback);}
  public Editor edit(){return new MemoryEditor();}
  final class MemoryEditor implements Editor {
   final Map<String,Object> pending=new HashMap<>();
   public Editor putString(String k,String v){pending.put(k,v);return this;}public Editor putBoolean(String k,boolean v){pending.put(k,v);return this;}
   public Editor putInt(String k,int v){pending.put(k,v);return this;}public Editor putLong(String k,long v){pending.put(k,v);return this;}
   public Editor remove(String k){pending.put(k,null);return this;}
   public void apply(){synchronized(MemoryPreferences.this){for(Map.Entry<String,Object> e:pending.entrySet()){if(e.getValue()==null)values.remove(e.getKey());else values.put(e.getKey(),e.getValue());}}}
  }
 }
}
