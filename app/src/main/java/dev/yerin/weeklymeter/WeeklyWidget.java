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
    @Override public void onUpdate(Context c,AppWidgetManager manager,int[] ids){repaintSafely(c);ensureSchedule(c);}
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager manager,int id,Bundle options){try{render(c,manager,id);}catch(RuntimeException unavailable){}}
    @Override public void onDisabled(Context c){ensureSchedule(c);}
    @Override public void onReceive(Context c,Intent i){super.onReceive(c,i);if(REFRESH.equals(i.getAction())){Scheduler.request(c);repaintSafely(c);}}
    static WidgetStyle style(Context c){return WidgetAppearance.load(c,"widget_style");}
    static void saveStyle(Context c,WidgetStyle original){
        if(WidgetAppearance.save(c,"widget_style",original))repaintSafely(c);
    }
    private static void repaintSafely(Context c){try{renderAll(c);}catch(RuntimeException unavailable){}}
    private static void ensureSchedule(Context c){try{Scheduler.ensure(c);}catch(RuntimeException unavailable){}}
    static void renderAll(Context c){
        renderFeedback(c,true,true);
    }
    static void renderFeedback(Context c,boolean home,boolean floating){
        // Initialize locale before taking the render lock. Language selection can
        // repaint while holding AppLanguage's monitor; its cached locale is then
        // lock-free, avoiding the reverse lock order during a cold service start.
        Texts.locale(c);
        try{if(home)synchronized(RENDER_LOCK){
            AppWidgetManager manager=AppWidgetManager.getInstance(c);
            Publication publication=new Publication(c);
            RuntimeException failure=null;
            for(int id:manager.getAppWidgetIds(new ComponentName(c,WeeklyWidget.class))){
                try{renderLocked(c,manager,id,publication);}
                catch(RuntimeException error){
                    // A stale or broken host instance must not prevent the others
                    // from receiving fresh data. Still report failure for retry.
                    if(failure==null)failure=error;
                    else if(failure!=error)failure.addSuppressed(error);
                }
            }
            if(failure!=null)throw failure;
            RefreshFeedback.published(false,publication.feedback);
        }}finally{if(floating)FloatingWidgetService.repaint(c);}
    }
    static void render(Context c,AppWidgetManager manager,int id){
        Texts.locale(c);
        synchronized(RENDER_LOCK){renderLocked(c,manager,id,new Publication(c));}
    }
    private static final class Publication {
        final Usage usage;final WidgetStyle style;final RefreshFeedback.Snapshot feedback;
        // Bounded to four renderer results for this publication only. RemoteViews
        // retains its bitmap; no retained cache can outlive data/style/locale changes.
        final Map<SizeF,WidgetRenderer.Result> images=new LinkedHashMap<SizeF,WidgetRenderer.Result>(4,.75f,true){
            @Override protected boolean removeEldestEntry(Map.Entry<SizeF,WidgetRenderer.Result> entry){return size()>4;}
        };
        Publication(Context c){usage=Store.selected(c);style=style(c);feedback=RefreshFeedback.snapshot(c);}
        WidgetRenderer.Result image(Context c,float width,float height){
            SizeF size=new SizeF(width,height);WidgetRenderer.Result result=images.get(size);
            if(result==null){result=WidgetRenderer.render(c,usage,style,width,height,feedback.visible?feedback.state:"none");images.put(size,result);}
            return result;
        }
    }
    private static void renderLocked(Context c,AppWidgetManager manager,int id,Publication publication){
        // Read the cache only after earlier publications finish, so an older
        // resize/feedback render cannot overwrite a completed automatic refresh.
        Bundle options=manager.getAppWidgetOptions(id);
        // Phones normally supply two sizes; up to four handles orientation/foldable
        // variants while keeping the aggregate bitmap allocation below ~3.6 MB.
        if(Build.VERSION.SDK_INT>=31){
            ArrayList<SizeF> sizes=options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES);
            Map<SizeF,RemoteViews> variants=new LinkedHashMap<>();
            if(sizes!=null)for(SizeF size:sizes){
                if(size==null||!Float.isFinite(size.getWidth())||!Float.isFinite(size.getHeight())||size.getWidth()<=0||size.getHeight()<=0)continue;
                if(variants.containsKey(size))continue;
                variants.put(size,views(c,publication.usage,publication.image(c,size.getWidth(),size.getHeight()),true,id));if(variants.size()>=4)break;
            }
            if(!variants.isEmpty()){manager.updateAppWidget(id,new RemoteViews(variants));RefreshFeedback.homePublishedOne(c,publication.feedback);return;}
        }
        float minW=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,64),minH=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,64);
        float maxW=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,(int)minW),maxH=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,(int)minH);
        manager.updateAppWidget(id,new RemoteViews(views(c,publication.usage,publication.image(c,maxW,minH),true,id),views(c,publication.usage,publication.image(c,minW,maxH),true,id)));
        RefreshFeedback.homePublishedOne(c,publication.feedback);
    }
    static RemoteViews views(Context c,Usage u,WidgetStyle s,float width,float height,boolean interactive){
        return views(c,u,s,width,height,interactive,-1);
    }
    private static RemoteViews views(Context c,Usage u,WidgetStyle s,float width,float height,boolean interactive,int widgetId){
        String state="none";if(interactive){RefreshFeedback.Snapshot snapshot=RefreshFeedback.snapshot(c);if(snapshot.visible)state=snapshot.state;}
        WidgetRenderer.Result result=WidgetRenderer.render(c,u,s,width,height,state);
        return views(c,u,result,interactive,widgetId);
    }
    private static RemoteViews views(Context c,Usage u,WidgetRenderer.Result result,boolean interactive,int widgetId){
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
