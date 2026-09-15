package dev.yerin.weeklymeter;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/** Short-lived, non-secret manual-refresh feedback. It never changes usage timestamps. */
final class RefreshFeedback {
    static final String REQUEST_ID="refresh_request_id";
    private static RefreshFeedbackModel current=RefreshFeedbackModel.none();
    private static long revision;
    static final class Snapshot {
        final String state;
        final long requestId,expiresAt;
        final boolean visible;
        Snapshot(RefreshFeedbackModel model,boolean visible){this.state=model.state;this.requestId=model.requestId;this.expiresAt=model.expiresAt;this.visible=visible;}
    }
    static synchronized Snapshot snapshot(Context c){
        boolean enabled;
        try{enabled=style(c).getBoolean("feedback_enabled",true);}catch(ClassCastException invalid){enabled=false;}
        boolean matching=current.matches(currentRequestId(c));
        return new Snapshot(current,matching&&current.visible(SystemClock.elapsedRealtime(),enabled));
    }
    static long currentRequestId(Context c){return Store.prefs(c).getLong(REQUEST_ID,0);}
    static synchronized long begin(Context c){
        long previous=currentRequestId(c),now=System.currentTimeMillis();
        long id=previous==Long.MAX_VALUE?1:Math.max(Math.max(1,now),previous+1);
        Store.prefs(c).edit().putLong(REQUEST_ID,id).apply();
        show(c,id,"waiting");return id;
    }
    static synchronized void acknowledge(Context c,long id){
        show(c,id,current.matches(id)&&"running".equals(current.state)?"running":"waiting");
    }
    static void waiting(Context c,long id){show(c,id,"waiting");}
    static void running(Context c,long id){show(c,id,"running");}
    static void success(Context c,long id){show(c,id,"success");}
    static void error(Context c,long id){show(c,id,"error");}
    static void skipped(Context c,long id){show(c,id,"skipped");}
    static synchronized void clear(Context c){
        current=RefreshFeedbackModel.none();revision++;
        Store.prefs(c).edit().remove(REQUEST_ID).apply();
    }
    private static SharedPreferences style(Context c){return c.getSharedPreferences("widget_style",Context.MODE_PRIVATE);}
    private static synchronized void show(Context context,long id,String state){
        if(id<=0||id!=currentRequestId(context))return;
        Context c=context.getApplicationContext();
        int duration;
        try{duration=RefreshFeedbackModel.duration(style(c).getInt("feedback_duration_ms",1000));}catch(ClassCastException invalid){duration=1000;}
        current=RefreshFeedbackModel.begin(state,id,SystemClock.elapsedRealtime(),duration);
        final long expectedRevision=++revision;
        final long delay=Math.max(1,current.expiresAt-SystemClock.elapsedRealtime());
        new Handler(Looper.getMainLooper()).postDelayed(()->expire(c,id,expectedRevision),delay);
    }
    private static void expire(Context c,long id,long expectedRevision){
        synchronized(RefreshFeedback.class){
            if(revision!=expectedRevision||!current.matches(id))return;
            // Handler delivery is best effort, not an exact alarm. Expiry is also
            // checked by every render, including after a delayed OS callback.
            long now=SystemClock.elapsedRealtime();
            if(now>=current.startedAt&&now<current.expiresAt){
                new Handler(Looper.getMainLooper()).postDelayed(()->expire(c,id,expectedRevision),current.expiresAt-now);return;
            }
            current=RefreshFeedbackModel.none();revision++;
        }
        WeeklyWidget.renderAll(c);
    }
    private RefreshFeedback(){}
}
