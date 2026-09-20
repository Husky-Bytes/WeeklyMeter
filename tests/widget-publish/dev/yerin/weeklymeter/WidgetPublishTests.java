package dev.yerin.weeklymeter;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.SizeF;
import android.widget.RemoteViews;
import java.util.*;
import java.util.concurrent.*;

/** Exercises the real provider/cache, with only Android host and bitmap drawing replaced. */
public final class WidgetPublishTests {
 private static int checks;private static final List<String> failures=new ArrayList<>();
 private static final AppWidgetManager manager=AppWidgetManager.INSTANCE;
 private static Context context;
 private static void check(boolean value,String message){checks++;if(!value)failures.add(message);}
 private static void reset(){manager.reset();context=new Context();Build.VERSION.SDK_INT=35;Texts.current=Locale.ENGLISH;WidgetRenderer.beforeRender=u->{};Scheduler.requests=0;manager.ids=new int[]{10,20};}
 private static void save(double used,long at){Store.save(context,Collections.singletonList(new Usage("codex","base",used,2000000000L,at)));}
 private static Bundle sizes(SizeF... sizes){Bundle options=new Bundle();options.putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,new ArrayList<>(Arrays.asList(sizes)));return options;}
 private static List<RemoteViews> leaves(RemoteViews views){if(views==null)return Collections.emptyList();if(views.variants!=null)return new ArrayList<>(views.variants.values());if(views.landscape!=null)return Arrays.asList(views.landscape,views.portrait);return Collections.singletonList(views);}
 private static void verify(int id,String percent,long at,int expectedLeaves){
  List<RemoteViews> entries=leaves(manager.published.get(id));check(entries.size()==expectedLeaves,"expected all size variants for "+id);
  for(RemoteViews views:entries){
   check(views.layout==R.layout.weekly_widget,"full widget layout");check(views.bitmap!=null,"bitmap included");if(views.bitmap==null)continue;
   check(percent.equals(views.bitmap.percent),"latest remaining percent for "+id);check(views.bitmap.fetchedAt==at,"latest successful fetch time for "+id);
   check(views.description!=null&&views.description.contains(percent)&&views.description.contains(Long.toString(at)),"accessibility updated with data");
   check(views.placeholder==8,"preview placeholder hidden");check(views.click!=null,"click action retained");
   if(views.click!=null){check(views.click.intent.target==WidgetRefreshService.class,"manual click still uses refresh service");check(WidgetRefreshService.ACTION_HOME_TAP.equals(views.click.intent.getAction()),"home tap action supports refresh/triplet");check((views.click.flags&PendingIntent.FLAG_IMMUTABLE)!=0,"immutable click retained");check(views.click.intent.getIntExtra(WidgetRefreshService.EXTRA_APP_WIDGET_ID,-1)==id,"tap carries matching home widget ID");}
  }
 }
 private static void freshAllSizes(){
  reset();manager.options.put(10,sizes(new SizeF(64,64),new SizeF(140,64)));manager.options.put(20,sizes(new SizeF(70,70),new SizeF(140,70),new SizeF(210,70)));
  save(10,1000);WeeklyWidget.renderAll(context);save(37,2000);WeeklyWidget.renderAll(context);
  verify(10,"63%",2000,2);verify(20,"63%",2000,3);check(Scheduler.requests==0,"repaint does not request a network sync");
  manager.options.put(10,sizes(new SizeF(200,80)));new WeeklyWidget().onAppWidgetOptionsChanged(context,manager,10,manager.options.get(10));verify(10,"63%",2000,1);
  check(leaves(manager.published.get(10)).get(0).bitmap.width==200,"resize uses new width");check(leaves(manager.published.get(10)).get(0).bitmap.height==80,"resize uses new height");
  int before=FloatingWidgetService.repaints;manager.ids=new int[0];WeeklyWidget.renderAll(context);check(FloatingWidgetService.repaints==before+1,"floating-only repaint receives cache updates without home widgets");
 }
 private static void legacyAndInvalidSizes(){
  reset();Build.VERSION.SDK_INT=30;Bundle options=new Bundle();options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,64);options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,60);options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,130);options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,95);manager.options.put(10,options);
  save(25,3000);WeeklyWidget.renderAll(context);verify(10,"75%",3000,2);verify(20,"75%",3000,2);
  RemoteViews views=manager.published.get(10);check(views.landscape.bitmap.width==130&&views.landscape.bitmap.height==60,"legacy landscape dimensions");check(views.portrait.bitmap.width==64&&views.portrait.bitmap.height==95,"legacy portrait dimensions");
  Build.VERSION.SDK_INT=35;manager.options.put(10,sizes(null,new SizeF(Float.NaN,60),new SizeF(0,60),new SizeF(64,64),new SizeF(64,64),new SizeF(100,60),new SizeF(130,60),new SizeF(160,60),new SizeF(190,60)));
  WeeklyWidget.renderAll(context);verify(10,"75%",3000,4);check(manager.published.get(10).variants.size()==4,"invalid and duplicate sizes ignored; memory bound retained");
  manager.options.put(10,sizes(null,new SizeF(-1,60),new SizeF(Float.POSITIVE_INFINITY,60)));WeeklyWidget.renderAll(context);verify(10,"75%",3000,2);
 }
 private static void failureIsolation(){
  reset();save(44,4000);RuntimeException first=new IllegalArgumentException("synthetic stale widget ID");manager.publishFailures.put(10,first);RuntimeException caught=null;
  try{WeeklyWidget.renderAll(context);}catch(RuntimeException error){caught=error;}
  check(caught==first,"publication failure propagated after remaining widgets");verify(20,"56%",4000,2);check(manager.attempts.contains(20),"failed first publication does not block second ID");check(FloatingWidgetService.repaints>0,"home publication failure still dispatches floating repaint");
  reset();save(45,5000);RuntimeException optionFailure=new IllegalStateException("synthetic options failure");manager.optionFailures.put(10,optionFailure);caught=null;
  try{WeeklyWidget.renderAll(context);}catch(RuntimeException error){caught=error;}
  check(caught==optionFailure,"option failure propagated");verify(20,"55%",5000,2);
  reset();save(46,6000);RuntimeException one=new IllegalStateException("first"),two=new IllegalArgumentException("second");manager.ids=new int[]{10,20,30};manager.publishFailures.put(10,one);manager.optionFailures.put(20,two);caught=null;
  try{WeeklyWidget.renderAll(context);}catch(RuntimeException error){caught=error;}
  check(caught==one,"first failure remains primary");check(caught!=null&&Arrays.asList(caught.getSuppressed()).contains(two),"later failures retained");verify(30,"54%",6000,2);
  reset();save(48,7000);RuntimeException shared=new IllegalStateException("same host failure");manager.ids=new int[]{10,20,30};manager.publishFailures.put(10,shared);manager.publishFailures.put(20,shared);caught=null;
  try{WeeklyWidget.renderAll(context);}catch(RuntimeException error){caught=error;}
  check(caught==shared,"same exception instance is not self-suppressed");verify(30,"52%",7000,2);
 }
 private static Thread daemon(String name,Runnable action){Thread thread=new Thread(action,name);thread.setDaemon(true);return thread;}
 private static void await(CountDownLatch latch){try{if(!latch.await(3,TimeUnit.SECONDS))throw new AssertionError("test coordination timeout");}catch(InterruptedException error){Thread.currentThread().interrupt();throw new AssertionError(error);}}
 private static void concurrentFreshness()throws Exception {
  reset();save(10,1000);CountDownLatch oldDrawing=new CountDownLatch(1),releaseOld=new CountDownLatch(1),freshStarted=new CountDownLatch(1);List<Throwable> errors=Collections.synchronizedList(new ArrayList<>());
  WidgetRenderer.beforeRender=u->{if(Thread.currentThread().getName().equals("older-resize")){oldDrawing.countDown();await(releaseOld);}};
  Thread old=daemon("older-resize",()->{try{WeeklyWidget.render(context,manager,10);}catch(Throwable error){errors.add(error);}});old.start();await(oldDrawing);save(65,9000);
  Thread fresh=daemon("fresh-auto",()->{freshStarted.countDown();try{WeeklyWidget.renderAll(context);}catch(Throwable error){errors.add(error);}});fresh.start();await(freshStarted);
  // The fixed provider waits for the old publication; the old provider publishes
  // immediately and is then overwritten when that older bitmap finishes.
  long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(fresh.isAlive()&&fresh.getState()!=Thread.State.BLOCKED&&System.nanoTime()<deadline)Thread.sleep(2);
  releaseOld.countDown();old.join(3000);fresh.join(3000);check(!old.isAlive()&&!fresh.isAlive(),"concurrent renders finish");check(errors.isEmpty(),"concurrent renders do not fail");verify(10,"35%",9000,2);verify(20,"35%",9000,2);
 }
 private static void localeLockOrdering()throws Exception {
  reset();save(50,10000);Texts.current=null;CountDownLatch complete=new CountDownLatch(1);List<Throwable> errors=Collections.synchronizedList(new ArrayList<>());
  Thread language=daemon("language-owner",()->{
   synchronized(Texts.LANGUAGE_LOCK){
    Thread cold=daemon("cold-widget",()->{try{WeeklyWidget.renderAll(context);}catch(Throwable error){errors.add(error);}});cold.start();
    long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(cold.isAlive()&&cold.getState()!=Thread.State.BLOCKED&&System.nanoTime()<deadline)Thread.yield();
    Texts.current=Locale.ENGLISH;
    try{WeeklyWidget.renderAll(context);}catch(Throwable error){errors.add(error);}complete.countDown();
   }
  });language.start();check(complete.await(3,TimeUnit.SECONDS),"language apply cannot deadlock with cold widget render");language.join(500);check(errors.isEmpty(),"language and widget publication succeed");
 }
 public static void main(String[] args)throws Exception {
  freshAllSizes();legacyAndInvalidSizes();failureIsolation();concurrentFreshness();localeLockOrdering();
  if(!failures.isEmpty()){for(String failure:failures)System.err.println("FAIL: "+failure);throw new AssertionError(failures.size()+" of "+checks+" widget publication checks failed");}
  System.out.println("Widget publication tests: "+checks+" checks passed (real provider/cache; synthetic Android host/renderer)");
 }
}
