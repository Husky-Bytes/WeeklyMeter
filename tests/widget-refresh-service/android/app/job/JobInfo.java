package android.app.job;
import android.content.ComponentName;
public class JobInfo {
    public static final int NETWORK_TYPE_ANY=1,BACKOFF_POLICY_EXPONENTIAL=1;
    public final int id;
    public long interval,backoff;
    public boolean expedited,persisted;
    public int network;
    public android.os.PersistableBundle extras=new android.os.PersistableBundle();
    JobInfo(int id){this.id=id;}
    public long getIntervalMillis(){return interval;}
    public android.os.PersistableBundle getExtras(){return extras;}
    public static class Builder {
        final JobInfo info;
        public Builder(int id,ComponentName c){info=new JobInfo(id);}
        public Builder setRequiredNetworkType(int n){info.network=n;return this;}
        public Builder setPeriodic(long n){info.interval=n;return this;}
        public Builder setPersisted(boolean p){info.persisted=p;return this;}
        public Builder setBackoffCriteria(long n,int type){info.backoff=n;return this;}
        public Builder setExpedited(boolean e){info.expedited=e;return this;}
        public Builder setExtras(android.os.PersistableBundle e){info.extras=e;return this;}
        public JobInfo build(){return info;}
    }
}
