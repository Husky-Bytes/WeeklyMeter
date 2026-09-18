package dev.yerin.weeklymeter;

import android.app.ActivityManager;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

/** Read-only platform diagnostics. None of these checks starts or reschedules a refresh. */
final class BackgroundAccess {
    static final int UNKNOWN=-1,NO=0,YES=1;
    static final class Snapshot {
        final int powerSave,batteryExempt,backgroundRestricted,scheduled,pendingReason;
        // A raw platform reason of -1 means executing; an unavailable read also
        // uses -1. Consumers must use this hint to distinguish those cases.
        final String pendingHint;
        Snapshot(int powerSave,int batteryExempt,int backgroundRestricted,int scheduled,int pendingReason,String pendingHint){
            this.powerSave=powerSave;this.batteryExempt=batteryExempt;this.backgroundRestricted=backgroundRestricted;
            this.scheduled=scheduled;this.pendingReason=pendingReason;this.pendingHint=pendingHint;
        }
    }
    static Snapshot read(Context c){
        int powerSave=UNKNOWN,batteryExempt=UNKNOWN,backgroundRestricted=UNKNOWN,scheduled=UNKNOWN,pendingReason=UNKNOWN;
        String pendingHint="unavailable";
        PowerManager power=service(c,PowerManager.class);
        if(power!=null){
            try{powerSave=state(power.isPowerSaveMode());}catch(RuntimeException|LinkageError unavailable){}
            try{batteryExempt=state(power.isIgnoringBatteryOptimizations(c.getPackageName()));}catch(RuntimeException|LinkageError unavailable){}
        }
        if(Build.VERSION.SDK_INT>=28){
            ActivityManager activity=service(c,ActivityManager.class);
            if(activity!=null)try{backgroundRestricted=state(activity.isBackgroundRestricted());}catch(RuntimeException|LinkageError unavailable){}
        }
        JobScheduler jobs=service(c,JobScheduler.class);
        if(jobs!=null){
            try{scheduled=state(jobs.getPendingJob(Scheduler.PERIODIC)!=null);}catch(RuntimeException|LinkageError unavailable){}
            if(scheduled==NO)pendingHint="not_scheduled";
            else if(scheduled==YES&&Build.VERSION.SDK_INT>=34){
                try{
                    pendingReason=jobs.getPendingJobReason(Scheduler.PERIODIC);
                    pendingHint=hint(pendingReason);
                    // The job can disappear between the two read-only queries.
                    if(pendingReason==JobScheduler.PENDING_JOB_REASON_INVALID_JOB_ID)scheduled=NO;
                }catch(RuntimeException|LinkageError unavailable){}
            }
        }
        return new Snapshot(powerSave,batteryExempt,backgroundRestricted,scheduled,pendingReason,pendingHint);
    }
    static Intent batterySettings(Context c){
        // Opens the user's settings list, not the direct exemption request that
        // requires REQUEST_IGNORE_BATTERY_OPTIMIZATIONS. The UI handles launch fallback.
        return new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
    }
    private static int state(boolean value){return value?YES:NO;}
    private static <T>T service(Context c,Class<T> type){
        try{return c.getSystemService(type);}catch(RuntimeException|LinkageError unavailable){return null;}
    }
    private static String hint(int reason){
        switch(reason){
            case JobScheduler.PENDING_JOB_REASON_EXECUTING:return "running";
            case JobScheduler.PENDING_JOB_REASON_INVALID_JOB_ID:return "not_scheduled";
            case JobScheduler.PENDING_JOB_REASON_APP:
            case JobScheduler.PENDING_JOB_REASON_APP_STANDBY:
            case JobScheduler.PENDING_JOB_REASON_BACKGROUND_RESTRICTION:
            case JobScheduler.PENDING_JOB_REASON_USER:return "app_restricted";
            case JobScheduler.PENDING_JOB_REASON_CONSTRAINT_BATTERY_NOT_LOW:
            case JobScheduler.PENDING_JOB_REASON_CONSTRAINT_CHARGING:return "battery";
            case JobScheduler.PENDING_JOB_REASON_CONSTRAINT_CONNECTIVITY:return "network";
            case JobScheduler.PENDING_JOB_REASON_CONSTRAINT_DEVICE_IDLE:return "idle";
            case JobScheduler.PENDING_JOB_REASON_CONSTRAINT_MINIMUM_LATENCY:return "latency";
            case JobScheduler.PENDING_JOB_REASON_CONSTRAINT_PREFETCH:
            case JobScheduler.PENDING_JOB_REASON_JOB_SCHEDULER_OPTIMIZATION:return "optimization";
            case JobScheduler.PENDING_JOB_REASON_DEVICE_STATE:return "device";
            case JobScheduler.PENDING_JOB_REASON_QUOTA:return "quota";
            // UNDEFINED is not proof that work is ready. Unknown/newer platform
            // reasons also must not be guessed to be battery restrictions.
            default:return "other";
        }
    }
    private BackgroundAccess(){}
}
