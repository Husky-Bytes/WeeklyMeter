package dev.yerin.weeklymeter;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import java.util.*;

/** A bounded native Canvas render supports real bundled fonts without custom RemoteViews classes. */
final class WidgetRenderer {
    private static final Typeface[] FONTS=new Typeface[5];
    private static final String[] ASSET_NAMES={"","gothic.ttf","rounded.ttf","serif.ttf","mono.ttf"};
    static final class Result {
        final Bitmap bitmap;final String warning,accessibility;final boolean overflow;
        Result(Bitmap bitmap,String warning,String accessibility,boolean overflow){this.bitmap=bitmap;this.warning=warning;this.accessibility=accessibility;this.overflow=overflow;}
    }
    private static final class LineRow {
        int id;WidgetStyle.Row spec;String[] lines;Paint paint;
        float height,width,lineHeight,logoSize,logoSlot;boolean hasLogo;Drawable logo;
        void measure(){
            Paint.FontMetrics fm=paint.getFontMetrics();lineHeight=Math.max(1,(fm.descent-fm.ascent)*1.06f);
            width=0;for(String line:lines)width=Math.max(width,paint.measureText(line));
            if(hasLogo)width+=logoSlot+(lines.length>0?Math.max(2,logoSize*.2f):0);
            height=Math.max(lines.length*lineHeight,logoSlot);
        }
        void scale(float factor){paint.setTextSize(Math.max(.5f,paint.getTextSize()*factor));logoSize*=factor;logoSlot*=factor;measure();}
    }
    static Result render(Context c,Usage usage,WidgetStyle style,float width,float height){return render(c,usage,style,width,height,"none");}
    static Result render(Context c,Usage usage,WidgetStyle original,float requestedWidth,float requestedHeight,String feedbackState){
        WidgetStyle s=original.copy();s.normalize();float w=WidgetStyle.dimension(requestedWidth),h=WidgetStyle.dimension(requestedHeight);
        float density=Math.max(.5f,c.getResources().getDisplayMetrics().density);
        int pixels=WidgetStyle.bitmapPixelBudget(c.getResources().getDisplayMetrics().widthPixels,c.getResources().getDisplayMetrics().heightPixels,4);
        float scale=Math.min(density,Math.min(1024f/Math.max(w,h),(float)Math.sqrt(pixels/(w*h))));
        int pixelW=Math.max(1,(int)Math.floor(w*scale)),pixelH=Math.max(1,(int)Math.floor(h*scale));
        Bitmap bitmap=Bitmap.createBitmap(pixelW,pixelH,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.scale(pixelW/w,pixelH/h);
        // Composite background, text, logo and feedback first; then fade the group
        // exactly once so overlapping elements do not become independently translucent.
        int opacityLayer=s.overallOpacity<100?canvas.saveLayerAlpha(0,0,w,h,s.overallAlpha()):-1;
        Paint bg=new Paint(Paint.ANTI_ALIAS_FLAG);bg.setColor(s.backgroundArgb());float radius=Math.min(s.radius,Math.min(w,h)/2);
        canvas.drawRoundRect(0,0,w,h,radius,radius,bg);
        WidgetStyle.Spacing edges=s.spacing(w,h,0);
        float paddingHorizontal=edges.horizontal,paddingVertical=edges.vertical,innerW=edges.innerWidth,innerH=edges.innerHeight;
        float fontScale=Math.max(.5f,Math.min(3,c.getResources().getConfiguration().fontScale));
        LinkedHashSet<String> warnings=new LinkedHashSet<>();List<LineRow> rows=new ArrayList<>();
        if(edges.paddingAdjusted)warnings.add(Texts.t(c,"내용에 맞춰 안쪽 여백 조정됨","Inner padding reduced to fit content."));
        long now=System.currentTimeMillis();boolean valid=usage!=null&&!usage.expired(now);TimeZone zone=TimeZone.getDefault();
        String[] texts={valid?usage.percent():"—%",s.resetDate.format(usage==null?0:usage.resetsAt,zone,Texts.t(c,"초기화","Resets"),Texts.locale(c)),
            s.lastDate.format(usage==null?0:usage.fetchedAt/1000,zone,Texts.t(c,"조회","Updated"),Texts.locale(c)),"ChatGPT"};
        for(int id:s.order){
            WidgetStyle.Row spec=s.rows[id];if(!spec.enabled)continue;
            if(id==WidgetStyle.BRAND&&s.brandMode==0)continue;
            String text=texts[id];
            // A selected timestamp with no source is shown as a dash, never the current time.
            if((id==WidgetStyle.RESET||id==WidgetStyle.LAST)&&text.isEmpty()){
                WidgetStyle.DateSpec d=id==WidgetStyle.RESET?s.resetDate:s.lastDate;
                // Turning every element off is an explicit, supported "hide" choice.
                if(!d.hasElements())continue;
                text=d.label?(id==WidgetStyle.RESET?Texts.t(c,"초기화 —","Resets —"):Texts.t(c,"조회 —","Updated —")):"—";
            }
            LineRow row=new LineRow();row.id=id;row.spec=spec;row.paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);
            row.paint.setColor(spec.color);row.paint.setTextSize(spec.sizeSp*fontScale);row.paint.setTypeface(font(c,spec.font,spec.bold,warnings));
            if(id==WidgetStyle.BRAND&&(s.brandMode==2||s.brandMode==3)){
                try{row.logo=c.getDrawable(R.drawable.chatgpt_logo).mutate();row.hasLogo=true;
                    row.logo.setTint(luminance(s.background)>.52f?Color.BLACK:Color.WHITE);
                    // The official vector already includes its original clear space.
                    row.logoSize=s.brandLogoSizeSp*fontScale;row.logoSlot=row.logoSize;
                }catch(Exception e){warnings.add(Texts.t(c,"로고 불러오기 실패 · 문구로 표시","Logo unavailable · showing text."));}
            }
            if(id==WidgetStyle.BRAND&&s.brandMode==2&&row.hasLogo)text="";
            row.lines=text.isEmpty()?new String[0]:text.split("\n",-1);row.measure();
            if(s.autoFit&&row.width>innerW){
                for(int pass=0;pass<4&&row.width>innerW;pass++)row.scale(Math.max(.001f,(innerW-.05f)/row.width));
                warnings.add(Texts.t(c,"폭에 맞춰 글자·로고 축소됨","Text or logos reduced to fit width."));
            }
            rows.add(row);
        }
        if(rows.isEmpty())warnings.add(Texts.t(c,"표시 항목 없음","No visible elements selected."));
        WidgetStyle.Spacing spacing=s.spacing(w,h,rows.size());
        float gap=spacing.gap,total=gap*Math.max(0,rows.size()-1);for(LineRow r:rows)total+=r.height;
        if(spacing.gapAdjusted)warnings.add(Texts.t(c,"내용에 맞춰 요소 간격 조정됨","Row spacing reduced to fit content."));
        if(s.autoFit&&total>innerH){float factor=Math.max(.01f,(innerH-gap*Math.max(0,rows.size()-1))/Math.max(1,total-gap*Math.max(0,rows.size()-1)));
            for(LineRow r:rows)r.scale(factor);warnings.add(Texts.t(c,"높이에 맞춰 축소됨 · 요소를 줄이거나 위젯을 키워 주세요.","Reduced to fit height · show fewer rows or enlarge the widget."));}
        float[] heights=new float[rows.size()],offsets=new float[rows.size()];boolean overflow=false;
        for(int i=0;i<rows.size();i++){LineRow r=rows.get(i);heights[i]=r.height;offsets[i]=r.spec.offsetY;if(r.width>innerW+.2f)overflow=true;if(r.paint.getTextSize()<6&&r.lines.length>0)warnings.add(Texts.t(c,"작은 글자 주의 · 요소 수나 크기를 조절해 주세요.","Some text is very small · reduce rows or adjust sizes."));}
        WidgetStyle.Placement placement=WidgetStyle.place(h,paddingVertical,heights,offsets,gap);overflow|=placement.overflow;
        if(placement.adjusted)warnings.add(Texts.t(c,"겹침 방지를 위해 세로 위치 조정됨","Vertical positions adjusted to avoid overlap."));
        if(overflow){
            LinkedHashSet<String> prioritized=new LinkedHashSet<>();
            prioritized.add(Texts.t(c,"공간 부족 · 자동 맞춤을 켜거나 글자·요소를 줄여 주세요.","Content does not fit · enable auto-fit or reduce text/rows."));prioritized.addAll(warnings);warnings=prioritized;
        }
        canvas.save();canvas.clipRect(0,0,w,h);
        for(int i=0;i<rows.size();i++)drawRow(canvas,rows.get(i),placement.tops[i],paddingHorizontal,innerW);
        canvas.restore();
        if(s.feedbackEnabled)drawFeedback(canvas,feedbackState,w,h);
        if(opacityLayer>=0)canvas.restoreToCount(opacityLayer);
        String access=(valid?Texts.t(c,"주간 잔여량 ","Weekly remaining ")+texts[0]:Texts.t(c,"주간 잔여량 확인 필요","Weekly remaining needs a refresh"))+". "+Display.reset(c,usage)+". "+Display.last(c,usage);
        if(!"none".equals(feedbackState))access+=". "+feedbackDescription(c,feedbackState);
        return new Result(bitmap,join(warnings),access,overflow);
    }
    private static void drawRow(Canvas canvas,LineRow row,float top,float padding,float width){
        float left=padding+(row.spec.alignment==0?0:row.spec.alignment==2?width-row.width:(width-row.width)/2);
        float textLeft=left,textWidth=row.width;
        if(row.hasLogo){
            float clear=(row.logoSlot-row.logoSize)/2;float logoTop=top+(row.height-row.logoSlot)/2+clear;
            int iw=Math.max(1,row.logo.getIntrinsicWidth()),ih=Math.max(1,row.logo.getIntrinsicHeight());float factor=row.logoSize/Math.max(iw,ih);
            canvas.save();canvas.translate(left+clear+(row.logoSize-iw*factor)/2,logoTop+(row.logoSize-ih*factor)/2);canvas.scale(factor,factor);
            row.logo.setBounds(0,0,iw,ih);row.logo.draw(canvas);canvas.restore();
            float reserved=row.logoSlot+(row.lines.length>0?Math.max(2,row.logoSize*.2f):0);textLeft+=reserved;textWidth-=reserved;
        }
        Paint.FontMetrics fm=row.paint.getFontMetrics();float baseline=top+(row.height-row.lines.length*row.lineHeight)/2-fm.ascent;
        for(String line:row.lines){float measured=row.paint.measureText(line);float x=textLeft+(row.spec.alignment==0?0:row.spec.alignment==2?textWidth-measured:(textWidth-measured)/2);canvas.drawText(line,x,baseline,row.paint);baseline+=row.lineHeight;}
    }
    private static synchronized Typeface font(Context c,int index,boolean bold,Set<String> warnings){
        Typeface face=Typeface.DEFAULT;
        if(index>0){try{if(FONTS[index]==null)FONTS[index]=Typeface.createFromAsset(c.getAssets(),"fonts/"+ASSET_NAMES[index]);face=FONTS[index];}
            catch(Exception e){warnings.add(Texts.t(c,"글꼴 불러오기 실패 · 기본 글꼴로 표시","Font unavailable · showing the system default."));}}
        return Typeface.create(face,bold?Typeface.BOLD:Typeface.NORMAL);
    }
    private static void drawFeedback(Canvas canvas,String state,float w,float h){
        if(state==null||"none".equals(state))return;
        float size=Math.max(5,Math.min(9,Math.min(w,h)*.14f)),cx=w-size*.7f-2,cy=size*.7f+2;
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(0xdd11161d);canvas.drawCircle(cx,cy,size*.66f,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1,size*.15f));p.setStrokeCap(Paint.Cap.ROUND);
        if("success".equals(state)){p.setColor(0xffb8efcf);Path path=new Path();path.moveTo(cx-size*.32f,cy);path.lineTo(cx-size*.06f,cy+size*.26f);path.lineTo(cx+size*.34f,cy-size*.26f);canvas.drawPath(path,p);}
        else if("error".equals(state)){p.setColor(0xffffb4ad);canvas.drawLine(cx,cy-size*.3f,cx,cy+size*.08f,p);canvas.drawPoint(cx,cy+size*.32f,p);}
        else if("running".equals(state)){p.setColor(0xffb8d9ff);canvas.drawArc(cx-size*.34f,cy-size*.34f,cx+size*.34f,cy+size*.34f,-70,285,false,p);}
        else {p.setColor(0xffd1dae5);canvas.drawPoint(cx-size*.3f,cy,p);canvas.drawPoint(cx,cy,p);canvas.drawPoint(cx+size*.3f,cy,p);}
    }
    static String feedbackDescription(Context c,String state){
        if("success".equals(state))return Texts.t(c,"새로고침 완료","Refresh complete");if("error".equals(state))return Texts.t(c,"새로고침 실패 · 앱에서 확인","Refresh failed; check the app");
        if("running".equals(state))return Texts.t(c,"사용량 조회 중","Refreshing usage");if("waiting".equals(state))return Texts.t(c,"새로고침 대기","Refresh received; waiting to start");
        if("skipped".equals(state))return Texts.t(c,"중복 요청 또는 재시도 대기","Duplicate request or waiting to retry");return "";
    }
    private static float luminance(int color){return (Color.red(color)*.2126f+Color.green(color)*.7152f+Color.blue(color)*.0722f)/255f;}
    private static String join(Set<String> values){StringBuilder out=new StringBuilder();for(String s:values){if(out.length()>0)out.append('\n');out.append(s);}return out.toString();}
    private WidgetRenderer(){}
}
