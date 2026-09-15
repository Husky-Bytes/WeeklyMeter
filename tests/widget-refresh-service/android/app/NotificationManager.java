package android.app;
public class NotificationManager {public static final int IMPORTANCE_LOW=2;public Notification last;public boolean blockChannel;public void createNotificationChannel(NotificationChannel channel){if(blockChannel)throw new IllegalStateException("Synthetic channel blocked");}public void notify(int id,Notification value){last=value;}}
