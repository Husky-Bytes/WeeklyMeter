package android.os;
import java.util.*;
public class Handler {
 public static final java.util.concurrent.ConcurrentLinkedQueue<Message> messages=new java.util.concurrent.ConcurrentLinkedQueue<>();
 public Handler(Looper looper){}
 public boolean post(Runnable action){return postDelayed(action,0);}
 public boolean postDelayed(Runnable action,long delay){messages.add(new Message(this,action,SystemClock.now+delay));return true;}
 public void removeCallbacksAndMessages(Object token){messages.removeIf(m->m.owner==this);}
 public void removeCallbacks(Runnable action){messages.removeIf(m->m.owner==this&&m.action==action);}
 public static void drain(){int count=0;while(true){Message next=null;for(Message m:messages)if(m.when<=SystemClock.now&&(next==null||m.when<next.when))next=m;if(next==null)return;if(messages.remove(next))next.action.run();if(++count>5000)throw new AssertionError("Handler did not settle");}}
 public static void advance(long millis){long target=SystemClock.now+millis;while(true){long next=Long.MAX_VALUE;for(Message m:messages)if(m.when<next)next=m.when;if(next>target)break;SystemClock.now=Math.max(SystemClock.now,next);drain();}SystemClock.now=target;drain();}
 public static final class Message{final Handler owner;final Runnable action;final long when;Message(Handler owner,Runnable action,long when){this.owner=owner;this.action=action;this.when=when;}}
}
