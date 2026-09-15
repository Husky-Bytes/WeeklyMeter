package dev.yerin.weeklymeter;
import android.content.*;
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        if(i!=null&&Intent.ACTION_LOCALE_CHANGED.equals(i.getAction())){
            AppLanguage.synchronize(c);WeeklyWidget.renderAll(c);return;
        }
        final PendingResult result=goAsync();
        Repo.IO.execute(()->{try{new Repo(c).reconcileConnection();}catch(Exception e){Store.error(c,Repo.friendly(e));}
            finally{Scheduler.ensure(c);WeeklyWidget.renderAll(c);result.finish();}});
    }
}
