package dev.yerin.weeklymeter;

import android.app.job.*;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Optional periodic refresh only. Manual taps are owned by WidgetRefreshService. */
public final class UsageJob extends JobService {
    private final Map<Integer,Task> running=new HashMap<>();
    private final Handler main=new Handler(Looper.getMainLooper());
    private static final class Task{
        final AtomicBoolean stopped=new AtomicBoolean();final long diagnosticId;Future<?> future;
        Task(long diagnosticId){this.diagnosticId=diagnosticId;}
    }
    @Override public boolean onStartJob(JobParameters params){
        // Retire any ONCE job left by an earlier installed app version. In
        // particular, opening the app must not wake an old manual request.
        // The display cache may say disconnected until cold-start vault recovery
        // finishes. Check the authoritative session on the serial worker instead.
        if(params.getJobId()!=Scheduler.PERIODIC)return false;
        long diagnosticId=AutoRefreshDiagnostics.begin(this);
        if(!enabled()){AutoRefreshDiagnostics.complete(this,diagnosticId,"disabled");return false;}
        Task task=new Task(diagnosticId);Task previous=running.put(params.getJobId(),task);
        if(previous!=null){previous.stopped.set(true);if(previous.future!=null)previous.future.cancel(false);}
        try{task.future=Repo.IO.submit(()->refresh(params,task));}
        catch(RejectedExecutionException unavailable){
            running.remove(params.getJobId());
            AutoRefreshDiagnostics.complete(this,task.diagnosticId,"executor_rejected");
            Store.error(this,"조회 작업을 시작하지 못했어.");publishLatest(task);return false;
        }
        return true;
    }
    private boolean enabled(){
        return Store.prefs(this).getBoolean("auto",true)&&
            (AppWidgetManager.getInstance(this).getAppWidgetIds(new ComponentName(this,WeeklyWidget.class)).length>0||FloatingWidgetService.isActive());
    }
    private void refresh(JobParameters params,Task task){
        if(task.stopped.get())return;
        String outcome="cancelled";
        try{
            if(!enabled()){outcome="disabled";return;}
            Repo repo=new Repo(this);
            AtomicBoolean disabled=new AtomicBoolean();
            Repo.SyncOutcome result=repo.syncConnected(task.stopped::get,()->{
                disabled.set(!enabled());
                return disabled.get();
            });
            outcome=result==Repo.SyncOutcome.UPDATED?"updated":result==Repo.SyncOutcome.SKIPPED?"skipped":
                result==Repo.SyncOutcome.SIGNED_OUT?"signed_out":disabled.get()?"disabled":"cancelled";
        }catch(Exception error){outcome="error";Store.error(this,Repo.friendly(error));}
        finally{
            AutoRefreshDiagnostics.complete(this,task.diagnosticId,outcome);
            // Publish the committed cache while the worker still owns this work,
            // BEFORE the main-thread completion callback. A stop can race with
            // Store.save, making sync return CANCELLED despite a new saved value.
            // Cancellation must prevent more network work, not hide that value.
            final boolean published=publishLatest(task);
            main.post(()->{
                // One cache-only retry if the launcher rejected the first update.
                // Always read current storage; a late task must not restore its
                // own older snapshot or finish a newer job generation.
                if(!published)publishLatest(task);
                if(task.stopped.get()||running.get(params.getJobId())!=task)return;
                running.remove(params.getJobId());jobFinished(params,false);
            });
        }
    }
    private boolean publishLatest(Task task){
        boolean success;
        try{WeeklyWidget.renderAll(getApplicationContext());success=true;}
        catch(RuntimeException unavailable){success=false;}
        AutoRefreshDiagnostics.publication(this,task.diagnosticId,success);return success;
    }
    @Override public boolean onStopJob(JobParameters params){
        Task task=running.remove(params.getJobId());
        if(task!=null){
            task.stopped.set(true);if(task.future!=null)task.future.cancel(false);
            AutoRefreshDiagnostics.stopped(this,task.diagnosticId,stopReason(params));
        }
        // Never interrupt an in-flight token rotation. Optional periodic work
        // resumes at its next interval; retired manual jobs never reschedule.
        return false;
    }
    private static int stopReason(JobParameters params){
        if(Build.VERSION.SDK_INT<31)return -1;
        try{return params.getStopReason();}
        catch(RuntimeException|LinkageError unavailable){return -1;}
    }
    @Override public void onDestroy(){
        for(Task task:running.values()){
            task.stopped.set(true);if(task.future!=null)task.future.cancel(false);
            AutoRefreshDiagnostics.destroyed(this,task.diagnosticId);publishLatest(task);
        }
        running.clear();super.onDestroy();
    }
}
