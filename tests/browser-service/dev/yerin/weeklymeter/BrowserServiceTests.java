package dev.yerin.weeklymeter;

import android.content.Intent;
import android.os.Handler;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Exercises the real service with fake Android, browser callbacks and token repository. */
public final class BrowserServiceTests {
    private static int checks;
    private static final java.util.List<BrowserLoginService> SERVICES=new java.util.ArrayList<>();
    private static BrowserLoginService fresh(){BrowserLoginService service=new BrowserLoginService();SERVICES.add(service);return service;}
    private static void check(boolean okay,String name){checks++;if(!okay)throw new AssertionError(name);}
    private static void until(BooleanSupplier condition)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(!condition.getAsBoolean()&&System.nanoTime()<end)Thread.yield();
        if(!condition.getAsBoolean())throw new AssertionError("Timed out waiting for synthetic state");
    }
    private static BrowserLoginService start()throws Exception {
        Repo.reset();BrowserAuth.reset();Handler.drain();
        BrowserLoginService service=fresh();
        service.onStartCommand(new Intent(service,BrowserLoginService.class),0,1);
        if(!BrowserAuth.bindEntered.await(3,TimeUnit.SECONDS))throw new AssertionError("Bind was not started");
        return service;
    }
    private static void cancel(BrowserLoginService service){service.onStartCommand(new Intent(service,BrowserLoginService.class).setAction("dev.yerin.weeklymeter.CANCEL_LOGIN"),0,2);}
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
            System.out.println("Browser service lifecycle checks passed: "+checks+" (fake Android and credentials; no real device)");
        } finally { Repo.releaseExchange.countDown();BrowserAuth.releaseBind.countDown();for(BrowserLoginService service:SERVICES)service.onDestroy();Repo.IO.shutdownNow(); }
    }
}
