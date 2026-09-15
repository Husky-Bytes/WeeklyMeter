package android.app.job;
import java.util.*;
public class JobScheduler {
    public static final int RESULT_SUCCESS=1,RESULT_FAILURE=0;
    public final Map<Integer,JobInfo> scheduled=new HashMap<>();
    public final List<JobInfo> calls=new ArrayList<>();
    public boolean rejectExpedited,rejectAll;
    public JobInfo getPendingJob(int id){return scheduled.get(id);}
    public void cancel(int id){scheduled.remove(id);}
    public int schedule(JobInfo info){
        calls.add(info);
        if(rejectAll||(rejectExpedited&&info.expedited))return RESULT_FAILURE;
        scheduled.put(info.id,info);return RESULT_SUCCESS;
    }
    public void reset(){scheduled.clear();calls.clear();rejectExpedited=false;rejectAll=false;}
}
