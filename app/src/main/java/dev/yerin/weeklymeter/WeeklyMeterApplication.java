package dev.yerin.weeklymeter;
import android.app.Application;

/** Restore the non-secret connection cache whenever any app component starts a process. */
public final class WeeklyMeterApplication extends Application {
    @Override public void onCreate(){
        super.onCreate();
        Repo.IO.execute(()->{
            try{new Repo(this).reconcileConnection();}catch(Exception e){Store.error(this,Repo.friendly(e));}
            finally{Scheduler.ensure(this);WeeklyWidget.renderAll(this);}
        });
    }
}
