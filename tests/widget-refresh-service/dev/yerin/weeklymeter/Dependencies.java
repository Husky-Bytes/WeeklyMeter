package dev.yerin.weeklymeter;
import android.content.Context;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
final class R {static final class drawable {static final int ic_meter=1;}}
final class Texts {static java.util.Locale locale(Context c){return java.util.Locale.KOREAN;}}
final class WeeklyWidget {
 static int renders,failures,homeRenders,floatingRenders;
 static java.util.function.IntPredicate reject=attempt->false;
 static void renderAll(Context c){renderFeedback(c,true,true);}
 static void renderFeedback(Context c,boolean home,boolean floating){int attempt=++renders;if(home)homeRenders++;if(floating)floatingRenders++;if(reject.test(attempt)){failures++;throw new IllegalStateException("Synthetic widget host publication failure");}if(home)RefreshFeedback.published(false,RefreshFeedback.snapshot(c));if(floating)RefreshFeedback.published(true,RefreshFeedback.snapshot(c,true));}
 static void reset(){renders=failures=homeRenders=floatingRenders=0;reject=attempt->false;RefreshFeedback.published(false,RefreshFeedback.snapshot(new Context()));RefreshFeedback.published(true,RefreshFeedback.snapshot(new Context(),true));}
}
final class Store {
 static final Prefs values=new Prefs();
 static Prefs prefs(Context c){return values;}
 static boolean connected(Context c){return values.getBoolean("connected",false);}
 static void error(Context c,String text){values.data.put("error",text);}
 static final class Prefs {
  final Map<String,Object> data=new ConcurrentHashMap<>();
  boolean getBoolean(String key,boolean fallback){Object v=data.get(key);return v instanceof Boolean?(Boolean)v:fallback;}
  int getInt(String key,int fallback){Object v=data.get(key);return v instanceof Number?((Number)v).intValue():fallback;}
  long getLong(String key,long fallback){Object v=data.get(key);return v instanceof Number?((Number)v).longValue():fallback;}
  Prefs edit(){return this;}Prefs putLong(String key,long value){data.put(key,value);return this;}Prefs remove(String key){data.remove(key);return this;}void apply(){}
 }
}
final class Repo {
 enum SyncOutcome {UPDATED,SKIPPED,CANCELLED,SIGNED_OUT}
 static ExecutorService IO=Executors.newSingleThreadExecutor();
 static final AtomicInteger syncCalls=new AtomicInteger(),reconcileCalls=new AtomicInteger();
 static volatile boolean vaultConnected=true,rotationSaved;
 static volatile SyncOutcome outcome=SyncOutcome.UPDATED;
 interface SyncAction {void run(BooleanSupplier stopped)throws Exception;}
 static volatile SyncAction action=stopped->{};
 Repo(Context c){}
 boolean reconcileConnection(){reconcileCalls.incrementAndGet();Context.EVENTS.add("reconcile");Store.values.data.put("connected",vaultConnected);return vaultConnected;}
 SyncOutcome syncConnected(BooleanSupplier stopped)throws Exception{if(stopped.getAsBoolean())return SyncOutcome.CANCELLED;boolean connected=reconcileConnection();if(stopped.getAsBoolean())return SyncOutcome.CANCELLED;return connected?sync(stopped):SyncOutcome.SIGNED_OUT;}
 SyncOutcome syncConnected(BooleanSupplier stopped,BooleanSupplier ineligible)throws Exception{if(stopped.getAsBoolean())return SyncOutcome.CANCELLED;boolean connected=reconcileConnection();if(stopped.getAsBoolean()||ineligible.getAsBoolean())return SyncOutcome.CANCELLED;return connected?sync(stopped):SyncOutcome.SIGNED_OUT;}
 SyncOutcome sync(BooleanSupplier stopped)throws Exception{if(stopped.getAsBoolean())return SyncOutcome.CANCELLED;syncCalls.incrementAndGet();Context.EVENTS.add("sync");action.run(stopped);return stopped.getAsBoolean()?SyncOutcome.CANCELLED:outcome;}
 static String friendly(Exception error){return "Synthetic request failed";}
 static void reset()throws Exception{IO.submit(()->{}).get(3,TimeUnit.SECONDS);syncCalls.set(0);reconcileCalls.set(0);vaultConnected=true;rotationSaved=false;outcome=SyncOutcome.UPDATED;action=stopped->{};}
}
