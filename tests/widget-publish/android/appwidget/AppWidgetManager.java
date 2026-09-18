package android.appwidget;
import android.content.*;
import android.os.Bundle;
import android.widget.RemoteViews;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
public final class AppWidgetManager {
 public static final String OPTION_APPWIDGET_SIZES="sizes",OPTION_APPWIDGET_MIN_WIDTH="min_width",OPTION_APPWIDGET_MIN_HEIGHT="min_height",OPTION_APPWIDGET_MAX_WIDTH="max_width",OPTION_APPWIDGET_MAX_HEIGHT="max_height";
 public static final AppWidgetManager INSTANCE=new AppWidgetManager();
 public volatile int[] ids=new int[0];public final Map<Integer,Bundle> options=new ConcurrentHashMap<>();public final Map<Integer,RemoteViews> published=new ConcurrentHashMap<>();
 public final Map<Integer,RuntimeException> optionFailures=new ConcurrentHashMap<>(),publishFailures=new ConcurrentHashMap<>();public final List<Integer> attempts=Collections.synchronizedList(new ArrayList<>());
 public static AppWidgetManager getInstance(Context c){return INSTANCE;}
 public int[] getAppWidgetIds(ComponentName name){return ids.clone();}
 public Bundle getAppWidgetOptions(int id){RuntimeException error=optionFailures.get(id);if(error!=null)throw error;return options.getOrDefault(id,new Bundle());}
 public void updateAppWidget(int id,RemoteViews value){attempts.add(id);RuntimeException error=publishFailures.get(id);if(error!=null)throw error;published.put(id,value);}
 public void reset(){ids=new int[0];options.clear();published.clear();optionFailures.clear();publishFailures.clear();attempts.clear();}
}
