package dev.yerin.weeklymeter;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inspector.WindowInspector;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Destructive only to synthetic test preferences: run exclusively on the isolated emulator. */
public final class RuntimeSmokeInstrumentation extends Instrumentation {
    private int checks;
    private final AtomicBoolean finished=new AtomicBoolean();
    private Context target;
    private Activity styleActivity,intervalActivity;
    private interface Task {void run() throws Exception;}
    private interface Value<T> {T get() throws Exception;}
    private interface Condition {boolean get() throws Exception;}
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    @Override public void onStart(){
        Thread deadline=new Thread(()->{
            try{Thread.sleep(90_000);}catch(InterruptedException complete){return;}
            Bundle timeout=new Bundle();timeout.putString("stream","\nRUNTIME_SMOKE_FAIL: 90-second test-run deadline exceeded\n");
            complete(Activity.RESULT_CANCELED,timeout);
        },"runtime-smoke-deadline");deadline.setDaemon(true);deadline.start();
        Bundle result=new Bundle();
        try{
            target=getTargetContext();
            if(!(android.os.Build.HARDWARE.contains("ranchu")||android.os.Build.HARDWARE.contains("goldfish")))throw new IllegalStateException("Isolated Android emulator required");
            test();
            result.putString("stream","\nRUNTIME_SMOKE_PASS checks="+checks+"\nNo account login, credentials or live usage API used.\n");
            complete(Activity.RESULT_OK,result);
        }catch(Throwable failure){
            try{cleanup();}catch(Throwable ignored){}
            result.putString("stream","\nRUNTIME_SMOKE_FAIL checks="+checks+"\n"+android.util.Log.getStackTraceString(failure)+"\n");
            complete(Activity.RESULT_CANCELED,result);
        }finally{deadline.interrupt();}
    }
    private void complete(int code,Bundle result){if(finished.compareAndSet(false,true))finish(code,result);}
    private void test() throws Exception {
        check(Settings.canDrawOverlays(target),"Overlay permission must be granted by test setup");
        check("0.6.1".equals(target.getPackageManager().getPackageInfo(target.getPackageName(),0).versionName),"Target APK version is 0.6.1");
        check(WidgetStyle.defaults().overallOpacity==100,"Overall opacity defaults to the original full visibility");
        bitmapOpacity();
        main(()->{
            FloatingWidgetService.hide(target);
            for(String name:new String[]{"display","widget_style","floating_style","floating_widget"})target.getSharedPreferences(name,Context.MODE_PRIVATE).edit().clear().commit();
            Store.prefs(target).edit().putBoolean("auto",false).putBoolean("connected",false).putString("selected","codex").commit();
            Store.save(target,Collections.singletonList(sample(25)));
            WidgetStyle home=WidgetStyle.defaults();home.background=0xff112233;home.overallOpacity=73;WidgetAppearance.save(target,"widget_style",home);
            WidgetStyle floating=WidgetStyle.defaults();floating.background=0xff445566;FloatingPreferences.saveStyle(target,floating);
            FloatingPreferences.saveSize(target,160,120);
        });
        intervalSettings();
        Intent style=new Intent(target,WidgetStyleSettingsActivity.class).putExtra(WidgetStyleSettingsActivity.EXTRA_FLOATING,true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        styleActivity=startActivitySync(style);waitForIdleSync();
        check(mainValue(()->(Boolean)field(styleActivity,"floating")),"Floating settings Activity opens the floating configuration");
        check(mainValue(()->((ImageView)field(styleActivity,"previewImage")).getDrawable()!=null),"Settings preview is a real rendered Android image");
        check(FloatingPreferences.widthDp(target)==160&&FloatingPreferences.heightDp(target)==120,"Settings retains independent floating size");
        check(WidgetAppearance.load(target,"widget_style").background==0xff112233&&FloatingPreferences.style(target).background==0xff445566,"Home and floating styles remain independent");
        check(WidgetAppearance.load(target,"widget_style").overallOpacity==73&&FloatingPreferences.style(target).overallOpacity==100,"Home and floating overall opacity persist independently");
        check(RefreshFeedback.currentRequestId(target)==0,"Opening settings does not start a refresh");

        main(()->FloatingWidgetService.show(target));
        await(()->FloatingWidgetService.isShowing(),8000,"Overlay attaches");
        ImageView first=image();
        check(mainValue(first::isAttachedToWindow),"Overlay view is attached to actual WindowManager");
        check(mainValue(()->first.getDrawable() instanceof BitmapDrawable),"Overlay uses real bitmap rendering");
        await(()->mainValue(()->String.valueOf(image().getContentDescription()).contains("75%")),3000,"Overlay renders synthetic cached 75%");
        check(FloatingWidgetService.isActive(),"Visible overlay has an active foreground session");
        check(RefreshFeedback.currentRequestId(target)==0,"Showing overlay does not query usage");
        liveOpacity();

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
    private void bitmapOpacity()throws Exception{
        int[] report=mainValue(()->{
            Usage usage=sample(25);WidgetStyle fixture=WidgetStyle.defaults();
            fixture.background=0xff103050;fixture.opacity=100;fixture.overallOpacity=100;fixture.radius=0;
            for(WidgetStyle.Row row:fixture.rows)row.enabled=false;
            fixture.rows[WidgetStyle.PERCENT].enabled=true;fixture.rows[WidgetStyle.PERCENT].color=0xfff8f3ec;
            fixture.feedbackEnabled=true;fixture.brandMode=0;
            Bitmap plain=null,text=null,full=null,half=null,hidden=null;
            try{
                WidgetStyle backgroundOnly=fixture.copy();backgroundOnly.rows[WidgetStyle.PERCENT].enabled=false;
                plain=WidgetRenderer.render(target,usage,backgroundOnly,144,108,"none").bitmap;
                text=WidgetRenderer.render(target,usage,fixture,144,108,"none").bitmap;
                full=WidgetRenderer.render(target,usage,fixture,144,108,"success").bitmap;
                fixture.overallOpacity=50;half=WidgetRenderer.render(target,usage,fixture,144,108,"success").bitmap;
                fixture.overallOpacity=0;hidden=WidgetRenderer.render(target,usage,fixture,144,108,"success").bitmap;
                // counts: background/text/feedback, alpha errors per area, RGB error,
                // zero-opacity alpha, baseline alpha error, dimension mismatch.
                int[] result=new int[10];Bitmap[] images={plain,text,full,half,hidden};
                for(Bitmap bitmap:images)if(bitmap.getWidth()!=full.getWidth()||bitmap.getHeight()!=full.getHeight())result[9]++;
                if(result[9]!=0)return result;
                int[] back=pixels(plain),words=pixels(text),original=pixels(full),faded=pixels(half),zero=pixels(hidden);
                for(int index=0;index<original.length;index++){
                    int region=original[index]!=words[index]?2:words[index]!=back[index]?1:0;result[region]++;
                    int originalAlpha=original[index]>>>24,halfAlpha=faded[index]>>>24;
                    int expected=Math.round(originalAlpha*128/255f);
                    result[region+3]=Math.max(result[region+3],Math.abs(halfAlpha-expected));
                    result[8]=Math.max(result[8],Math.abs(originalAlpha-255));
                    result[7]=Math.max(result[7],zero[index]>>>24);
                    // Android stores premultiplied channels; one round trip can alter
                    // unpremultiplied RGB by a few units at 50% alpha.
                    if(halfAlpha>8)for(int shift:new int[]{0,8,16})result[6]=Math.max(result[6],Math.abs(((original[index]>>>shift)&255)-((faded[index]>>>shift)&255)));
                }
                return result;
            }finally{for(Bitmap bitmap:new Bitmap[]{plain,text,full,half,hidden})if(bitmap!=null)bitmap.recycle();}
        });
        check(report[9]==0,"100/50/0 opacity renders retain identical bitmap dimensions");
        check(report[0]>100,"Opacity bitmap fixture includes background pixels");
        check(report[1]>20,"Opacity bitmap fixture includes text pixels");
        check(report[2]>10,"Opacity bitmap fixture includes success-feedback pixels");
        check(report[8]==0,"100% overall opacity preserves an opaque composed widget");
        check(report[3]<=1,"50% overall opacity scales background alpha (max error "+report[3]+")");
        check(report[4]<=1,"50% overall opacity scales text alpha (max error "+report[4]+")");
        check(report[5]<=1,"50% overall opacity scales success-feedback alpha (max error "+report[5]+")");
        check(report[6]<=3,"50% overall opacity preserves colors within premultiplication rounding (max error "+report[6]+")");
        check(report[7]==0,"0% overall opacity makes all background, text and feedback pixels transparent");
    }
    private void liveOpacity()throws Exception{
        await(()->mainValue(()->maxAlpha(((BitmapDrawable)image().getDrawable()).getBitmap())==255),3000,"Live overlay starts at full overall opacity");
        main(()->{WidgetStyle style=FloatingPreferences.style(target);style.overallOpacity=50;FloatingPreferences.saveStyle(target,style);});
        await(()->mainValue(()->Math.abs(maxAlpha(((BitmapDrawable)image().getDrawable()).getBitmap())-128)<=1),3000,"Live overlay repaints to 50% overall opacity");
        check(WidgetAppearance.load(target,"widget_style").overallOpacity==73,"Changing floating overall opacity leaves home opacity unchanged");
        main(()->{WidgetStyle style=FloatingPreferences.style(target);style.overallOpacity=0;FloatingPreferences.saveStyle(target,style);});
        await(()->mainValue(()->maxAlpha(((BitmapDrawable)image().getDrawable()).getBitmap())==0),3000,"Live overlay repaints to fully transparent at 0%");
        check(FloatingWidgetService.isActive(),"Fully transparent floating widget retains a closable session");
        main(()->{WidgetStyle style=FloatingPreferences.style(target);style.overallOpacity=100;FloatingPreferences.saveStyle(target,style);});
        await(()->mainValue(()->maxAlpha(((BitmapDrawable)image().getDrawable()).getBitmap())==255),3000,"Live overlay restores full opacity without reopening");
        check(RefreshFeedback.currentRequestId(target)==0,"Overall-opacity changes never start a usage query");
    }
    private static int[] pixels(Bitmap bitmap){int[] pixels=new int[bitmap.getWidth()*bitmap.getHeight()];bitmap.getPixels(pixels,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());return pixels;}
    private static int maxAlpha(Bitmap bitmap){int max=0;for(int pixel:pixels(bitmap))max=Math.max(max,pixel>>>24);return max;}
    private void intervalSettings()throws Exception{
        main(()->Store.prefs(target).edit().putInt("minutes",47).commit());
        check(Scheduler.minutes(target)==47,"Android preferences retain a custom 47-minute interval");
        main(()->Store.prefs(target).edit().putInt("minutes",10080).commit());
        check(Scheduler.minutes(target)==10080,"Android preferences retain the seven-day interval limit");
        main(()->Store.prefs(target).edit().putInt("minutes",14).commit());
        check(Scheduler.minutes(target)==15,"Out-of-range stored interval falls back to 15 minutes");
        main(()->Store.prefs(target).edit().putString("minutes","47").commit());
        check(Scheduler.minutes(target)==15,"Wrong-type Android preference falls back safely");
        main(()->Store.prefs(target).edit().remove("minutes").commit());
        intervalActivity=startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        main(this::openIntervalDialog);waitForIdleSync();
        IntervalDialog valid=mainValue(this::intervalDialog);
        main(()->{valid.input.setText("47");valid.root.findViewById(android.R.id.button1).performClick();});
        check(Scheduler.minutes(target)==47,"Actual interval dialog saves a typed 47-minute value");
        await(()->mainValue(()->!valid.input.isAttachedToWindow()),3000,"Valid interval closes the dialog");
        main(this::openIntervalDialog);waitForIdleSync();
        IntervalDialog invalid=mainValue(this::intervalDialog);
        main(()->{invalid.input.setText("14");invalid.root.findViewById(android.R.id.button1).performClick();});
        check(mainValue(()->invalid.input.isAttachedToWindow()&&invalid.input.getError()!=null),"Actual dialog rejects 14 minutes without dismissing");
        check(Scheduler.minutes(target)==47,"Invalid input does not overwrite the saved interval");
        main(()->invalid.root.findViewById(android.R.id.button2).performClick());
        check(Scheduler.minutes(target)==47,"Cancelling interval input preserves the saved value");
        main(this::openIntervalDialog);waitForIdleSync();
        IntervalDialog preset=mainValue(this::intervalDialog);
        main(()->findPreset(preset.root,"30").performClick());
        check(mainValue(()->"30".contentEquals(preset.input.getText())),"30-minute preset remains available in the custom dialog");
        check(Scheduler.minutes(target)==47,"Preset does not persist until Save is pressed");
        main(()->{preset.root.findViewById(android.R.id.button2).performClick();intervalActivity.finish();});waitForIdleSync();
        check(RefreshFeedback.currentRequestId(target)==0&&!Store.connected(target),"Interval settings do not query or connect an account");
    }
    private void openIntervalDialog()throws Exception{java.lang.reflect.Method method=MainActivity.class.getDeclaredMethod("refreshInterval");method.setAccessible(true);method.invoke(intervalActivity);}
    private static final class IntervalDialog {final View root;final EditText input;IntervalDialog(View root,EditText input){this.root=root;this.input=input;}}
    private IntervalDialog intervalDialog(){
        for(View root:WindowInspector.getGlobalWindowViews()){EditText input=findInput(root);if(root.isAttachedToWindow()&&input!=null&&root.findViewById(android.R.id.button1)!=null)return new IntervalDialog(root,input);}
        throw new AssertionError("Actual interval dialog input was not found");
    }
    private static EditText findInput(View view){if(view instanceof EditText)return (EditText)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){EditText found=findInput(group.getChildAt(i));if(found!=null)return found;}}return null;}
    private static Button findPreset(View view,String minutes){if(view instanceof Button&&((Button)view).getText().toString().startsWith(minutes))return (Button)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){Button found=findPreset(group.getChildAt(i),minutes);if(found!=null)return found;}}return null;}
    private Usage sample(double used){long now=System.currentTimeMillis();return new Usage("codex","Synthetic emulator fixture",used,now/1000+86400,now);}
    private void cleanup() throws Exception {if(target!=null)main(()->{FloatingWidgetService.hide(target);target.stopService(new Intent(target,WidgetRefreshService.class));if(styleActivity!=null)styleActivity.finish();if(intervalActivity!=null)intervalActivity.finish();});}
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
