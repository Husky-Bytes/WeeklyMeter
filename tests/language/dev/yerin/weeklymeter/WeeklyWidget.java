package dev.yerin.weeklymeter;
import android.content.Context;
final class WeeklyWidget {static int renders;static boolean fail;static void renderAll(Context c){renders++;if(fail)throw new IllegalStateException("synthetic widget host failure");}}
