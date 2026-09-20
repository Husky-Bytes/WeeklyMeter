package dev.yerin.weeklymeter;

/** A move, cancellation or long press can never become a refresh on release. */
final class FloatingGesture {
    static final long LONG_PRESS_MS=600;
    private final float slopSquared;
    private boolean active,dragging;
    private float downX,downY;
    FloatingGesture(float slop){slopSquared=Math.max(1,slop)*Math.max(1,slop);}
    void down(float x,float y){active=true;dragging=false;downX=x;downY=y;}
    boolean move(float x,float y){
        if(!active)return false;
        float dx=x-downX,dy=y-downY;
        if(dx*dx+dy*dy>slopSquared)dragging=true;
        return dragging;
    }
    boolean release(){boolean tap=active&&!dragging;cancel();return tap;}
    boolean longPress(){boolean close=active&&!dragging;cancel();return close;}
    boolean dragging(){return active&&dragging;}
    void cancel(){active=false;dragging=false;}
}
