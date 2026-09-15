package android.content;
public final class Intent {
    public final Class<?> target;private String action;
    public Intent(Context context,Class<?> target){this.target=target;}
    public Intent setAction(String action){this.action=action;return this;}
    public String getAction(){return action;}
}
