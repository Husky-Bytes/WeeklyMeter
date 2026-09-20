package dev.yerin.weeklymeter;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import java.net.BindException;
import java.net.SocketTimeoutException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** A temporary phone-local callback listener, never a relay or a permanent background service. */
public final class BrowserLoginService extends Service {
    private static final String CHANNEL="browser_login", CANCEL="dev.yerin.weeklymeter.CANCEL_LOGIN";
    private static final int NOTIFICATION=1455;
    static final class Status {
        final long attempt; final String phase,url,message;
        Status(long attempt,String phase,String url,String message){this.attempt=attempt;this.phase=phase;this.url=url;this.message=message;}
        boolean active(){return phase.equals("starting")||phase.equals("waiting")||phase.equals("finishing");}
    }
    private static volatile Status status=new Status(0,"idle","","");
    static Status status(){return status;}
    private static void updateStatus(Status value){status=value;AppSignals.changed();}
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService listener=Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private volatile BrowserAuth.Session session;
    private long attempt;
    private boolean started;
    @Override public IBinder onBind(Intent i){return null;}
    @Override public int onStartCommand(Intent i,int flags,int startId){
        if(i!=null&&CANCEL.equals(i.getAction())){
            if(!status.phase.equals("finishing")){cancelled.set(true);closeListener();finish("로그인을 취소했어.");}
            return START_NOT_STICKY;
        }
        if(started)return START_NOT_STICKY;
        started=true;attempt=SystemClock.elapsedRealtimeNanos();
        updateStatus(new Status(attempt,"starting","","브라우저 로그인 준비 중…"));
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,Messages.localize("로그인 진행",Texts.locale(this)),NotificationManager.IMPORTANCE_LOW));
        Notification n=notification("폰 브라우저에서 로그인을 완료해 줘.");
        try{
            if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            else startForeground(NOTIFICATION,n);
        }catch(RuntimeException e){finish("로그인 작업을 시작할 수 없어. 앱을 연 상태에서 다시 눌러 줘.");return START_NOT_STICKY;}
        listener.execute(()->{
            try{
                BrowserAuth.Session current=BrowserAuth.bind(Texts.locale(this));session=current;
                if(cancelled.get()){current.close();return;}
                final String authorizationUrl=current.authorizeUrl();
                main.post(()->{if(!cancelled.get()&&status.attempt==attempt)updateStatus(new Status(attempt,"waiting",authorizationUrl,"브라우저에서 로그인한 뒤 이 앱으로 돌아와 줘."));});
                BrowserAuth.Result result=current.awaitCallback();
                if(cancelled.get())return;
                if(result.denied){main.post(()->{if(currentAttempt())finish("브라우저 로그인이 승인되지 않았어. 다시 시도할 수 있어.");});return;}
                main.post(()->{
                    if(cancelled.get()||status.attempt!=attempt)return;
                    updateStatus(new Status(attempt,"finishing","","로그인 정보를 안전하게 저장하는 중…"));
                    // The long browser wait never occupies the serial credential executor.
                    Repo.IO.execute(()->{
                    String message;
                    try{
                        if(cancelled.get())return;
                        new Repo(this).finishBrowserLogin(result.code,current.verifier());
                        message="로그인 완료";
                        try{new Repo(this).sync();}catch(Exception ignored){/* Details remain in the app; keep the login. */}
                    }catch(Exception e){message=Repo.friendly(e);}
                    final String done=message;main.post(()->{if(currentAttempt())finish(done);else publish();});
                    });
                });
            }catch(Exception e){
                if(cancelled.get())return;
                final String message=e instanceof BindException?"다른 앱이 로그인 연결을 사용 중이야. 잠시 후 다시 눌러 줘.":
                    e instanceof SocketTimeoutException?"로그인 대기 시간이 끝났어. 다시 로그인 버튼을 눌러 줘.":"로그인 연결을 열지 못했어. 앱에서 다시 시작해 줘.";
                main.post(()->{if(currentAttempt())finish(message);});
            }
        });
        return START_NOT_STICKY;
    }
    private Notification notification(String text){
        Intent open=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent view=PendingIntent.getActivity(this,1455,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent cancel=new Intent(this,BrowserLoginService.class).setAction(CANCEL);
        PendingIntent stop=PendingIntent.getService(this,1456,cancel,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_meter).setContentTitle(Messages.localize("Weekly Meter 로그인",Texts.locale(this)))
            .setContentText(Messages.localize(text,Texts.locale(this))).setContentIntent(view).setOngoing(true).setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PRIVATE).addAction(new Notification.Action.Builder(null,Messages.localize("취소",Texts.locale(this)),stop).build()).build();
    }
    static void cancel(Context c){c.startService(new Intent(c,BrowserLoginService.class).setAction(CANCEL));}
    private boolean currentAttempt(){return !cancelled.get()&&status.attempt==attempt&&status.active();}
    private void closeListener(){BrowserAuth.Session current=session;if(current!=null)try{current.close();}catch(Exception ignored){}}
    private void publish(){
        // A saved login must not fail or be exchanged again because a system
        // scheduler or widget host rejects this independent cache update.
        try{Scheduler.ensure(this);}catch(RuntimeException unavailable){}
        try{WeeklyWidget.renderAll(this);}catch(RuntimeException unavailable){}
    }
    private void finish(String message){
        try{
            closeListener();
            if(status.attempt==attempt)updateStatus(new Status(attempt,"done","",message));
            publish();
        }finally{try{stopForeground(STOP_FOREGROUND_REMOVE);}finally{stopSelf();}}
    }
    @Override public void onTimeout(int startId,int type){cancelled.set(true);finish("로그인 대기 시간이 끝났어. 다시 시작해 줘.");}
    @Override public void onDestroy(){
        cancelled.set(true);closeListener();listener.shutdownNow();
        if(status.attempt==attempt&&status.active())updateStatus(new Status(attempt,"done","","로그인 작업이 종료됐어. 다시 시작해 줘."));
        super.onDestroy();
    }
}
