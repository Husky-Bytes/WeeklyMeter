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
    private static final String[] displayed={"none","none"};
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static Runnable pendingExpiry;
    private static boolean repairQueued;
    private static long revision;
    static final class Snapshot {
        final String state;
        final long requestId,expiresAt;
        final boolean visible;
        Snapshot(RefreshFeedbackModel model,boolean visible){this.state=model.state;this.requestId=model.requestId;this.expiresAt=model.expiresAt;this.visible=visible;}
    }
    static Snapshot snapshot(Context c){return snapshot(c,false);}
    static synchronized Snapshot snapshot(Context c,boolean floating){
        int target=floating?1:0;
        boolean matching=current[target].matches(currentRequestId(c));
        return new Snapshot(current[target],matching&&current[target].visible(SystemClock.elapsedRealtime(),enabled(c,floating)));
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
        for(int target=0;target<2;target++)current[target]=RefreshFeedbackModel.none();
        cancelExpiry();
        Store.prefs(c).edit().remove(REQUEST_ID).apply();
    }
    private static boolean enabled(Context c,boolean floating){try{return style(c,floating).getBoolean("feedback_enabled",true);}catch(ClassCastException invalid){return false;}}
    private static SharedPreferences style(Context c,boolean floating){return c.getSharedPreferences(floating?"floating_style":"widget_style",Context.MODE_PRIVATE);}
    static synchronized void published(boolean floating,Snapshot snapshot){displayed[floating?1:0]=snapshot.visible?snapshot.state:"none";}
    // A partial publication can put a badge on one ID even if another host ID
    // throws. Only a complete successful publication may mark all IDs clear.
    static synchronized void homePublishedOne(Context context,Snapshot snapshot){
        if(!snapshot.visible)return;
        displayed[0]=snapshot.state;
        Snapshot latest=snapshot(context,false);
        // The expiry callback may have run while the background bitmap/host call
        // was still in flight. Clear a late stale publication on the main queue.
        if(!displayed[0].equals(latest.visible?latest.state:"none")&&!repairQueued){
            repairQueued=true;Context app=context.getApplicationContext();
            main.post(()->{synchronized(RefreshFeedback.class){repairQueued=false;}publishChanged(app);});
        }
    }
    static void publishChanged(Context c){
        boolean home,floating;
        synchronized(RefreshFeedback.class){
            Snapshot h=snapshot(c,false),f=snapshot(c,true);
            home=!displayed[0].equals(h.visible?h.state:"none");floating=!displayed[1].equals(f.visible?f.state:"none");
        }
        if(home||floating)try{WeeklyWidget.renderFeedback(c,home,floating);}catch(RuntimeException unavailable){}
    }
    private static synchronized void show(Context context,long id,String state){
        if(id<=0||id!=currentRequestId(context))return;
        Context c=context.getApplicationContext();
        long now=SystemClock.elapsedRealtime();
        for(int target=0;target<2;target++){
            int duration;
            try{duration=RefreshFeedbackModel.duration(style(c,target==1).getInt("feedback_duration_ms",1000));}catch(ClassCastException invalid){duration=1000;}
            current[target]=RefreshFeedbackModel.begin(state,id,now,duration);
        }
        scheduleExpiry(c,now);
    }
    private static void cancelExpiry(){
        revision++;if(pendingExpiry!=null){main.removeCallbacks(pendingExpiry);pendingExpiry=null;}
    }
    private static void scheduleExpiry(Context c,long now){
        cancelExpiry();long nearest=Long.MAX_VALUE;
        for(RefreshFeedbackModel model:current)if(model.requestId>0)nearest=Math.min(nearest,model.expiresAt);
        if(nearest==Long.MAX_VALUE)return;
        final long expected=revision;
        pendingExpiry=()->expire(c,expected);main.postDelayed(pendingExpiry,Math.max(1,nearest-now));
    }
    private static void expire(Context c,long expectedRevision){
        synchronized(RefreshFeedback.class){
            if(revision!=expectedRevision)return;
            // Handler delivery is best effort, not an exact alarm. Expiry is also
            // checked by every render, including after a delayed OS callback.
            long now=SystemClock.elapsedRealtime();
            for(int target=0;target<2;target++)if(current[target].requestId>0&&!current[target].visible(now,true))current[target]=RefreshFeedbackModel.none();
            scheduleExpiry(c,now);
        }
        // The state is already expired. A rejected launcher/overlay update
        // must not crash this main callback or restart the completed request.
        publishChanged(c);
    }
    private RefreshFeedback(){}
}
