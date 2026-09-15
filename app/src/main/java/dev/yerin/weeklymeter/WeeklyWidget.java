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
    @Override public void onUpdate(Context c,AppWidgetManager manager,int[] ids){renderAll(c);Scheduler.ensure(c);}
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager manager,int id,Bundle options){render(c,manager,id);}
    @Override public void onDisabled(Context c){Scheduler.cancel(c);}
    @Override public void onReceive(Context c,Intent i){super.onReceive(c,i);if(REFRESH.equals(i.getAction())){Scheduler.request(c);renderAll(c);}}
    static WidgetStyle style(Context c){
        SharedPreferences p=c.getSharedPreferences("widget_style",Context.MODE_PRIVATE);WidgetStyle s=WidgetStyle.defaults();
        try {
            String data=p.getString("style_v3","");
            if(!data.isEmpty()&&data.length()<=65536){
                Map<String,Object> m=Json.object(Json.parse(data));
                s.background=integer(m,"background",s.background);s.opacity=integer(m,"opacity",s.opacity);s.radius=integer(m,"radius",s.radius);
                s.autoFit=bool(m,"auto_fit",s.autoFit);s.brandMode=integer(m,"brand_mode",s.brandMode);s.brandLogoSizeSp=decimal(m,"logo_size",s.brandLogoSizeSp);
                s.automaticPadding=bool(m,"automatic_padding",s.automaticPadding);
                s.paddingHorizontalDp=decimal(m,"padding_horizontal_dp",s.paddingHorizontalDp);s.paddingVerticalDp=decimal(m,"padding_vertical_dp",s.paddingVerticalDp);
                s.rowGapDp=decimal(m,"row_gap_dp",s.rowGapDp);
                List<Object> rows=Json.array(m.get("rows"));for(int i=0;i<Math.min(4,rows.size());i++){
                    Map<String,Object> r=Json.object(rows.get(i));WidgetStyle.Row row=s.rows[i];
                    row.enabled=bool(r,"enabled",row.enabled);row.font=integer(r,"font",row.font);row.sizeSp=decimal(r,"size",row.sizeSp);row.color=integer(r,"color",row.color);
                    row.bold=bool(r,"bold",row.bold);row.alignment=integer(r,"alignment",row.alignment);row.offsetY=decimal(r,"offset_y",row.offsetY);
                }
                List<Object> order=Json.array(m.get("order"));if(!order.isEmpty()){s.order=new int[order.size()];for(int i=0;i<order.size();i++)s.order[i]=(int)Json.integer(order.get(i),-1);}
                readDate(s.resetDate,Json.object(m.get("reset_date")));readDate(s.lastDate,Json.object(m.get("last_date")));
            } else {
                // Migrate existing local appearance only; login/usage preferences are untouched.
                s.background=p.getInt("background",s.background);s.opacity=p.getInt("opacity",s.opacity);s.radius=p.getInt("radius",s.radius);
                s.rows[WidgetStyle.PERCENT].color=p.getInt("text",s.rows[WidgetStyle.PERCENT].color);
                s.rows[WidgetStyle.RESET].color=p.getInt("reset",s.rows[WidgetStyle.RESET].color);
                s.rows[WidgetStyle.RESET].enabled=p.getBoolean("show_reset",true);
                s.rows[WidgetStyle.PERCENT].bold=p.getBoolean("bold",true);
                int alignment=p.getInt("alignment",0)==1?0:1;for(WidgetStyle.Row row:s.rows)row.alignment=alignment;
                int size=p.getInt("text_size",1);s.rows[WidgetStyle.PERCENT].sizeSp=size==0?27:size==2?37.5f:32;
                int format=p.getInt("date_format",0);
                if(format==1)s.resetDate.weekday=true;
                if(format==2){s.resetDate.separator=1;s.resetDate.use24h=false;s.resetDate.ampm=true;}
                if(format==3){s.resetDate.year=true;s.resetDate.leadingZero=true;}
            }
            s.feedbackEnabled=p.getBoolean("feedback_enabled",true);s.feedbackDurationMs=p.getInt("feedback_duration_ms",1000);
        }catch(Exception ignored){s=WidgetStyle.defaults();}
        s.normalize();return s;
    }
    static void saveStyle(Context c,WidgetStyle original){
        WidgetStyle s=original.copy();s.normalize();List<Object> rows=new ArrayList<>(),order=new ArrayList<>();
        for(WidgetStyle.Row r:s.rows)rows.add(Json.map("enabled",r.enabled,"font",r.font,"size",r.sizeSp,"color",r.color,"bold",r.bold,"alignment",r.alignment,"offset_y",r.offsetY));
        for(int id:s.order)order.add(id);
        Map<String,Object> value=Json.map("version",3,"background",s.background,"opacity",s.opacity,"radius",s.radius,"auto_fit",s.autoFit,
            "automatic_padding",s.automaticPadding,"padding_horizontal_dp",s.paddingHorizontalDp,"padding_vertical_dp",s.paddingVerticalDp,"row_gap_dp",s.rowGapDp,
            "brand_mode",s.brandMode,"logo_size",s.brandLogoSizeSp,"rows",rows,"order",order,"reset_date",dateMap(s.resetDate),"last_date",dateMap(s.lastDate));
        c.getSharedPreferences("widget_style",Context.MODE_PRIVATE).edit().putString("style_v3",Json.encode(value))
            .putBoolean("feedback_enabled",s.feedbackEnabled).putInt("feedback_duration_ms",s.feedbackDurationMs).apply();
        renderAll(c);
    }
    private static Map<String,Object> dateMap(WidgetStyle.DateSpec d){return Json.map("year",d.year,"month",d.month,"day",d.day,"weekday",d.weekday,"hour",d.hour,"minute",d.minute,"second",d.second,"use_24h",d.use24h,"ampm",d.ampm,"leading_zero",d.leadingZero,"two_lines",d.twoLines,"label",d.label,"separator",d.separator);}
    private static void readDate(WidgetStyle.DateSpec d,Map<String,Object> m){
        d.year=bool(m,"year",d.year);d.month=bool(m,"month",d.month);d.day=bool(m,"day",d.day);d.weekday=bool(m,"weekday",d.weekday);
        d.hour=bool(m,"hour",d.hour);d.minute=bool(m,"minute",d.minute);d.second=bool(m,"second",d.second);d.use24h=bool(m,"use_24h",d.use24h);
        d.ampm=bool(m,"ampm",d.ampm);d.leadingZero=bool(m,"leading_zero",d.leadingZero);d.twoLines=bool(m,"two_lines",d.twoLines);d.label=bool(m,"label",d.label);d.separator=integer(m,"separator",d.separator);
    }
    private static boolean bool(Map<String,Object> m,String key,boolean fallback){Object v=m.get(key);return v instanceof Boolean?(Boolean)v:fallback;}
    private static int integer(Map<String,Object> m,String key,int fallback){long n=Json.integer(m.get(key),fallback);return n<Integer.MIN_VALUE||n>Integer.MAX_VALUE?fallback:(int)n;}
    private static float decimal(Map<String,Object> m,String key,float fallback){return (float)Json.number(m.get(key),fallback);}
    static void renderAll(Context c){
        AppWidgetManager manager=AppWidgetManager.getInstance(c);
        for(int id:manager.getAppWidgetIds(new ComponentName(c,WeeklyWidget.class)))render(c,manager,id);
    }
    static void render(Context c,AppWidgetManager manager,int id){
        Usage u=Store.selected(c);WidgetStyle s=style(c);Bundle options=manager.getAppWidgetOptions(id);
        // Phones normally supply two sizes; up to four handles orientation/foldable
        // variants while keeping the aggregate bitmap allocation below ~3.6 MB.
        if(Build.VERSION.SDK_INT>=31){
            ArrayList<SizeF> sizes=options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES);
            Map<SizeF,RemoteViews> variants=new LinkedHashMap<>();
            if(sizes!=null)for(SizeF size:sizes){
                if(size==null||!Float.isFinite(size.getWidth())||!Float.isFinite(size.getHeight())||size.getWidth()<=0||size.getHeight()<=0)continue;
                variants.put(size,views(c,u,s,size.getWidth(),size.getHeight(),true));if(variants.size()>=4)break;
            }
            if(!variants.isEmpty()){manager.updateAppWidget(id,new RemoteViews(variants));return;}
        }
        float minW=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,64),minH=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,64);
        float maxW=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,(int)minW),maxH=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,(int)minH);
        manager.updateAppWidget(id,new RemoteViews(views(c,u,s,maxW,minH,true),views(c,u,s,minW,maxH,true)));
    }
    static RemoteViews views(Context c,Usage u,WidgetStyle s,float width,float height,boolean interactive){
        String state="none";if(interactive){RefreshFeedback.Snapshot snapshot=RefreshFeedback.snapshot(c);if(snapshot.visible)state=snapshot.state;}
        WidgetRenderer.Result result=WidgetRenderer.render(c,u,s,width,height,state);
        RemoteViews view=new RemoteViews(c.getPackageName(),R.layout.weekly_widget);
        view.setImageViewBitmap(R.id.widget_image,result.bitmap);view.setViewVisibility(R.id.preview_percent,View.GONE);
        if(interactive){
            // Direct user interaction grants a short foreground-service start window.
            // A JobScheduler queue can defer a manual tap until the app is foreground.
            Intent refresh=new Intent(c,WidgetRefreshService.class).setAction(WidgetRefreshService.ACTION_REFRESH);
            view.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getForegroundService(c,1,refresh,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
            view.setContentDescription(R.id.widget_root,result.accessibility+". "+Display.state(c,u)+". 누르면 새로고침");
        }
        return view;
    }
}
