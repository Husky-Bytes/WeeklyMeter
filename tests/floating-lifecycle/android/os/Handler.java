package android.os;
import java.util.ArrayList;
import java.util.List;
/** Deterministic main looper. Delays run in due-time/insertion order, never wall-clock time. */
public final class Handler {
    private static final List<Message> messages=new ArrayList<>();
    private static long sequence;
    public Handler(Looper looper){}
    public boolean post(Runnable action){return postDelayed(action,0);}
    public boolean postDelayed(Runnable action,long delay){messages.add(new Message(this,action,SystemClock.now+Math.max(0,delay),sequence++));return true;}
    public void removeCallbacks(Runnable action){messages.removeIf(m->m.owner==this&&m.action==action);}
    public void removeCallbacksAndMessages(Object token){messages.removeIf(m->m.owner==this);}
    public static int pending(){return messages.size();}
    public static void reset(){messages.clear();sequence=0;SystemClock.now=10000;}
    public static void drain(){
        int count=0;
        while(true){
            Message next=null;
            for(Message m:messages)if(m.when<=SystemClock.now&&(next==null||m.when<next.when||m.when==next.when&&m.sequence<next.sequence))next=m;
            if(next==null)return;
            messages.remove(next);next.action.run();
            if(++count>10000)throw new AssertionError("Immediate Handler callbacks did not settle");
        }
    }
    public static void advance(long millis){
        long target=SystemClock.now+millis;int count=0;
        while(true){
            long due=Long.MAX_VALUE;
            for(Message m:messages)due=Math.min(due,m.when);
            if(due>target)break;
            SystemClock.now=Math.max(SystemClock.now,due);drain();
            if(++count>10000)throw new AssertionError("Delayed Handler callbacks did not settle");
        }
        SystemClock.now=target;drain();
    }
    private static final class Message {
        final Handler owner;final Runnable action;final long when,sequence;
        Message(Handler owner,Runnable action,long when,long sequence){this.owner=owner;this.action=action;this.when=when;this.sequence=sequence;}
    }
}
