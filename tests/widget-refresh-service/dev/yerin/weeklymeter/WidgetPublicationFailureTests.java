package dev.yerin.weeklymeter;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Actual service and feedback callbacks with rejected widget publication; no device/network. */
public final class WidgetPublicationFailureTests {
    private static int checks;
    private static long elapsed=10000;
    private static final Context context=new Context();
    private static final List<String> failures=new ArrayList<>();
    private static final List<WidgetRefreshService> services=new ArrayList<>();
    private static final List<CountDownLatch> releases=new ArrayList<>();
    private interface Checked {void run()throws Exception;}
    private static void check(boolean value,String label){checks++;if(!value)failures.add(label);}
    private static void noCrash(Checked action,String label)throws Exception{
        try{action.run();check(true,label);}catch(RuntimeException failure){check(false,label+" (escaped "+failure.getClass().getSimpleName()+")");}
    }
    private static void scenario(String name,Checked action)throws Exception{
        reset();try{action.run();}catch(Exception failure){failures.add(name+": unexpected "+failure.getClass().getSimpleName());}
    }
    private static void reset()throws Exception{
        WeeklyWidget.reject=attempt->false;
        for(CountDownLatch release:releases)release.countDown();releases.clear();
        for(WidgetRefreshService service:services)service.onDestroy();services.clear();
        if(Repo.IO.isShutdown())Repo.IO=Executors.newSingleThreadExecutor();
        Repo.reset();Handler.messages.clear();SystemClock.now=(elapsed+=100000);
        Context.JOBS.reset();Context.STYLE.data.clear();Context.FLOATING_STYLE.data.clear();Context.starts.clear();Context.stops.clear();Context.EVENTS.clear();
        Context.blockStart=false;Context.dispatch=null;Context.stopDispatch=null;Context.NOTIFICATIONS.blockChannel=false;Context.NOTIFICATIONS.last=null;
        FloatingWidgetService.shows=0;FloatingWidgetService.showing=false;
        Store.values.data.clear();RefreshFeedback.clear(context);WeeklyWidget.reset();
    }
    private static void badgesOff(){Context.STYLE.data.put("feedback_enabled",false);Context.FLOATING_STYLE.data.put("feedback_enabled",false);}
    private static WidgetRefreshService fresh(){WidgetRefreshService service=new WidgetRefreshService();services.add(service);return service;}
    private static Intent tap(WidgetRefreshService service){return new Intent(service,WidgetRefreshService.class).setAction(WidgetRefreshService.ACTION_REFRESH);}
    private static void start(WidgetRefreshService service)throws Exception{noCrash(()->service.onStartCommand(tap(service),0,1),"start callback tolerates rejected publication");}
    private static void flush()throws Exception{Repo.IO.submit(()->{}).get(3,TimeUnit.SECONDS);Handler.drain();}
    private static CountDownLatch releaseGate(){CountDownLatch release=new CountDownLatch(1);releases.add(release);return release;}
    private static void rejectNext(){final int next=WeeklyWidget.renders+1;WeeklyWidget.reject=attempt->attempt==next;}
    private static void stopped(WidgetRefreshService service,int id,String label){
        check(service.foregroundRemoved==1,label+": foreground removed exactly once");
        check(service.stopped.size()==1&&service.stopped.get(0)==id,label+": latest service start stopped exactly once");
    }
    private static final class Running {
        final WidgetRefreshService service=fresh();
        final CountDownLatch entered=new CountDownLatch(1),release=releaseGate();
        final AtomicBoolean interrupted=new AtomicBoolean();
        Running()throws Exception{
            Repo.action=cancelled->{entered.countDown();try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException failure){interrupted.set(true);throw failure;}Repo.rotationSaved=true;};
            start(service);check(entered.await(3,TimeUnit.SECONDS),"controlled request reaches repository");Handler.drain();
        }
        void finish()throws Exception{release.countDown();flush();}
    }
    private static void initialFailure()throws Exception{
        WeeklyWidget.reject=attempt->attempt==1;WidgetRefreshService service=fresh();start(service);flush();
        check(WeeklyWidget.failures==1,"initial host failure actually injected");
        check(Repo.syncCalls.get()==1,"initial render failure cannot prevent query submission");
        Handler.advance(1000);stopped(service,1,"initial render failure");
        Handler.advance(70000);check(Repo.syncCalls.get()==1&&service.stopped.size()==1,"initial failure never duplicates query or cleanup later");
    }
    private static void runningCallbackFailure()throws Exception{
        CountDownLatch entered=new CountDownLatch(1),release=releaseGate();
        Repo.action=cancelled->{entered.countDown();release.await(3,TimeUnit.SECONDS);};
        WidgetRefreshService service=fresh();start(service);check(entered.await(3,TimeUnit.SECONDS),"query starts before running-state callback");
        rejectNext();noCrash(Handler::drain,"running-state callback tolerates host failure");
        release.countDown();flush();check(WeeklyWidget.failures==1&&Repo.syncCalls.get()==1,"running-state failure does not repeat query");
        Handler.advance(1000);stopped(service,1,"running-state failure");
    }
    private static void repeatedTapFailure()throws Exception{
        badgesOff();Running running=new Running();long id=RefreshFeedback.currentRequestId(context);rejectNext();
        noCrash(()->running.service.onStartCommand(tap(running.service),0,2),"coalesced tap tolerates host failure");
        check(RefreshFeedback.currentRequestId(context)==id&&Repo.syncCalls.get()==1,"failed repeat publication retains same request");
        running.finish();stopped(running.service,2,"coalesced tap failure");
    }
    private static void queuedHeartbeatFailure()throws Exception{
        badgesOff();CountDownLatch entered=new CountDownLatch(1),release=releaseGate();
        Repo.IO.submit(()->{entered.countDown();try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException failure){Thread.currentThread().interrupt();}});
        check(entered.await(3,TimeUnit.SECONDS),"serial queue deliberately held");WidgetRefreshService service=fresh();start(service);rejectNext();
        noCrash(()->Handler.advance(10000),"waiting heartbeat tolerates host failure");
        check(WeeklyWidget.failures==0&&WeeklyWidget.renders==0&&Repo.syncCalls.get()==0,"disabled waiting badge causes no publication or premature query");
        release.countDown();flush();check(Repo.syncCalls.get()==1,"waiting request runs once after queue clears");stopped(service,1,"waiting heartbeat failure");
    }
    private static void terminalFailure()throws Exception{
        badgesOff();Running running=new Running();rejectNext();
        noCrash(running::finish,"successful completion tolerates terminal publication failure");
        check(WeeklyWidget.failures==1&&Repo.syncCalls.get()==1,"terminal failure is injected after a single successful query");
        check(!Store.values.data.containsKey("error"),"display failure does not relabel saved usage as an account/query failure");
        stopped(running.service,1,"terminal failure");
    }
    private static void closeFailure()throws Exception{
        badgesOff();Running running=new Running();final int cleanupAttempt=WeeklyWidget.renders+2;WeeklyWidget.reject=attempt->attempt==cleanupAttempt;
        noCrash(running::finish,"final badge-clear publication cannot escape service cleanup");
        check(WeeklyWidget.failures==0&&WeeklyWidget.renders==cleanupAttempt-1,"cleanup avoids a second publication when disabled badges are already clear");stopped(running.service,1,"cleanup failure");
        check(RefreshFeedback.currentRequestId(context)==0,"cleanup failure still clears request generation");
        Handler.advance(70000);check(running.service.stopped.size()==1&&Repo.syncCalls.get()==1,"old callbacks cannot resurrect cleaned task");
    }
    private static void timeoutAndLateFailure()throws Exception{
        Running running=new Running();rejectNext();
        noCrash(()->running.service.onTimeout(1,1),"OS timeout tolerates rejected error publication");
        stopped(running.service,1,"OS timeout publication failure");
        long newer=RefreshFeedback.begin(context);rejectNext();noCrash(running::finish,"late completion tolerates rejected cache publication");
        check(Repo.rotationSaved&&!running.interrupted.get(),"OS timeout does not interrupt issued-token transaction");
        check(RefreshFeedback.currentRequestId(context)==newer&&"waiting".equals(RefreshFeedback.snapshot(context).state),"failed late publication never overwrites new feedback generation");
        check(running.service.stopped.size()==1&&Repo.syncCalls.get()==1,"late completion neither restarts nor stops service twice");
    }
    private static void hardCapFailure()throws Exception{
        Running running=new Running();WeeklyWidget.reject=attempt->true;
        noCrash(()->Handler.advance(70000),"watchdog, expiry and hard-cap publications tolerate persistent host rejection");
        stopped(running.service,1,"persistent host rejection at service cap");
        check(WeeklyWidget.failures>0,"persistent failures reached actual main callbacks");
        noCrash(running::finish,"transport draining after service cap tolerates host rejection");
        check(Repo.rotationSaved&&!running.interrupted.get()&&Repo.syncCalls.get()==1,"hard cap preserves transaction and prevents duplicate query");
    }
    private static void destroyFailure()throws Exception{
        Running running=new Running();rejectNext();
        noCrash(running.service::onDestroy,"destroy callback tolerates rejected error publication");
        check(running.service.destroyCalls==1,"destroy always delegates to framework superclass");
        check(running.service.foregroundRemoved==1,"destroy removes foreground independently of widget publication");
        long newer=RefreshFeedback.begin(context);rejectNext();noCrash(running::finish,"destroyed task late completion tolerates publication failure");
        check(Repo.rotationSaved&&!running.interrupted.get(),"destroy allows token transaction to finish safely");
        check(RefreshFeedback.currentRequestId(context)==newer&&"waiting".equals(RefreshFeedback.snapshot(context).state),"destroyed task cannot restore old success badge");
    }
    private static void foregroundDeniedFailure()throws Exception{
        WidgetRefreshService service=fresh();service.blockForeground=true;rejectNext();start(service);
        check(WeeklyWidget.failures==1,"blocked foreground path attempts publication once");
        check(service.stopped.size()==1&&service.stopped.get(0)==1,"failed denial publication cannot prevent service stop");
        check(Repo.syncCalls.get()==0&&Repo.reconcileCalls.get()==0,"foreground-denied request never reads account or queries");
        check("error".equals(RefreshFeedback.snapshot(context).state),"foreground denial remains error rather than success");
    }
    private static void rejectedExecutorFailure()throws Exception{
        badgesOff();Repo.IO.shutdown();check(Repo.IO.awaitTermination(3,TimeUnit.SECONDS),"executor deliberately rejected");
        WeeklyWidget.reject=attempt->true;WidgetRefreshService service=fresh();start(service);
        check(Repo.syncCalls.get()==0&&WeeklyWidget.failures==1,"executor rejection publishes final error once without querying or redundant badge cleanup");
        stopped(service,1,"rejected executor plus host failure");
        Repo.IO=Executors.newSingleThreadExecutor();
    }
    private static void feedbackExpiryFailure()throws Exception{
        Context.STYLE.data.put("feedback_duration_ms",100);Context.FLOATING_STYLE.data.put("feedback_duration_ms",200);
        long id=RefreshFeedback.begin(context);RefreshFeedback.success(context,id);RefreshFeedback.publishChanged(context);WeeklyWidget.reject=attempt->true;
        noCrash(()->Handler.advance(100),"home feedback expiry tolerates failed publication");
        check(!RefreshFeedback.snapshot(context).visible&&RefreshFeedback.snapshot(context,true).visible,"home expiry still ends model independently of floating badge");
        noCrash(()->Handler.advance(100),"floating feedback expiry tolerates failed publication");
        check(!RefreshFeedback.snapshot(context,true).visible&&WeeklyWidget.failures==2,"both expiry models settle despite host rejection");
        check(Repo.syncCalls.get()==0&&Context.starts.isEmpty(),"failed feedback expiry starts no service or query");
    }
    private static void optimizedFeedback(){
        long id=RefreshFeedback.begin(context);RefreshFeedback.success(context,id);RefreshFeedback.publishChanged(context);
        int before=WeeklyWidget.renders;check(Handler.messages.size()==1,"equal surface deadlines use one timer");
        Handler.advance(1000);check(WeeklyWidget.renders==before+1,"equal surface expiries use one publication");
        RefreshFeedback.clear(context);RefreshFeedback.publishChanged(context);check(WeeklyWidget.renders==before+1,"service cleanup after expiry does not duplicate publication");
        badgesOff();id=RefreshFeedback.begin(context);RefreshFeedback.success(context,id);RefreshFeedback.publishChanged(context);Handler.advance(1000);
        check(WeeklyWidget.renders==before+1,"disabled badges trigger no begin, success or expiry publications");
        Context.STYLE.data.put("feedback_enabled",true);Context.FLOATING_STYLE.data.put("feedback_enabled",true);
        id=RefreshFeedback.begin(context);RefreshFeedback.success(context,id);RefreshFeedback.Snapshot inFlight=RefreshFeedback.snapshot(context);
        Handler.advance(1000);before=WeeklyWidget.renders;
        RefreshFeedback.homePublishedOne(context,inFlight);RefreshFeedback.published(false,inFlight);Handler.drain();
        check(WeeklyWidget.renders==before+1,"late visible bitmap is cleared even when its expiry callback ran before publication");
        check(WeeklyWidget.homeRenders>0&&!RefreshFeedback.snapshot(context).visible,"late-publication repair keeps expiry and performs no account request");
        id=RefreshFeedback.begin(context);RefreshFeedback.success(context,id);inFlight=RefreshFeedback.snapshot(context);
        RefreshFeedback.clear(context);before=WeeklyWidget.renders;RefreshFeedback.homePublishedOne(context,inFlight);Handler.drain();
        check(WeeklyWidget.renders==before+1,"request cleared during drawing still repairs its late visible publication");
        id=RefreshFeedback.begin(context);RefreshFeedback.success(context,id);RefreshFeedback.homePublishedOne(context,RefreshFeedback.snapshot(context));
        // Another ID fails: no full-surface acknowledgement occurs.
        before=WeeklyWidget.renders;Handler.advance(1000);
        check(WeeklyWidget.renders==before+1,"partial visible publication still clears after expiry");
        check(Repo.syncCalls.get()==0&&Context.starts.isEmpty(),"feedback optimization and repairs are cache-only");
    }
    public static void main(String[] args)throws Exception{
        try{
            scenario("initial",WidgetPublicationFailureTests::initialFailure);
            scenario("running",WidgetPublicationFailureTests::runningCallbackFailure);
            scenario("repeat tap",WidgetPublicationFailureTests::repeatedTapFailure);
            scenario("queued heartbeat",WidgetPublicationFailureTests::queuedHeartbeatFailure);
            scenario("terminal",WidgetPublicationFailureTests::terminalFailure);
            scenario("cleanup",WidgetPublicationFailureTests::closeFailure);
            scenario("OS timeout",WidgetPublicationFailureTests::timeoutAndLateFailure);
            scenario("hard cap",WidgetPublicationFailureTests::hardCapFailure);
            scenario("destroy",WidgetPublicationFailureTests::destroyFailure);
            scenario("foreground denied",WidgetPublicationFailureTests::foregroundDeniedFailure);
            scenario("executor rejected",WidgetPublicationFailureTests::rejectedExecutorFailure);
            scenario("feedback expiry",WidgetPublicationFailureTests::feedbackExpiryFailure);
            scenario("optimized feedback",WidgetPublicationFailureTests::optimizedFeedback);
            if(!failures.isEmpty()){
                for(String failure:failures)System.err.println("FAIL: "+failure);
                throw new AssertionError(failures.size()+" of "+checks+" publication-failure checks failed");
            }
            System.out.println("PASS: "+checks+" publication-failure lifecycle checks (actual service/feedback; fake Android/Repo; no device/account/network)");
        }finally{
            WeeklyWidget.reject=attempt->false;for(CountDownLatch release:releases)release.countDown();
            for(WidgetRefreshService service:services)service.onDestroy();Repo.IO.shutdownNow();
        }
    }
}
