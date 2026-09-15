package android.appwidget;
import android.content.*;
public class AppWidgetManager {
    public static int[] ids={1};
    public static AppWidgetManager getInstance(Context c){return new AppWidgetManager();}
    public int[] getAppWidgetIds(ComponentName c){return ids;}
}
