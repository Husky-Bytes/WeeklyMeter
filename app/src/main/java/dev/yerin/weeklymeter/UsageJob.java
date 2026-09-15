package dev.yerin.weeklymeter;

import android.app.job.*;
import android.os.Handler;
import android.os.Looper;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Optional periodic refresh only. Manual taps are owned by WidgetRefreshService. */
public final class UsageJob extends JobService {
    private final Map<Integer,Task> running=new HashMap<>();
    private static final class Task{final AtomicBoolean stopped=new AtomicBoolean();Future<?> future;}
    @Override public boolean onStartJob(JobParameters params){
        // Retire any ONCE job left by an earlier installed app version. In
        // particular, opening the app must not wake an old manual request.
        if(params.getJobId()!=Scheduler.PERIODIC||!Store.connected(this))return false;
        Task task=new Task();Task previous=running.put(params.getJobId(),task);
        if(previous!=null){previous.stopped.set(true);if(previous.future!=null)previous.future.cancel(false);}
        task.future=Repo.IO.submit(()->{
            if(task.stopped.get())return;
            try{new Repo(this).sync(task.stopped::get);}catch(Exception ignored){}
            new Handler(Looper.getMainLooper()).post(()->{
                if(task.stopped.get()||running.get(params.getJobId())!=task)return;
                running.remove(params.getJobId());WeeklyWidget.renderAll(this);jobFinished(params,false);
            });
        });
        return true;
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
        if(!running.isEmpty())WeeklyWidget.renderAll(this);
        running.clear();super.onDestroy();
    }
}
