package android.content;
public class Context {
    public final android.app.NotificationManager notifications = new android.app.NotificationManager();
    public <T> T getSystemService(Class<T> kind) { return kind.cast(notifications); }
    public Intent startService(Intent intent) { return intent; }
}
