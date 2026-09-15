package android.app.job;
public class JobParameters {
    public static final int STOP_REASON_CANCELLED_BY_APP=1,STOP_REASON_PREEMPT=2,
        STOP_REASON_TIMEOUT=3,STOP_REASON_CONSTRAINT_CONNECTIVITY=7,
        STOP_REASON_DEVICE_STATE=4,STOP_REASON_QUOTA=10,STOP_REASON_APP_STANDBY=12,
        STOP_REASON_USER=13,STOP_REASON_SYSTEM_PROCESSING=14;
    private final int id,reason;
    private final android.os.PersistableBundle extras;
    public JobParameters(int id,int reason){this(id,reason,new android.os.PersistableBundle());}
    public JobParameters(int id,int reason,android.os.PersistableBundle extras){this.id=id;this.reason=reason;this.extras=extras;}
    public int getJobId(){return id;}
    public int getStopReason(){return reason;}
    public android.os.PersistableBundle getExtras(){return extras;}
}
