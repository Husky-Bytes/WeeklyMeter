package android.app;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.IBinder;
public class Service extends Context {
    public static final int START_NOT_STICKY=2,START_STICKY=1,STOP_FOREGROUND_REMOVE=1;
    public int foregroundCalls,foregroundRemoved,stopCalls;
    public void onCreate(){}
    public IBinder onBind(Intent intent){return null;}
    public int onStartCommand(Intent intent,int flags,int startId){return START_NOT_STICKY;}
    public void startForeground(int id,Notification notification){foregroundCalls++;}
    public void startForeground(int id,Notification notification,int type){startForeground(id,notification);}
    public void stopForeground(int flags){foregroundRemoved++;}
    public void stopSelf(){stopCalls++;}
    public void onConfigurationChanged(Configuration configuration){}
    public void onDestroy(){}
}
