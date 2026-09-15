package android.app;
public class Notification {
    public static final String CATEGORY_SERVICE="service";
    public static final int VISIBILITY_PRIVATE=0;
    public static class Builder {
        public Builder(android.content.Context context,String channel){}
        public Builder setSmallIcon(int v){return this;} public Builder setContentTitle(String v){return this;}
        public Builder setContentText(String v){return this;} public Builder setContentIntent(PendingIntent v){return this;}
        public Builder setOngoing(boolean v){return this;} public Builder setCategory(String v){return this;}
        public Builder setVisibility(int v){return this;} public Builder addAction(Action v){return this;}
        public Notification build(){return new Notification();}
    }
    public static class Action {
        public static class Builder {
            public Builder(Object icon,String title,PendingIntent intent){}
            public Action build(){return new Action();}
        }
    }
}
