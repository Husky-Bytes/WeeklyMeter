package android.view;
import android.graphics.Insets;
public final class WindowInsets {
    public Insets getInsetsIgnoringVisibility(int mask){return new Insets(0,90,0,90);}
    public static final class Type { public static int systemBars(){return 1;}public static int displayCutout(){return 2;} }
}
