package android.content;
public class Intent {
 public final Class<?> target;private String action;private final java.util.Map<String,Integer> extras=new java.util.HashMap<>();
 public Intent(Context context,Class<?> target){this.target=target;}
 public Intent setAction(String action){this.action=action;return this;}
 public String getAction(){return action;}
 public Intent putExtra(String key,int value){extras.put(key,value);return this;}
 public int getIntExtra(String key,int fallback){Integer value=extras.get(key);return value==null?fallback:value;}
}
