package dev.yerin.weeklymeter;
import android.content.Context;
import java.util.concurrent.*;
final class Repo {
    enum SyncOutcome { UPDATED, SKIPPED, CANCELLED }
    static final ExecutorService IO=Executors.newSingleThreadExecutor();
    interface Action {void run()throws Exception;}
    static volatile Action action=()->{};
    static volatile SyncOutcome outcome=SyncOutcome.UPDATED;
    Repo(Context c){}
    SyncOutcome sync()throws Exception{action.run();return outcome;}
    SyncOutcome sync(java.util.function.BooleanSupplier stopped)throws Exception{if(stopped.getAsBoolean())return SyncOutcome.CANCELLED;action.run();return stopped.getAsBoolean()?SyncOutcome.CANCELLED:outcome;}
}
