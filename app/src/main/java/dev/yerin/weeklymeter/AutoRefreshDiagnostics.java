package dev.yerin.weeklymeter;

import android.content.Context;
import android.content.SharedPreferences;

/** Local automatic-work status only: no credentials, server content or exception text. */
final class AutoRefreshDiagnostics {
    private static final String PREFS="auto_refresh_status";
    private static long lastIssued;
    static final class Snapshot {
        final long startedAt,finishedAt,succeededAt,publishedAt;
        final String outcome;
        final int stopReason;
        final boolean publishAttempted,publishSucceeded;
        Snapshot(long startedAt,long finishedAt,long succeededAt,long publishedAt,String outcome,
                 int stopReason,boolean publishAttempted,boolean publishSucceeded){
            this.startedAt=startedAt;this.finishedAt=finishedAt;this.succeededAt=succeededAt;this.publishedAt=publishedAt;
            this.outcome=outcome;this.stopReason=stopReason;this.publishAttempted=publishAttempted;this.publishSucceeded=publishSucceeded;
        }
    }
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    private static long now(){return Math.max(1,System.currentTimeMillis());}
    private static String known(String value){
        if(value==null)return "never_started";
        switch(value){
            case "never_started":case "running":case "updated":case "skipped":case "cancelled":case "error":
            case "signed_out":case "stopped":case "destroyed":case "executor_rejected":case "disabled":case "unavailable":return value;
            default:return "error";
        }
    }
    static synchronized Snapshot read(Context c){
        try{
            SharedPreferences p=prefs(c);
            return new Snapshot(p.getLong("started_at",0),p.getLong("finished_at",0),p.getLong("succeeded_at",0),
                p.getLong("published_at",0),known(p.getString("outcome","never_started")),p.getInt("stop_reason",-1),
                p.getBoolean("publish_attempted",false),p.getBoolean("publish_succeeded",false));
        }catch(RuntimeException unavailable){return new Snapshot(0,0,0,0,"unavailable",-1,false,false);}
    }
    static synchronized long begin(Context c){
        try{
            SharedPreferences p=prefs(c);long previous=Math.max(lastIssued,p.getLong("generation",0));
            // Exhausted/corrupt diagnostic IDs must not affect account work or reuse an ID.
            if(previous==Long.MAX_VALUE)return 0;
            long time=now(),id=Math.max(time,previous+1);lastIssued=id;
            p.edit().putLong("generation",id).putLong("started_at",time).putLong("finished_at",0)
                .putString("outcome","running").putInt("stop_reason",-1)
                .putBoolean("publish_attempted",false).putBoolean("publish_succeeded",false).apply();
            return id;
        }catch(RuntimeException unavailable){return 0;}
    }
    private static boolean current(SharedPreferences p,long id){return id>0&&p.getLong("generation",0)==id;}
    static synchronized void complete(Context c,long id,String outcome){
        try{
            SharedPreferences p=prefs(c);if(!current(p,id))return;
            String result=known(outcome),existing=p.getString("outcome","never_started");long time=now();
            SharedPreferences.Editor edit=p.edit();
            // A completed real query may be known even if the OS has just stopped
            // the job. Keep that success time, but do not hide the stop/destroy event.
            if("updated".equals(result))edit.putLong("succeeded_at",time);
            if(!"stopped".equals(existing)&&!"destroyed".equals(existing))
                edit.putString("outcome",result).putLong("finished_at",time);
            edit.apply();
        }catch(RuntimeException unavailable){/* Diagnostics must never stop account work. */}
    }
    static synchronized void stopped(Context c,long id,int reason){
        try{
            SharedPreferences p=prefs(c);if(!current(p,id))return;
            p.edit().putString("outcome","stopped").putLong("finished_at",now()).putInt("stop_reason",reason).apply();
        }catch(RuntimeException unavailable){}
    }
    static synchronized void destroyed(Context c,long id){
        try{
            SharedPreferences p=prefs(c);if(!current(p,id)||"stopped".equals(p.getString("outcome","")))return;
            p.edit().putString("outcome","destroyed").putLong("finished_at",now()).apply();
        }catch(RuntimeException unavailable){}
    }
    static synchronized void publication(Context c,long id,boolean success){
        try{
            SharedPreferences p=prefs(c);if(!current(p,id))return;
            SharedPreferences.Editor edit=p.edit().putBoolean("publish_attempted",true).putBoolean("publish_succeeded",success);
            if(success)edit.putLong("published_at",now());
            edit.apply();
        }catch(RuntimeException unavailable){}
    }
    private AutoRefreshDiagnostics(){}
}
