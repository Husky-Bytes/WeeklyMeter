package dev.yerin.weeklymeter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
/** Pure local deadlines and event bus; source checks are not Android UI execution. */
public final class DisplayStateTests {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    public static void main(String[] args)throws Exception{
        long now=1_800_000_000_000L,week=604800000L;
        check(DisplayExpiry.nextDelay(now,now,0)==1800001,"fresh snapshot schedules strict 30-minute state boundary");
        check(DisplayExpiry.nextDelay(now,now-1800000,0)==1,"exactly 30 minutes still valid for one millisecond");
        check(DisplayExpiry.nextDelay(now,now-1800001,0)==week-1800001,"already stale schedules only 7-day expiry");
        check(DisplayExpiry.nextDelay(now,now-week+1,0)==1,"last valid millisecond");
        check(DisplayExpiry.nextDelay(now,now-week,0)==-1,"7-day expiry has no recurring callback");
        check(DisplayExpiry.nextDelay(now,now-week-1,0)==-1,"old snapshots cannot create polling");
        check(DisplayExpiry.nextDelay(now,now,now/1000)==-1,"reset at current second is expired");
        check(DisplayExpiry.nextDelay(now+999,now,now/1000+1)==1,"subsecond reset boundary");
        check(DisplayExpiry.nextDelay(now,now-1900000,now/1000+25)==25000,"reset earlier than age expiry wins");
        check(DisplayExpiry.nextDelay(now,now+1,0)==-1,"backward clock is invalid until data or clock event");
        check(DisplayExpiry.nextDelay(-1,0,0)==-1&&DisplayExpiry.nextDelay(now,-1,0)==-1,"invalid clocks do not schedule");
        check(DisplayExpiry.nextDelay(Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE)==1800001,"no overflowing reset multiplication");
        for(long age:new long[]{0,1,1799999,1800000,1800001,week-1000,week-1}){
            long delay=DisplayExpiry.nextDelay(now,now-age,0);
            check(delay>0&&delay<=week-age,"deadline is bounded by snapshot lifetime");
            check(DisplayExpiry.nextDelay(now+delay,now-age,0)!=0,"deadline never yields zero-delay loop");
        }
        AtomicInteger calls=new AtomicInteger();Runnable listener=calls::incrementAndGet;
        Runnable broken=()->{throw new IllegalStateException("synthetic detached screen");};
        AppSignals.register(null);AppSignals.register(broken);AppSignals.register(listener);AppSignals.register(listener);
        AppSignals.changed();check(calls.get()==1,"duplicate observers coalesce and failed observers do not stop others");
        AppSignals.unregister(listener);AppSignals.changed();check(calls.get()==1,"paused screen receives no notification");AppSignals.unregister(broken);
        Runnable selfRemoving=new Runnable(){public void run(){calls.incrementAndGet();AppSignals.unregister(this);}};
        AppSignals.register(selfRemoving);AppSignals.changed();AppSignals.changed();check(calls.get()==2,"observer can unregister during dispatch");
        AppSignals.register(listener);Thread worker=new Thread(AppSignals::changed);worker.start();worker.join();AppSignals.unregister(listener);
        check(calls.get()==3,"background service can notify without a UI dependency");
        if(args.length>0){
            Path source=Paths.get(args[0]);String main=read(source,"MainActivity.java"),editor=read(source,"WidgetStyleSettingsActivity.java");
            check(!main.contains("postDelayed(this,500)")&&!main.contains("loginUpdates"),"main has no repeating login polling loop");
            check(main.contains("registerOnSharedPreferenceChangeListener(preferenceObserver)")&&main.contains("unregisterOnSharedPreferenceChangeListener(preferenceObserver)"),"main display listener has paired lifecycle registration");
            check(main.contains("AppSignals.register(stateObserver)")&&main.contains("AppSignals.unregister(stateObserver)"),"service state listener is lifecycle-owned");
            check(main.contains("else updateUsage();")&&main.contains("key.equals(\"meters\")"),"cached updates have a non-rebuilding label path");
            check(main.contains("Intent.ACTION_TIME_CHANGED")&&main.contains("DisplayExpiry.nextDelay"),"visible cache expiry and clock changes update local labels");
            check(editor.contains("if(pages[category]!=null)return;")&&editor.contains("textPanels[selectedElement]==null"),"settings pages and elements initialize lazily");
            check(editor.contains("if(show&&opened[0]==null)")&&editor.contains("buildColorEditor(editor"),"color wheel initializes when opened");
            check(editor.contains("postOnAnimation(renderFrame)")&&editor.contains("if(framePosted"),"preview batches edits into a single animation frame");
            check(editor.contains("controlVisible(update.view)")&&editor.contains("!appearanceDirty"),"only visible controls reflect and unchanged lifecycle flushes do not save");
        }
        System.out.println("PASS: "+checks+" display state checks (pure Java + source contracts; no device UI)");
    }
    private static String read(Path root,String file)throws Exception{return new String(Files.readAllBytes(root.resolve(file)),StandardCharsets.UTF_8);}
}
