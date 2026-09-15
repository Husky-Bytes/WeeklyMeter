package dev.yerin.weeklymeter;

import android.app.job.*;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.os.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Production periodic scheduler/job and feedback with deterministic Android fakes.
 * Manual foreground service is exercised separately by WidgetRefreshServiceTests. */
public final class LifecycleTests {
    private static int checks;
    private static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    private static void reset(){
        Context.JOBS.reset();Store.values.data.clear();Store.values.data.put("connected",true);
        Build.VERSION.SDK_INT=35;AppWidgetManager.ids=new int[]{1};WeeklyWidget.renders=0;Handler.drain();
        RefreshFeedback.clear(new Context());Context.STYLE.data.clear();Context.starts.clear();Context.stops.clear();Context.blockStart=false;
        SystemClock.now=10000;Handler.queue.clear();Handler.delayed.clear();Repo.outcome=Repo.SyncOutcome.UPDATED;Repo.action=()->{};
    }
    private static void flush()throws Exception{Repo.IO.submit(()->{}).get(5,TimeUnit.SECONDS);Handler.drain();}
    public static void main(String[]args)throws Exception{
        try{scheduler();lifecycle();feedback();System.out.println("PASS: "+checks+" independent scheduler/lifecycle checks (fake platform; no device/account/network)");}
        finally{Repo.IO.shutdownNow();}
    }
    private static void scheduler(){
        Context c=new Context();reset();Scheduler.ensure(c);JobInfo periodic=Context.JOBS.getPendingJob(Scheduler.PERIODIC);
        check(periodic!=null&&periodic.interval==900000&&periodic.persisted,"15m persisted periodic schedule");
        Scheduler.ensure(c);check(Context.JOBS.calls.size()==1,"unchanged periodic schedule not replaced");
        Store.values.data.put("auto",false);Scheduler.ensure(c);check(Context.JOBS.getPendingJob(Scheduler.PERIODIC)==null,"auto-off removes periodic schedule");
        Store.values.data.put("auto",true);Store.values.data.put("minutes",30);Scheduler.ensure(c);check(Context.JOBS.getPendingJob(Scheduler.PERIODIC).interval==1800000,"30m setting honored");
        AppWidgetManager.ids=new int[0];Scheduler.ensure(c);check(Context.JOBS.getPendingJob(Scheduler.PERIODIC)==null,"no widgets cancels periodic work");
        reset();Context.JOBS.schedule(new JobInfo.Builder(Scheduler.ONCE,new ComponentName(c,UsageJob.class)).build());Store.values.data.put("requested",1L);Scheduler.ensure(c);
        check(Context.JOBS.getPendingJob(Scheduler.ONCE)==null&&!Store.values.data.containsKey("requested"),"ensure retires old manual job and timestamp");
        reset();Scheduler.request(c);check(Context.JOBS.calls.isEmpty(),"manual request never schedules a job");
        check(Context.starts.size()==1&&Context.starts.get(0).target==WidgetRefreshService.class&&WidgetRefreshService.ACTION_REFRESH.equals(Context.starts.get(0).getAction()),"manual forwarding targets foreground helper");
        Store.values.data.put("connected",false);Scheduler.request(c);check(Context.starts.size()==2,"cold false display preference cannot block helper startup");
        Context.blockStart=true;Scheduler.request(c);check(RefreshFeedback.snapshot(c).state.equals("error")&&Store.values.data.containsKey("error"),"blocked helper startup gives error");
        Scheduler.cancel(c);check(Context.stops.size()==1&&Context.stops.get(0).target==WidgetRefreshService.class,"logout stops temporary manual helper");
        check(RefreshFeedback.currentRequestId(c)==0,"logout clears feedback generation");
        reset();Context.JOBS.rejectAll=true;Scheduler.ensure(c);check(Store.values.data.containsKey("error"),"periodic schedule failure is reported");
    }
    private static void lifecycle()throws Exception{
        reset();UsageJob service=new UsageJob();JobParameters p=new JobParameters(Scheduler.PERIODIC,0);
        check(service.onStartJob(p),"connected periodic job starts asynchronously");flush();
        check(service.finished.size()==1&&service.finished.get(0)==p,"periodic job finishes exactly once");
        check(WeeklyWidget.renders>0&&!RefreshFeedback.snapshot(service).visible,"periodic completion redraws timestamp without success flash");
        AtomicInteger count=new AtomicInteger();Repo.action=count::incrementAndGet;
        check(!service.onStartJob(new JobParameters(Scheduler.ONCE,0))&&!service.onStartJob(new JobParameters(99999,0)),"legacy manual and unknown jobs refused");flush();
        check(count.get()==0,"retired job cannot fetch later on app opening");
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicBoolean interrupted=new AtomicBoolean();
        Repo.action=()->{entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){interrupted.set(true);throw e;}};
        service.onStartJob(new JobParameters(Scheduler.PERIODIC,0));check(entered.await(5,TimeUnit.SECONDS),"token critical-section simulation started");
        check(!service.onStopJob(new JobParameters(Scheduler.PERIODIC,JobParameters.STOP_REASON_CONSTRAINT_CONNECTIVITY)),"periodic stop waits for normal next interval");
        release.countDown();flush();check(!interrupted.get(),"periodic cancellation never interrupts token transaction");
        check(service.finished.size()==1,"cancelled periodic generation never calls jobFinished");
        check(!service.onStopJob(new JobParameters(Scheduler.ONCE,JobParameters.STOP_REASON_CONSTRAINT_CONNECTIVITY)),"legacy manual stop never requests retry");
        Store.values.data.put("connected",false);check(!service.onStartJob(p),"signed-out periodic job is not started");Store.values.data.put("connected",true);
        CountDownLatch occupied=new CountDownLatch(1),unblock=new CountDownLatch(1);
        Repo.IO.submit(()->{occupied.countDown();try{unblock.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        check(occupied.await(5,TimeUnit.SECONDS),"serial credential queue occupied");count.set(0);Repo.action=count::incrementAndGet;
        service.onStartJob(p);service.onStopJob(p);unblock.countDown();flush();check(count.get()==0,"cancelled queued periodic work never starts HTTP");
        CountDownLatch firstEntered=new CountDownLatch(1),firstRelease=new CountDownLatch(1);AtomicInteger invocations=new AtomicInteger();
        Repo.action=()->{if(invocations.incrementAndGet()==1){firstEntered.countDown();firstRelease.await(5,TimeUnit.SECONDS);}};
        JobParameters first=new JobParameters(Scheduler.PERIODIC,0),second=new JobParameters(Scheduler.PERIODIC,0);service.onStartJob(first);
        check(firstEntered.await(5,TimeUnit.SECONDS),"old periodic generation begins");
        service.onStartJob(second);firstRelease.countDown();flush();
        check(service.finished.size()==2&&service.finished.get(1)==second,"replaced generation cannot finish newer job");
        service.onDestroy();
    }
    private static void feedback(){
        Context c=new Context();reset();long id=RefreshFeedback.begin(c);check(RefreshFeedback.snapshot(c).visible&&RefreshFeedback.snapshot(c).state.equals("waiting"),"new request waiting acknowledgement");
        RefreshFeedback.running(c,id);RefreshFeedback.acknowledge(c,id);check(RefreshFeedback.snapshot(c).state.equals("running"),"acknowledge retains running phase");
        RefreshFeedback.success(c,id);long next=RefreshFeedback.begin(c);RefreshFeedback.error(c,id);check(next!=id&&RefreshFeedback.snapshot(c).state.equals("waiting"),"stale result cannot change newer generation");
        SystemClock.now+=1000;Handler.drain();check(RefreshFeedback.snapshot(c).visible,"old terminal expiry cannot clear new waiting");
        RefreshFeedback.success(c,next);long expires=RefreshFeedback.snapshot(c).expiresAt;check(expires-SystemClock.now==1000,"default terminal duration one second");
        SystemClock.now=expires;check(!RefreshFeedback.snapshot(c).visible,"render expires badge even before callback");
        int before=WeeklyWidget.renders;Handler.drain();check(WeeklyWidget.renders>before,"expiry callback redraws widget");
        Context.STYLE.data.put("feedback_enabled",false);next=RefreshFeedback.begin(c);check(!RefreshFeedback.snapshot(c).visible,"disabled feedback remains hidden");
        Context.STYLE.data.put("feedback_enabled",true);Context.STYLE.data.put("feedback_duration_ms",100);RefreshFeedback.success(c,next);
        check(RefreshFeedback.snapshot(c).expiresAt-SystemClock.now==100,"minimum terminal duration honored");
        Context.STYLE.data.put("feedback_duration_ms",Integer.MAX_VALUE);RefreshFeedback.error(c,next);check(RefreshFeedback.snapshot(c).expiresAt-SystemClock.now==10000,"oversized duration capped");
        SystemClock.now--;check(!RefreshFeedback.snapshot(c).visible,"clock rollback hides stale feedback");
        RefreshFeedback.clear(c);check(RefreshFeedback.currentRequestId(c)==0&&!RefreshFeedback.snapshot(c).visible,"clear removes generation and badge");
    }
}
