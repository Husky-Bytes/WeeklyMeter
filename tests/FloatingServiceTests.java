package dev.yerin.weeklymeter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Pure geometry/gesture tests plus source-contract checks; not an Android UI emulator. */
public final class FloatingServiceTests {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        FloatingGeometry.Frame frame=FloatingGeometry.fit(20,32,400,800,128,96,1,.35f);
        check(frame.x==272&&frame.y==267&&frame.width==128&&frame.height==96,"default safe insets");
        FloatingGeometry.Frame oversized=FloatingGeometry.fit(20,32,400,800,1000,1000,1,1);
        check(oversized.x==20&&oversized.y==32&&oversized.width==380&&oversized.height==768,"oversize clamped to usable screen");
        FloatingGeometry.Frame invalid=FloatingGeometry.fit(0,0,400,800,-10,0,Float.NaN,Float.POSITIVE_INFINITY);
        check(invalid.width==1&&invalid.height==1&&invalid.x==0&&invalid.y==799,"invalid inputs bounded");
        check(FloatingGeometry.position(500,20,400,380)==0,"full width has no available position range");
        check(FloatingGeometry.position(-500,20,400,128)==0,"left clamp fraction");
        check(FloatingGeometry.position(500,20,400,128)==1,"right clamp fraction");
        for(int width=48;width<=360;width+=13){
            for(int height=48;height<=300;height+=17){
                FloatingGeometry.Frame portrait=FloatingGeometry.fit(0,60,1080,2240,width*3,height*3,.75f,.9f);
                check(portrait.width>0&&portrait.height>0&&portrait.x>=0&&portrait.y>=60&&portrait.x+portrait.width<=1080&&portrait.y+portrait.height<=2240,"portrait stays in bounds");
                FloatingGeometry.Frame landscape=FloatingGeometry.fit(100,30,2240,1080,portrait.width,portrait.height,FloatingGeometry.position(portrait.x,0,1080,portrait.width),FloatingGeometry.position(portrait.y,60,2240,portrait.height));
                check(landscape.x>=100&&landscape.y>=30&&landscape.x+landscape.width<=2240&&landscape.y+landscape.height<=1080,"rotation stays in bounds");
                FloatingGeometry.Frame moved=FloatingGeometry.move(portrait,0,60,1080,2240,-10000,10000);
                check(moved.x==0&&moved.y+moved.height==2240,"drag edge clamp");
            }
        }
        FloatingGesture gesture=new FloatingGesture(8);
        check(FloatingGesture.LONG_PRESS_MS==600,"close hold time");
        gesture.down(100,100);check(gesture.release(),"stationary tap refreshes");check(!gesture.release(),"duplicate release ignored");
        gesture.down(100,100);check(!gesture.move(104,104),"slop does not begin drag");check(gesture.release(),"small move still taps");
        gesture.down(100,100);check(gesture.move(109,100),"larger move drags");check(gesture.dragging(),"drag state");check(!gesture.release(),"drag never refreshes");
        gesture.down(100,100);gesture.move(109,100);gesture.move(100,100);check(!gesture.release(),"returning drag is not a tap");
        gesture.down(100,100);check(gesture.longPress(),"hold closes");check(!gesture.release(),"close never refreshes");check(!gesture.longPress(),"duplicate hold ignored");
        gesture.down(100,100);gesture.move(109,100);check(!gesture.longPress(),"drag cancels long press");check(!gesture.release(),"cancelled hold never refreshes");
        gesture.down(100,100);gesture.cancel();check(!gesture.release()&&!gesture.longPress()&&!gesture.move(900,900),"cancel/multitouch does not trigger gesture");
        if(args.length>0){
            String source=new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8);
            check(source.contains("TYPE_APPLICATION_OVERLAY")&&source.contains("FLAG_NOT_FOCUSABLE")&&source.contains("FLAG_NOT_TOUCH_MODAL"),"bounded unfocused overlay");
            int layoutStart=source.indexOf("layout=new WindowManager.LayoutParams(");
            int layoutEnd=source.indexOf(");",layoutStart);
            check(layoutStart>=0&&layoutEnd>layoutStart,"overlay window flag declaration is inspected");
            String layout=source.substring(layoutStart,layoutEnd);
            check(!layout.contains("FLAG_SECURE"),"floating display does not request secure-window screenshot blocking");
            check(layout.contains("FLAG_NOT_FOCUSABLE")&&layout.contains("FLAG_NOT_TOUCH_MODAL")&&layout.contains("FLAG_LAYOUT_IN_SCREEN"),"screenshot change preserves overlay focus, outside-touch and placement flags");
            check(!layout.contains("FLAG_NOT_TOUCHABLE"),"floating tap, drag and hold remain touchable");
            String mainSource=new String(Files.readAllBytes(Paths.get(args[0]).resolveSibling("MainActivity.java")),StandardCharsets.UTF_8).replaceAll("\\s+","");
            check(mainSource.contains("getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE)"),"main/account screen retains secure-window capture protection");
            check(!mainSource.contains("clearFlags(WindowManager.LayoutParams.FLAG_SECURE)"),"floating screenshot change does not clear main-screen security");
            check(source.contains("Settings.canDrawOverlays")&&source.contains("OPSTR_SYSTEM_ALERT_WINDOW"),"permission check plus revocation observer");
            check(source.contains("FOREGROUND_SERVICE_TYPE_SPECIAL_USE")&&source.contains("START_NOT_STICKY")&&!source.contains("START_STICKY"),"user-controlled foreground lifetime");
            check(source.contains("Intent.ACTION_SCREEN_OFF")&&source.contains("isKeyguardLocked()")&&source.contains("!power.isInteractive()"),"privacy while screen locked or off");
            check(source.contains("WidgetRefreshService.ACTION_REFRESH")&&!source.contains("ACTION_HOME_TAP"),"floating tap cannot trigger home triple tap");
            check(source.contains("FloatingPreferences.style(this)")&&source.contains("RefreshFeedback.snapshot(this,true)"),"independent floating style and feedback");
            check(source.contains("setOnLongClickListener(view->{close();return true;})"),"long click closes overlay");
            check(source.contains("PendingIntent.getService")&&source.contains("setAction(ACTION_HIDE)"),"notification provides close action");
            check(source.contains("WidgetStyleSettingsActivity.EXTRA_FLOATING,true"),"notification opens floating settings");
            check(source.contains("unregisterOnSharedPreferenceChangeListener")&&source.contains("unregisterReceiver")&&source.contains("stopWatchingMode")&&source.contains("removeCallbacksAndMessages"),"destroy cleans observers and callbacks");
            check(!source.contains("repo.sync")&&!source.contains("new Repo")&&!source.contains("HttpURLConnection")&&!source.contains("AlarmManager"),"show and repaint never query network or schedule alarms");
            check(source.contains("boolean isActive(){return active;}")&&source.contains("foreground=true;active=true;ensureSchedule()"),"active foreground session is separate from visible attachment");
            String detach=source.substring(source.indexOf("private void detach()"),source.indexOf("private void ensureSchedule()"));
            check(!detach.contains("active=false")&&!detach.contains("ensureSchedule()"),"screen-off hiding does not reset periodic scheduling");
            check(source.contains("closing=true;active=false;")&&source.contains("destroyed=true;if(instance==this)active=false;"),"close and destroy clear active session eligibility");
        }
        System.out.println("Floating overlay geometry/gesture/contracts: "+checks+" checks passed");
    }
}
