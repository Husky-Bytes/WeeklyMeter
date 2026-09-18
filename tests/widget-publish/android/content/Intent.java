package android.content;
public class Intent {public final Class<?> target;private String action;public Intent(Context c,Class<?> target){this.target=target;}public Intent setAction(String value){action=value;return this;}public String getAction(){return action;}}
