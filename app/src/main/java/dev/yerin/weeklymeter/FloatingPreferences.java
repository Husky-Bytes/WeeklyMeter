package dev.yerin.weeklymeter;

import android.content.Context;
import android.content.SharedPreferences;

/** Floating appearance and geometry never read or overwrite home-widget preferences. */
final class FloatingPreferences {
    static final String PREFS="floating_widget",STYLE_PREFS="floating_style";
    static final String WIDTH="width_dp",HEIGHT="height_dp";
    static final int DEFAULT_WIDTH_DP=128,DEFAULT_HEIGHT_DP=96,MIN_WIDTH_DP=48,MAX_WIDTH_DP=360,MIN_HEIGHT_DP=48,MAX_HEIGHT_DP=300;
    private FloatingPreferences(){}
    static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    static WidgetStyle style(Context c){return WidgetAppearance.load(c,STYLE_PREFS);}
    static void saveStyle(Context c,WidgetStyle value){WidgetAppearance.save(c,STYLE_PREFS,value);}
    static int widthDp(Context c){return dimension(c,WIDTH,DEFAULT_WIDTH_DP,MIN_WIDTH_DP,MAX_WIDTH_DP);}
    static int heightDp(Context c){return dimension(c,HEIGHT,DEFAULT_HEIGHT_DP,MIN_HEIGHT_DP,MAX_HEIGHT_DP);}
    static void saveSize(Context c,int width,int height){prefs(c).edit().putInt(WIDTH,clamp(width,MIN_WIDTH_DP,MAX_WIDTH_DP)).putInt(HEIGHT,clamp(height,MIN_HEIGHT_DP,MAX_HEIGHT_DP)).apply();}
    private static int dimension(Context c,String key,int fallback,int min,int max){try{return clamp(prefs(c).getInt(key,fallback),min,max);}catch(RuntimeException ignored){return fallback;}}
    private static int clamp(int value,int min,int max){return Math.max(min,Math.min(max,value));}
}
