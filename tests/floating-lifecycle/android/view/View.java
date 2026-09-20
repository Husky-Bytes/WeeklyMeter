package android.view;
public class View {
    public static final int IMPORTANT_FOR_ACCESSIBILITY_YES=1;
    public interface OnClickListener { void onClick(View view); }
    public interface OnLongClickListener { boolean onLongClick(View view); }
    public interface OnTouchListener { boolean onTouch(View view,MotionEvent event); }
    private OnClickListener click;private OnLongClickListener longClick;private OnTouchListener touch;
    public void setImportantForAccessibility(int value){}
    public void setOnClickListener(OnClickListener listener){click=listener;}
    public void setOnLongClickListener(OnLongClickListener listener){longClick=listener;}
    public void setOnTouchListener(OnTouchListener listener){touch=listener;}
    public boolean performClick(){if(click==null)return false;click.onClick(this);return true;}
    public boolean performLongClick(){return longClick!=null&&longClick.onLongClick(this);}
    public boolean dispatchTouchEvent(MotionEvent event){return touch!=null&&touch.onTouch(this,event);}
    public void setContentDescription(String value){}
}
