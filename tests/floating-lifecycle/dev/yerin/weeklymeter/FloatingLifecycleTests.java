package dev.yerin.weeklymeter;
import android.content.Context;
import android.content.Intent;
import android.app.Service;
import android.os.Handler;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/** Executable lifecycle regressions: actual service, no Android device, account or network. */
public final class FloatingLifecycleTests {
    private static int checks;
    private static final List<String> failures=new ArrayList<>();
    private static FloatingWidgetService service;
    private static void check(boolean condition,String label){checks++;if(!condition)failures.add(label);}
    private static FloatingWidgetService start(){
        if(service!=null)service.onDestroy();
        Handler.reset();Context.reset();Scheduler.calls=0;WidgetRenderer.renders=0;WidgetRenderer.bodyPixel=0xff123456;WidgetRenderer.duringRender=()->{};RefreshFeedback.until=0;Store.usage=null;
        service=new FloatingWidgetService();service.onCreate();
        int result=service.onStartCommand(new Intent(service,FloatingWidgetService.class).setAction(FloatingWidgetService.ACTION_SHOW),0,1);
        Handler.drain();
        check(FloatingWidgetService.isActive(),"user-started session active");
        check(FloatingWidgetService.isShowing()&&Context.WINDOW.attachedCount()==1,"unlocked service initially attached");
        check(service.foregroundCalls==1,"foreground entered once");
        check(result==Service.START_STICKY,"explicit show requests system-managed session restoration");
        return service;
    }
    private static void off(){
        Context.POWER.interactive=false;Context.KEYGUARD.locked=true;
        service.broadcast(Intent.ACTION_SCREEN_OFF);Handler.drain();
    }
    private static void earlyWake(){
        Context.POWER.interactive=true;
        service.broadcast(Intent.ACTION_SCREEN_ON);Handler.drain();
        service.broadcast(Intent.ACTION_USER_PRESENT);Handler.drain();
    }
    private static void expectNoFetch(String label){check(Context.starts.isEmpty(),label+": no refresh service/network request");}
    private static void delayedUnlock(){
        start();int scheduled=Scheduler.calls;off();
        check(!FloatingWidgetService.isShowing()&&Context.WINDOW.attachedCount()==0,"screen off detaches overlay");
        check(FloatingWidgetService.isActive()&&service.stopCalls==0,"screen off preserves active session");
        check(service.foregroundRemoved==0,"screen off does not end foreground session");
        Context.POWER.interactive=true;service.broadcast(Intent.ACTION_SCREEN_ON);Handler.drain();
        check(!FloatingWidgetService.isShowing(),"SCREEN_ON while keyguard locked does not reveal overlay");
        service.broadcast(Intent.ACTION_USER_PRESENT);Handler.drain();
        check(!FloatingWidgetService.isShowing(),"early USER_PRESENT still respects keyguard");
        Handler.advance(250);Context.KEYGUARD.locked=false;
        // No second broadcast, activity opening, preference update or usage refresh.
        Handler.advance(30000);
        check(FloatingWidgetService.isShowing()&&Context.WINDOW.attachedCount()==1,"eventual unlock without another broadcast reattaches overlay");
        check(FloatingWidgetService.isActive()&&service.stopCalls==0,"delayed unlock keeps original session");
        check(service.foregroundCalls==1&&service.foregroundRemoved==0,"wake reuses foreground notification");
        check(Scheduler.calls==scheduled,"wake does not change automatic schedule");
        expectNoFetch("delayed unlock");
        Handler.advance(120000);check(Handler.pending()==0,"successful wake leaves no resume polling");
    }
    private static void boundedLockedCheck(){
        start();off();earlyWake();int added=Context.WINDOW.addCalls;
        Handler.advance(120000);
        check(Handler.pending()==0,"locked wake retry window is bounded");
        check(Context.WINDOW.addCalls==added&&!FloatingWidgetService.isShowing(),"never attaches while keyguard remains locked");
        check(FloatingWidgetService.isActive(),"bounded lock retry expiry preserves user session");
        Context.KEYGUARD.locked=false;service.broadcast(Intent.ACTION_USER_PRESENT);Handler.drain();Handler.advance(30000);
        check(FloatingWidgetService.isShowing(),"later real unlock broadcast can resume after retry expiry");
        expectNoFetch("locked wake");
    }
    private static void delayedInteractiveState(){
        start();int scheduled=Scheduler.calls;off();Context.KEYGUARD.locked=false;
        service.broadcast(Intent.ACTION_SCREEN_ON);service.broadcast(Intent.ACTION_USER_PRESENT);Handler.drain();
        check(!FloatingWidgetService.isShowing()&&Context.WINDOW.attachedCount()==0,"early unlock does not draw on noninteractive screen");
        Handler.advance(250);Context.POWER.interactive=true;Handler.advance(30000);
        check(FloatingWidgetService.isShowing()&&Context.WINDOW.attachedCount()==1,"power becoming interactive without another broadcast restores window");
        check(Scheduler.calls==scheduled,"delayed interactive state does not change schedule");
        check(Handler.pending()==0,"interactive recovery clears resume polling");
        expectNoFetch("delayed interactive state");
    }
    private static void screenOffCancelsPendingWake(){
        start();off();earlyWake();int added=Context.WINDOW.addCalls;int rendered=WidgetRenderer.renders;
        check(Handler.pending()>0,"early wake has a pending local resume check");
        off();Handler.advance(120000);
        check(Handler.pending()==0,"screen off cancels pending resume polling");
        check(Context.WINDOW.addCalls==added&&WidgetRenderer.renders==rendered,"no window or bitmap work while asleep");
        check(FloatingWidgetService.isActive()&&!FloatingWidgetService.isShowing(),"sleep preserves hidden session");
        expectNoFetch("screen off during pending wake");
    }
    private static void explicitHideCancelsResume(){
        start();off();earlyWake();
        int result=service.onStartCommand(new Intent(service,FloatingWidgetService.class).setAction(FloatingWidgetService.ACTION_HIDE),0,2);
        int added=Context.WINDOW.addCalls;
        Context.KEYGUARD.locked=false;service.broadcast(Intent.ACTION_USER_PRESENT);Handler.advance(120000);
        check(!FloatingWidgetService.isActive()&&!FloatingWidgetService.isShowing(),"explicit hide remains closed");
        check(Context.WINDOW.addCalls==added&&Context.WINDOW.attachedCount()==0,"explicit hide has no delayed resurrection");
        check(Handler.pending()==0,"explicit hide cancels resume callbacks");
        check(service.stopCalls==1&&service.foregroundRemoved==1,"explicit hide stops foreground service once");
        check(result==Service.START_NOT_STICKY,"explicit hide does not request restart");
        check(service.onStartCommand(null,0,3)==Service.START_NOT_STICKY,"null intent cannot reopen an explicitly closed service instance");
        expectNoFetch("explicit hide");
    }
    private static void longPressDoesNotResurrect(){
        start();View widget=Context.WINDOW.lastAdded;
        widget.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_DOWN,100,100));
        Handler.advance(FloatingGesture.LONG_PRESS_MS);
        int added=Context.WINDOW.addCalls;
        service.broadcast(Intent.ACTION_SCREEN_OFF);service.broadcast(Intent.ACTION_SCREEN_ON);service.broadcast(Intent.ACTION_USER_PRESENT);
        Handler.advance(120000);
        check(!FloatingWidgetService.isActive()&&!FloatingWidgetService.isShowing(),"long press closes session");
        check(Context.WINDOW.addCalls==added&&Context.WINDOW.attachedCount()==0,"long-press close cannot resume on screen broadcast");
        check(service.stopCalls==1&&service.foregroundRemoved==1,"long press terminates service once");
        check(Handler.pending()==0,"long-press close leaves no callbacks");
        expectNoFetch("long press");
    }
    private static void destroyCancelsResume(){
        start();off();earlyWake();int added=Context.WINDOW.addCalls;
        service.onDestroy();Context.KEYGUARD.locked=false;Handler.advance(120000);
        check(Context.WINDOW.addCalls==added&&Context.WINDOW.attachedCount()==0,"destroyed service cannot reattach");
        check(!FloatingWidgetService.isActive()&&!FloatingWidgetService.isShowing(),"destroy clears active and visible state");
        check(Handler.pending()==0&&service.receiverCount()==0,"destroy removes queued work and screen receiver");
        check(Context.APP_OPS.listener==null,"destroy removes permission watcher");
        int listeners=0;for(android.content.SharedPreferences prefs:Context.preferences.values())listeners+=prefs.listeners.size();
        check(listeners==0,"destroy removes preference watchers");
        expectNoFetch("destroy");
        service=null;
    }
    private static void permissionRevocationCancelsResume(){
        start();off();earlyWake();int added=Context.WINDOW.addCalls;
        Settings.allowed=false;Context.APP_OPS.notifyChanged();Handler.drain();
        Context.KEYGUARD.locked=false;service.broadcast(Intent.ACTION_USER_PRESENT);Handler.advance(120000);
        check(!FloatingWidgetService.isActive()&&!FloatingWidgetService.isShowing(),"permission revocation closes session");
        check(Context.WINDOW.addCalls==added,"revoked permission cannot retry addView");
        check(Handler.pending()==0,"permission revocation cancels resume callbacks");
        check(service.stopCalls==1&&service.foregroundRemoved==1,"revocation stops foreground service");
        expectNoFetch("permission revocation");
    }
    private static void transientWakeHostFailure(){
        start();int scheduled=Scheduler.calls;off();Context.KEYGUARD.locked=false;Context.POWER.interactive=true;
        Context.WINDOW.failAdds=1;service.broadcast(Intent.ACTION_SCREEN_ON);Handler.drain();
        check(FloatingWidgetService.isActive(),"single wake addView failure does not permanently close session");
        Handler.advance(30000);
        check(FloatingWidgetService.isShowing()&&Context.WINDOW.attachedCount()==1,"single wake addView failure retries and reattaches");
        check(service.stopCalls==0&&service.foregroundRemoved==0,"transient wake failure retains foreground lifecycle");
        check(Scheduler.calls==scheduled,"transient wake failure does not cancel or restart automatic schedule");
        check(Handler.pending()==0,"recovered host has no polling callbacks");
        expectNoFetch("transient host failure");
    }
    private static void persistentWakeHostFailureIsBounded(){
        start();off();Context.KEYGUARD.locked=false;Context.POWER.interactive=true;
        Context.WINDOW.failAdds=Integer.MAX_VALUE;service.broadcast(Intent.ACTION_SCREEN_ON);Handler.drain();
        Handler.advance(120000);
        check(Handler.pending()==0,"persistent host failure retries are bounded");
        check(Context.WINDOW.attachedCount()==0&&!FloatingWidgetService.isShowing(),"failed host never reports visible overlay");
        expectNoFetch("persistent host failure");
    }
    private static void transientUpdateHostFailure(){
        start();int scheduled=Scheduler.calls;Context.WINDOW.failUpdates=1;
        // Some hosts retain the old view across wake, then reject its first update.
        service.broadcast(Intent.ACTION_SCREEN_ON);Handler.drain();
        check(FloatingWidgetService.isActive(),"transient updateViewLayout failure preserves session");
        check(Context.WINDOW.attachedCount()==0&&Context.WINDOW.removeCalls==1,"failed update detaches stale host view");
        Handler.advance(30000);
        check(FloatingWidgetService.isShowing()&&Context.WINDOW.attachedCount()==1&&Context.WINDOW.addCalls==2,"failed update recovers by adding one fresh window");
        check(service.stopCalls==0&&service.foregroundRemoved==0,"update recovery retains foreground lifecycle");
        check(Scheduler.calls==scheduled,"update recovery does not restart schedule");
        check(Handler.pending()==0,"update recovery leaves no polling");
        expectNoFetch("transient update failure");
    }
    private static void systemRecreationRespectsPrivacy(){
        start();service.onDestroy();Handler.reset();Context.WINDOW.reset();Context.starts.clear();Scheduler.calls=0;
        Context.POWER.interactive=false;Context.KEYGUARD.locked=true;
        service=new FloatingWidgetService();service.onCreate();
        int result=service.onStartCommand(null,0,1);Handler.drain();
        check(result==Service.START_STICKY,"null system recreation restores a sticky session");
        check(FloatingWidgetService.isActive()&&!FloatingWidgetService.isShowing(),"restored session remains hidden on noninteractive lock screen");
        check(Context.WINDOW.addCalls==0&&service.foregroundCalls==1,"system recreation starts foreground without drawing private screen");
        int scheduled=Scheduler.calls;
        earlyWake();Handler.advance(250);Context.KEYGUARD.locked=false;Handler.advance(30000);
        check(FloatingWidgetService.isShowing()&&Context.WINDOW.attachedCount()==1,"restored session reattaches after delayed unlock");
        check(Scheduler.calls==scheduled,"restored session wake does not reschedule automatic refresh");
        expectNoFetch("system recreation");
    }
    private static void deniedSystemRecreationDoesNotRestart(){
        start();service.onDestroy();Handler.reset();Context.WINDOW.reset();Context.starts.clear();
        Settings.allowed=false;service=new FloatingWidgetService();service.onCreate();
        int result=service.onStartCommand(null,0,1);Handler.advance(120000);
        check(result==Service.START_NOT_STICKY,"null system recreation without overlay permission is not sticky");
        check(!FloatingWidgetService.isActive()&&Context.WINDOW.addCalls==0,"denied system recreation cannot show or activate");
        check(service.stopCalls==1&&service.foregroundCalls==0,"denied system recreation stops without foreground entry");
        check(Handler.pending()==0,"denied system recreation leaves no polling");
        expectNoFetch("denied system recreation");
    }
    private static void styleFixture(String key,boolean value){
        service.getSharedPreferences("floating_style",0).edit().putBoolean(key,value).apply();Handler.drain();
    }
    private static void transparentStyleDetachesAndRestores(){
        start();int scheduled=Scheduler.calls;View previous=Context.WINDOW.lastAdded;
        styleFixture("synthetic_all_transparent",true);
        check(!FloatingWidgetService.isShowing()&&Context.WINDOW.attachedCount()==0,"fully transparent rendered output removes the touch window");
        check(FloatingWidgetService.isActive()&&service.stopCalls==0,"transparent style preserves the user-started session");
        check(Context.WINDOW.removeCalls==1,"style change physically detaches the old window");
        check(((android.widget.ImageView)previous).bitmap==null,"hidden window releases its displayed bitmap reference");
        check(WidgetRenderer.lastBitmap.recycled,"never-displayed transparent bitmap is released immediately");
        previous.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_DOWN,10,10));
        previous.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_UP,10,10));
        Handler.advance(120000);
        check(Handler.pending()==0,"transparent style does not start a wake or feedback polling loop");
        check(Scheduler.calls==scheduled&&service.foregroundRemoved==0,"transparent style does not alter automatic scheduling or foreground session");
        expectNoFetch("transparent style and stale detached touch");
        styleFixture("synthetic_all_transparent",false);
        check(FloatingWidgetService.isShowing()&&Context.WINDOW.attachedCount()==1,"visible style preference reattaches without reopening session");
        check(!WidgetRenderer.lastBitmap.recycled,"displayed bitmap is not prematurely recycled");
        check(Scheduler.calls==scheduled,"restoring visible style does not restart automatic interval");
        expectNoFetch("style visibility restoration");
    }
    private static void emptyBodyCanShowOnlyFeedback(){
        start();int scheduled=Scheduler.calls;
        styleFixture("synthetic_body_hidden",true);
        check(!FloatingWidgetService.isShowing()&&Context.WINDOW.attachedCount()==0,"transparent background with hidden content has no touch window");
        RefreshFeedback.show(service,1000);
        FloatingWidgetService.repaint(service);Handler.drain();
        check(FloatingWidgetService.isShowing()&&Context.WINDOW.attachedCount()==1,"visible feedback alone can temporarily attach an otherwise blank widget");
        Handler.advance(1001);
        check(!FloatingWidgetService.isShowing()&&Context.WINDOW.attachedCount()==0,"feedback expiry removes the newly blank touch window");
        check(FloatingWidgetService.isActive()&&Handler.pending()==0,"feedback-only expiry leaves active session without polling");
        check(Scheduler.calls==scheduled,"blank and feedback-only transitions keep automatic interval");
        expectNoFetch("feedback-only visibility");
    }
    private static void transparentFeedbackAndWakeStayDetached(){
        start();styleFixture("synthetic_all_transparent",true);int scheduled=Scheduler.calls,added=Context.WINDOW.addCalls;
        RefreshFeedback.show(service,1000);
        FloatingWidgetService.repaint(service);Handler.drain();
        check(!FloatingWidgetService.isShowing()&&Context.WINDOW.addCalls==added,"feedback cannot reveal a completely transparent composed bitmap");
        off();Context.POWER.interactive=true;Context.KEYGUARD.locked=false;
        service.broadcast(Intent.ACTION_SCREEN_ON);service.broadcast(Intent.ACTION_USER_PRESENT);Handler.drain();
        int rendered=WidgetRenderer.renders;Handler.advance(120000);
        check(!FloatingWidgetService.isShowing()&&Context.WINDOW.addCalls==added,"wake does not attach an intentionally transparent window");
        check(WidgetRenderer.renders==rendered+1&&Handler.pending()==0,"only the centralized feedback expiry redraw remains; no resume polling");
        check(FloatingWidgetService.isActive()&&Scheduler.calls==scheduled,"transparent wake preserves original active schedule");
        expectNoFetch("transparent feedback and wake");
    }
    private static void sparseLowAlphaRemainsTouchable(){
        start();WidgetRenderer.bodyPixel=0x01123456;
        FloatingWidgetService.repaint(service);Handler.drain();
        check(FloatingWidgetService.isShowing()&&Context.WINDOW.attachedCount()==1,"one alpha-1 pixel at the last bitmap corner keeps content visible");
        check(WidgetRenderer.lastBitmap.reads==3,"visibility check reaches the actual last pixel and ignores transparent RGB");
        check(!WidgetRenderer.lastBitmap.recycled,"faint visible bitmap remains valid");
        expectNoFetch("sparse alpha visibility");
    }
    private static void transparentStickySessionRemainsHidden(){
        start();styleFixture("synthetic_all_transparent",true);service.onDestroy();Handler.reset();Context.WINDOW.reset();Context.starts.clear();
        service=new FloatingWidgetService();service.onCreate();
        int result=service.onStartCommand(null,0,1);Handler.advance(120000);
        check(result==Service.START_STICKY&&FloatingWidgetService.isActive(),"null-intent recreation preserves transparent active session");
        check(!FloatingWidgetService.isShowing()&&Context.WINDOW.addCalls==0,"sticky recreation of transparent style never adds a window");
        check(Handler.pending()==0,"transparent sticky recreation leaves no resume polling");
        service.onStartCommand(new Intent(service,FloatingWidgetService.class).setAction(FloatingWidgetService.ACTION_HIDE),0,2);
        check(!FloatingWidgetService.isActive()&&service.stopCalls==1,"app or notification hide still closes an invisible active session");
        expectNoFetch("transparent sticky session");
    }
    private static void automaticAndPositionPreferencesDoNotRedraw(){
        start();int rendered=WidgetRenderer.renders,scheduled=Scheduler.calls;
        Object bitmap=WidgetRenderer.lastBitmap;
        Store.prefs(service).edit().putBoolean("auto",false).putInt("refresh_minutes",30).apply();
        FloatingPreferences.prefs(service).edit().putFloat("position_x_fraction",.2f).putFloat("position_y_fraction",.4f).apply();
        Handler.drain();
        check(WidgetRenderer.renders==rendered&&WidgetRenderer.lastBitmap==bitmap,"automatic settings and saved positions do not allocate a bitmap");
        View widget=Context.WINDOW.lastAdded;int moved=Context.WINDOW.updateCalls;
        widget.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_DOWN,100,100));
        widget.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_MOVE,180,200));
        widget.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_UP,190,210));Handler.drain();
        check(Context.WINDOW.updateCalls>moved,"drag still moves the existing input window");
        check(WidgetRenderer.renders==rendered&&WidgetRenderer.lastBitmap==bitmap,"drag release saves position without regenerating content");
        check(Handler.pending()==0&&Scheduler.calls==scheduled,"position-only changes leave no new timer or automatic scheduling work");
        expectNoFetch("geometry and automatic preferences");
    }
    private static void dimensionsInvalidateOnlyChangedGeometry(){
        start();int rendered=WidgetRenderer.renders;Object first=WidgetRenderer.lastBitmap;
        FloatingPreferences.prefs(service).edit().putInt(FloatingPreferences.WIDTH,150).putInt(FloatingPreferences.HEIGHT,90).apply();Handler.drain();
        check(WidgetRenderer.renders==rendered+1&&WidgetRenderer.lastBitmap!=first,"width and height change coalesce into one new bitmap");
        check(WidgetRenderer.lastWidth==150&&WidgetRenderer.lastHeight==90,"new bitmap uses updated requested dimensions");
        Object resized=WidgetRenderer.lastBitmap;int updates=Context.WINDOW.updateCalls;
        FloatingPreferences.prefs(service).edit().putInt(FloatingPreferences.WIDTH,150).putInt(FloatingPreferences.HEIGHT,90).apply();Handler.drain();
        check(WidgetRenderer.renders==rendered+1&&WidgetRenderer.lastBitmap==resized,"same geometry reuses the existing rendered content");
        check(Context.WINDOW.updateCalls==updates+1&&Context.WINDOW.attachedCount()==1,"cached geometry update retains one valid input window");
        expectNoFetch("dimensions and cached geometry");
    }
    private static void setRenderTime(String field,long value){
        try{java.lang.reflect.Field target=FloatingWidgetService.class.getDeclaredField(field);target.setAccessible(true);target.setLong(service,value);}
        catch(ReflectiveOperationException failure){throw new AssertionError("Missing cache deadline field: "+field,failure);}
    }
    private static Usage freshUsage(){long now=System.currentTimeMillis();return new Usage(now,now/1000+120);}
    private static void paintUsage(Usage usage){Store.usage=usage;FloatingWidgetService.repaint(service);Handler.drain();}
    private static void retryReusesOnlyUnexpiredContent(){
        start();off();Context.KEYGUARD.locked=false;Context.POWER.interactive=true;Context.WINDOW.failAdds=1;
        service.broadcast(Intent.ACTION_SCREEN_ON);Handler.drain();int rendered=WidgetRenderer.renders;Object bitmap=WidgetRenderer.lastBitmap;
        check(!FloatingWidgetService.isShowing()&&Handler.pending()==1,"failed add leaves one bounded resume callback");
        Handler.advance(500);
        check(FloatingWidgetService.isShowing()&&WidgetRenderer.renders==rendered&&WidgetRenderer.lastBitmap==bitmap,"host retry reuses content while its display deadline is still valid");
        expectNoFetch("unexpired host retry");

        start();off();Store.usage=freshUsage();Context.KEYGUARD.locked=false;Context.POWER.interactive=true;Context.WINDOW.failAdds=1;
        service.broadcast(Intent.ACTION_SCREEN_ON);Handler.drain();rendered=WidgetRenderer.renders;bitmap=WidgetRenderer.lastBitmap;
        // Model a wall-clock boundary crossed while the failed window waits for its
        // elapsed-time retry. No production clock abstraction or real sleep needed.
        Store.usage=new Usage(System.currentTimeMillis()-604800_001L,0);
        setRenderTime("renderedUntil",System.currentTimeMillis()-1);
        Handler.advance(500);
        check(FloatingWidgetService.isShowing()&&Context.WINDOW.attachedCount()==1,"host retry after usage expiry restores one window");
        check(WidgetRenderer.renders==rendered+1&&WidgetRenderer.lastBitmap!=bitmap&&WidgetRenderer.lastUsage==Store.usage,"expired retry refreshes cached usage before attaching a new bitmap");
        check(Handler.pending()==0,"expired retry schedules neither an obsolete deadline nor a polling loop");
        expectNoFetch("expired host retry");
    }
    private static void geometryCannotCancelAnOverdueDeadline(){
        start();paintUsage(freshUsage());int rendered=WidgetRenderer.renders;
        check(Handler.pending()==1,"visible fresh usage schedules exactly one cache-only deadline");
        Store.usage=new Usage(System.currentTimeMillis()-604800_001L,0);setRenderTime("renderedUntil",System.currentTimeMillis()-1);
        FloatingPreferences.prefs(service).edit().putInt(FloatingPreferences.WIDTH,100).apply();Handler.drain();
        check(WidgetRenderer.renders==rendered+1&&WidgetRenderer.lastUsage==Store.usage,"same-size geometry event after deadline redraws instead of preserving expired content");
        check(Handler.pending()==0,"overdue geometry render retires its previous pending deadline");
        rendered=WidgetRenderer.renders;setRenderTime("renderedAt",System.currentTimeMillis()+60_000);
        FloatingPreferences.prefs(service).edit().putInt(FloatingPreferences.HEIGHT,70).apply();Handler.drain();
        check(WidgetRenderer.renders==rendered+1,"backwards wall-clock change invalidates otherwise reusable content");
        expectNoFetch("overdue geometry and clock rollback");
    }
    private static void screenOffCancelsDisplayDeadline(){
        start();paintUsage(freshUsage());int rendered=WidgetRenderer.renders,scheduled=Scheduler.calls;
        check(Handler.pending()==1,"visible usage owns one future display boundary");
        off();Handler.advance(604800_000L);
        check(Handler.pending()==0&&WidgetRenderer.renders==rendered,"screen-off cancels the display deadline with no hidden bitmap work or polling");
        check(FloatingWidgetService.isActive()&&!FloatingWidgetService.isShowing(),"deadline cancellation preserves the private user session");
        Context.KEYGUARD.locked=false;Context.POWER.interactive=true;service.broadcast(Intent.ACTION_USER_PRESENT);Handler.drain();
        check(FloatingWidgetService.isShowing()&&WidgetRenderer.renders==rendered+1&&Handler.pending()==1,"wake draws current cache and restores only its next local deadline");
        check(Scheduler.calls==scheduled,"local display expiry does not change network scheduling");
        expectNoFetch("sleeping display deadline");
    }
    private static void backgroundPublicationDuringDrawIsNotLost(){
        start();int rendered=WidgetRenderer.renders,scheduled=Scheduler.calls;
        long now=System.currentTimeMillis();Usage older=new Usage(now-10_000,now/1000+120),latest=new Usage(now,now/1000+120);
        Store.usage=older;
        WidgetRenderer.duringRender=()->{
            check(WidgetRenderer.lastUsage==older,"first render captured usage before the concurrent publication");
            java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
            Thread publisher=new Thread(()->{
                try{Store.usage=latest;for(int i=0;i<5;i++)FloatingWidgetService.repaint(service);}
                catch(Throwable error){failure.set(error);}
            },"synthetic-auto-publication");
            publisher.setDaemon(true);publisher.start();
            try{publisher.join(3000);}catch(InterruptedException error){Thread.currentThread().interrupt();throw new AssertionError(error);}
            check(!publisher.isAlive()&&failure.get()==null,"background publication finishes while old bitmap is still drawing");
        };
        FloatingWidgetService.repaint(service);Handler.drain();
        check(WidgetRenderer.renders==rendered+2,"requests arriving during a draw coalesce into exactly one follow-up render");
        check(WidgetRenderer.lastUsage==latest,"follow-up render publishes latest usage instead of losing the concurrent update");
        check(FloatingWidgetService.isShowing()&&Context.WINDOW.attachedCount()==1,"concurrent publication retains one visible input window");
        check(((android.widget.ImageView)Context.WINDOW.lastAdded).bitmap==WidgetRenderer.lastBitmap,"visible image contains the last completed bitmap");
        check(Handler.pending()==1&&Scheduler.calls==scheduled,"concurrent repaint leaves only the usage deadline, without extra refresh scheduling");
        expectNoFetch("background publication during rendering");
    }
    public static void main(String[] args){
        try{
            delayedUnlock();delayedInteractiveState();boundedLockedCheck();screenOffCancelsPendingWake();explicitHideCancelsResume();longPressDoesNotResurrect();
            destroyCancelsResume();permissionRevocationCancelsResume();transientWakeHostFailure();persistentWakeHostFailureIsBounded();
            transientUpdateHostFailure();
            systemRecreationRespectsPrivacy();deniedSystemRecreationDoesNotRestart();
            transparentStyleDetachesAndRestores();emptyBodyCanShowOnlyFeedback();transparentFeedbackAndWakeStayDetached();
            sparseLowAlphaRemainsTouchable();transparentStickySessionRemainsHidden();
            automaticAndPositionPreferencesDoNotRedraw();dimensionsInvalidateOnlyChangedGeometry();retryReusesOnlyUnexpiredContent();
            geometryCannotCancelAnOverdueDeadline();screenOffCancelsDisplayDeadline();
            backgroundPublicationDuringDrawIsNotLost();
            if(!failures.isEmpty()){
                for(String failure:failures)System.err.println("FAIL: "+failure);
                throw new AssertionError(failures.size()+" of "+checks+" floating lifecycle checks failed");
            }
            System.out.println("PASS: "+checks+" floating lifecycle checks (actual service; fake Android; no device/account/network)");
        }finally{if(service!=null)service.onDestroy();Handler.reset();}
    }
}
