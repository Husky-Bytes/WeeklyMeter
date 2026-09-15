package dev.yerin.weeklymeter;

import android.content.Context;
import android.content.Intent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.os.Handler;
import android.os.SystemClock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Real manual service/scheduler/feedback with fake Android and serial credential operations.
 * No Activity class is present in this test environment; no account or network is used. */
public final class WidgetRefreshServiceTests {
    private static int checks;
    private static final List<WidgetRefreshService> services=new ArrayList<>();
    private static final List<CountDownLatch> releases=new ArrayList<>();
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static WidgetRefreshService fresh(){WidgetRefreshService s=new WidgetRefreshService();services.add(s);return s;}
    private static Intent tap(Context c){return new Intent(c,WidgetRefreshService.class).setAction(WidgetRefreshService.ACTION_REFRESH);}
    private static void flush()throws Exception{Repo.IO.submit(()->{}).get(3,TimeUnit.SECONDS);Handler.drain();}
    private static void reset()throws Exception{
        for(CountDownLatch latch:releases)latch.countDown();releases.clear();
        for(WidgetRefreshService s:services)s.onDestroy();services.clear();
        Repo.reset();Handler.messages.clear();SystemClock.now=10000;
        Context.JOBS.reset();Context.STYLE.data.clear();Context.starts.clear();Context.stops.clear();Context.EVENTS.clear();
        Context.blockStart=false;Context.dispatch=null;Context.stopDispatch=null;Context.NOTIFICATIONS.blockChannel=false;Context.NOTIFICATIONS.last=null;
        Store.values.data.clear();RefreshFeedback.clear(new Context());WeeklyWidget.renders=0;
    }
    private static WidgetRefreshService start(){WidgetRefreshService s=fresh();s.onStartCommand(tap(s),0,1);return s;}
    private static CountDownLatch gate(){CountDownLatch release=new CountDownLatch(1);releases.add(release);return release;}
    public static void main(String[]args)throws Exception{
        try{
            reset();Context c=new Context();Store.values.data.put("connected",false);
            WidgetRefreshService cold=start();flush();
            check(cold.foregroundCalls==1,"cold widget request enters foreground once");
            check(Context.EVENTS.indexOf("foreground")<Context.EVENTS.indexOf("reconcile"),"foreground begins before any encrypted-session read");
            check(Repo.reconcileCalls.get()==1&&Repo.syncCalls.get()==1&&Store.connected(c),"cold false preference reconciles vault and fetches without app entry");
            check(Context.JOBS.calls.isEmpty(),"manual refresh schedules no JobScheduler work");
            check(RefreshFeedback.snapshot(c).state.equals("success"),"saved valid response alone produces success");
            check(cold.stopped.isEmpty(),"foreground survives success badge hold");
            check(Context.NOTIFICATIONS.last!=null&&Context.NOTIFICATIONS.last.ongoing,"temporary ongoing notification is posted");
            Handler.advance(999);check(cold.stopped.isEmpty(),"default badge hold lasts requested interval");
            Handler.advance(1);check(cold.stopped.size()==1&&cold.foregroundRemoved==1,"terminal expiry clears foreground service");
            check(!RefreshFeedback.snapshot(c).visible&&RefreshFeedback.currentRequestId(c)==0,"terminal badge is cleared before service stop");

            reset();Scheduler.request(c);
            check(Context.starts.size()==1&&Context.starts.get(0).target==WidgetRefreshService.class,"legacy broadcast forwards explicit foreground service");
            check(WidgetRefreshService.ACTION_REFRESH.equals(Context.starts.get(0).getAction()),"legacy forwarding action matches direct widget PendingIntent");
            check(Context.JOBS.calls.isEmpty(),"legacy forwarding creates no manual job");
            check(!Store.connected(c)&&Context.starts.size()==1,"legacy forwarding does not gate cold start on display preference");
            Context.blockStart=true;Scheduler.request(c);
            check(RefreshFeedback.snapshot(c).state.equals("error")&&Store.values.data.containsKey("error"),"blocked foreground launch surfaces error without app redirect");

            reset();WidgetRefreshService denied=fresh();denied.blockForeground=true;denied.onStartCommand(tap(denied),0,1);flush();
            check(Repo.reconcileCalls.get()==0&&Repo.syncCalls.get()==0,"foreground denial starts no credential or usage operation");
            check(denied.stopped.size()==1&&RefreshFeedback.snapshot(c).state.equals("error"),"foreground denial stops with truthful error");
            reset();Context.NOTIFICATIONS.blockChannel=true;denied=start();flush();
            check(denied.stopped.size()==1&&Repo.syncCalls.get()==0,"notification setup denial is recoverable");

            reset();Repo.vaultConnected=false;WidgetRefreshService signedOut=start();flush();
            check(Repo.reconcileCalls.get()==1&&Repo.syncCalls.get()==0,"missing encrypted session never attempts usage fetch");
            check(RefreshFeedback.snapshot(c).state.equals("error")&&Store.values.data.containsKey("error"),"signed-out request reports failure");
            Handler.advance(1000);check(signedOut.stopped.size()==1,"signed-out terminal hold ends service");

            reset();CountDownLatch entered=new CountDownLatch(1),release=gate();
            Repo.action=stopped->{entered.countDown();release.await(3,TimeUnit.SECONDS);};
            WidgetRefreshService repeated=start();check(entered.await(3,TimeUnit.SECONDS),"first tap enters actual serial work");Handler.drain();
            long firstId=RefreshFeedback.currentRequestId(c);repeated.onStartCommand(tap(repeated),0,2);repeated.onStartCommand(tap(repeated),0,3);
            check(Repo.syncCalls.get()==1&&RefreshFeedback.currentRequestId(c)==firstId,"repeated running taps coalesce into same request");
            check(RefreshFeedback.snapshot(c).state.equals("running")&&repeated.foregroundCalls==1,"coalesced taps keep running state and same foreground instance");
            release.countDown();flush();Handler.advance(1000);
            check(repeated.stopped.size()==1&&repeated.stopped.get(0)==3,"service stop uses latest delivered startId");

            reset();WidgetRefreshService terminal=start();flush();long oldId=RefreshFeedback.currentRequestId(c);
            Handler.advance(500);Repo.outcome=Repo.SyncOutcome.SKIPPED;terminal.onStartCommand(tap(terminal),0,2);flush();
            check(Repo.syncCalls.get()==2&&RefreshFeedback.currentRequestId(c)!=oldId,"tap during finished badge hold starts new generation");
            check(RefreshFeedback.snapshot(c).state.equals("skipped"),"new throttled generation cannot inherit old success");
            Handler.advance(500);check(terminal.stopped.isEmpty(),"old badge clear callback cannot stop newer generation");
            Handler.advance(500);check(terminal.stopped.size()==1&&terminal.stopped.get(0)==2,"new generation owns its terminal stop");

            for(Repo.SyncOutcome outcome:new Repo.SyncOutcome[]{Repo.SyncOutcome.SKIPPED,Repo.SyncOutcome.CANCELLED}){
                reset();Repo.outcome=outcome;WidgetRefreshService skipped=start();flush();
                check(RefreshFeedback.snapshot(c).state.equals("skipped"),outcome+" never appears as success");Handler.advance(1000);check(skipped.stopped.size()==1,outcome+" ends foreground");
            }
            reset();Repo.action=stopped->{throw new IllegalStateException("Synthetic failure");};WidgetRefreshService failed=start();flush();
            check(RefreshFeedback.snapshot(c).state.equals("error")&&Store.values.data.containsKey("error"),"request failure is visible without app opening");Handler.advance(1000);check(failed.stopped.size()==1,"failed request cleans foreground");

            for(int duration:new int[]{100,10000}){
                reset();Context.STYLE.data.put("feedback_duration_ms",duration);WidgetRefreshService timed=start();flush();
                Handler.advance(duration-1);check(timed.stopped.isEmpty(),"hold honors "+duration+"ms setting");Handler.advance(1);check(timed.stopped.size()==1,"hold clears at "+duration+"ms simulated deadline");
            }
            reset();Context.STYLE.data.put("feedback_enabled",false);WidgetRefreshService hidden=start();flush();
            check(hidden.stopped.size()==1&&!RefreshFeedback.snapshot(c).visible,"disabled badge adds no terminal service hold");

            reset();CountDownLatch queueEntered=new CountDownLatch(1),queueRelease=gate();
            Repo.IO.submit(()->{queueEntered.countDown();try{queueRelease.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
            check(queueEntered.await(3,TimeUnit.SECONDS),"shared credential queue can be occupied");WidgetRefreshService queued=start();
            Handler.advance(50000);check(RefreshFeedback.snapshot(c).visible&&RefreshFeedback.snapshot(c).state.equals("waiting"),"waiting heartbeat prevents silent badge disappearance before deadline");
            Handler.advance(5000);check(RefreshFeedback.snapshot(c).state.equals("error"),"queued request reaches truthful timeout");
            queueRelease.countDown();flush();check(Repo.syncCalls.get()==0,"timed-out queued request never later fetches when app opens");
            Handler.advance(1000);check(queued.stopped.size()==1,"queued timeout stops after terminal hold");

            reset();CountDownLatch rotationEntered=new CountDownLatch(1),rotationRelease=gate();AtomicBoolean interrupted=new AtomicBoolean();
            Repo.action=stopped->{rotationEntered.countDown();try{rotationRelease.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){interrupted.set(true);throw e;}Repo.rotationSaved=true;};
            WidgetRefreshService rotating=start();check(rotationEntered.await(3,TimeUnit.SECONDS),"in-flight rotation simulation starts");Handler.drain();
            Handler.advance(55000);check(RefreshFeedback.snapshot(c).state.equals("error")&&rotating.stopped.isEmpty(),"watchdog reports error but lets token transaction drain");
            rotating.onStartCommand(tap(rotating),0,2);check(Repo.syncCalls.get()==1,"tap during timeout drain does not queue another rotation");
            rotationRelease.countDown();flush();check(Repo.rotationSaved&&!interrupted.get(),"timeout preserves issued token transaction without interruption");
            check(!RefreshFeedback.snapshot(c).state.equals("success"),"late timed-out completion never becomes success");
            Handler.advance(1000);check(rotating.stopped.size()==1,"drained timeout finishes bounded service");

            reset();CountDownLatch slowEntered=new CountDownLatch(1),slowRelease=gate();Repo.action=stopped->{slowEntered.countDown();slowRelease.await(3,TimeUnit.SECONDS);Repo.rotationSaved=true;};
            WidgetRefreshService capped=start();slowEntered.await(3,TimeUnit.SECONDS);Handler.drain();Handler.advance(70000);
            check(capped.stopped.size()==1&&capped.foregroundRemoved==1,"absolute service cap stops foreground even during unusually slow transport");
            long replacement=RefreshFeedback.begin(c);slowRelease.countDown();flush();
            check(Repo.rotationSaved&&RefreshFeedback.snapshot(c).requestId==replacement&&RefreshFeedback.snapshot(c).state.equals("waiting"),"late transport save does not overwrite a new generation");

            reset();WidgetRefreshService successDestroy=start();flush();successDestroy.onDestroy();
            check(RefreshFeedback.snapshot(c).state.equals("success")&&!Store.values.data.containsKey("error"),"destroy during terminal hold does not relabel successful fetch as failure");
            reset();WidgetRefreshService successTimeout=start();flush();successTimeout.onTimeout(1,1);
            check(RefreshFeedback.snapshot(c).state.equals("success")&&successTimeout.stopped.size()==1,"OS timeout preserves already successful result while stopping service");

            reset();CountDownLatch destroyEntered=new CountDownLatch(1),destroyRelease=gate();Repo.action=stopped->{destroyEntered.countDown();destroyRelease.await(3,TimeUnit.SECONDS);Repo.rotationSaved=true;};
            WidgetRefreshService destroyed=start();destroyEntered.await(3,TimeUnit.SECONDS);Handler.drain();destroyed.onDestroy();long afterDestroy=RefreshFeedback.begin(c);destroyRelease.countDown();flush();
            check(Repo.rotationSaved&&RefreshFeedback.snapshot(c).requestId==afterDestroy&&RefreshFeedback.snapshot(c).state.equals("waiting"),"destroyed old transaction cannot complete new feedback");

            reset();CountDownLatch logoutEntered=new CountDownLatch(1),logoutRelease=gate();Repo.action=stopped->{logoutEntered.countDown();logoutRelease.await(3,TimeUnit.SECONDS);};
            WidgetRefreshService logout=start();logoutEntered.await(3,TimeUnit.SECONDS);Handler.drain();Context.stopDispatch=intent->logout.onDestroy();
            logoutRelease.countDown();Repo.IO.submit(()->{Scheduler.cancel(c);Store.values.data.clear();}).get(3,TimeUnit.SECONDS);Handler.drain();
            check(Context.stops.size()==1&&RefreshFeedback.currentRequestId(c)==0&&!RefreshFeedback.snapshot(c).visible,"serial logout stops helper without deadlock or late feedback restoration");

            reset();WidgetRefreshService wrong=fresh();wrong.onStartCommand(new Intent(wrong,WidgetRefreshService.class).setAction("wrong"),0,9);
            check(wrong.foregroundCalls==0&&Repo.syncCalls.get()==0&&wrong.stopped.get(0)==9,"unknown action performs no refresh");
            reset();WidgetRefreshService nullIntent=fresh();nullIntent.onStartCommand(null,0,10);check(nullIntent.stopped.size()==1&&Repo.syncCalls.get()==0,"nonsticky null restart never replays request");
            reset();JobInfo legacy=new JobInfo.Builder(Scheduler.ONCE,new android.content.ComponentName(c,UsageJob.class)).build();Context.JOBS.schedule(legacy);Store.values.data.put("requested",1L);Scheduler.ensure(c);
            check(Context.JOBS.getPendingJob(Scheduler.ONCE)==null&&!Store.values.data.containsKey("requested"),"app ensure retires persisted legacy manual request");
            UsageJob auto=new UsageJob();check(!auto.onStartJob(new JobParameters(Scheduler.ONCE,0)),"old manual job refuses to run after app opening");check(Repo.syncCalls.get()==0,"legacy manual job does no delayed usage fetch");

            reset();ExecutorService active=Repo.IO;active.shutdown();active.awaitTermination(3,TimeUnit.SECONDS);WidgetRefreshService rejected=start();
            check(RefreshFeedback.snapshot(c).state.equals("error")&&Repo.syncCalls.get()==0,"executor rejection reports failure without crash");Handler.advance(1000);check(rejected.stopped.size()==1,"executor rejection stops foreground");Repo.IO=Executors.newSingleThreadExecutor();
            System.out.println("PASS: "+checks+" widget foreground-refresh checks (fake Android/Repo; no Activity/device/account/network)");
        }finally{for(CountDownLatch latch:releases)latch.countDown();for(WidgetRefreshService s:services)s.onDestroy();Repo.IO.shutdownNow();}
    }
}
