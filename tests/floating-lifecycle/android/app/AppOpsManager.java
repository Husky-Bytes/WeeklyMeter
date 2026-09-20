package android.app;
public final class AppOpsManager {
    public static final String OPSTR_SYSTEM_ALERT_WINDOW="android:system_alert_window";
    public interface OnOpChangedListener { void onOpChanged(String operation,String packageName); }
    public OnOpChangedListener listener;
    public void startWatchingMode(String operation,String packageName,OnOpChangedListener listener){this.listener=listener;}
    public void stopWatchingMode(OnOpChangedListener listener){if(this.listener==listener)this.listener=null;}
    public void notifyChanged(){if(listener!=null)listener.onOpChanged(OPSTR_SYSTEM_ALERT_WINDOW,"dev.yerin.weeklymeter");}
}
