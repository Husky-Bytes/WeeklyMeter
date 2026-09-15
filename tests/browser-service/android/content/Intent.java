package android.content;
public class Intent {
    public static final int FLAG_ACTIVITY_CLEAR_TOP=1, FLAG_ACTIVITY_SINGLE_TOP=2;
    private String action;
    public Intent(Context context, Class<?> target) { }
    public Intent addFlags(int flags) { return this; }
    public Intent setAction(String action) { this.action=action; return this; }
    public String getAction() { return action; }
}
