package android.widget;
import android.app.PendingIntent;
import android.graphics.Bitmap;
import android.util.SizeF;
import java.util.*;
public final class RemoteViews {
 public final Map<SizeF,RemoteViews> variants;public final RemoteViews landscape,portrait;public final int layout;
 public Bitmap bitmap;public PendingIntent click;public String description;public int placeholder=-1;
 public RemoteViews(String packageName,int layout){this.layout=layout;variants=null;landscape=null;portrait=null;}
 public RemoteViews(Map<SizeF,RemoteViews> variants){this.variants=new LinkedHashMap<>(variants);layout=0;landscape=null;portrait=null;}
 public RemoteViews(RemoteViews landscape,RemoteViews portrait){this.landscape=landscape;this.portrait=portrait;layout=0;variants=null;}
 public void setImageViewBitmap(int id,Bitmap bitmap){this.bitmap=bitmap;}
 public void setViewVisibility(int id,int visibility){placeholder=visibility;}
 public void setOnClickPendingIntent(int id,PendingIntent click){this.click=click;}
 public void setContentDescription(int id,CharSequence text){description=text.toString();}
}
