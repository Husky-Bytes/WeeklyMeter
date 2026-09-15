package dev.yerin.weeklymeter;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;

/** HSV wheel used only in the settings UI, with hue/saturation retained at zero brightness. */
public final class ColorWheelView extends View {
    public interface OnColorChangedListener {void onColorChanged(int color,boolean finished);}
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] hsv={0,0,1};
    private OnColorChangedListener listener;
    private float centerX,centerY,radius;
    private Shader hues,saturation;
    private boolean tracking;
    public ColorWheelView(Context context){super(context);setFocusable(true);setContentDescription(Texts.t(context,"색상 휠. 각도: 색상, 중심으로부터 거리: 채도. HEX 직접 입력 가능.","Color wheel. Angle selects hue; distance from the center selects saturation. You can also enter a HEX color."));}
    public void setColor(int color){float[] value=new float[3];Color.colorToHSV(color,value);if(value[2]>0){if(value[1]>0)hsv[0]=value[0];hsv[1]=value[1];}hsv[2]=value[2];invalidate();}
    public void setBrightness(float value){hsv[2]=Math.max(0,Math.min(1,value));invalidate();}
    public float[] getHsv(){return hsv.clone();}
    public int getColor(){return Color.HSVToColor(hsv);}
    public void setOnColorChangedListener(OnColorChangedListener value){listener=value;}
    @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight){centerX=width/2f;centerY=height/2f;radius=Math.max(1,Math.min(width,height)/2f-dp(12));hues=new SweepGradient(centerX,centerY,new int[]{Color.RED,Color.YELLOW,Color.GREEN,Color.CYAN,Color.BLUE,Color.MAGENTA,Color.RED},null);saturation=new RadialGradient(centerX,centerY,radius,new int[]{Color.WHITE,0x00ffffff},null,Shader.TileMode.CLAMP);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);paint.setStyle(Paint.Style.FILL);paint.setShader(hues);paint.setAlpha(255);canvas.drawCircle(centerX,centerY,radius,paint);paint.setShader(saturation);canvas.drawCircle(centerX,centerY,radius,paint);paint.setShader(null);paint.setColor(Color.BLACK);paint.setAlpha(Math.round((1-hsv[2])*255));canvas.drawCircle(centerX,centerY,radius,paint);paint.setAlpha(255);
        double angle=Math.toRadians(hsv[0]);float x=centerX+(float)Math.cos(angle)*hsv[1]*radius,y=centerY+(float)Math.sin(angle)*hsv[1]*radius;paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(4));paint.setColor(Color.BLACK);canvas.drawCircle(x,y,dp(8),paint);paint.setStrokeWidth(dp(2));paint.setColor(Color.WHITE);canvas.drawCircle(x,y,dp(8),paint);paint.setStyle(Paint.Style.FILL);
    }
    @Override public boolean onTouchEvent(MotionEvent event){float dx=event.getX()-centerX,dy=event.getY()-centerY;int action=event.getActionMasked();if(action==MotionEvent.ACTION_DOWN){if(Math.hypot(dx,dy)>radius+dp(10))return false;tracking=true;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);}if(!tracking)return false;
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_MOVE||action==MotionEvent.ACTION_UP){hsv[0]=(float)((Math.toDegrees(Math.atan2(dy,dx))+360)%360);hsv[1]=Math.min(1,(float)Math.hypot(dx,dy)/radius);invalidate();if(listener!=null)listener.onColorChanged(getColor(),action==MotionEvent.ACTION_UP);}
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){tracking=false;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);if(action==MotionEvent.ACTION_UP)performClick();}return true;}
    @Override public boolean performClick(){super.performClick();return true;}
    private float dp(float value){return value*getResources().getDisplayMetrics().density;}
}
