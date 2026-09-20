package dev.yerin.weeklymeter;
import android.content.Context;
import android.content.SharedPreferences;
/** Only collaborators are synthetic: the service, gestures and geometry are production code. */
final class Store {
    static SharedPreferences prefs(Context context){return context.getSharedPreferences("weeklymeter",0);}
    static Usage usage;
    static Usage selected(Context context){return usage;}
}
final class Usage {final long fetchedAt,resetsAt;Usage(long fetchedAt,long resetsAt){this.fetchedAt=fetchedAt;this.resetsAt=resetsAt;}}
final class FloatingPreferences {
    static final String WIDTH="width_dp",HEIGHT="height_dp";
    static SharedPreferences prefs(Context context){return context.getSharedPreferences("floating_widget",0);}
    static int widthDp(Context context){return prefs(context).getInt(WIDTH,100);}
    static int heightDp(Context context){return prefs(context).getInt(HEIGHT,70);}
    static Object style(Context context){return new Object();}
}
final class RefreshFeedback {
    static long until;
    static void show(Context context,long duration){until=android.os.SystemClock.elapsedRealtime()+duration;new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->FloatingWidgetService.repaint(context),duration);}
    static void published(boolean floating,Snapshot snapshot){}
    static Snapshot snapshot(Context context,boolean floating){Snapshot result=new Snapshot();result.visible=until>android.os.SystemClock.elapsedRealtime();result.state=result.visible?"success":"none";result.expiresAt=until;return result;}
    static final class Snapshot { boolean visible;String state="none";long expiresAt; }
}
final class WidgetRenderer {
    static int renders;
    static int bodyPixel=0xff123456;
    static float lastWidth,lastHeight;
    static Object lastUsage;
    static Runnable duringRender=()->{};
    static android.graphics.Bitmap lastBitmap;
    static Result render(Context context,Object usage,Object style,float width,float height,String state){
        renders++;lastWidth=width;lastHeight=height;lastUsage=usage;
        Runnable callback=duringRender;duringRender=()->{};callback.run();
        SharedPreferences fixture=context.getSharedPreferences("floating_style",0);
        int[] pixels=new int[12];java.util.Arrays.fill(pixels,0x00123456);
        if(!fixture.getBoolean("synthetic_all_transparent",false)){
            if(!fixture.getBoolean("synthetic_body_hidden",false))pixels[11]=bodyPixel;
            if(!"none".equals(state))pixels[5]=0xff00ff00;
        }
        return new Result(lastBitmap=new android.graphics.Bitmap(4,3,pixels));
    }
    static final class Result { final android.graphics.Bitmap bitmap;final String accessibility="88%";Result(android.graphics.Bitmap bitmap){this.bitmap=bitmap;} }
}
final class Texts { static String t(Context context,String ko,String en){return en;} }
final class Scheduler { static int calls;static void ensure(Context context){calls++;} }
final class WidgetStyleSettingsActivity { static final String EXTRA_FLOATING="floating"; }
final class WidgetRefreshService { static final String ACTION_REFRESH="refresh"; }
final class R { static final class drawable { static final int ic_meter=1; } }
