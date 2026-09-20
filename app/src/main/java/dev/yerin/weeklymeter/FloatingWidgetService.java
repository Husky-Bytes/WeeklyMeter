package dev.yerin.weeklymeter;

import android.app.KeyguardManager;
import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.ImageView;
import android.widget.Toast;

/** User-started, on-device overlay. Rendering observes only the existing display cache. */
public final class FloatingWidgetService extends Service {
    public static final String ACTION_SHOW="dev.yerin.weeklymeter.SHOW_FLOATING_WIDGET";
    public static final String ACTION_HIDE="dev.yerin.weeklymeter.HIDE_FLOATING_WIDGET";
    private static final String CHANNEL="floating_widget",X="position_x_fraction",Y="position_y_fraction";
    private static final int NOTIFICATION=22004;
    private static final long RESUME_RETRY_MS=500,RESUME_TIMEOUT_MS=15_000;
    private static volatile FloatingWidgetService instance;
    private static volatile boolean showing,active;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable redraw=this::paint;
    private final Runnable invalidateContent=this::markContentDirty;
    private final Runnable displayExpiry=()->{contentDirty=true;paint();};
    private final Runnable resume=this::resumeWindow;
    private final Runnable longPress=this::handleLongPress;
    private final SharedPreferences.OnSharedPreferenceChangeListener changed=this::preferenceChanged;
    private final AppOpsManager.OnOpChangedListener permissionChanged=(operation,packageName)->main.post(()->{if(!allowed(this))close();});
    private final BroadcastReceiver screen=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            String action=intent.getAction();
            if(Intent.ACTION_SCREEN_OFF.equals(action)){screenOff=true;cancelResume();main.removeCallbacks(redraw);detach();}
            else if(Intent.ACTION_SCREEN_ON.equals(action)||Intent.ACTION_USER_PRESENT.equals(action)){screenOff=false;beginResume();queuePaint();}
            else if(Intent.ACTION_TIME_CHANGED.equals(action)||Intent.ACTION_TIMEZONE_CHANGED.equals(action))queuePaint();
        }
    };
    private WindowManager window;
    private ImageView image;
    private WindowManager.LayoutParams layout;
    private FloatingGeometry.Frame frame;
    private FloatingGesture gesture;
    private Rect safeArea;
    private SharedPreferences[] watched;
    private AppOpsManager appOps;
    private boolean attached,foreground,receiverRegistered,screenOff,closing,destroyed;
    private boolean contentDirty=true;
    private WidgetRenderer.Result cachedRender;
    private Usage cachedUsage;
    private int renderedWidth,renderedHeight;
    private float renderedDensity;
    private long renderedAt,renderedUntil=Long.MAX_VALUE;
    private String renderedFeedback="none";
    private long resumeUntil;
    private int downWindowX,downWindowY;
    private float downTouchX,downTouchY;

    public static boolean isShowing(){return showing;}
    /** A user-started session stays active while its window is hidden on the lock screen. */
    public static boolean isActive(){return active;}
    private static void setActive(boolean value){if(active!=value){active=value;AppSignals.changed();}}
    private static void setShowing(boolean value){if(showing!=value){showing=value;AppSignals.changed();}}
    public static void show(Context context){
        Context c=context.getApplicationContext();
        if(!allowed(c)){message(c,"앱에서 다른 앱 위에 표시를 허용해 주세요.","Allow display over other apps in WeeklyMeter.");return;}
        try{c.startForegroundService(new Intent(c,FloatingWidgetService.class).setAction(ACTION_SHOW));}
        catch(RuntimeException blocked){message(c,"플로팅 위젯을 열지 못했습니다. 앱에서 다시 시도해 주세요.","Could not open the floating widget. Try again in the app.");}
    }
    public static void hide(Context context){context.getApplicationContext().stopService(new Intent(context,FloatingWidgetService.class));}
    public static void repaint(Context context){FloatingWidgetService active=instance;if(active!=null)active.queuePaint();}
    private static boolean allowed(Context c){try{return Settings.canDrawOverlays(c);}catch(RuntimeException unavailable){return false;}}
    private static void message(Context c,String ko,String en){new Handler(Looper.getMainLooper()).post(()->Toast.makeText(c,Texts.t(c,ko,en),Toast.LENGTH_SHORT).show());}

    @Override public void onCreate(){
        super.onCreate();instance=this;
        window=getSystemService(WindowManager.class);
        gesture=new FloatingGesture(ViewConfiguration.get(this).getScaledTouchSlop());
        watched=new SharedPreferences[]{Store.prefs(this),getSharedPreferences("floating_style",MODE_PRIVATE),FloatingPreferences.prefs(this),getSharedPreferences("language_settings",MODE_PRIVATE)};
        for(SharedPreferences prefs:watched)prefs.registerOnSharedPreferenceChangeListener(changed);
        IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_TIME_CHANGED);filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        try{if(Build.VERSION.SDK_INT>=33)registerReceiver(screen,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(screen,filter);receiverRegistered=true;}
        catch(RuntimeException unavailable){close();}
        try{appOps=getSystemService(AppOpsManager.class);if(appOps!=null)appOps.startWatchingMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,getPackageName(),permissionChanged);}
        catch(RuntimeException unavailable){appOps=null;}
    }
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&ACTION_HIDE.equals(intent.getAction())){close();return START_NOT_STICKY;}
        // A null intent is Android restoring a previously started sticky session.
        // stopService/stopSelf ends that session; no boot or saved auto-start flag.
        if((intent!=null&&!ACTION_SHOW.equals(intent.getAction()))||closing||!allowed(this)){close();return START_NOT_STICKY;}
        try{enterForeground();screenOff=false;beginResume();paint();}
        catch(RuntimeException blocked){message(this,"플로팅 위젯을 표시하지 못했습니다.","Could not display the floating widget.");close();}
        return closing?START_NOT_STICKY:START_STICKY;
    }
    private void enterForeground(){
        if(foreground)return;
        NotificationManager manager=getSystemService(NotificationManager.class);
        if(manager==null)throw new IllegalStateException("Notification service unavailable");
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,Texts.t(this,"플로팅 위젯","Floating widget"),NotificationManager.IMPORTANCE_LOW));
        if(Build.VERSION.SDK_INT>=34)startForeground(NOTIFICATION,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(NOTIFICATION,notification());
        foreground=true;setActive(true);ensureSchedule();
    }
    private Notification notification(){
        int flags=PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE;
        PendingIntent settings=PendingIntent.getActivity(this,22004,new Intent(this,WidgetStyleSettingsActivity.class).putExtra(WidgetStyleSettingsActivity.EXTRA_FLOATING,true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),flags);
        PendingIntent dismiss=PendingIntent.getService(this,22005,new Intent(this,FloatingWidgetService.class).setAction(ACTION_HIDE),flags);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_meter)
            .setContentTitle(Texts.t(this,"WeeklyMeter 플로팅 위젯","WeeklyMeter floating widget"))
            .setContentText(Texts.t(this,"누르면 설정 · 위젯을 길게 누르면 닫기","Tap for settings · hold the widget to close"))
            .setContentIntent(settings).addAction(new Notification.Action.Builder(null,Texts.t(this,"닫기","Close"),dismiss).build())
            .setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE).setVisibility(Notification.VISIBILITY_PRIVATE).build();
    }
    private void preferenceChanged(SharedPreferences prefs,String key){
        if(watched==null)return;
        if(key==null){queuePaint();return;}
        if(prefs==watched[0]){
            if("meters".equals(key)||"selected".equals(key)||"connected".equals(key)||"error".equals(key)||"requested".equals(key))queuePaint();
        }else if(prefs==watched[2]){
            // Drag already moved the input window; persisting its position does not
            // change pixels. Automatic-refresh settings likewise do not draw here.
            if(FloatingPreferences.WIDTH.equals(key)||FloatingPreferences.HEIGHT.equals(key))queueGeometry();
        }else queuePaint();
    }
    private void markContentDirty(){if(!destroyed&&!closing){contentDirty=true;queueGeometry();}}
    private void queuePaint(){
        // A worker can publish newer usage while the main thread draws an older
        // snapshot. Serialize invalidation after that draw so its cleanup cannot
        // clear the new dirty flag or remove the pending content update.
        main.removeCallbacks(invalidateContent);main.post(invalidateContent);
    }
    private void queueGeometry(){if(!destroyed&&!closing){main.removeCallbacks(redraw);main.post(redraw);}}
    private void beginResume(){
        if(destroyed||closing||!foreground)return;
        resumeUntil=SystemClock.elapsedRealtime()+RESUME_TIMEOUT_MS;scheduleResume();
    }
    private boolean scheduleResume(){
        main.removeCallbacks(resume);
        long remaining=resumeUntil-SystemClock.elapsedRealtime();
        if(destroyed||closing||!foreground||screenOff||remaining<=0)return false;
        main.postDelayed(resume,Math.min(RESUME_RETRY_MS,remaining));return true;
    }
    private void cancelResume(){main.removeCallbacks(resume);resumeUntil=0;}
    private void resumeWindow(){
        if(destroyed||closing||!foreground||screenOff||resumeUntil==0)return;
        // Wake/unlock broadcasts can arrive before power/keyguard/window state settles.
        // Recheck locally for a bounded time; screen-off cancels retries. No usage fetch.
        paint();
        if(!attached&&!scheduleResume())cancelResume();
    }
    private boolean privateScreen(){
        if(screenOff)return true;
        try{KeyguardManager keyguard=getSystemService(KeyguardManager.class);PowerManager power=getSystemService(PowerManager.class);
            return keyguard==null||power==null||keyguard.isKeyguardLocked()||!power.isInteractive();
        }catch(RuntimeException unavailable){return true;}
    }
    private void paint(){
        if(destroyed||closing||!foreground)return;
        if(!allowed(this)){close();return;}
        if(privateScreen()){detach();return;}
        try{
            if(window==null)throw new IllegalStateException("Window service unavailable");
            Rect bounds=screenBounds();float density=Math.max(.5f,getResources().getDisplayMetrics().density);
            int wantedWidth=Math.max(1,Math.round(FloatingPreferences.widthDp(this)*density));
            int wantedHeight=Math.max(1,Math.round(FloatingPreferences.heightDp(this)*density));
            boolean resized=frame==null||safeArea==null||!bounds.equals(safeArea)||frame.width!=Math.min(wantedWidth,bounds.width())||frame.height!=Math.min(wantedHeight,bounds.height());
            if(resized){
                gesture.cancel();main.removeCallbacks(longPress);
                frame=FloatingGeometry.fit(bounds.left,bounds.top,bounds.right,bounds.bottom,wantedWidth,wantedHeight,position(X,1),position(Y,.35f));
            }else frame=FloatingGeometry.move(frame,bounds.left,bounds.top,bounds.right,bounds.bottom,frame.x,frame.y);
            safeArea=bounds;
            if(image==null){
                image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_XY);image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
                image.setOnClickListener(view->refresh());image.setOnLongClickListener(view->{close();return true;});image.setOnTouchListener(this::touch);
            }
            RefreshFeedback.Snapshot feedback=RefreshFeedback.snapshot(this,true);
            String feedbackState=feedback.visible?feedback.state:"none";
            long now=System.currentTimeMillis();
            boolean changed=contentDirty||cachedRender==null||now<renderedAt||now>=renderedUntil||renderedWidth!=frame.width||renderedHeight!=frame.height||renderedDensity!=density||!renderedFeedback.equals(feedbackState);
            if(changed){
                cachedUsage=Store.selected(this);
                cachedRender=WidgetRenderer.render(this,cachedUsage,FloatingPreferences.style(this),frame.width/density,frame.height/density,feedbackState);
                renderedWidth=frame.width;renderedHeight=frame.height;renderedDensity=density;renderedFeedback=feedbackState;contentDirty=false;
                renderedAt=now;long delay=cachedUsage==null?-1:DisplayExpiry.nextDelay(now,cachedUsage.fetchedAt,cachedUsage.resetsAt);
                renderedUntil=delay>0&&now<=Long.MAX_VALUE-delay?now+delay:Long.MAX_VALUE;
            }
            WidgetRenderer.Result rendered=cachedRender;
            if(changed&&!hasVisiblePixels(rendered.bitmap)){
                // Pixel transparency must also remove the input window. A transparent
                // Bitmap alone still intercepts touches in WindowManager's rectangle.
                cancelResume();main.removeCallbacks(redraw);detach();
                // This fresh bitmap was never handed to ImageView or its renderer.
                rendered.bitmap.recycle();cachedRender=null;RefreshFeedback.published(true,feedback);return;
            }
            // ImageView releases the previous bitmap reference. Do not recycle a bitmap
            // while Android's hardware renderer can still be drawing the prior frame.
            if(changed||!attached){
                image.setImageBitmap(rendered.bitmap);
                image.setContentDescription(rendered.accessibility+". "+Texts.t(this,"누르면 조회, 끌어서 이동, 길게 누르면 닫기","Tap to refresh, drag to move, hold to close"));
            }
            if(layout==null){
                // Usage-only overlays must not block screenshots of the app underneath.
                layout=new WindowManager.LayoutParams(frame.width,frame.height,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
                layout.gravity=Gravity.TOP|Gravity.LEFT;
                if(Build.VERSION.SDK_INT>=28)layout.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
            place();
            if(!attached){window.addView(image,layout);attached=true;setShowing(true);}else window.updateViewLayout(image,layout);
            cancelResume();
            main.removeCallbacks(redraw);
            RefreshFeedback.published(true,feedback);
            scheduleDisplayExpiry();
        }catch(RuntimeException failed){
            detach();
            if(resumeUntil==0)beginResume();
            if(!scheduleResume()){
                message(this,"플로팅 위젯을 다시 표시하지 못했습니다. 앱에서 다시 열어 주세요.","Could not restore the floating widget. Reopen it in the app.");close();
            }
        }
    }
    private void scheduleDisplayExpiry(){
        main.removeCallbacks(displayExpiry);
        if(!attached||renderedUntil==Long.MAX_VALUE)return;
        main.postDelayed(displayExpiry,Math.max(1,renderedUntil-System.currentTimeMillis()));
    }
    private static boolean hasVisiblePixels(Bitmap bitmap){
        // Inspect the bounded renderer output, not a second copy of style/date/logo
        // rules. Reuse one row and stop at the first nonzero alpha, including alpha 1.
        int width=bitmap.getWidth();int[] row=new int[width];
        for(int y=0;y<bitmap.getHeight();y++){
            bitmap.getPixels(row,0,width,0,y,width,1);
            for(int pixel:row)if((pixel>>>24)!=0)return true;
        }
        return false;
    }
    private float position(String key,float fallback){try{return FloatingPreferences.prefs(this).getFloat(key,fallback);}catch(ClassCastException invalid){return fallback;}}
    private Rect screenBounds(){
        if(Build.VERSION.SDK_INT>=30){
            WindowMetrics metrics=window.getCurrentWindowMetrics();Rect bounds=metrics.getBounds();
            Insets insets=metrics.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            return validBounds(insets.left,insets.top,bounds.width()-insets.right,bounds.height()-insets.bottom);
        }
        DisplayMetrics metrics=new DisplayMetrics();window.getDefaultDisplay().getRealMetrics(metrics);
        android.graphics.Point usable=new android.graphics.Point();window.getDefaultDisplay().getSize(usable);
        int sideNavigation=Math.max(0,metrics.widthPixels-usable.x),bottomNavigation=Math.max(0,metrics.heightPixels-usable.y);
        int left=window.getDefaultDisplay().getRotation()==android.view.Surface.ROTATION_270?sideNavigation:0;
        return validBounds(left,dimension("status_bar_height"),metrics.widthPixels-(sideNavigation-left),metrics.heightPixels-bottomNavigation);
    }
    private int dimension(String name){int id=getResources().getIdentifier(name,"dimen","android");return id==0?0:getResources().getDimensionPixelSize(id);}
    private static Rect validBounds(int left,int top,int right,int bottom){return new Rect(Math.max(0,left),Math.max(0,top),Math.max(Math.max(0,left)+1,right),Math.max(Math.max(0,top)+1,bottom));}
    private void place(){layout.x=frame.x;layout.y=frame.y;layout.width=frame.width;layout.height=frame.height;}
    private boolean touch(View view,MotionEvent event){
        if(closing||destroyed||!attached)return true;
        if(privateScreen()||!allowed(this)){gesture.cancel();main.removeCallbacks(longPress);queuePaint();return true;}
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                gesture.down(event.getRawX(),event.getRawY());downTouchX=event.getRawX();downTouchY=event.getRawY();downWindowX=frame.x;downWindowY=frame.y;
                main.removeCallbacks(longPress);main.postDelayed(longPress,FloatingGesture.LONG_PRESS_MS);return true;
            case MotionEvent.ACTION_MOVE:
                if(event.getPointerCount()!=1){cancelTouch();return true;}
                if(gesture.move(event.getRawX(),event.getRawY())){
                    main.removeCallbacks(longPress);
                    moveWindow(event.getRawX(),event.getRawY());
                }return true;
            case MotionEvent.ACTION_UP:
                main.removeCallbacks(longPress);
                // Include the release coordinate even when Android coalesces MOVE events.
                gesture.move(event.getRawX(),event.getRawY());boolean moved=gesture.dragging();boolean tap=gesture.release();
                if(moved){moveWindow(event.getRawX(),event.getRawY());if(!closing)savePosition();}else if(tap)view.performClick();return true;
            case MotionEvent.ACTION_POINTER_DOWN:case MotionEvent.ACTION_CANCEL:cancelTouch();return true;
            default:return true;
        }
    }
    private void cancelTouch(){gesture.cancel();main.removeCallbacks(longPress);}
    private void handleLongPress(){if(gesture!=null&&gesture.longPress()&&image!=null)image.performLongClick();}
    private void moveWindow(float x,float y){
        frame=FloatingGeometry.move(frame,safeArea.left,safeArea.top,safeArea.right,safeArea.bottom,downWindowX+x-downTouchX,downWindowY+y-downTouchY);
        place();try{window.updateViewLayout(image,layout);}catch(RuntimeException failed){close();}
    }
    private void savePosition(){
        if(frame==null||safeArea==null)return;
        FloatingPreferences.prefs(this).edit().putFloat(X,FloatingGeometry.position(frame.x,safeArea.left,safeArea.right,frame.width))
            .putFloat(Y,FloatingGeometry.position(frame.y,safeArea.top,safeArea.bottom,frame.height)).apply();
    }
    private void refresh(){
        if(privateScreen()||!allowed(this)){queuePaint();return;}
        try{startForegroundService(new Intent(this,WidgetRefreshService.class).setAction(WidgetRefreshService.ACTION_REFRESH));}
        catch(RuntimeException blocked){message(this,"조회를 시작하지 못했습니다. 앱에서 확인해 주세요.","Could not start refreshing. Check the app.");}
    }
    private void detach(){
        main.removeCallbacks(displayExpiry);
        cancelTouch();
        if(attached&&window!=null&&image!=null){try{window.removeViewImmediate(image);}catch(RuntimeException ignored){}}
        attached=false;setShowing(false);
        if(image!=null)image.setImageDrawable(null);
    }
    private void ensureSchedule(){try{Scheduler.ensure(this);}catch(RuntimeException unavailable){}}
    private void close(){
        if(closing)return;closing=true;setActive(false);cancelResume();main.removeCallbacksAndMessages(null);detach();cachedRender=null;cachedUsage=null;
        if(foreground){stopForeground(STOP_FOREGROUND_REMOVE);foreground=false;}
        ensureSchedule();stopSelf();
    }
    @Override public void onConfigurationChanged(Configuration configuration){super.onConfigurationChanged(configuration);queuePaint();}
    @Override public void onDestroy(){
        destroyed=true;if(instance==this)setActive(false);cancelResume();main.removeCallbacksAndMessages(null);detach();
        if(receiverRegistered){try{unregisterReceiver(screen);}catch(RuntimeException ignored){}receiverRegistered=false;}
        if(appOps!=null){try{appOps.stopWatchingMode(permissionChanged);}catch(RuntimeException ignored){}appOps=null;}
        if(watched!=null){for(SharedPreferences prefs:watched)prefs.unregisterOnSharedPreferenceChangeListener(changed);watched=null;}
        image=null;layout=null;frame=null;window=null;cachedRender=null;cachedUsage=null;
        if(instance==this){instance=null;setShowing(false);}
        if(foreground){stopForeground(STOP_FOREGROUND_REMOVE);foreground=false;}
        ensureSchedule();
        super.onDestroy();
    }
}
