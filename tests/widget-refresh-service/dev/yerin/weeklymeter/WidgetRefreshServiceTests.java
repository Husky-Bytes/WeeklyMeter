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
    private static long elapsedBase=10000;
    private static final List<WidgetRefreshService> services=new ArrayList<>();
    private static final List<CountDownLatch> releases=new ArrayList<>();
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static WidgetRefreshService fresh(){WidgetRefreshService s=new WidgetRefreshService();services.add(s);return s;}
    private static Intent tap(Context c){return new Intent(c,WidgetRefreshService.class).setAction(WidgetRefreshService.ACTION_REFRESH);}
    private static Intent homeTap(Context c,int widgetId){return new Intent(c,WidgetRefreshService.class).setAction(WidgetRefreshService.ACTION_HOME_TAP).putExtra(WidgetRefreshService.EXTRA_APP_WIDGET_ID,widgetId);}
    private static void flush()throws Exception{Repo.IO.submit(()->{}).get(3,TimeUnit.SECONDS);Handler.drain();}
    private static void reset()throws Exception{
        for(CountDownLatch latch:releases)latch.countDown();releases.clear();
        for(WidgetRefreshService s:services)s.onDestroy();services.clear();
        Repo.reset();Handler.messages.clear();SystemClock.now=(elapsedBase+=100000);
        Context.JOBS.reset();Context.STYLE.data.clear();Context.FLOATING_STYLE.data.clear();Context.starts.clear();Context.stops.clear();Context.EVENTS.clear();
        FloatingWidgetService.shows=0;FloatingWidgetService.showing=false;
        Context.blockStart=false;Context.dispatch=null;Context.stopDispatch=null;Context.NOTIFICATIONS.blockChannel=false;Context.NOTIFICATIONS.last=null;
        Store.values.data.clear();RefreshFeedback.clear(new Context());WeeklyWidget.reset();
    }
    private static WidgetRefreshService start(){WidgetRefreshService s=fresh();s.onStartCommand(tap(s),0,1);return s;}
    private static CountDownLatch gate(){CountDownLatch release=new CountDownLatch(1);releases.add(release);return release;}
    private static void tapSequenceChecks(){
        WidgetTapSequence sequence=new WidgetTapSequence();
        check(sequence.tap(7,0)==WidgetTapSequence.Action.REFRESH,"first home tap refreshes immediately at elapsed zero");
        check(sequence.tap(7,450)==WidgetTapSequence.Action.SUPPRESS,"second home tap is gesture-only");
        check(sequence.tap(7,900)==WidgetTapSequence.Action.SHOW_FLOATING,"third home tap at exact 900 ms boundary opens floating");
        check(sequence.tap(7,901)==WidgetTapSequence.Action.SUPPRESS,"fourth rapid tap cannot duplicate open or fetch");
        check(sequence.tap(99,1801)==WidgetTapSequence.Action.SUPPRESS,"continuous burst is suppressed even across widget IDs");
        check(sequence.tap(99,2702)==WidgetTapSequence.Action.REFRESH,"a 901 ms quiet gap admits a new request");
        sequence.reset();
        check(sequence.tap(1,100)==WidgetTapSequence.Action.REFRESH,"fresh sequence admits first widget");
        check(sequence.tap(2,200)==WidgetTapSequence.Action.REFRESH,"different widget starts its own sequence");
        check(sequence.tap(1,300)==WidgetTapSequence.Action.REFRESH,"alternating widgets cannot complete a triplet");
        check(sequence.tap(1,400)==WidgetTapSequence.Action.SUPPRESS,"same widget second tap after switch is suppressed");
        check(sequence.tap(1,500)==WidgetTapSequence.Action.SHOW_FLOATING,"three consecutive same-widget taps open once");
        sequence.reset();
        check(sequence.tap(1,0)==WidgetTapSequence.Action.REFRESH,"slow tap sequence starts normally");
        check(sequence.tap(1,901)==WidgetTapSequence.Action.REFRESH,"tap outside total window becomes a new refresh");
        check(sequence.tap(1,902)==WidgetTapSequence.Action.SUPPRESS,"new window owns only its next tap");
        check(sequence.tap(1,1801)==WidgetTapSequence.Action.SHOW_FLOATING,"restarted sequence also accepts its inclusive boundary");
        sequence.reset();sequence.tap(1,5000);sequence.tap(1,5100);
        check(sequence.tap(1,100)==WidgetTapSequence.Action.REFRESH,"backward clock invalidates pending triple");
        check(sequence.tap(1,200)==WidgetTapSequence.Action.SUPPRESS,"backward clock starts a new count");
        check(sequence.tap(1,300)==WidgetTapSequence.Action.SHOW_FLOATING,"new time base still supports a full fresh triple");
        check(sequence.tap(1,-1)==WidgetTapSequence.Action.REFRESH,"invalid negative time cannot trigger floating");
        check(sequence.tap(1,1)==WidgetTapSequence.Action.REFRESH,"invalid time leaves no partial gesture");
        sequence.reset();
        check(sequence.tap(-1,Long.MAX_VALUE-900)==WidgetTapSequence.Action.REFRESH,"legacy missing widget ID may start a gesture");
        check(sequence.tap(-1,Long.MAX_VALUE-400)==WidgetTapSequence.Action.SUPPRESS,"large monotonic times do not overflow interval comparison");
        check(sequence.tap(-1,Long.MAX_VALUE)==WidgetTapSequence.Action.SHOW_FLOATING,"large-clock boundary completes safely");
    }
    public static void main(String[]args)throws Exception{
        try{
            tapSequenceChecks();
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

            reset();WidgetRefreshService triple=fresh();triple.onStartCommand(homeTap(triple,11),0,1);flush();
            long tripleRequest=RefreshFeedback.currentRequestId(c);
            check(Repo.syncCalls.get()==1&&FloatingWidgetService.shows==0,"first home tap fetches without opening overlay");
            Handler.advance(200);triple.onStartCommand(homeTap(triple,11),0,2);flush();
            check(Repo.syncCalls.get()==1&&RefreshFeedback.currentRequestId(c)==tripleRequest,"second home tap never refetches a fast completed request");
            Handler.advance(200);triple.onStartCommand(homeTap(triple,11),0,3);flush();
            check(FloatingWidgetService.shows==1&&Repo.syncCalls.get()==1,"third home tap opens floating without another HTTP request");
            check(Context.EVENTS.indexOf("foreground")<Context.EVENTS.indexOf("floating"),"triplet opens overlay only after timely refresh foreground entry");
            Handler.advance(200);triple.onStartCommand(homeTap(triple,11),0,4);flush();
            check(FloatingWidgetService.shows==1&&Repo.syncCalls.get()==1,"fourth rapid home tap is harmless after triplet");

            reset();Context.STYLE.data.put("feedback_enabled",false);Context.FLOATING_STYLE.data.put("feedback_enabled",false);
            WidgetRefreshService fastFirst=fresh();fastFirst.onStartCommand(homeTap(fastFirst,12),0,1);flush();
            check(fastFirst.stopped.size()==1,"disabled home badge may end the first request before the next tap");
            Handler.advance(150);WidgetRefreshService fastSecond=fresh();fastSecond.onStartCommand(homeTap(fastSecond,12),0,2);flush();
            check(fastSecond.foregroundCalls==1&&fastSecond.foregroundRemoved==1&&fastSecond.stopped.size()==1,"second tap after service end promptly enters and exits foreground");
            Handler.advance(150);WidgetRefreshService fastThird=fresh();fastThird.onStartCommand(homeTap(fastThird,12),0,3);flush();
            check(FloatingWidgetService.shows==1&&Repo.syncCalls.get()==1,"triplet survives refresh service replacement without extra HTTP");
            check(fastThird.foregroundCalls==1&&fastThird.foregroundRemoved==1&&fastThird.stopped.size()==1,"third gesture-only cold service stops after overlay dispatch");

            reset();WidgetRefreshService mixed=fresh();mixed.onStartCommand(homeTap(mixed,21),0,1);flush();
            Handler.advance(100);mixed.onStartCommand(tap(mixed),0,2);flush();
            Handler.advance(100);mixed.onStartCommand(homeTap(mixed,21),0,3);flush();
            Handler.advance(100);mixed.onStartCommand(homeTap(mixed,21),0,4);flush();
            check(FloatingWidgetService.shows==0&&Repo.syncCalls.get()==3,"manual/floating refresh breaks a partial home gesture and is never counted");
            Handler.advance(100);mixed.onStartCommand(homeTap(mixed,21),0,5);flush();
            check(FloatingWidgetService.shows==1&&Repo.syncCalls.get()==3,"fresh consecutive home triplet opens after unrelated manual refresh");

            reset();WidgetRefreshService slowTaps=fresh();slowTaps.onStartCommand(homeTap(slowTaps,31),0,1);flush();
            Handler.advance(901);slowTaps.onStartCommand(homeTap(slowTaps,31),0,2);flush();
            Handler.advance(901);slowTaps.onStartCommand(homeTap(slowTaps,31),0,3);flush();
            check(Repo.syncCalls.get()==3&&FloatingWidgetService.shows==0,"ordinary spaced home taps each refresh and never open floating");

            reset();Context.STYLE.data.put("feedback_duration_ms",100);Context.FLOATING_STYLE.data.put("feedback_duration_ms",10000);FloatingWidgetService.showing=true;
            WidgetRefreshService longFloating=start();flush();long sharedId=RefreshFeedback.currentRequestId(c);
            check(RefreshFeedback.snapshot(c).requestId==sharedId&&RefreshFeedback.snapshot(c,true).requestId==sharedId,"home and floating feedback share the same actual query ID");
            Handler.advance(100);
            check(!RefreshFeedback.snapshot(c).visible&&RefreshFeedback.snapshot(c,true).visible,"home feedback expires independently of ten-second floating feedback");
            check(longFloating.stopped.isEmpty()&&RefreshFeedback.currentRequestId(c)==sharedId,"short home expiry cannot clear active floating feedback generation");
            Handler.advance(9899);check(longFloating.stopped.isEmpty(),"floating terminal hold remains until its own last millisecond");
            Handler.advance(1);check(longFloating.stopped.size()==1&&!RefreshFeedback.snapshot(c,true).visible,"maximum visible terminal expiry stops bounded refresh service");

            reset();Context.STYLE.data.put("feedback_duration_ms",10000);Context.FLOATING_STYLE.data.put("feedback_duration_ms",100);FloatingWidgetService.showing=true;
            WidgetRefreshService longHome=start();flush();Handler.advance(100);
            check(RefreshFeedback.snapshot(c).visible&&!RefreshFeedback.snapshot(c,true).visible&&longHome.stopped.isEmpty(),"floating expiry cannot shorten home feedback");
            Handler.advance(9900);check(longHome.stopped.size()==1,"home longer duration still owns its normal terminal hold");

            reset();Context.STYLE.data.put("feedback_enabled",false);Context.FLOATING_STYLE.data.put("feedback_duration_ms",5000);FloatingWidgetService.showing=true;
            WidgetRefreshService floatingOnly=start();flush();
            check(!RefreshFeedback.snapshot(c).visible&&RefreshFeedback.snapshot(c,true).visible&&floatingOnly.stopped.isEmpty(),"disabled home feedback cannot disable visible floating feedback");
            Handler.advance(5000);check(floatingOnly.stopped.size()==1,"floating-only badge ends at its configured duration");

            reset();Context.FLOATING_STYLE.data.put("feedback_enabled",false);FloatingWidgetService.showing=true;
            WidgetRefreshService homeOnly=start();flush();
            check(RefreshFeedback.snapshot(c).visible&&!RefreshFeedback.snapshot(c,true).visible,"disabled floating feedback does not affect home feedback");
            Handler.advance(1000);check(homeOnly.stopped.size()==1,"disabled floating feedback does not prolong home service");

            reset();Context.STYLE.data.put("feedback_duration_ms",100);Context.FLOATING_STYLE.data.put("feedback_duration_ms",10000);
            WidgetRefreshService openedDuringHold=start();flush();Handler.advance(50);FloatingWidgetService.showing=true;Handler.advance(50);
            check(openedDuringHold.stopped.isEmpty()&&RefreshFeedback.snapshot(c,true).visible,"floating opened after HTTP completion retains its independent badge");
            Handler.advance(9900);check(openedDuringHold.stopped.size()==1,"late floating appearance cannot extend badge beyond original expiry");

            reset();Context.STYLE.data.put("feedback_duration_ms",100);Context.FLOATING_STYLE.data.put("feedback_duration_ms",1000);
            long firstFeedback=RefreshFeedback.begin(c);RefreshFeedback.success(c,firstFeedback);Handler.advance(50);
            long nextFeedback=RefreshFeedback.begin(c);RefreshFeedback.success(c,nextFeedback);Handler.advance(50);
            check(RefreshFeedback.snapshot(c).requestId==nextFeedback&&RefreshFeedback.snapshot(c).visible&&RefreshFeedback.snapshot(c,true).requestId==nextFeedback,"old per-surface expiry cannot clear a newer shared request");
            Handler.advance(50);check(!RefreshFeedback.snapshot(c).visible&&RefreshFeedback.snapshot(c,true).visible,"new generation independently expires only its home surface");
            RefreshFeedback.clear(c);check(!RefreshFeedback.snapshot(c).visible&&!RefreshFeedback.snapshot(c,true).visible&&RefreshFeedback.currentRequestId(c)==0,"logout clear removes both feedback models and shared request");

            reset();Context.FLOATING_STYLE.data.put("feedback_enabled","invalid");Context.FLOATING_STYLE.data.put("feedback_duration_ms","invalid");
            long malformed=RefreshFeedback.begin(c);RefreshFeedback.success(c,malformed);
            check(RefreshFeedback.snapshot(c).visible&&!RefreshFeedback.snapshot(c,true).visible,"malformed floating options cannot disable valid home feedback");
            Context.FLOATING_STYLE.data.put("feedback_enabled",true);
            check(RefreshFeedback.snapshot(c,true).visible&&RefreshFeedback.snapshot(c,true).expiresAt-SystemClock.now==1000,"malformed floating duration falls back independently to one second");
            Context.STYLE.data.put("feedback_enabled",false);RefreshFeedback.running(c,malformed);
            check(!RefreshFeedback.snapshot(c).visible&&RefreshFeedback.snapshot(c,true).state.equals("running")&&RefreshFeedback.snapshot(c,true).visible,"disabled home surface does not hide floating in-flight feedback");

            reset();ExecutorService active=Repo.IO;active.shutdown();active.awaitTermination(3,TimeUnit.SECONDS);WidgetRefreshService rejected=start();
            check(RefreshFeedback.snapshot(c).state.equals("error")&&Repo.syncCalls.get()==0,"executor rejection reports failure without crash");Handler.advance(1000);check(rejected.stopped.size()==1,"executor rejection stops foreground");Repo.IO=Executors.newSingleThreadExecutor();
            System.out.println("PASS: "+checks+" widget foreground-refresh checks (fake Android/Repo; no Activity/device/account/network)");
        }finally{for(CountDownLatch latch:releases)latch.countDown();for(WidgetRefreshService s:services)s.onDestroy();Repo.IO.shutdownNow();}
    }
}
