package dev.yerin.weeklymeter;

import android.content.Context;

/** Overlay permission/window behavior belongs to the separate overlay harness. */
final class FloatingWidgetService {
    static int shows;
    static boolean showing;
    static boolean isShowing(){return showing;}
    static boolean isActive(){return showing;}
    static void show(Context c){shows++;showing=true;Context.EVENTS.add("floating");}
    static void hide(Context c){showing=false;}
}
