package android.app.job;
import android.content.Context;
import java.util.*;
public abstract class JobService extends Context {
    public final List<JobParameters> finished=new ArrayList<>();
    public abstract boolean onStartJob(JobParameters p);
    public abstract boolean onStopJob(JobParameters p);
    public void jobFinished(JobParameters p,boolean retry){finished.add(p);}
    public void onDestroy(){}
}
