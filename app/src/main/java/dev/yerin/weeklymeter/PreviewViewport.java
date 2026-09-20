package dev.yerin.weeklymeter;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

/** A clipped, two-axis pannable viewport. Its single child's requested pixels stay exact. */
final class PreviewViewport extends ViewGroup {
    private final int slop;
    private float lastX,lastY,downX,downY;
    private boolean dragging;
    PreviewViewport(Context context){super(context);slop=ViewConfiguration.get(context).getScaledTouchSlop();setClipChildren(true);setClipToPadding(true);setHorizontalScrollBarEnabled(true);setVerticalScrollBarEnabled(true);setWillNotDraw(false);}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=MeasureSpec.getSize(widthSpec),height=MeasureSpec.getSize(heightSpec);
        if(getChildCount()>0){View child=getChildAt(0);LayoutParams lp=child.getLayoutParams();child.measure(MeasureSpec.makeMeasureSpec(Math.max(1,lp.width),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.max(1,lp.height),MeasureSpec.EXACTLY));}
        setMeasuredDimension(width,height);
    }
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
        if(getChildCount()==0)return;View child=getChildAt(0);int x=Math.max(0,(getWidth()-child.getMeasuredWidth())/2),y=Math.max(0,(getHeight()-child.getMeasuredHeight())/2);
        child.layout(x,y,x+child.getMeasuredWidth(),y+child.getMeasuredHeight());scrollTo(getScrollX(),getScrollY());
    }
    @Override public void scrollTo(int x,int y){super.scrollTo(PreviewGeometry.pan(x,contentWidth(),getWidth()),PreviewGeometry.pan(y,contentHeight(),getHeight()));}
    private int contentWidth(){return getChildCount()==0?0:getChildAt(0).getMeasuredWidth();}
    private int contentHeight(){return getChildCount()==0?0:getChildAt(0).getMeasuredHeight();}
    @Override protected int computeHorizontalScrollRange(){return Math.max(getWidth(),contentWidth());}
    @Override protected int computeVerticalScrollRange(){return Math.max(getHeight(),contentHeight());}
    @Override public boolean onInterceptTouchEvent(MotionEvent event){
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:dragging=false;downX=lastX=event.getX();downY=lastY=event.getY();return false;
            case MotionEvent.ACTION_MOVE:
                if(event.getPointerCount()!=1)return false;
                if(Math.abs(event.getX()-downX)>slop||Math.abs(event.getY()-downY)>slop){dragging=true;return true;}break;
            case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:dragging=false;break;
        }
        return false;
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:downX=lastX=event.getX();downY=lastY=event.getY();return true;
            case MotionEvent.ACTION_MOVE:
                if(event.getPointerCount()!=1)return true;
                scrollTo(getScrollX()+Math.round(lastX-event.getX()),getScrollY()+Math.round(lastY-event.getY()));lastX=event.getX();lastY=event.getY();return true;
            case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:dragging=false;return true;
            default:return true;
        }
    }
}
