package dev.yerin.weeklymeter;

/** Pixel-only overlay geometry, independent of density, configuration and Android. */
final class FloatingGeometry {
    static final class Frame {
        final int x,y,width,height;
        Frame(int x,int y,int width,int height){this.x=x;this.y=y;this.width=width;this.height=height;}
    }
    static Frame fit(int left,int top,int right,int bottom,int requestedWidth,int requestedHeight,float xFraction,float yFraction){
        int width=Math.max(1,Math.min(Math.max(1,requestedWidth),span(left,right)));
        int height=Math.max(1,Math.min(Math.max(1,requestedHeight),span(top,bottom)));
        int x=left+Math.round((span(left,right)-width)*fraction(xFraction));
        int y=top+Math.round((span(top,bottom)-height)*fraction(yFraction));
        return new Frame(x,y,width,height);
    }
    static Frame move(Frame frame,int left,int top,int right,int bottom,float requestedX,float requestedY){
        Frame size=fit(left,top,right,bottom,frame.width,frame.height,0,0);
        int x=clampCoordinate(requestedX,left,Math.max(left,right-size.width));
        int y=clampCoordinate(requestedY,top,Math.max(top,bottom-size.height));
        return new Frame(x,y,size.width,size.height);
    }
    static float position(int value,int start,int end,int size){
        long free=(long)end-start-size;
        return free<=0?0:fraction((float)(((double)value-start)/free));
    }
    static float fraction(float value){return Float.isNaN(value)?0:Math.max(0,Math.min(1,value));}
    private static int span(int start,int end){return (int)Math.max(1,Math.min(Integer.MAX_VALUE,(long)end-start));}
    private static int clampCoordinate(float value,int min,int max){return Float.isNaN(value)?min:Math.round(Math.max(min,Math.min(max,value)));}
    private FloatingGeometry(){}
}
