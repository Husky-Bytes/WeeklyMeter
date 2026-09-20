package android.app;
public class Service extends android.content.Context {
 public static final int START_NOT_STICKY=2,STOP_FOREGROUND_REMOVE=1;
 public int foregroundCalls,foregroundRemoved,destroyCalls;public boolean blockForeground;public final java.util.List<Integer> stopped=new java.util.ArrayList<>();
 public android.os.IBinder onBind(android.content.Intent intent){return null;}
 public int onStartCommand(android.content.Intent intent,int flags,int startId){return START_NOT_STICKY;}
 public void startForeground(int id,Notification notification){if(blockForeground)throw new IllegalStateException("Synthetic foreground blocked");foregroundCalls++;EVENTS.add("foreground");NOTIFICATIONS.last=notification;}
 public void startForeground(int id,Notification notification,int type){startForeground(id,notification);}
 public void stopForeground(int flags){foregroundRemoved++;}
 public boolean stopSelfResult(int id){stopped.add(id);return true;}
 public void onTimeout(int id,int type){}
 public void onDestroy(){destroyCalls++;}
}
