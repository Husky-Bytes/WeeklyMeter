package dev.yerin.weeklymeter;

import android.app.Activity;
import android.app.Instrumentation;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inspector.WindowInspector;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Destructive only to synthetic test preferences: run exclusively on the isolated emulator. */
public final class RuntimeSmokeInstrumentation extends Instrumentation {
    private int checks;
    private final AtomicBoolean finished=new AtomicBoolean();
    private Context target;
    private Activity styleActivity,intervalActivity,navigationActivity;
    private interface Task {void run() throws Exception;}
    private interface Value<T> {T get() throws Exception;}
    private interface Condition {boolean get() throws Exception;}
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    @Override public void onStart(){
        Thread deadline=new Thread(()->{
            try{Thread.sleep(150_000);}catch(InterruptedException complete){return;}
            Bundle timeout=new Bundle();timeout.putString("stream","\nRUNTIME_SMOKE_FAIL: 150-second test-run deadline exceeded\n");
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
        check("0.6.3".equals(target.getPackageManager().getPackageInfo(target.getPackageName(),0).versionName),"Target APK version is 0.6.3");
        check(target.getPackageManager().getPackageInfo(target.getPackageName(),0).getLongVersionCode()==13,"Target APK version code is 13");
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
        styleActivity=mainStyleEntries();waitForIdleSync();
        check(mainValue(()->(Boolean)field(styleActivity,"floating")),"Floating settings Activity opens the floating configuration");
        check(mainValue(()->((ImageView)field(styleActivity,"previewImage")).getDrawable()!=null),"Settings preview is a real rendered Android image");
        check(FloatingPreferences.widthDp(target)==160&&FloatingPreferences.heightDp(target)==120,"Settings retains independent floating size");
        check(WidgetAppearance.load(target,"widget_style").background==0xff112233&&FloatingPreferences.style(target).background==0xff445566,"Home and floating styles remain independent");
        check(WidgetAppearance.load(target,"widget_style").overallOpacity==73&&FloatingPreferences.style(target).overallOpacity==100,"Home and floating overall opacity persist independently");
        check(RefreshFeedback.currentRequestId(target)==0,"Opening settings does not start a refresh");
        fullSizePreview();

        main(()->FloatingWidgetService.show(target));
        await(()->FloatingWidgetService.isShowing(),8000,"Overlay attaches");
        ImageView first=image();
        check(mainValue(first::isAttachedToWindow),"Overlay view is attached to actual WindowManager");
        overlayWindowFlags();
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
        check(mainValue(()->(attachedOverlayLayout().flags&WindowManager.LayoutParams.FLAG_SECURE)==0),"Restored overlay still does not request screenshot blocking");

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
    private Activity mainStyleEntries()throws Exception{
        navigationActivity=startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        await(()->mainValue(()->!(Boolean)field(navigationActivity,"busy")),3000,"Main screen finishes local-only account-state check");
        check(mainValue(()->(navigationActivity.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)!=0),"Main account/sign-in Activity retains its secure-window protection");
        Button[] entries=mainValue(()->new Button[]{styleEntry(false),styleEntry(true)});
        check(mainValue(()->entries[0].getParent()==entries[1].getParent()),"Home and floating style entries share the same main-screen parent");
        check(mainValue(()->((ViewGroup)entries[0].getParent()).indexOfChild(entries[1])==((ViewGroup)entries[0].getParent()).indexOfChild(entries[0])+1),"Home and floating style entries are adjacent peers");
        check(mainValue(()->entries[0].getCurrentTextColor()==entries[1].getCurrentTextColor()&&entries[0].getTextSize()==entries[1].getTextSize()&&entries[0].getMinHeight()==entries[1].getMinHeight()),"Both style entries have matching text and button dimensions");
        check(mainValue(()->backgroundColor(entries[0])==backgroundColor(entries[1])&&backgroundColor(entries[0])==(Integer)field(null,MainActivity.class,"ACCENT")),"Both style entries use the same primary background color");
        main(()->captureWindow(navigationActivity,"main-customization.png"));
        styleActivity=openStyleEntry(false);
        check(mainValue(()->!(Boolean)field(styleActivity,"floating")),"One home-style button click opens the home editor directly");
        check(AppWidgetManager.getInstance(target).getAppWidgetIds(new ComponentName(target,WeeklyWidget.class)).length==0,"Isolated emulator has no installed home widget for estimate fixture");
        check(mainValue(()->{
            String text=((Button)field(styleActivity,"previewSizeButton")).getText().toString().toLowerCase(java.util.Locale.ROOT);
            return text.contains("예상")||text.contains("estimate");
        }),"Home preview labels its size as estimated when no host widget exists");
        main(()->styleActivity.finish());waitForIdleSync();
        await(()->mainValue(()->!(Boolean)field(navigationActivity,"busy")),3000,"Main screen resumes without a usage request");
        Activity floating=openStyleEntry(true);
        check(!FloatingWidgetService.isActive(),"Opening floating style does not show the overlay or require a nested menu");
        return floating;
    }
    private Button styleEntry(boolean floating){
        Button button=findExactButton(navigationActivity.getWindow().getDecorView(),floating?"플로팅 위젯 꾸미기":"홈 위젯 꾸미기",floating?"Customize floating widget":"Customize home widget");
        if(button==null)throw new AssertionError("Main-screen style entry was not found: "+floating);return button;
    }
    private Activity openStyleEntry(boolean floating)throws Exception{
        ActivityMonitor monitor=addMonitor(WidgetStyleSettingsActivity.class.getName(),null,false);
        try{main(()->styleEntry(floating).performClick());Activity activity=waitForMonitorWithTimeout(monitor,15000);check(activity!=null,"Single main-screen style click starts an editor Activity (floating="+floating+")");return activity;}
        finally{removeMonitor(monitor);}
    }
    private static int backgroundColor(Button button){return ((GradientDrawable)button.getBackground()).getColor().getDefaultColor();}
    private static Button findExactButton(View view,String ko,String en){
        if(view instanceof Button){String text=((Button)view).getText().toString();if(text.equals(ko)||text.equals(en))return (Button)view;}
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){Button found=findExactButton(group.getChildAt(i),ko,en);if(found!=null)return found;}}return null;
    }
    private void fullSizePreview()throws Exception{
        final float density=styleActivity.getResources().getDisplayMetrics().density;
        final int width160=Math.round(160*density),height120=Math.round(120*density),width300=Math.round(300*density),height240=Math.round(240*density);
        await(()->mainValue(()->preview().getWidth()==width160&&preview().getHeight()==height120),3000,"Floating preview measures exactly 160 by 120 dp at display density");
        main(()->captureWindow(styleActivity,"floating-preview-160x120.png"));
        check(mainValue(()->preview().getScaleX()==1f&&preview().getScaleY()==1f),"Preview ImageView has no extra view-level shrink transform");
        int[] fixed=mainValue(()->location(stage()));int[] imageFixed=mainValue(()->location(preview()));
        main(()->settingsScroll().scrollTo(0,Integer.MAX_VALUE));waitForIdleSync();
        check(mainValue(()->settingsScroll().getScrollY()>0),"Settings content actually scrolls beneath the preview");
        check(mainValue(()->java.util.Arrays.equals(fixed,location(stage()))&&java.util.Arrays.equals(imageFixed,location(preview()))),"Preview viewport and image stay fixed while settings scroll");
        main(()->{settingsScroll().scrollTo(0,0);((Button[])field(styleActivity,"categoryButtons"))[1].performClick();});waitForIdleSync();
        main(()->setPreviewDimensions(300,240));waitForIdleSync();
        await(()->mainValue(()->preview().getWidth()==width300&&preview().getHeight()==height240),3000,"Actual size inputs resize preview to 300 by 240 dp without fitting it down");
        check(FloatingPreferences.widthDp(target)==300&&FloatingPreferences.heightDp(target)==240,"Actual size inputs persist the chosen floating dimensions");
        int oldWidth=styleActivity.getWindow().getAttributes().width,oldHeight=styleActivity.getWindow().getAttributes().height;
        try{
            main(()->styleActivity.getWindow().setLayout(Math.round(220*density),Math.round(340*density)));waitForIdleSync();
            await(()->mainValue(()->stage().getWidth()<width300&&stage().getHeight()<height240),3000,"A small app window constrains the preview viewport on both axes");
            check(mainValue(()->preview().getWidth()==width300&&preview().getHeight()==height240),"Small viewport does not reduce the preview's 1:1 pixel dimensions");
            main(()->{
                View viewport=stage();viewport.scrollTo(0,0);long now=SystemClock.uptimeMillis();
                float x=Math.min(100,viewport.getWidth()/2f),y=Math.min(100,viewport.getHeight()/2f);
                event(viewport,now,now,MotionEvent.ACTION_DOWN,x,y);
                event(viewport,now,now+40,MotionEvent.ACTION_MOVE,x-40,y-40);
                // The first intercepted MOVE cancels the clickable ImageView child.
                // A subsequent MOVE reaches the viewport's own touch handler.
                event(viewport,now,now+80,MotionEvent.ACTION_MOVE,x-70,y-70);
                event(viewport,now,now+120,MotionEvent.ACTION_UP,x-70,y-70);
            });
            check(mainValue(()->stage().getScrollX()>0&&stage().getScrollY()>0),"A real preview drag gesture pans on both axes");
            check(mainValue(()->"none".equals(field(styleActivity,"feedbackState"))),"Panning does not accidentally activate the preview click effect");
            main(()->stage().scrollTo(Integer.MAX_VALUE,Integer.MAX_VALUE));waitForIdleSync();
            check(mainValue(()->stage().getScrollX()>0&&stage().getScrollY()>0),"Oversized preview can pan on both axes");
            check(mainValue(()->stage().getScrollX()==maxPreviewScrollX()&&stage().getScrollY()==maxPreviewScrollY()),"Positive preview panning clamps to the content edges");
            main(()->stage().scrollTo(-10000,-10000));waitForIdleSync();
            check(mainValue(()->stage().getScrollX()==0&&stage().getScrollY()==0),"Negative preview panning clamps to zero");
            check(mainValue(()->settingsScroll().getHeight()>0),"Settings retain a usable scrolling viewport beside the pinned preview");
        }finally{
            main(()->{styleActivity.getWindow().setLayout(oldWidth,oldHeight);stage().scrollTo(0,0);});waitForIdleSync();
            main(()->setPreviewDimensions(160,120));waitForIdleSync();
        }
        main(()->setPreviewDimensions(360,300));waitForIdleSync();
        await(()->mainValue(()->preview().getWidth()==Math.round(360*density)&&preview().getHeight()==Math.round(300*density)),3000,"Maximum-size preview still measures 360 by 300 dp without shrinking");
        main(()->{stage().scrollTo(0,0);captureWindow(styleActivity,"floating-preview-360x300.png");setPreviewDimensions(160,120);});waitForIdleSync();
        await(()->mainValue(()->preview().getWidth()==width160&&preview().getHeight()==height120),3000,"Restoring size returns the preview to its original 1:1 dimensions");
        check(RefreshFeedback.currentRequestId(target)==0,"Preview resizing, scrolling and panning never query usage");
    }
    private ImageView preview()throws Exception{return (ImageView)field(styleActivity,"previewImage");}
    private View stage()throws Exception{return (View)field(styleActivity,"previewStage");}
    private ScrollView settingsScroll()throws Exception{return (ScrollView)field(styleActivity,"scroll");}
    private static int[] location(View view){int[] point=new int[2];view.getLocationOnScreen(point);return point;}
    private int maxPreviewScrollX()throws Exception{return Math.max(0,preview().getRight()+stage().getPaddingRight()-stage().getWidth());}
    private int maxPreviewScrollY()throws Exception{return Math.max(0,preview().getBottom()+stage().getPaddingBottom()-stage().getHeight());}
    private void setPreviewDimensions(int width,int height)throws Exception{
        EditText widthInput=findNumericInput(styleActivity.getWindow().getDecorView(),"너비 직접 입력","Width direct input");
        EditText heightInput=findNumericInput(styleActivity.getWindow().getDecorView(),"높이 직접 입력","Height direct input");
        if(widthInput==null||heightInput==null)throw new AssertionError("Floating width/height inputs were not found");
        widthInput.requestFocus();widthInput.setText(String.valueOf(width));widthInput.onEditorAction(EditorInfo.IME_ACTION_DONE);
        heightInput.requestFocus();heightInput.setText(String.valueOf(height));heightInput.onEditorAction(EditorInfo.IME_ACTION_DONE);
    }
    private static EditText findNumericInput(View view,String ko,String en){
        if(view instanceof EditText){String description=String.valueOf(view.getContentDescription());if(description.startsWith(ko)||description.startsWith(en))return (EditText)view;}
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){EditText found=findNumericInput(group.getChildAt(i),ko,en);if(found!=null)return found;}}return null;
    }
    private void captureWindow(Activity activity,String name)throws Exception{
        View decor=activity.getWindow().getDecorView();
        if(decor.getWidth()<=0||decor.getHeight()<=0)throw new AssertionError("App-owned view is not laid out for visual capture");
        java.io.File external=target.getExternalFilesDir(null);
        if(external==null)throw new IllegalStateException("App-scoped test-output directory unavailable");
        java.io.File directory=new java.io.File(external,"runtime-v063");
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IllegalStateException("Could not create app-scoped visual-output directory");
        java.io.File output=new java.io.File(directory,name);
        Bitmap bitmap=Bitmap.createBitmap(decor.getWidth(),decor.getHeight(),Bitmap.Config.ARGB_8888);
        try(java.io.FileOutputStream stream=new java.io.FileOutputStream(output)){
            // Only this synthetic-fixture Activity's own view tree is drawn. This does
            // not capture the screen, another app, a system dialog or account content.
            decor.draw(new Canvas(bitmap));
            if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,stream))throw new IllegalStateException("Visual PNG encoding failed");
        }finally{bitmap.recycle();}
        Bundle status=new Bundle();status.putString("stream","VISUAL_CAPTURE "+output.getAbsolutePath()+"\n");sendStatus(1,status);
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
    private void cleanup() throws Exception {if(target!=null)main(()->{FloatingWidgetService.hide(target);target.stopService(new Intent(target,WidgetRefreshService.class));if(styleActivity!=null)styleActivity.finish();if(intervalActivity!=null)intervalActivity.finish();if(navigationActivity!=null)navigationActivity.finish();});}
    private static Object field(Object object,String name)throws Exception{return field(object,object.getClass(),name);}
    private static Object field(Object object,Class<?> type,String name)throws Exception{Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    private FloatingWidgetService service()throws Exception{return (FloatingWidgetService)field(null,FloatingWidgetService.class,"instance");}
    private ImageView image()throws Exception{return (ImageView)field(service(),"image");}
    private WindowManager.LayoutParams layout()throws Exception{return (WindowManager.LayoutParams)field(service(),"layout");}
    private WindowManager.LayoutParams attachedOverlayLayout()throws Exception{
        ImageView view=image();
        if(!view.isAttachedToWindow()||!(view.getLayoutParams() instanceof WindowManager.LayoutParams))throw new AssertionError("Attached overlay WindowManager attributes are unavailable");
        return (WindowManager.LayoutParams)view.getLayoutParams();
    }
    private void overlayWindowFlags()throws Exception{
        check(mainValue(()->attachedOverlayLayout().type==WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY),"Attached window remains an application overlay");
        check(mainValue(()->(attachedOverlayLayout().flags&WindowManager.LayoutParams.FLAG_SECURE)==0),"Attached overlay does not request screenshot blocking");
        check(mainValue(()->(attachedOverlayLayout().flags&WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)!=0),"Attached overlay does not take keyboard focus");
        check(mainValue(()->(attachedOverlayLayout().flags&WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)!=0),"Attached overlay preserves outside-touch pass-through");
        check(mainValue(()->(attachedOverlayLayout().flags&WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)!=0),"Attached overlay retains screen-coordinate layout behavior");
    }
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
