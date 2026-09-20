package dev.yerin.weeklymeter;

import android.content.Intent;
import android.os.Handler;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Exercises the real service with fake Android, browser callbacks and token repository. */
public final class BrowserServiceTests {
    private static int checks;
    private static final java.util.List<String> publicationFailures=new java.util.ArrayList<>();
    private static final java.util.List<BrowserLoginService> SERVICES=new java.util.ArrayList<>();
    private static BrowserLoginService fresh(){BrowserLoginService service=new BrowserLoginService();SERVICES.add(service);return service;}
    private static void check(boolean okay,String name){checks++;if(!okay)throw new AssertionError(name);}
    private static void until(BooleanSupplier condition)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(!condition.getAsBoolean()&&System.nanoTime()<end)Thread.yield();
        if(!condition.getAsBoolean())throw new AssertionError("Timed out waiting for synthetic state");
    }
    private static BrowserLoginService start()throws Exception {
        Scheduler.reject=false;Scheduler.calls=0;WeeklyWidget.reject=false;WeeklyWidget.calls=0;
        Repo.reset();BrowserAuth.reset();Handler.drain();
        BrowserLoginService service=fresh();
        service.onStartCommand(new Intent(service,BrowserLoginService.class),0,1);
        if(!BrowserAuth.bindEntered.await(3,TimeUnit.SECONDS))throw new AssertionError("Bind was not started");
        return service;
    }
    private static void cancel(BrowserLoginService service){service.onStartCommand(new Intent(service,BrowserLoginService.class).setAction("dev.yerin.weeklymeter.CANCEL_LOGIN"),0,2);}
    private static void publicationCheck(boolean okay,String label){checks++;if(!okay)publicationFailures.add(label);}
    private static void noPublicationCrash(Runnable action,String label){
        try{action.run();publicationCheck(true,label);}catch(RuntimeException failure){publicationCheck(false,label+" (escaped "+failure.getClass().getSimpleName()+")");}
    }
    private static void publicationFailureChecks()throws Exception {
        BrowserLoginService service=start();BrowserAuth.Session session=BrowserAuth.next;
        session.waiting.await(3,TimeUnit.SECONDS);Handler.drain();WeeklyWidget.reject=true;
        session.complete(false);until(()->!Handler.MAIN.isEmpty());noPublicationCrash(Handler::drain,"successful login callback tolerates widget publication rejection");
        Repo.IO.submit(()->{}).get(3,TimeUnit.SECONDS);noPublicationCrash(Handler::drain,"saved login completion tolerates widget publication rejection");
        publicationCheck(Repo.saved&&Repo.exchanges.get()==1&&Repo.syncs.get()==1,"failed display does not repeat exchange or initial query");
        publicationCheck(WeeklyWidget.calls==1,"successful login injected actual host rejection");
        publicationCheck(service.stopped==1&&service.foregroundRemoved==1,"host rejection cannot skip login foreground/service cleanup");
        publicationCheck(session.closed&&BrowserLoginService.status().phase.equals("done"),"successful login closes listener and records completion despite display rejection");
        service.onDestroy();

        service=start();session=BrowserAuth.next;session.waiting.await(3,TimeUnit.SECONDS);Handler.drain();WeeklyWidget.reject=true;
        final BrowserLoginService cancelled=service;noPublicationCrash(()->cancel(cancelled),"login cancellation tolerates display rejection");
        publicationCheck(service.stopped==1&&service.foregroundRemoved==1&&session.closed,"cancel stops listener and service despite host rejection");
        publicationCheck(Repo.exchanges.get()==0&&Repo.syncs.get()==0,"failed cancellation display initiates no credential or usage request");
        service.onDestroy();

        service=start();session=BrowserAuth.next;session.waiting.await(3,TimeUnit.SECONDS);Handler.drain();Scheduler.reject=true;
        final BrowserLoginService schedulerDenied=service;noPublicationCrash(()->cancel(schedulerDenied),"schedule repair failure cannot escape login cleanup");
        publicationCheck(service.stopped==1&&service.foregroundRemoved==1,"scheduler rejection cannot skip foreground/service stop");
        publicationCheck(WeeklyWidget.calls==1,"scheduler rejection does not suppress independent cached widget publication");
        service.onDestroy();

        service=start();session=BrowserAuth.next;session.waiting.await(3,TimeUnit.SECONDS);Handler.drain();
        Repo.releaseExchange=new java.util.concurrent.CountDownLatch(1);session.complete(false);
        until(()->!Handler.MAIN.isEmpty());Handler.drain();Repo.exchanged.await(3,TimeUnit.SECONDS);
        WeeklyWidget.reject=true;Scheduler.reject=true;final BrowserLoginService timedOut=service;
        noPublicationCrash(()->timedOut.onTimeout(1,1),"OS login timeout stops despite scheduling and display failures");
        publicationCheck(service.stopped==1&&service.foregroundRemoved==1,"OS timeout always removes login foreground service");
        BrowserAuth.reset();BrowserLoginService newer=fresh();newer.onStartCommand(new Intent(newer,BrowserLoginService.class),0,1);
        BrowserAuth.bindEntered.await(3,TimeUnit.SECONDS);BrowserAuth.next.waiting.await(3,TimeUnit.SECONDS);Handler.drain();
        long newerAttempt=BrowserLoginService.status().attempt;
        Repo.releaseExchange.countDown();Repo.IO.submit(()->{}).get(3,TimeUnit.SECONDS);
        noPublicationCrash(Handler::drain,"late token save tolerates failed cache publication without affecting new login");
        publicationCheck(Repo.saved&&Repo.exchanges.get()==1&&Repo.syncs.get()==1,"timed-out exchange still persists issued credentials exactly once");
        publicationCheck(service.stopped==1&&newer.stopped==0,"old late callback cannot stop newer login service");
        publicationCheck(BrowserLoginService.status().attempt==newerAttempt&&BrowserLoginService.status().phase.equals("waiting"),"failed stale publication cannot overwrite new login phase");
        Scheduler.reject=false;WeeklyWidget.reject=false;cancel(newer);newer.onDestroy();service.onDestroy();

        Repo.reset();BrowserAuth.reset();WeeklyWidget.reject=true;Scheduler.reject=true;
        service=fresh();service.denyForeground=true;final BrowserLoginService denied=service;
        noPublicationCrash(()->denied.onStartCommand(new Intent(denied,BrowserLoginService.class),0,1),"foreground denial tolerates display failure during finish");
        publicationCheck(service.stopped==1&&service.foregroundRemoved==1,"denied foreground still completes service stop after repair failure");
        publicationCheck(BrowserAuth.bindEntered.getCount()==1&&Repo.exchanges.get()==0&&Repo.syncs.get()==0,"foreground denial with host failure opens no listener or network operation");
        service.onDestroy();Scheduler.reject=false;WeeklyWidget.reject=false;
        if(!publicationFailures.isEmpty()){
            for(String failure:publicationFailures)System.err.println("FAIL: "+failure);
            throw new AssertionError(publicationFailures.size()+" browser publication-failure checks failed");
        }
    }
    public static void main(String[] args)throws Exception{
        try {
            BrowserLoginService service=start();
            BrowserAuth.Session session=BrowserAuth.next;
            check(service.foregroundCalls==1,"Login starts foreground service once");
            service.onStartCommand(new Intent(service,BrowserLoginService.class),0,2);
            check(service.foregroundCalls==1,"Duplicate start is coalesced");
            session.waiting.await(3,TimeUnit.SECONDS);Handler.drain();
            check(BrowserLoginService.status().phase.equals("waiting"),"Waiting state is delivered");
            check(BrowserLoginService.status().url.startsWith("https://auth.openai.com/"),"Only authorization URL in waiting state");
            cancel(service);Handler.drain();
            check(session.closed,"Cancel closes callback listener");
            check(BrowserLoginService.status().phase.equals("done")&&!BrowserLoginService.status().active(),"Cancel clears active status");
            check(BrowserLoginService.status().url.isEmpty(),"Cancel clears authorization URL");
            check(Repo.exchanges.get()==0,"Cancel does not exchange tokens");
            service.onDestroy();

            Repo.reset();BrowserAuth.reset();BrowserAuth.releaseBind=new java.util.concurrent.CountDownLatch(1);
            service=fresh();service.onStartCommand(new Intent(service,BrowserLoginService.class),0,1);
            BrowserAuth.bindEntered.await(3,TimeUnit.SECONDS);session=BrowserAuth.next;
            cancel(service);service.onDestroy();BrowserAuth.releaseBind.countDown();
            final BrowserAuth.Session cancelledBeforeBind=session;until(()->cancelledBeforeBind.closed);Handler.drain();
            check(BrowserLoginService.status().phase.equals("done"),"Bind completed after cancellation cannot reactivate status");
            check(Repo.exchanges.get()==0,"Bind cancellation cannot exchange credentials");

            service=start();session=BrowserAuth.next;session.waiting.await(3,TimeUnit.SECONDS);
            // Waiting may still be queued on main when the user cancels.
            cancel(service);Handler.drain();
            check(BrowserLoginService.status().phase.equals("done"),"Queued waiting update cannot resurrect cancelled login");
            service.onDestroy();

            service=start();session=BrowserAuth.next;session.waiting.await(3,TimeUnit.SECONDS);Handler.drain();
            session.complete(true);until(()->!Handler.MAIN.isEmpty());cancel(service);String cancelMessage=BrowserLoginService.status().message;Handler.drain();
            check(BrowserLoginService.status().phase.equals("done"),"Queued denial cannot reactivate login");
            check(BrowserLoginService.status().message.equals(cancelMessage),"Queued denial does not replace cancellation result");
            check(Repo.exchanges.get()==0,"Denied consent does not exchange credentials");service.onDestroy();

            service=start();session=BrowserAuth.next;session.waiting.await(3,TimeUnit.SECONDS);Handler.drain();
            Repo.releaseExchange=new java.util.concurrent.CountDownLatch(1);session.complete(false);
            until(()->!Handler.MAIN.isEmpty());Handler.drain();
            check(BrowserLoginService.status().phase.equals("finishing"),"Valid callback enters finishing phase");
            check(BrowserLoginService.status().url.isEmpty(),"Finishing clears browser URL/state");
            Repo.exchanged.await(3,TimeUnit.SECONDS);cancel(service);
            check(BrowserLoginService.status().phase.equals("finishing")&&service.stopped==0,"Cancellation cannot discard in-flight issued credentials");
            Repo.releaseExchange.countDown();until(()->!Handler.MAIN.isEmpty());Handler.drain();
            check(Repo.exchanges.get()==1&&Repo.syncs.get()==1,"Single exchange followed by initial usage sync");
            check(Repo.saved&&Repo.syncSawSaved,"Credentials are saved before initial usage sync");
            check(BrowserLoginService.status().phase.equals("done"),"Successful login completes");
            check(service.stopped>0&&service.foregroundRemoved>0,"Completed login stops foreground service");service.onDestroy();

            service=start();session=BrowserAuth.next;session.waiting.await(3,TimeUnit.SECONDS);Handler.drain();
            Repo.releaseExchange=new java.util.concurrent.CountDownLatch(1);session.complete(false);
            until(()->!Handler.MAIN.isEmpty());Handler.drain();Repo.exchanged.await(3,TimeUnit.SECONDS);
            service.onTimeout(1,1);String timeoutMessage=BrowserLoginService.status().message;int timeoutStops=service.stopped;
            Repo.releaseExchange.countDown();until(()->!Handler.MAIN.isEmpty());Handler.drain();
            check(Repo.saved&&Repo.exchanges.get()==1,"Issued credentials survive OS timeout during exchange");
            check(BrowserLoginService.status().message.equals(timeoutMessage)&&service.stopped==timeoutStops,"Late persisted completion preserves timeout UI and does not stop another attempt");
            service.onDestroy();

            service=start();session=BrowserAuth.next;session.waiting.await(3,TimeUnit.SECONDS);Handler.drain();
            long oldAttempt=BrowserLoginService.status().attempt;
            session.complete(false);until(()->!Handler.MAIN.isEmpty());service.onDestroy();
            BrowserAuth.reset();BrowserLoginService newer=fresh();newer.onStartCommand(new Intent(newer,BrowserLoginService.class),0,1);
            BrowserAuth.bindEntered.await(3,TimeUnit.SECONDS);BrowserAuth.next.waiting.await(3,TimeUnit.SECONDS);Handler.drain();
            check(BrowserLoginService.status().attempt!=oldAttempt&&BrowserLoginService.status().phase.equals("waiting"),"Old queued success cannot change newer attempt");
            check(Repo.exchanges.get()==0,"Destroyed attempt cannot queue an authorization exchange");cancel(newer);newer.onDestroy();

            Repo.reset();BrowserAuth.reset();service=fresh();service.denyForeground=true;
            service.onStartCommand(new Intent(service,BrowserLoginService.class),0,1);
            check(BrowserLoginService.status().phase.equals("done")&&service.stopped>0,"Foreground denial is recoverable");
            check(BrowserAuth.bindEntered.getCount()==1,"Foreground denial opens no listener");service.onDestroy();

            service=start();session=BrowserAuth.next;session.waiting.await(3,TimeUnit.SECONDS);Handler.drain();service.onTimeout(1,1);Handler.drain();
            check(session.closed&&BrowserLoginService.status().phase.equals("done"),"OS foreground timeout closes login");service.onDestroy();
            publicationFailureChecks();
            System.out.println("Browser service lifecycle checks passed: "+checks+" (fake Android and credentials; no real device)");
        } finally { Repo.releaseExchange.countDown();BrowserAuth.releaseBind.countDown();for(BrowserLoginService service:SERVICES)service.onDestroy();Repo.IO.shutdownNow(); }
    }
}
