package android.view;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
public final class WindowManager {
    public int addCalls,updateCalls,removeCalls,failAdds,failUpdates;
    public View lastAdded;
    private final Set<View> attached=Collections.newSetFromMap(new IdentityHashMap<View,Boolean>());
    public void addView(View view,LayoutParams params){
        addCalls++;
        if(failAdds>0){failAdds--;throw new IllegalStateException("Synthetic transient host add failure");}
        if(!attached.add(view))throw new IllegalStateException("View already attached");
        lastAdded=view;
    }
    public void updateViewLayout(View view,LayoutParams params){
        updateCalls++;
        if(failUpdates>0){failUpdates--;throw new IllegalStateException("Synthetic transient host update failure");}
        if(!attached.contains(view))throw new IllegalStateException("View not attached");
    }
    public void removeViewImmediate(View view){removeCalls++;if(!attached.remove(view))throw new IllegalStateException("View not attached");}
    public int attachedCount(){return attached.size();}
    public void reset(){addCalls=updateCalls=removeCalls=failAdds=failUpdates=0;lastAdded=null;attached.clear();}
    public WindowMetrics getCurrentWindowMetrics(){return new WindowMetrics();}
    public Display getDefaultDisplay(){return new Display();}
    public static final class LayoutParams {
        public static final int TYPE_APPLICATION_OVERLAY=2038,FLAG_NOT_FOCUSABLE=8,FLAG_NOT_TOUCH_MODAL=32,FLAG_LAYOUT_IN_SCREEN=256,LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES=1;
        public int x,y,width,height,gravity,layoutInDisplayCutoutMode;
        public LayoutParams(int width,int height,int type,int flags,int format){this.width=width;this.height=height;}
    }
}
