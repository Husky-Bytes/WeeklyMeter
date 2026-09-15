package dev.yerin.weeklymeter;
import android.content.*;
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        final PendingResult result=goAsync();
        Repo.IO.execute(()->{try{new Repo(c).reconcileConnection();}catch(Exception e){Store.error(c,Repo.friendly(e));}
            finally{Scheduler.ensure(c);WeeklyWidget.renderAll(c);result.finish();}});
    }
}
