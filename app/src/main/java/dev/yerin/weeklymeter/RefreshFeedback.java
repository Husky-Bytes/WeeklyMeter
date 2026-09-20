package dev.yerin.weeklymeter;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/** Short-lived, non-secret manual-refresh feedback. It never changes usage timestamps. */
final class RefreshFeedback {
    static final String REQUEST_ID="refresh_request_id";
    private static final RefreshFeedbackModel[] current={RefreshFeedbackModel.none(),RefreshFeedbackModel.none()};
    private static final long[] revision={0,0};
    static final class Snapshot {
        final String state;
        final long requestId,expiresAt;
        final boolean visible;
        Snapshot(RefreshFeedbackModel model,boolean visible){this.state=model.state;this.requestId=model.requestId;this.expiresAt=model.expiresAt;this.visible=visible;}
    }
    static Snapshot snapshot(Context c){return snapshot(c,false);}
    static synchronized Snapshot snapshot(Context c,boolean floating){
        int target=floating?1:0;
        boolean enabled;
        try{enabled=style(c,floating).getBoolean("feedback_enabled",true);}catch(ClassCastException invalid){enabled=false;}
        boolean matching=current[target].matches(currentRequestId(c));
        return new Snapshot(current[target],matching&&current[target].visible(SystemClock.elapsedRealtime(),enabled));
    }
    static long currentRequestId(Context c){return Store.prefs(c).getLong(REQUEST_ID,0);}
    static synchronized long begin(Context c){
        long previous=currentRequestId(c),now=System.currentTimeMillis();
        long id=previous==Long.MAX_VALUE?1:Math.max(Math.max(1,now),previous+1);
        Store.prefs(c).edit().putLong(REQUEST_ID,id).apply();
        show(c,id,"waiting");return id;
    }
    static synchronized void acknowledge(Context c,long id){
        boolean running=(current[0].matches(id)&&"running".equals(current[0].state))||
            (current[1].matches(id)&&"running".equals(current[1].state));
        show(c,id,running?"running":"waiting");
    }
    static void waiting(Context c,long id){show(c,id,"waiting");}
    static void running(Context c,long id){show(c,id,"running");}
    static void success(Context c,long id){show(c,id,"success");}
    static void error(Context c,long id){show(c,id,"error");}
    static void skipped(Context c,long id){show(c,id,"skipped");}
    static synchronized void clear(Context c){
        for(int target=0;target<2;target++){current[target]=RefreshFeedbackModel.none();revision[target]++;}
        Store.prefs(c).edit().remove(REQUEST_ID).apply();
    }
    private static SharedPreferences style(Context c,boolean floating){return c.getSharedPreferences(floating?"floating_style":"widget_style",Context.MODE_PRIVATE);}
    private static synchronized void show(Context context,long id,String state){
        if(id<=0||id!=currentRequestId(context))return;
        Context c=context.getApplicationContext();
        long now=SystemClock.elapsedRealtime();
        for(int target=0;target<2;target++){
            int duration;
            try{duration=RefreshFeedbackModel.duration(style(c,target==1).getInt("feedback_duration_ms",1000));}catch(ClassCastException invalid){duration=1000;}
            current[target]=RefreshFeedbackModel.begin(state,id,now,duration);
            final int destination=target;
            final long expectedRevision=++revision[target];
            final long delay=Math.max(1,current[target].expiresAt-now);
            new Handler(Looper.getMainLooper()).postDelayed(()->expire(c,id,expectedRevision,destination),delay);
        }
    }
    private static void expire(Context c,long id,long expectedRevision,int target){
        synchronized(RefreshFeedback.class){
            if(revision[target]!=expectedRevision||!current[target].matches(id))return;
            // Handler delivery is best effort, not an exact alarm. Expiry is also
            // checked by every render, including after a delayed OS callback.
            long now=SystemClock.elapsedRealtime();
            if(now>=current[target].startedAt&&now<current[target].expiresAt){
                new Handler(Looper.getMainLooper()).postDelayed(()->expire(c,id,expectedRevision,target),current[target].expiresAt-now);return;
            }
            current[target]=RefreshFeedbackModel.none();revision[target]++;
        }
        WeeklyWidget.renderAll(c);
    }
    private RefreshFeedback(){}
}
