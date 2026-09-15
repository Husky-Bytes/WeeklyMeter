package dev.yerin.weeklymeter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** A direct user-tap request, with no Activity launch or manual JobScheduler dependency. */
public final class WidgetRefreshService extends Service {
    public static final String ACTION_REFRESH="dev.yerin.weeklymeter.REFRESH_WIDGET_NOW";
    static final long REQUEST_TIMEOUT_MS=55_000, SERVICE_CAP_MS=70_000;
    private static final String CHANNEL="widget_refresh";
    private static final int NOTIFICATION=22003;
    private static final int QUEUED=0, RUNNING=1, DONE=2;
    private final Handler main=new Handler(Looper.getMainLooper());
    private Task current;
    private int latestStartId;
    private boolean foreground,destroyed;
    private static final class Task {
        final long id,startedAt;
        final AtomicBoolean stopped=new AtomicBoolean();
        final AtomicInteger phase=new AtomicInteger(QUEUED);
        Future<?> future;
        boolean terminal;
        String terminalState="";
        long holdUntil;
        Task(long id,long startedAt){this.id=id;this.startedAt=startedAt;}
    }
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        latestStartId=startId;
        if(intent==null||!ACTION_REFRESH.equals(intent.getAction())){
            if(current==null)stopSelfResult(startId);
            return START_NOT_STICKY;
        }
        // Enter foreground before credential/cache reads or any executor work.
        try{enterForeground();}
        catch(RuntimeException blocked){
            long id=RefreshFeedback.begin(this);RefreshFeedback.error(this,id);
            Store.error(this,"Android가 위젯 조회 시작을 막았어. 배터리 제한을 확인하고 다시 눌러 줘.");
            WeeklyWidget.renderAll(this);stopSelfResult(startId);return START_NOT_STICKY;
        }
        Scheduler.retireLegacyManual(this);
        Task old=current;
        if(old!=null&&(!old.terminal||old.phase.get()!=DONE)){
            if(old.terminal){showTerminal(old);updateHold(old);}
            else if(old.phase.get()==RUNNING)RefreshFeedback.running(this,old.id);
            else RefreshFeedback.waiting(this,old.id);
            WeeklyWidget.renderAll(this);
            return START_NOT_STICKY;
        }
        Task task=new Task(RefreshFeedback.begin(this),SystemClock.elapsedRealtime());current=task;
        notifyProgress("위젯 사용량을 조회하고 있어.");
        WeeklyWidget.renderAll(this);
        main.postDelayed(()->watchdog(task),REQUEST_TIMEOUT_MS);
        main.postDelayed(()->hardStop(task),SERVICE_CAP_MS);
        main.postDelayed(()->keepWaitingVisible(task),10_000);
        try{task.future=Repo.IO.submit(()->{
            if(!task.phase.compareAndSet(QUEUED,RUNNING))return;
            Repo.SyncOutcome outcome=Repo.SyncOutcome.CANCELLED;String failure="";
            try{
                if(!task.stopped.get()){
                    main.post(()->{if(active(task)&&!task.stopped.get()&&!task.terminal){RefreshFeedback.running(this,task.id);WeeklyWidget.renderAll(this);}});
                    Repo repo=new Repo(this);
                    // SharedPreferences may not yet reflect a durably saved
                    // session after a cold start. The encrypted vault is truth.
                    if(!repo.reconcileConnection())throw new IllegalStateException("앱에서 먼저 로그인해 줘.");
                    if(!task.stopped.get())outcome=repo.sync(task.stopped::get);
                }
            }catch(Exception error){failure=Repo.friendly(error);}
            finally{task.phase.set(DONE);}
            final Repo.SyncOutcome result=outcome;final String message=failure;
            main.post(()->completed(task,result,message));
        });}catch(RejectedExecutionException unavailable){
            task.phase.set(DONE);Store.error(this,"조회 작업을 시작하지 못했어. 위젯을 다시 눌러 줘.");
            terminal(task,"error","조회 작업을 시작하지 못했어.");
        }
        return START_NOT_STICKY;
    }
    private void enterForeground(){
        if(foreground)return;
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,"위젯 새로고침",NotificationManager.IMPORTANCE_LOW));
        Notification notification=notification("위젯 사용량을 조회하고 있어.");
        if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(NOTIFICATION,notification);
        foreground=true;
    }
    private Notification notification(String text){
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_meter)
            .setContentTitle("Weekly Meter 새로고침").setContentText(text).setOnlyAlertOnce(true)
            .setOngoing(true).setCategory(Notification.CATEGORY_SERVICE).setVisibility(Notification.VISIBILITY_PRIVATE).build();
    }
    private void notifyProgress(String text){
        try{getSystemService(NotificationManager.class).notify(NOTIFICATION,notification(text));}catch(RuntimeException ignored){}
    }
    private boolean active(Task task){return !destroyed&&current==task&&task.id==RefreshFeedback.currentRequestId(this);}
    private void keepWaitingVisible(Task task){
        if(!active(task)||task.terminal||task.stopped.get()||task.phase.get()!=QUEUED)return;
        RefreshFeedback.waiting(this,task.id);WeeklyWidget.renderAll(this);
        main.postDelayed(()->keepWaitingVisible(task),10_000);
    }
    private void completed(Task task,Repo.SyncOutcome outcome,String error){
        if(!active(task)){WeeklyWidget.renderAll(this);return;}
        if(task.terminal){stopWhenReady(task);return;}
        if(!error.isEmpty()){
            Store.error(this,error);terminal(task,"error","조회를 완료하지 못했어. 위젯을 다시 눌러 줘.");
        }else if(outcome==Repo.SyncOutcome.UPDATED){
            terminal(task,"success","위젯을 업데이트했어.");
        }else{
            terminal(task,"skipped","최근 조회값을 유지했어.");
        }
    }
    private void terminal(Task task,String state,String notificationText){
        if(!active(task))return;
        task.terminal=true;task.terminalState=state;showTerminal(task);
        updateHold(task);
        WeeklyWidget.renderAll(this);notifyProgress(notificationText);stopWhenReady(task);
    }
    private void updateHold(Task task){
        RefreshFeedback.Snapshot snapshot=RefreshFeedback.snapshot(this);
        long now=SystemClock.elapsedRealtime();
        task.holdUntil=snapshot.visible?Math.min(snapshot.expiresAt,task.startedAt+SERVICE_CAP_MS):now;
    }
    private void showTerminal(Task task){
        if("success".equals(task.terminalState))RefreshFeedback.success(this,task.id);
        else if("error".equals(task.terminalState))RefreshFeedback.error(this,task.id);
        else RefreshFeedback.skipped(this,task.id);
    }
    private void watchdog(Task task){
        if(!active(task)||task.terminal)return;
        cancelAtSafeCheckpoint(task);
        Store.error(this,"조회 시간이 길어져 이번 요청을 중단했어. 연결 상태를 확인하고 위젯을 다시 눌러 줘.");
        terminal(task,"error","조회를 마무리하고 있어. 잠시 후 다시 눌러 줘.");
    }
    private void cancelAtSafeCheckpoint(Task task){
        task.stopped.set(true);
        if(task.phase.compareAndSet(QUEUED,DONE)&&task.future!=null)task.future.cancel(false);
        // RUNNING token exchanges are not interrupted. Repo commits newly issued
        // refresh credentials before it observes the cooperative cancellation.
    }
    private void stopWhenReady(Task task){
        if(!active(task)||!task.terminal||task.phase.get()!=DONE)return;
        long delay=task.holdUntil-SystemClock.elapsedRealtime();
        if(delay>0){main.postDelayed(()->stopWhenReady(task),delay);return;}
        closeTask(task);
    }
    private void hardStop(Task task){
        if(destroyed||current!=task)return;
        cancelAtSafeCheckpoint(task);
        if(active(task)&&!task.terminal){Store.error(this,"조회 제한 시간이 지났어. 위젯을 다시 눌러 줘.");RefreshFeedback.error(this,task.id);}
        closeTask(task);
    }
    private void closeTask(Task task){
        if(current!=task)return;
        if(task.id==RefreshFeedback.currentRequestId(this))RefreshFeedback.clear(this);
        WeeklyWidget.renderAll(this);current=null;
        if(foreground){stopForeground(STOP_FOREGROUND_REMOVE);foreground=false;}
        stopSelfResult(latestStartId);
    }
    @Override public void onTimeout(int startId,int type){
        Task task=current;
        if(task!=null){cancelAtSafeCheckpoint(task);if(!task.terminal&&active(task)){Store.error(this,"Android가 조회 작업을 종료했어. 위젯을 다시 눌러 줘.");RefreshFeedback.error(this,task.id);WeeklyWidget.renderAll(this);}}
        current=null;if(foreground){stopForeground(STOP_FOREGROUND_REMOVE);foreground=false;}stopSelfResult(latestStartId);
    }
    @Override public void onDestroy(){
        destroyed=true;main.removeCallbacksAndMessages(null);
        Task task=current;if(task!=null){cancelAtSafeCheckpoint(task);if(!task.terminal&&task.id==RefreshFeedback.currentRequestId(this)){
            Store.error(this,"위젯 조회 작업이 종료됐어. 위젯을 다시 눌러 줘.");RefreshFeedback.error(this,task.id);WeeklyWidget.renderAll(this);
        }}
        current=null;
        // Repo.IO belongs to the whole app. Never shut it down or interrupt a
        // running rotation merely because this temporary service is destroyed.
        super.onDestroy();
    }
}
