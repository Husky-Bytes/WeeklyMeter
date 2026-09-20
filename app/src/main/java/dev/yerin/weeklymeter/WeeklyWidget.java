package dev.yerin.weeklymeter;

import android.app.PendingIntent;
import android.appwidget.*;
import android.content.*;
import android.os.Build;
import android.os.Bundle;
import android.util.SizeF;
import android.view.View;
import android.widget.RemoteViews;
import java.util.*;

public final class WeeklyWidget extends AppWidgetProvider {
    static final String REFRESH="dev.yerin.weeklymeter.REFRESH";
    private static final Object RENDER_LOCK=new Object();
    @Override public void onUpdate(Context c,AppWidgetManager manager,int[] ids){renderAll(c);Scheduler.ensure(c);}
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager manager,int id,Bundle options){render(c,manager,id);}
    @Override public void onDisabled(Context c){Scheduler.ensure(c);}
    @Override public void onReceive(Context c,Intent i){super.onReceive(c,i);if(REFRESH.equals(i.getAction())){Scheduler.request(c);renderAll(c);}}
    static WidgetStyle style(Context c){return WidgetAppearance.load(c,"widget_style");}
    static void saveStyle(Context c,WidgetStyle original){
        WidgetAppearance.save(c,"widget_style",original);renderAll(c);
    }
    static void renderAll(Context c){
        // Initialize locale before taking the render lock. Language selection can
        // repaint while holding AppLanguage's monitor; its cached locale is then
        // lock-free, avoiding the reverse lock order during a cold service start.
        Texts.locale(c);
        try{synchronized(RENDER_LOCK){
            AppWidgetManager manager=AppWidgetManager.getInstance(c);
            RuntimeException failure=null;
            for(int id:manager.getAppWidgetIds(new ComponentName(c,WeeklyWidget.class))){
                try{renderLocked(c,manager,id);}
                catch(RuntimeException error){
                    // A stale or broken host instance must not prevent the others
                    // from receiving fresh data. Still report failure for retry.
                    if(failure==null)failure=error;
                    else if(failure!=error)failure.addSuppressed(error);
                }
            }
            if(failure!=null)throw failure;
        }}finally{FloatingWidgetService.repaint(c);}
    }
    static void render(Context c,AppWidgetManager manager,int id){
        Texts.locale(c);
        synchronized(RENDER_LOCK){renderLocked(c,manager,id);}
    }
    private static void renderLocked(Context c,AppWidgetManager manager,int id){
        // Read the cache only after earlier publications finish, so an older
        // resize/feedback render cannot overwrite a completed automatic refresh.
        Usage u=Store.selected(c);WidgetStyle s=style(c);Bundle options=manager.getAppWidgetOptions(id);
        // Phones normally supply two sizes; up to four handles orientation/foldable
        // variants while keeping the aggregate bitmap allocation below ~3.6 MB.
        if(Build.VERSION.SDK_INT>=31){
            ArrayList<SizeF> sizes=options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES);
            Map<SizeF,RemoteViews> variants=new LinkedHashMap<>();
            if(sizes!=null)for(SizeF size:sizes){
                if(size==null||!Float.isFinite(size.getWidth())||!Float.isFinite(size.getHeight())||size.getWidth()<=0||size.getHeight()<=0)continue;
                variants.put(size,views(c,u,s,size.getWidth(),size.getHeight(),true,id));if(variants.size()>=4)break;
            }
            if(!variants.isEmpty()){manager.updateAppWidget(id,new RemoteViews(variants));return;}
        }
        float minW=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,64),minH=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,64);
        float maxW=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,(int)minW),maxH=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,(int)minH);
        manager.updateAppWidget(id,new RemoteViews(views(c,u,s,maxW,minH,true,id),views(c,u,s,minW,maxH,true,id)));
    }
    static RemoteViews views(Context c,Usage u,WidgetStyle s,float width,float height,boolean interactive){
        return views(c,u,s,width,height,interactive,-1);
    }
    private static RemoteViews views(Context c,Usage u,WidgetStyle s,float width,float height,boolean interactive,int widgetId){
        String state="none";if(interactive){RefreshFeedback.Snapshot snapshot=RefreshFeedback.snapshot(c);if(snapshot.visible)state=snapshot.state;}
        WidgetRenderer.Result result=WidgetRenderer.render(c,u,s,width,height,state);
        RemoteViews view=new RemoteViews(c.getPackageName(),R.layout.weekly_widget);
        view.setImageViewBitmap(R.id.widget_image,result.bitmap);view.setViewVisibility(R.id.preview_percent,View.GONE);
        if(interactive){
            // Direct user interaction grants a short foreground-service start window.
            // A JobScheduler queue can defer a manual tap until the app is foreground.
            Intent refresh=new Intent(c,WidgetRefreshService.class).setAction(WidgetRefreshService.ACTION_HOME_TAP).putExtra(WidgetRefreshService.EXTRA_APP_WIDGET_ID,widgetId);
            view.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getForegroundService(c,widgetId<0?1:widgetId,refresh,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
            view.setContentDescription(R.id.widget_root,result.accessibility+". "+Display.state(c,u)+". "+Texts.t(c,"누르면 새로고침 · 빠르게 3번 누르면 플로팅 위젯","Tap to refresh · Tap 3 times quickly for floating widget"));
        }
        return view;
    }
}
