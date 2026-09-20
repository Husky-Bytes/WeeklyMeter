package android.widget;
public final class ImageView extends android.view.View {
    public Object bitmap;
    public enum ScaleType { FIT_XY }
    public ImageView(android.content.Context context){}
    public void setScaleType(ScaleType type){}
    public void setImageBitmap(Object bitmap){this.bitmap=bitmap;}
    public void setImageDrawable(Object drawable){bitmap=drawable;}
}
