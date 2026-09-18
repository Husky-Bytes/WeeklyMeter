package dev.yerin.weeklymeter;

import android.app.ActivityManager;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

/** Real read-only diagnostics, synthetic Android services; no device or account access. */
public final class BackgroundAccessTests {
    private static int checks;
    private static Context context;
    private static PowerManager power;
    private static ActivityManager activity;
    private static JobScheduler jobs;
    private static void check(boolean result,String message){checks++;if(!result)throw new AssertionError(message);}
    private static void setup(int api){
        Build.VERSION.SDK_INT=api;context=new Context();power=new PowerManager();activity=new ActivityManager();jobs=new JobScheduler();
        context.services.put(PowerManager.class,power);context.services.put(ActivityManager.class,activity);context.services.put(JobScheduler.class,jobs);
    }
    private static void valuesAndApiGates(){
        for(int api:new int[]{26,27,28,33,34,35,36})for(int mask=0;mask<8;mask++){
            setup(api);power.powerSave=(mask&1)!=0;power.batteryExempt=(mask&2)!=0;activity.restricted=(mask&4)!=0;
            BackgroundAccess.Snapshot state=BackgroundAccess.read(context);
            check(state.powerSave==((mask&1)!=0?1:0),"power save state");check(state.batteryExempt==((mask&2)!=0?1:0),"optimization exemption state");
            check(state.backgroundRestricted==(api<28?-1:((mask&4)!=0?1:0)),"background restriction API gate/state");check(state.scheduled==1,"job is registered");
            check(state.pendingReason==(api<34?-1:0),"raw pending reason API gate");check(state.pendingHint.equals(api<34?"unavailable":"other"),"undefined reason is not proof of readiness");
            check(power.powerReads==1&&power.exemptReads==1,"independent power reads once");check(context.getPackageName().equals(power.queriedPackage),"only this package's exemption is read");
            check(activity.reads==(api<28?0:1),"no unsupported background restriction call");check(context.reads.contains(ActivityManager.class)==(api>=28),"no unsupported service acquisition");
            check(jobs.jobReads==1&&jobs.jobId==Scheduler.PERIODIC,"only periodic job lookup");check(jobs.reasonReads==(api<34?0:1),"no unsupported pending reason call");
            if(api>=34)check(jobs.reasonJobId==Scheduler.PERIODIC,"only periodic job reason lookup");
            jobs.pending=null;int before=jobs.reasonReads;state=BackgroundAccess.read(context);
            check(state.scheduled==0,"missing job is unscheduled");check(state.pendingReason==-1&&"not_scheduled".equals(state.pendingHint),"missing job has no invented platform reason");check(jobs.reasonReads==before,"missing job reason is not queried");
        }
    }
    private static void reasons(){
        String[] expected={"not_scheduled","running","other","app_restricted","app_restricted","app_restricted","battery","battery","network","other","idle","latency","optimization","other","device","optimization","quota","app_restricted","other"};
        for(int reason=-2;reason<=16;reason++){
            setup(34);jobs.reason=reason;BackgroundAccess.Snapshot state=BackgroundAccess.read(context);
            check(state.pendingReason==reason,"platform reason preserved: "+reason);check(expected[reason+2].equals(state.pendingHint),"mapped platform reason: "+reason);
            check(state.scheduled==(reason==-2?0:1),"removed-between-reads job is not shown registered");
        }
        for(int reason:new int[]{-900,17,42,Integer.MAX_VALUE,Integer.MIN_VALUE}){
            setup(36);jobs.reason=reason;BackgroundAccess.Snapshot state=BackgroundAccess.read(context);
            check(state.pendingReason==reason,"future raw reason preserved");check("other".equals(state.pendingHint),"future reason does not imply battery restriction");
        }
        setup(34);jobs.reason=JobScheduler.PENDING_JOB_REASON_EXECUTING;BackgroundAccess.Snapshot running=BackgroundAccess.read(context);jobs.reasonFailure=new SecurityException();BackgroundAccess.Snapshot unavailable=BackgroundAccess.read(context);
        check(running.pendingReason==-1&&unavailable.pendingReason==-1,"raw executing/unavailable collision retained");check("running".equals(running.pendingHint)&&"unavailable".equals(unavailable.pendingHint),"hint disambiguates executing from unavailable");
    }
    private static void missingServices(){
        setup(34);context.services.clear();BackgroundAccess.Snapshot state=BackgroundAccess.read(context);
        check(state.powerSave==-1&&state.batteryExempt==-1,"missing power service is unknown, not disabled");check(state.backgroundRestricted==-1,"missing activity service is unknown");
        check(state.scheduled==-1&&state.pendingReason==-1&&"unavailable".equals(state.pendingHint),"missing scheduler is unknown, not unscheduled");
        setup(34);context.services.remove(PowerManager.class);state=BackgroundAccess.read(context);check(state.powerSave==-1&&state.batteryExempt==-1&&state.scheduled==1&&state.backgroundRestricted==0,"missing power service preserves unrelated reads");
        setup(34);context.services.remove(ActivityManager.class);state=BackgroundAccess.read(context);check(state.backgroundRestricted==-1&&state.powerSave==0&&state.scheduled==1,"missing activity service preserves unrelated reads");
        setup(34);context.services.remove(JobScheduler.class);state=BackgroundAccess.read(context);check(state.scheduled==-1&&state.powerSave==0&&state.backgroundRestricted==0,"missing scheduler preserves unrelated reads");
    }
    private static Throwable[] failures(){return new Throwable[]{new SecurityException("denied"),new IllegalStateException("unavailable"),new UnsupportedOperationException("unsupported"),new NoSuchMethodError("OEM method absent"),new NoClassDefFoundError("OEM class absent")};}
    private static void independentFailures(){
        for(Throwable failure:failures()){
            setup(34);power.powerFailure=failure;BackgroundAccess.Snapshot state=BackgroundAccess.read(context);check(state.powerSave==-1&&state.batteryExempt==0,"power mode failure does not mask exemption");check(state.backgroundRestricted==0&&state.scheduled==1,"power mode failure does not mask other services");
            setup(34);power.exemptFailure=failure;state=BackgroundAccess.read(context);check(state.batteryExempt==-1&&state.powerSave==0,"exemption failure does not mask power mode");
            setup(34);activity.failure=failure;state=BackgroundAccess.read(context);check(state.backgroundRestricted==-1&&state.powerSave==0&&state.scheduled==1,"restriction failure isolated");
            setup(34);jobs.jobFailure=failure;state=BackgroundAccess.read(context);check(state.scheduled==-1&&state.pendingReason==-1&&"unavailable".equals(state.pendingHint),"job read failure not mislabeled absent");check(jobs.reasonReads==0,"no reason lookup after unknown job state");
            setup(34);jobs.reasonFailure=failure;state=BackgroundAccess.read(context);check(state.scheduled==1&&state.pendingReason==-1&&"unavailable".equals(state.pendingHint),"reason read failure retains known registered job");
            setup(34);context.failures.put(PowerManager.class,failure);state=BackgroundAccess.read(context);check(state.powerSave==-1&&state.batteryExempt==-1&&state.scheduled==1,"power service acquisition failure isolated");
            setup(34);context.failures.put(ActivityManager.class,failure);state=BackgroundAccess.read(context);check(state.backgroundRestricted==-1&&state.powerSave==0&&state.scheduled==1,"activity service acquisition failure isolated");
            setup(34);context.failures.put(JobScheduler.class,failure);state=BackgroundAccess.read(context);check(state.scheduled==-1&&"unavailable".equals(state.pendingHint)&&state.powerSave==0,"scheduler service acquisition failure isolated");
        }
        setup(34);jobs.reasonFailure=new AssertionError("not a platform availability exception");boolean propagated=false;try{BackgroundAccess.read(context);}catch(AssertionError expected){propagated=true;}check(propagated,"unexpected fatal errors are not hidden");
    }
    private static void freshReadsAndSettings(){
        setup(34);BackgroundAccess.Snapshot old=BackgroundAccess.read(context);power.powerSave=true;power.batteryExempt=true;activity.restricted=true;jobs.reason=JobScheduler.PENDING_JOB_REASON_DEVICE_STATE;BackgroundAccess.Snapshot current=BackgroundAccess.read(context);
        check(old.powerSave==0&&old.batteryExempt==0&&old.backgroundRestricted==0,"prior snapshot is immutable");check(current.powerSave==1&&current.batteryExempt==1&&current.backgroundRestricted==1,"settings return reads fresh platform values");
        check("device".equals(current.pendingHint),"new device reason immediately visible");
        for(int api:new int[]{26,28,34,36}){
            setup(api);Intent intent=BackgroundAccess.batterySettings(context);
            check(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS.equals(intent.getAction()),"generic optimization settings action");
            check(!"android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS".equals(intent.getAction()),"not direct exemption request");check(context.reads.isEmpty(),"constructing settings intent does not inspect or mutate services");
        }
    }
    public static void main(String[] args){
        check(BackgroundAccess.UNKNOWN==-1&&BackgroundAccess.NO==0&&BackgroundAccess.YES==1,"stable tristate contract");
        valuesAndApiGates();reasons();missingServices();independentFailures();freshReadsAndSettings();
        System.out.println("Background access tests: "+checks+" checks passed (real diagnostics; synthetic Android services)");
    }
}
