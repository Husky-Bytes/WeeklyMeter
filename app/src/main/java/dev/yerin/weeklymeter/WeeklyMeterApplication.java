package dev.yerin.weeklymeter;
import android.app.Application;
import android.content.res.Configuration;

/** Restore the non-secret connection cache whenever any app component starts a process. */
public final class WeeklyMeterApplication extends Application {
    @Override public void onCreate(){
        super.onCreate();
        AppLanguage.synchronize(this);
        Repo.IO.execute(()->{
            try{new Repo(this).reconcileConnection();}catch(Exception e){Store.error(this,Repo.friendly(e));}
            finally{
                try{Scheduler.ensure(this);}catch(RuntimeException unavailable){}
                try{WeeklyWidget.renderAll(this);}catch(RuntimeException unavailable){}
            }
        });
    }
    @Override public void onConfigurationChanged(Configuration configuration){
        super.onConfigurationChanged(configuration);
        AppLanguage.synchronize(this);
        try{WeeklyWidget.renderAll(this);}catch(RuntimeException unavailable){}
    }
}
