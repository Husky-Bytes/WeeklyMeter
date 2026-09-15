package android.os;
import java.util.concurrent.*;
public class Handler {
    public static final ConcurrentLinkedQueue<Runnable> queue=new ConcurrentLinkedQueue<>();
    public static final ConcurrentLinkedQueue<Delayed> delayed=new ConcurrentLinkedQueue<>();
    public Handler(Looper l){}
    public boolean post(Runnable action){queue.add(action);return true;}
    public boolean postDelayed(Runnable action,long delay){delayed.add(new Delayed(action,SystemClock.elapsedRealtime()+delay));return true;}
    public static void drain(){for(Delayed d:delayed)if(d.when<=SystemClock.elapsedRealtime()&&delayed.remove(d))queue.add(d.action);for(Runnable task;(task=queue.poll())!=null;)task.run();}
    static final class Delayed{final Runnable action;final long when;Delayed(Runnable action,long when){this.action=action;this.when=when;}}
}
