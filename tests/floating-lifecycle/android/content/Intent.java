package android.content;
public final class Intent {
    public static final String ACTION_SCREEN_OFF="screen-off",ACTION_SCREEN_ON="screen-on",ACTION_USER_PRESENT="user-present";
    public static final String ACTION_TIME_CHANGED="time-changed",ACTION_TIMEZONE_CHANGED="timezone-changed";
    public static final int FLAG_ACTIVITY_NEW_TASK=0x10000000;
    public final Class<?> target;private String action;
    public Intent(String action){this.target=null;this.action=action;}
    public Intent(Context context,Class<?> target){this.target=target;}
    public Intent setAction(String action){this.action=action;return this;}
    public String getAction(){return action;}
    public Intent putExtra(String key,boolean value){return this;}
    public Intent addFlags(int flags){return this;}
}
