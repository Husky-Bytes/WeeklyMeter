package android.app;
public class Notification {
 public static final String CATEGORY_SERVICE="service";public static final int VISIBILITY_PRIVATE=0;
 public String text="";public int icon;public boolean ongoing;
 public static final class Builder {
  private final Notification value=new Notification();
  public Builder(android.content.Context context,String channel){}
  public Builder setSmallIcon(int icon){value.icon=icon;return this;}
  public Builder setContentTitle(String text){return this;}
  public Builder setContentText(String text){value.text=text;return this;}
  public Builder setOnlyAlertOnce(boolean value){return this;}
  public Builder setOngoing(boolean ongoing){value.ongoing=ongoing;return this;}
  public Builder setCategory(String text){return this;}
  public Builder setVisibility(int value){return this;}
  public Notification build(){return value;}
 }
}
