package dev.yerin.weeklymeter;

import android.app.job.*;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.os.Handler;
import android.os.Looper;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Optional periodic refresh only. Manual taps are owned by WidgetRefreshService. */
public final class UsageJob extends JobService {
    private final Map<Integer,Task> running=new HashMap<>();
    private final Handler main=new Handler(Looper.getMainLooper());
    private static final class Task{final AtomicBoolean stopped=new AtomicBoolean();Future<?> future;}
    @Override public boolean onStartJob(JobParameters params){
        // Retire any ONCE job left by an earlier installed app version. In
        // particular, opening the app must not wake an old manual request.
        // The display cache may say disconnected until cold-start vault recovery
        // finishes. Check the authoritative session on the serial worker instead.
        if(params.getJobId()!=Scheduler.PERIODIC||!enabled())return false;
        Task task=new Task();Task previous=running.put(params.getJobId(),task);
        if(previous!=null){previous.stopped.set(true);if(previous.future!=null)previous.future.cancel(false);}
        try{task.future=Repo.IO.submit(()->refresh(params,task));}
        catch(RejectedExecutionException unavailable){
            running.remove(params.getJobId());
            Store.error(this,"조회 작업을 시작하지 못했어.");publishLatest();return false;
        }
        return true;
    }
    private boolean enabled(){
        return Store.prefs(this).getBoolean("auto",true)&&
            AppWidgetManager.getInstance(this).getAppWidgetIds(new ComponentName(this,WeeklyWidget.class)).length>0;
    }
    private void refresh(JobParameters params,Task task){
        if(task.stopped.get())return;
        try{
            if(!enabled())return;
            Repo repo=new Repo(this);
            if(repo.reconcileConnection()&&!task.stopped.get()&&enabled())repo.sync(task.stopped::get);
        }catch(Exception error){Store.error(this,Repo.friendly(error));}
        finally{
            // Publish the committed cache while the worker still owns this work,
            // BEFORE the main-thread completion callback. A stop can race with
            // Store.save, making sync return CANCELLED despite a new saved value.
            // Cancellation must prevent more network work, not hide that value.
            final boolean published=publishLatest();
            main.post(()->{
                // One cache-only retry if the launcher rejected the first update.
                // Always read current storage; a late task must not restore its
                // own older snapshot or finish a newer job generation.
                if(!published)publishLatest();
                if(task.stopped.get()||running.get(params.getJobId())!=task)return;
                running.remove(params.getJobId());jobFinished(params,false);
            });
        }
    }
    private boolean publishLatest(){
        try{WeeklyWidget.renderAll(getApplicationContext());return true;}
        catch(RuntimeException unavailable){return false;}
    }
    @Override public boolean onStopJob(JobParameters params){
        Task task=running.remove(params.getJobId());
        if(task!=null){task.stopped.set(true);if(task.future!=null)task.future.cancel(false);}
        // Never interrupt an in-flight token rotation. Optional periodic work
        // resumes at its next interval; retired manual jobs never reschedule.
        return false;
    }
    @Override public void onDestroy(){
        for(Task task:running.values()){task.stopped.set(true);if(task.future!=null)task.future.cancel(false);}
        if(!running.isEmpty())publishLatest();
        running.clear();super.onDestroy();
    }
}
