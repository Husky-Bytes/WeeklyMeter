package android.content;
public class Intent {
    public static final String ACTION_LOCALE_CHANGED="android.intent.action.LOCALE_CHANGED";
    private final String action;
    public Intent(String value){action=value;}
    public String getAction(){return action;}
}
