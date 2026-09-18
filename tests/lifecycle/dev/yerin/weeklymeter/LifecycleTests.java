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
        Build.VERSION.SDK_INT=35;AppWidgetManager.ids=new int[]{1};Handler.drain();
        WeeklyWidget.renders=0;WeeklyWidget.attempts=0;WeeklyWidget.percent=0;WeeklyWidget.fetchedAt=0;WeeklyWidget.failures.set(0);
        Store.saveFixture(88,1000);
        RefreshFeedback.clear(new Context());Context.STYLE.data.clear();Context.starts.clear();Context.stops.clear();Context.blockStart=false;
        SystemClock.now=10000;Handler.queue.clear();Handler.delayed.clear();Repo.outcome=Repo.SyncOutcome.UPDATED;Repo.action=()->{};
        Repo.vaultConnected=true;Repo.reconcileFailure=null;Repo.reconcileAction=()->{};Repo.reconcileCalls.set(0);Repo.syncCalls.set(0);
        Context.DIAGNOSTICS.data.clear();Context.failDiagnostics=false;
    }
    private static void finishWorker()throws Exception{Repo.IO.submit(()->{}).get(5,TimeUnit.SECONDS);}
    private static void flush()throws Exception{Repo.IO.submit(()->{}).get(5,TimeUnit.SECONDS);Handler.drain();}
    public static void main(String[]args)throws Exception{
        try{publicationBoundary();scheduler();lifecycle();automaticRecovery();automaticEligibility();feedback();diagnosticStorage();diagnosticLifecycle();System.out.println("PASS: "+checks+" independent scheduler/lifecycle checks (fake platform; no device/account/network)");}
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
        Store.values.data.put("connected",false);Repo.vaultConnected=false;int signedOutCalls=Repo.syncCalls.get();
        check(service.onStartJob(p),"signed-out display cache is verified asynchronously");flush();
        check(Repo.syncCalls.get()==signedOutCalls,"genuinely signed-out periodic job never fetches usage");
        Store.values.data.put("connected",true);Repo.vaultConnected=true;
        CountDownLatch occupied=new CountDownLatch(1),unblock=new CountDownLatch(1);
        Repo.IO.submit(()->{occupied.countDown();try{unblock.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        check(occupied.await(5,TimeUnit.SECONDS),"serial credential queue occupied");count.set(0);Repo.action=count::incrementAndGet;
        service.onStartJob(p);service.onStopJob(p);unblock.countDown();flush();check(count.get()==0,"cancelled queued periodic work never starts HTTP");
        CountDownLatch firstEntered=new CountDownLatch(1),firstRelease=new CountDownLatch(1);AtomicInteger invocations=new AtomicInteger();
        Repo.action=()->{if(invocations.incrementAndGet()==1){firstEntered.countDown();firstRelease.await(5,TimeUnit.SECONDS);}};
        JobParameters first=new JobParameters(Scheduler.PERIODIC,0),second=new JobParameters(Scheduler.PERIODIC,0);service.onStartJob(first);
        check(firstEntered.await(5,TimeUnit.SECONDS),"old periodic generation begins");
        service.onStartJob(second);firstRelease.countDown();flush();
        check(service.finished.size()==3&&service.finished.get(2)==second,"replaced generation cannot finish newer job");
        service.onDestroy();
    }
    private static void publicationBoundary()throws Exception{
        reset();UsageJob service=new UsageJob();JobParameters p=new JobParameters(Scheduler.PERIODIC,0);
        Repo.action=()->Store.saveFixture(73,2000);
        check(service.onStartJob(p),"automatic refresh starts without an Activity");finishWorker();
        boolean publishedBeforeCompletion=WeeklyWidget.percent==73&&WeeklyWidget.fetchedAt==2000;
        check(service.finished.isEmpty(),"widget publication precedes jobFinished");
        service.onStopJob(p);Handler.drain();
        check(WeeklyWidget.percent==73&&WeeklyWidget.fetchedAt==2000,"stop after cache commit cannot discard widget publication");
        check(publishedBeforeCompletion,"automatic response reaches widget before the main-thread completion callback");
        check(service.finished.isEmpty(),"stopped job cannot report a late completion");
        check(!RefreshFeedback.snapshot(service).visible,"automatic publication never invents manual success feedback");service.onDestroy();

        reset();service=new UsageJob();p=new JobParameters(Scheduler.PERIODIC,0);Repo.action=()->Store.saveFixture(61,3000);
        service.onStartJob(p);finishWorker();service.onDestroy();Handler.drain();
        check(WeeklyWidget.percent==61&&WeeklyWidget.fetchedAt==3000,"destroy after cache commit keeps real percentage and receipt timestamp");
        check(service.finished.isEmpty(),"destroyed generation cannot call jobFinished");

        reset();service=new UsageJob();JobParameters first=new JobParameters(Scheduler.PERIODIC,0),second=new JobParameters(Scheduler.PERIODIC,0);
        Repo.action=()->Store.saveFixture(57,4000);service.onStartJob(first);finishWorker();
        Repo.action=()->Store.saveFixture(52,5000);service.onStartJob(second);finishWorker();Handler.drain();
        check(WeeklyWidget.percent==52&&WeeklyWidget.fetchedAt==5000,"replaced callback cannot restore older percentage or timestamp");
        check(service.finished.size()==1&&service.finished.get(0)==second,"only newest live generation finishes after replacement");service.onDestroy();

        reset();service=new UsageJob();p=new JobParameters(Scheduler.PERIODIC,0);
        CountDownLatch committing=new CountDownLatch(1),finishCommit=new CountDownLatch(1);AtomicBoolean interrupted=new AtomicBoolean();
        Repo.action=()->{committing.countDown();try{finishCommit.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){interrupted.set(true);throw e;}Store.saveFixture(44,6000);};
        service.onStartJob(p);check(committing.await(5,TimeUnit.SECONDS),"response cache commit race is held deterministically");
        service.onStopJob(p);service.onDestroy();finishCommit.countDown();flush();
        check(!interrupted.get(),"OS stop never interrupts an in-flight credential/cache transaction");
        check(WeeklyWidget.percent==44&&WeeklyWidget.fetchedAt==6000,"already committed response publishes even after stop and destroy");
        check(service.finished.isEmpty(),"late cancelled cache commit never finishes stopped job");
    }
    private static void automaticRecovery()throws Exception{
        reset();Store.values.data.put("connected",false);Repo.vaultConnected=true;UsageJob service=new UsageJob();JobParameters p=new JobParameters(Scheduler.PERIODIC,0);
        Repo.action=()->Store.saveFixture(37,7000);
        check(service.onStartJob(p),"cold automatic job does not reject stale disconnected display cache");flush();
        check(Repo.reconcileCalls.get()==1&&Store.connected(service),"automatic job reconciles encrypted session before query");
        check(Repo.syncCalls.get()==1&&WeeklyWidget.percent==37&&WeeklyWidget.fetchedAt==7000,"cold automatic job updates actual percentage and timestamp without Activity");
        check(service.finished.size()==1,"cold automatic job finishes normally");service.onDestroy();

        reset();Repo.vaultConnected=false;service=new UsageJob();service.onStartJob(p);flush();
        check(Repo.reconcileCalls.get()==1&&!Store.connected(service)&&Repo.syncCalls.get()==0,"stale connected cache cannot authorize a signed-out query");
        check(service.finished.size()==1,"signed-out automatic job still completes");service.onDestroy();

        reset();Repo.reconcileFailure=new IllegalStateException("Synthetic unavailable session");service=new UsageJob();service.onStartJob(p);flush();
        check(Repo.syncCalls.get()==0&&service.finished.size()==1,"session-read failure cannot hang job or start usage query");service.onDestroy();

        reset();Store.values.data.put("auto",false);service=new UsageJob();check(!service.onStartJob(p),"disabled automatic refresh rejects stale scheduled callback");flush();
        check(Repo.reconcileCalls.get()==0&&Repo.syncCalls.get()==0,"auto-off callback performs no session read or query");
        reset();AppWidgetManager.ids=new int[0];check(!service.onStartJob(p),"automatic callback without widgets is ignored");flush();
        check(Repo.reconcileCalls.get()==0&&Repo.syncCalls.get()==0,"no widgets means no background query");service.onDestroy();

        reset();Repo.action=()->Store.saveFixture(29,8000);WeeklyWidget.failures.set(1);service=new UsageJob();service.onStartJob(p);flush();
        check(WeeklyWidget.attempts>=2&&WeeklyWidget.percent==29&&WeeklyWidget.fetchedAt==8000,"temporary launcher publication failure retries cached response");
        check(service.finished.size()==1,"temporary launcher failure still finishes exactly once");service.onDestroy();

        reset();WeeklyWidget.failures.set(10);service=new UsageJob();service.onStartJob(p);flush();
        check(service.finished.size()==1,"persistent launcher failure cannot leave job hanging");WeeklyWidget.failures.set(0);service.onDestroy();

        reset();Repo.IO.shutdown();check(Repo.IO.awaitTermination(5,TimeUnit.SECONDS),"serial executor is deterministically unavailable");
        service=new UsageJob();boolean started;
        try{started=service.onStartJob(p);Handler.drain();}
        finally{Repo.IO=Executors.newSingleThreadExecutor();}
        check(!started||service.finished.size()==1,"executor rejection ends job instead of hanging");
        check(Repo.syncCalls.get()==0,"executor rejection performs no query");service.onDestroy();
    }
    private static void automaticEligibility()throws Exception{
        for(int mode=0;mode<2;mode++){
            reset();CountDownLatch occupied=new CountDownLatch(1),release=new CountDownLatch(1);
            Repo.IO.submit(()->{occupied.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
            check(occupied.await(5,TimeUnit.SECONDS),"automatic eligibility test holds credential queue");
            UsageJob service=new UsageJob();JobParameters p=new JobParameters(Scheduler.PERIODIC,0);
            check(service.onStartJob(p),"eligible automatic job enters held queue");
            if(mode==0)Store.values.data.put("auto",false);else AppWidgetManager.ids=new int[0];
            release.countDown();flush();
            check(Repo.reconcileCalls.get()==0&&Repo.syncCalls.get()==0,
                (mode==0?"auto disabled":"widgets removed")+" while queued prevents session read and HTTP");
            check(service.finished.size()==1&&Store.percent==88&&Store.fetchedAt==1000,"ineligible queued job finishes without inventing fetched data");service.onDestroy();
        }
        for(int mode=0;mode<3;mode++){
            reset();CountDownLatch checking=new CountDownLatch(1),release=new CountDownLatch(1);AtomicBoolean interrupted=new AtomicBoolean();
            Repo.reconcileAction=()->{checking.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){interrupted.set(true);throw e;}};
            UsageJob service=new UsageJob();JobParameters p=new JobParameters(Scheduler.PERIODIC,0);service.onStartJob(p);
            check(checking.await(5,TimeUnit.SECONDS),"automatic session reconciliation is held before query");
            if(mode==0)service.onStopJob(p);else if(mode==1)Store.values.data.put("auto",false);else AppWidgetManager.ids=new int[0];
            release.countDown();flush();
            check(!interrupted.get()&&Repo.reconcileCalls.get()==1&&Repo.syncCalls.get()==0,
                (mode==0?"job stopped":mode==1?"auto disabled":"widgets removed")+" during reconciliation prevents later HTTP without thread interruption");
            check(service.finished.size()==(mode==0?0:1),"reconciled cancelled or ineligible generation has correct completion ownership");service.onDestroy();
        }
        reset();WeeklyWidget.failures.set(1);Repo.action=()->Store.saveFixture(27,9000);UsageJob service=new UsageJob();
        JobParameters first=new JobParameters(Scheduler.PERIODIC,0),second=new JobParameters(Scheduler.PERIODIC,0);
        service.onStartJob(first);finishWorker();Repo.action=()->Store.saveFixture(21,10000);service.onStartJob(second);finishWorker();Handler.drain();
        check(WeeklyWidget.percent==21&&WeeklyWidget.fetchedAt==10000,"failed old publication retries current cache rather than old response");
        check(service.finished.size()==1&&service.finished.get(0)==second,"old publication retry cannot finish newer job");service.onDestroy();
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
    private static void diagnosticStorage(){
        reset();Context c=new Context();AutoRefreshDiagnostics.Snapshot empty=AutoRefreshDiagnostics.read(c);
        check(empty.outcome.equals("never_started")&&empty.startedAt==0&&empty.finishedAt==0&&empty.succeededAt==0&&empty.publishedAt==0,
            "new installation has no invented automatic attempt or success");
        check(empty.stopReason==-1&&!empty.publishAttempted&&!empty.publishSucceeded,"new diagnostic flags represent unknown and unattempted");
        long first=AutoRefreshDiagnostics.begin(c);AutoRefreshDiagnostics.Snapshot started=AutoRefreshDiagnostics.read(c);
        check(first>0&&started.startedAt>0&&started.finishedAt==0&&started.outcome.equals("running"),"automatic start records time but no completion");
        check(!Store.values.data.containsKey("started_at")&&!Context.STYLE.data.containsKey("started_at"),"automatic diagnostics are isolated from account/display/style preferences");
        AutoRefreshDiagnostics.complete(c,first,"updated");AutoRefreshDiagnostics.publication(c,first,false);
        AutoRefreshDiagnostics.Snapshot fetched=AutoRefreshDiagnostics.read(c);
        check(fetched.outcome.equals("updated")&&fetched.finishedAt>0&&fetched.succeededAt>0,"real updated outcome records query success independently of publication");
        check(fetched.publishAttempted&&!fetched.publishSucceeded&&fetched.publishedAt==0,"publication failure never fabricates delivery time");
        AutoRefreshDiagnostics.publication(c,first,true);long oldPublished=AutoRefreshDiagnostics.read(c).publishedAt;
        check(oldPublished>0&&AutoRefreshDiagnostics.read(c).publishSucceeded,"successful cache-only retry records publication");
        long second=AutoRefreshDiagnostics.begin(c);AutoRefreshDiagnostics.Snapshot newer=AutoRefreshDiagnostics.read(c);
        check(second>first&&newer.finishedAt==0&&newer.stopReason==-1&&!newer.publishAttempted&&!newer.publishSucceeded,"next generation is monotonic and resets only current-attempt fields");
        check(newer.succeededAt==fetched.succeededAt&&newer.publishedAt==oldPublished,"next attempt preserves last real query and publication success times");
        AutoRefreshDiagnostics.complete(c,first,"error");AutoRefreshDiagnostics.stopped(c,first,7);
        AutoRefreshDiagnostics.destroyed(c,first);AutoRefreshDiagnostics.publication(c,first,false);
        check(AutoRefreshDiagnostics.read(c).outcome.equals("running")&&AutoRefreshDiagnostics.read(c).stopReason==-1&&!AutoRefreshDiagnostics.read(c).publishAttempted,
            "all late diagnostic callbacks are rejected by newer generation");
        AutoRefreshDiagnostics.stopped(c,second,7);long stopTime=AutoRefreshDiagnostics.read(c).finishedAt;
        AutoRefreshDiagnostics.complete(c,second,"updated");AutoRefreshDiagnostics.publication(c,second,true);AutoRefreshDiagnostics.destroyed(c,second);
        AutoRefreshDiagnostics.Snapshot stopped=AutoRefreshDiagnostics.read(c);
        check(stopped.outcome.equals("stopped")&&stopped.stopReason==7&&stopped.finishedAt==stopTime,"late completion publication and destruction preserve OS stop reason and time");
        check(stopped.succeededAt>0&&stopped.publishAttempted&&stopped.publishSucceeded,"known query/publication success can coexist with an OS stop record");
        long third=AutoRefreshDiagnostics.begin(c);AutoRefreshDiagnostics.destroyed(c,third);AutoRefreshDiagnostics.complete(c,third,"cancelled");
        check(AutoRefreshDiagnostics.read(c).outcome.equals("destroyed")&&AutoRefreshDiagnostics.read(c).finishedAt>0,"worker completion does not hide service destruction");
        long invalid=AutoRefreshDiagnostics.begin(c);AutoRefreshDiagnostics.complete(c,invalid,"https://fixture.invalid/?token=do-not-store");
        check(AutoRefreshDiagnostics.read(c).outcome.equals("error")&&!Context.DIAGNOSTICS.data.values().toString().contains("do-not-store"),"unknown outcome content is sanitized to a fixed label");
        for(String result:new String[]{"updated","skipped","cancelled","error","signed_out","executor_rejected","disabled"}){
            long id=AutoRefreshDiagnostics.begin(c);AutoRefreshDiagnostics.complete(c,id,result);
            check(AutoRefreshDiagnostics.read(c).outcome.equals(result)&&AutoRefreshDiagnostics.read(c).finishedAt>0,"fixed terminal diagnostic label "+result);
        }
        long beforeReset=AutoRefreshDiagnostics.begin(c);Context.DIAGNOSTICS.data.clear();long afterReset=AutoRefreshDiagnostics.begin(c);
        check(afterReset>beforeReset,"in-process preference reset does not reuse an active generation ID");
        Context.failDiagnostics=true;
        check(AutoRefreshDiagnostics.begin(c)==0&&AutoRefreshDiagnostics.read(c).outcome.equals("unavailable"),"unavailable diagnostic storage is not mistaken for no past attempts");
        AutoRefreshDiagnostics.complete(c,afterReset,"error");AutoRefreshDiagnostics.stopped(c,afterReset,7);
        AutoRefreshDiagnostics.destroyed(c,afterReset);AutoRefreshDiagnostics.publication(c,afterReset,false);Context.failDiagnostics=false;
        check(AutoRefreshDiagnostics.read(c).outcome.equals("running"),"failed diagnostic writes cannot corrupt prior stored attempt");
    }
    private static void diagnosticLifecycle()throws Exception{
        reset();UsageJob service=new UsageJob();JobParameters p=new JobParameters(Scheduler.PERIODIC,0);
        service.onStartJob(p);flush();AutoRefreshDiagnostics.Snapshot successful=AutoRefreshDiagnostics.read(service);
        check(successful.outcome.equals("updated")&&successful.succeededAt>0&&successful.finishedAt>0,"production job records actual updated result");
        check(successful.publishAttempted&&successful.publishSucceeded&&successful.publishedAt>0,"production job records successful widget publication");service.onDestroy();
        for(Repo.SyncOutcome result:new Repo.SyncOutcome[]{Repo.SyncOutcome.SKIPPED,Repo.SyncOutcome.CANCELLED}){
            reset();Repo.outcome=result;service=new UsageJob();service.onStartJob(p);flush();AutoRefreshDiagnostics.Snapshot state=AutoRefreshDiagnostics.read(service);
            check(state.outcome.equals(result==Repo.SyncOutcome.SKIPPED?"skipped":"cancelled")&&state.succeededAt==0,"production non-updated result never reports automatic query success");service.onDestroy();
        }
        reset();Repo.vaultConnected=false;service=new UsageJob();service.onStartJob(p);flush();
        check(AutoRefreshDiagnostics.read(service).outcome.equals("signed_out")&&Repo.syncCalls.get()==0,"production signed-out result is diagnostic without any query");service.onDestroy();
        reset();Repo.action=()->{throw new IllegalStateException("Synthetic secret-shaped error");};service=new UsageJob();service.onStartJob(p);flush();
        check(AutoRefreshDiagnostics.read(service).outcome.equals("error")&&!Context.DIAGNOSTICS.data.values().toString().contains("secret-shaped"),"production failure records only fixed diagnostic label");service.onDestroy();
        reset();Store.values.data.put("auto",false);service=new UsageJob();service.onStartJob(p);
        check(AutoRefreshDiagnostics.read(service).outcome.equals("disabled")&&Repo.syncCalls.get()==0,"ineligible callback records disabled without usage query");service.onDestroy();
        reset();WeeklyWidget.failures.set(1);service=new UsageJob();service.onStartJob(p);finishWorker();
        check(AutoRefreshDiagnostics.read(service).publishAttempted&&!AutoRefreshDiagnostics.read(service).publishSucceeded,"first publication failure is visible before retry");Handler.drain();
        check(AutoRefreshDiagnostics.read(service).publishSucceeded&&AutoRefreshDiagnostics.read(service).outcome.equals("updated"),"cache-only retry updates publication status without altering query outcome");service.onDestroy();
        reset();WeeklyWidget.failures.set(10);service=new UsageJob();service.onStartJob(p);flush();
        check(AutoRefreshDiagnostics.read(service).succeededAt>0&&!AutoRefreshDiagnostics.read(service).publishSucceeded&&AutoRefreshDiagnostics.read(service).publishedAt==0,
            "successful query and persistent publication failure remain distinguishable");WeeklyWidget.failures.set(0);service.onDestroy();
        for(int sdk:new int[]{26,35}){
            reset();Build.VERSION.SDK_INT=sdk;CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
            Repo.action=()->{entered.countDown();release.await(5,TimeUnit.SECONDS);Store.saveFixture(19,11000);};service=new UsageJob();service.onStartJob(p);
            check(entered.await(5,TimeUnit.SECONDS),"diagnostic cancellation holds query before cache completion");
            service.onStopJob(new JobParameters(Scheduler.PERIODIC,JobParameters.STOP_REASON_CONSTRAINT_CONNECTIVITY));release.countDown();flush();
            AutoRefreshDiagnostics.Snapshot state=AutoRefreshDiagnostics.read(service);
            check(state.outcome.equals("stopped")&&state.stopReason==(sdk>=31?JobParameters.STOP_REASON_CONSTRAINT_CONNECTIVITY:-1),"production stop reason is retained with API-safe fallback");
            check(state.publishSucceeded&&WeeklyWidget.percent==19&&service.finished.isEmpty(),"late stopped-worker publication updates cache but not completion ownership");service.onDestroy();
        }
        for(final boolean linkage:new boolean[]{false,true}){
            reset();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
            Repo.action=()->{entered.countDown();release.await(5,TimeUnit.SECONDS);};service=new UsageJob();service.onStartJob(p);
            check(entered.await(5,TimeUnit.SECONDS),"stop-reason failure test holds active work");
            JobParameters broken=new JobParameters(Scheduler.PERIODIC,0){
                @Override public int getStopReason(){if(linkage)throw new NoSuchMethodError("Synthetic unavailable method");throw new IllegalStateException("Synthetic platform failure");}
            };
            boolean retry=service.onStopJob(broken);release.countDown();flush();AutoRefreshDiagnostics.Snapshot state=AutoRefreshDiagnostics.read(service);
            check(!retry&&state.outcome.equals("stopped")&&state.stopReason==-1,"unavailable platform stop reason preserves safe cancellation and unknown reason");
            check(service.finished.isEmpty()&&state.publishAttempted,"stop-reason read failure cannot let a stopped worker finish the job");service.onDestroy();
        }
        reset();service=new UsageJob();Repo.action=()->Store.saveFixture(18,12000);JobParameters first=new JobParameters(Scheduler.PERIODIC,0),second=new JobParameters(Scheduler.PERIODIC,0);
        WeeklyWidget.failures.set(1);service.onStartJob(first);finishWorker();Repo.outcome=Repo.SyncOutcome.SKIPPED;service.onStartJob(second);finishWorker();
        AutoRefreshDiagnostics.Snapshot beforeOldCallback=AutoRefreshDiagnostics.read(service);Handler.drain();AutoRefreshDiagnostics.Snapshot afterOldCallback=AutoRefreshDiagnostics.read(service);
        check(afterOldCallback.outcome.equals("skipped")&&afterOldCallback.startedAt==beforeOldCallback.startedAt&&afterOldCallback.finishedAt==beforeOldCallback.finishedAt,
            "late old publication retry cannot overwrite newer job diagnostics");service.onDestroy();
        reset();service=new UsageJob();service.onStartJob(p);finishWorker();service.onDestroy();Handler.drain();
        check(AutoRefreshDiagnostics.read(service).outcome.equals("destroyed")&&service.finished.isEmpty(),"production destruction before completion callback remains recorded");
        reset();Context.failDiagnostics=true;service=new UsageJob();service.onStartJob(p);flush();
        check(Repo.syncCalls.get()==1&&service.finished.size()==1&&WeeklyWidget.renders>0,"diagnostic storage failure never prevents successful account work or publication");Context.failDiagnostics=false;service.onDestroy();
        reset();Repo.IO.shutdown();check(Repo.IO.awaitTermination(5,TimeUnit.SECONDS),"diagnostic rejection test stops executor");service=new UsageJob();
        try{check(!service.onStartJob(p)&&AutoRefreshDiagnostics.read(service).outcome.equals("executor_rejected"),"production executor rejection records distinct outcome");}
        finally{Repo.IO=Executors.newSingleThreadExecutor();}service.onDestroy();
    }
}
