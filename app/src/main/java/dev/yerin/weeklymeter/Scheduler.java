package dev.yerin.weeklymeter;

import android.app.job.*;
import android.appwidget.AppWidgetManager;
import android.content.*;

/** JobScheduler is used only for optional periodic work, never for a widget tap. */
final class Scheduler {
    static final int PERIODIC=22001, ONCE=22002;
    static JobScheduler jobs(Context c){return c.getSystemService(JobScheduler.class);}
    static int minutes(Context c){
        try{return RefreshInterval.normalize(Store.prefs(c).getInt("minutes",RefreshInterval.DEFAULT_MINUTES));}
        catch(ClassCastException invalid){return RefreshInterval.DEFAULT_MINUTES;}
    }
    static void retireLegacyManual(Context c){
        jobs(c).cancel(ONCE);
        Store.prefs(c).edit().remove("requested").apply();
    }
    static void ensure(Context c){
        retireLegacyManual(c);
        boolean enabled=Store.connected(c)&&Store.prefs(c).getBoolean("auto",true)&&
            (AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c,WeeklyWidget.class)).length>0||FloatingWidgetService.isActive());
        if(!enabled){jobs(c).cancel(PERIODIC);return;}
        long interval=RefreshInterval.millis(minutes(c));
        JobInfo old=jobs(c).getPendingJob(PERIODIC);
        if(old!=null&&old.getIntervalMillis()==interval)return;
        JobInfo job=new JobInfo.Builder(PERIODIC,new ComponentName(c,UsageJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(interval)
            .setPersisted(true).setBackoffCriteria(60_000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build();
        if(jobs(c).schedule(job)!=JobScheduler.RESULT_SUCCESS)Store.error(c,"자동 갱신 작업을 등록하지 못했어. 앱에서 새로고침해 줘.");
    }
    /** Compatibility path for an already installed widget's old refresh broadcast. */
    static void request(Context c){
        retireLegacyManual(c);
        try{c.startForegroundService(new Intent(c,WidgetRefreshService.class).setAction(WidgetRefreshService.ACTION_REFRESH));}
        catch(RuntimeException blocked){
            long id=RefreshFeedback.begin(c);RefreshFeedback.error(c,id);
            Store.error(c,"Android가 위젯 조회 시작을 막았어. 배터리 제한을 확인한 뒤 위젯을 다시 눌러 줘.");
            try{WeeklyWidget.renderAll(c);}catch(RuntimeException unavailable){}
        }
    }
    static void cancel(Context c){
        jobs(c).cancel(PERIODIC);retireLegacyManual(c);
        c.stopService(new Intent(c,WidgetRefreshService.class));
        RefreshFeedback.clear(c);
    }
    private Scheduler(){}
}
