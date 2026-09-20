package dev.yerin.weeklymeter;
import android.content.Context;
import android.graphics.Bitmap;
import java.util.Locale;
import java.util.function.Consumer;
final class R {static final class layout {static final int weekly_widget=1;}static final class id {static final int widget_image=2,preview_percent=3,widget_root=4;}}
final class Texts {
 static volatile Locale current;static final Object LANGUAGE_LOCK=new Object();
 static Locale locale(Context c){Locale locale=current;if(locale==null){synchronized(LANGUAGE_LOCK){if(current==null)current=Locale.ENGLISH;locale=current;}}return locale;}
 static String t(Context c,String ko,String en){return "ko".equals(locale(c).getLanguage())?ko:en;}
}
final class Display {static String state(Context c,Usage u){return u==null?"empty":"latest";}}
final class Scheduler {static int requests,ensures;static boolean fail;static void ensure(Context c){ensures++;if(fail)throw new IllegalStateException("synthetic scheduler failure");}static void cancel(Context c){}static void request(Context c){requests++;}}
final class WidgetRefreshService {static final String ACTION_REFRESH="refresh",ACTION_HOME_TAP="home_tap",EXTRA_APP_WIDGET_ID="appWidgetId";}
final class FloatingWidgetService {static int repaints;static void repaint(Context c){repaints++;}}
final class RefreshFeedback {static boolean visible;static int individualPublications;static final class Snapshot {boolean visible;String state="none";}static Snapshot snapshot(Context c){Snapshot value=new Snapshot();value.visible=visible;value.state=visible?"success":"none";return value;}static void published(boolean floating,Snapshot snapshot){}static void homePublishedOne(Context c,Snapshot snapshot){individualPublications++;}}
final class WidgetRenderer {
 static volatile Consumer<Usage> beforeRender=u->{};
 static final class Result {final Bitmap bitmap;final String accessibility;Result(Bitmap bitmap,String accessibility){this.bitmap=bitmap;this.accessibility=accessibility;}}
 static Result render(Context c,Usage u,WidgetStyle s,float width,float height,String state){beforeRender.accept(u);return new Result(new Bitmap(u==null?"—%":u.percent(),u==null?0:u.fetchedAt,width,height,state),(u==null?"empty":u.percent()+" @ "+u.fetchedAt)+" "+Texts.locale(c).toLanguageTag());}
}
