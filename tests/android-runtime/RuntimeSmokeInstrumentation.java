package dev.yerin.weeklymeter;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

/** Destructive only to synthetic test preferences: run exclusively on the isolated emulator. */
public final class RuntimeSmokeInstrumentation extends Instrumentation {
    private int checks;
    private Context target;
    private Activity styleActivity;
    private interface Task {void run() throws Exception;}
    private interface Value<T> {T get() throws Exception;}
    private interface Condition {boolean get() throws Exception;}
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            target=getTargetContext();
            if(!(android.os.Build.HARDWARE.contains("ranchu")||android.os.Build.HARDWARE.contains("goldfish")))throw new IllegalStateException("Isolated Android emulator required");
            test();
            result.putString("stream","\nRUNTIME_SMOKE_PASS checks="+checks+"\nNo account login, credentials or live usage API used.\n");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable failure){
            try{cleanup();}catch(Throwable ignored){}
            result.putString("stream","\nRUNTIME_SMOKE_FAIL checks="+checks+"\n"+android.util.Log.getStackTraceString(failure)+"\n");
            finish(Activity.RESULT_CANCELED,result);
        }
    }
    private void test() throws Exception {
        check(Settings.canDrawOverlays(target),"Overlay permission must be granted by test setup");
        check("0.6.0".equals(target.getPackageManager().getPackageInfo(target.getPackageName(),0).versionName),"Target APK version is 0.6.0");
        main(()->{
            FloatingWidgetService.hide(target);
            for(String name:new String[]{"display","widget_style","floating_style","floating_widget"})target.getSharedPreferences(name,Context.MODE_PRIVATE).edit().clear().commit();
            Store.prefs(target).edit().putBoolean("auto",false).putBoolean("connected",false).putString("selected","codex").commit();
            Store.save(target,Collections.singletonList(sample(25)));
            WidgetStyle home=WidgetStyle.defaults();home.background=0xff112233;WidgetAppearance.save(target,"widget_style",home);
            WidgetStyle floating=WidgetStyle.defaults();floating.background=0xff445566;FloatingPreferences.saveStyle(target,floating);
            FloatingPreferences.saveSize(target,160,120);
        });
        Intent style=new Intent(target,WidgetStyleSettingsActivity.class).putExtra(WidgetStyleSettingsActivity.EXTRA_FLOATING,true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        styleActivity=startActivitySync(style);waitForIdleSync();
        check(mainValue(()->(Boolean)field(styleActivity,"floating")),"Floating settings Activity opens the floating configuration");
        check(mainValue(()->((ImageView)field(styleActivity,"previewImage")).getDrawable()!=null),"Settings preview is a real rendered Android image");
        check(FloatingPreferences.widthDp(target)==160&&FloatingPreferences.heightDp(target)==120,"Settings retains independent floating size");
        check(WidgetAppearance.load(target,"widget_style").background==0xff112233&&FloatingPreferences.style(target).background==0xff445566,"Home and floating styles remain independent");
        check(RefreshFeedback.currentRequestId(target)==0,"Opening settings does not start a refresh");

        main(()->FloatingWidgetService.show(target));
        await(()->FloatingWidgetService.isShowing(),8000,"Overlay attaches");
        ImageView first=image();
        check(mainValue(first::isAttachedToWindow),"Overlay view is attached to actual WindowManager");
        check(mainValue(()->first.getDrawable() instanceof BitmapDrawable),"Overlay uses real bitmap rendering");
        await(()->mainValue(()->String.valueOf(image().getContentDescription()).contains("75%")),3000,"Overlay renders synthetic cached 75%");
        check(FloatingWidgetService.isActive(),"Visible overlay has an active foreground session");
        check(RefreshFeedback.currentRequestId(target)==0,"Showing overlay does not query usage");

        main(()->FloatingPreferences.saveSize(target,96,72));
        float density=target.getResources().getDisplayMetrics().density;
        await(()->mainValue(()->layout().width==Math.round(96*density)&&layout().height==Math.round(72*density)),3000,"Live size settings update WindowManager dimensions");
        main(()->Store.save(target,Collections.singletonList(sample(40))));
        await(()->mainValue(()->String.valueOf(image().getContentDescription()).contains("60%")),3000,"Cache save immediately repaints overlay to 60%");
        check(RefreshFeedback.currentRequestId(target)==0,"Cache/settings repaint starts no manual request");

        // Exercise the real receiver callback and real window lifecycle without broadcasting
        // privileged system events. This is not a physical lock-screen or One UI test.
        main(()->receiver().onReceive(target,new Intent(Intent.ACTION_SCREEN_OFF)));
        check(!FloatingWidgetService.isShowing()&&FloatingWidgetService.isActive(),"Screen-off callback hides window but keeps session active");
        check(mainValue(()->!first.isAttachedToWindow()),"Hidden window is physically detached");
        main(()->receiver().onReceive(target,new Intent(Intent.ACTION_USER_PRESENT)));
        await(()->FloatingWidgetService.isShowing(),3000,"User-present callback restores overlay");

        int beforeX=mainValue(()->layout().x),beforeY=mainValue(()->layout().y);
        main(()->{
            ImageView view=image();long now=SystemClock.uptimeMillis();
            event(view,now,now,MotionEvent.ACTION_DOWN,100,100);
            event(view,now,now+40,MotionEvent.ACTION_MOVE,20,50);
            event(view,now,now+80,MotionEvent.ACTION_UP,5,40);
        });
        check(mainValue(()->layout().x<beforeX||layout().y<beforeY),"Drag updates real overlay position including final release coordinate");
        check(RefreshFeedback.currentRequestId(target)==0,"Drag does not refresh");
        check(FloatingPreferences.prefs(target).contains("position_x_fraction"),"Drag position is persisted");

        ImageView held=image();
        main(()->{long now=SystemClock.uptimeMillis();event(held,now,now,MotionEvent.ACTION_DOWN,20,20);});
        await(()->!FloatingWidgetService.isActive(),2500,"600ms hold closes foreground session");
        await(()->mainValue(()->field(null,FloatingWidgetService.class,"instance")==null),3000,"Closed overlay service is destroyed");
        check(mainValue(()->!held.isAttachedToWindow()),"Long press removes actual overlay window");
        check(RefreshFeedback.currentRequestId(target)==0,"Long press does not refresh");

        main(()->FloatingWidgetService.show(target));await(()->FloatingWidgetService.isShowing(),5000,"Overlay can reopen after long press");
        main(()->{ImageView view=image();long now=SystemClock.uptimeMillis();event(view,now,now,MotionEvent.ACTION_DOWN,20,20);event(view,now,now+30,MotionEvent.ACTION_UP,20,20);});
        await(()->!Store.error(target).isEmpty(),5000,"Tap reaches manual-refresh service and reports signed-out failure");
        check(!Store.connected(target),"No account is connected by tap");
        check(FloatingWidgetService.isShowing(),"Manual-refresh failure does not close overlay");
        cleanup();
        await(()->!FloatingWidgetService.isActive()&&!FloatingWidgetService.isShowing(),3000,"Explicit hide cleans foreground overlay state");
    }
    private Usage sample(double used){long now=System.currentTimeMillis();return new Usage("codex","Synthetic emulator fixture",used,now/1000+86400,now);}
    private void cleanup() throws Exception {if(target!=null)main(()->{FloatingWidgetService.hide(target);target.stopService(new Intent(target,WidgetRefreshService.class));if(styleActivity!=null)styleActivity.finish();});}
    private static Object field(Object object,String name)throws Exception{return field(object,object.getClass(),name);}
    private static Object field(Object object,Class<?> type,String name)throws Exception{Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    private FloatingWidgetService service()throws Exception{return (FloatingWidgetService)field(null,FloatingWidgetService.class,"instance");}
    private ImageView image()throws Exception{return (ImageView)field(service(),"image");}
    private WindowManager.LayoutParams layout()throws Exception{return (WindowManager.LayoutParams)field(service(),"layout");}
    private BroadcastReceiver receiver()throws Exception{return (BroadcastReceiver)field(service(),"screen");}
    private static void event(View view,long down,long time,int action,float x,float y){MotionEvent event=MotionEvent.obtain(down,time,action,x,y,0);try{view.dispatchTouchEvent(event);}finally{event.recycle();}}
    private void main(Task task)throws Exception{mainValue(()->{task.run();return null;});}
    private <T>T mainValue(Value<T> task)throws Exception{
        AtomicReference<T> value=new AtomicReference<>();AtomicReference<Throwable> failure=new AtomicReference<>();
        runOnMainSync(()->{try{value.set(task.get());}catch(Throwable error){failure.set(error);}});
        if(failure.get()!=null)throw new Exception("Main-thread check failed",failure.get());return value.get();
    }
    private void await(Condition condition,long timeout,String description)throws Exception{
        long end=SystemClock.uptimeMillis()+timeout;
        while(SystemClock.uptimeMillis()<end){if(condition.get()){check(true,description);return;}SystemClock.sleep(40);}
        check(false,description+" (timed out)");
    }
    private void check(boolean value,String description){if(!value)throw new AssertionError(description);checks++;Bundle status=new Bundle();status.putString("stream","PASS "+description+"\n");sendStatus(1,status);}
}
