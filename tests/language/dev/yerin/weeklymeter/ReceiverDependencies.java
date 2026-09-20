package dev.yerin.weeklymeter;
import android.content.Context;
final class Repo {
    static int operations,reconciles;
    static final java.util.concurrent.Executor IO=action->{operations++;action.run();};
    Repo(Context c){}
    void reconcileConnection(){reconciles++;}
    static String friendly(Exception e){return "failure";}
}
final class Scheduler {static int ensures;static boolean fail;static void ensure(Context c){ensures++;if(fail)throw new IllegalStateException("synthetic scheduler failure");}}
final class Store {static int errors;static void error(Context c,String value){errors++;}}
