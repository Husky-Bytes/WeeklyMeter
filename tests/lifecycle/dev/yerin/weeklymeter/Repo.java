package dev.yerin.weeklymeter;
import android.content.Context;
import java.util.concurrent.*;
final class Repo {
    enum SyncOutcome { UPDATED, SKIPPED, CANCELLED, SIGNED_OUT }
    static ExecutorService IO=Executors.newSingleThreadExecutor();
    interface Action {void run()throws Exception;}
    static volatile Action action=()->{};
    static volatile Action reconcileAction=()->{};
    static volatile SyncOutcome outcome=SyncOutcome.UPDATED;
    static volatile boolean vaultConnected=true;
    static volatile Exception reconcileFailure;
    static final java.util.concurrent.atomic.AtomicInteger reconcileCalls=new java.util.concurrent.atomic.AtomicInteger();
    static final java.util.concurrent.atomic.AtomicInteger syncCalls=new java.util.concurrent.atomic.AtomicInteger();
    Repo(Context c){}
    boolean reconcileConnection()throws Exception{
        reconcileCalls.incrementAndGet();
        reconcileAction.run();
        if(reconcileFailure!=null)throw reconcileFailure;
        Store.values.data.put("connected",vaultConnected);return vaultConnected;
    }
    static String friendly(Exception e){return e.getMessage()==null?"Synthetic refresh failure":e.getMessage();}
    SyncOutcome sync()throws Exception{return sync(()->false);}
    SyncOutcome syncConnected(java.util.function.BooleanSupplier stopped)throws Exception{if(stopped.getAsBoolean())return SyncOutcome.CANCELLED;boolean connected=reconcileConnection();if(stopped.getAsBoolean())return SyncOutcome.CANCELLED;return connected?sync(stopped):SyncOutcome.SIGNED_OUT;}
    SyncOutcome syncConnected(java.util.function.BooleanSupplier stopped,java.util.function.BooleanSupplier ineligible)throws Exception{if(stopped.getAsBoolean())return SyncOutcome.CANCELLED;boolean connected=reconcileConnection();if(stopped.getAsBoolean()||ineligible.getAsBoolean())return SyncOutcome.CANCELLED;return connected?sync(stopped):SyncOutcome.SIGNED_OUT;}
    SyncOutcome sync(java.util.function.BooleanSupplier stopped)throws Exception{if(stopped.getAsBoolean())return SyncOutcome.CANCELLED;syncCalls.incrementAndGet();action.run();return stopped.getAsBoolean()?SyncOutcome.CANCELLED:outcome;}
}
