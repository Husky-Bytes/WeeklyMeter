package android.content.res;
import android.util.DisplayMetrics;
public final class Resources {
    private final DisplayMetrics metrics=new DisplayMetrics();
    public DisplayMetrics getDisplayMetrics(){return metrics;}
    public int getIdentifier(String name,String kind,String pkg){return 0;}
    public int getDimensionPixelSize(int id){return 0;}
}
