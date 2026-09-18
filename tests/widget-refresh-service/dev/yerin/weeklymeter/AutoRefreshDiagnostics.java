package dev.yerin.weeklymeter;
import android.content.Context;
/** Manual-service harness does not exercise automatic diagnostic persistence. */
final class AutoRefreshDiagnostics {
    static long begin(Context c){return 1;}
    static void complete(Context c,long id,String outcome){}
    static void stopped(Context c,long id,int reason){}
    static void destroyed(Context c,long id){}
    static void publication(Context c,long id,boolean success){}
}
