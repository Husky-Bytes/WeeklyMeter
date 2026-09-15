package android.app;
public class Service extends android.content.Context {
    public static final int START_NOT_STICKY=2, STOP_FOREGROUND_REMOVE=1;
    public int foregroundCalls, stopped, foregroundRemoved;
    public boolean denyForeground;
    public android.os.IBinder onBind(android.content.Intent intent) { return null; }
    public int onStartCommand(android.content.Intent intent,int flags,int startId) { return START_NOT_STICKY; }
    public void startForeground(int id,Notification notification) { if(denyForeground)throw new IllegalStateException("Synthetic denial");foregroundCalls++; }
    public void startForeground(int id,Notification notification,int type) { startForeground(id,notification); }
    public void stopForeground(int flags) { foregroundRemoved++; }
    public void stopSelf() { stopped++; }
    public void onTimeout(int startId,int type) { }
    public void onDestroy() { }
}
