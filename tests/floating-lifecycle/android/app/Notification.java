package android.app;
import android.content.Context;
public final class Notification {
    public static final String CATEGORY_SERVICE="service";public static final int VISIBILITY_PRIVATE=0;
    public static final class Builder {
        public Builder(Context context,String channel){}
        public Builder setSmallIcon(int value){return this;}
        public Builder setContentTitle(String value){return this;}
        public Builder setContentText(String value){return this;}
        public Builder setContentIntent(PendingIntent value){return this;}
        public Builder setOngoing(boolean value){return this;}
        public Builder setOnlyAlertOnce(boolean value){return this;}
        public Builder setCategory(String value){return this;}
        public Builder setVisibility(int value){return this;}
        public Builder addAction(Action value){return this;}
        public Notification build(){return new Notification();}
    }
    public static final class Action { public static final class Builder {
        public Builder(Object icon,String title,PendingIntent intent){}
        public Action build(){return new Action();}
    }}
}
