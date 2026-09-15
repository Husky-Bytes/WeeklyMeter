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
        Paint bg=new Paint(Paint.ANTI_ALIAS_FLAG);bg.setColor(s.backgroundArgb());float radius=Math.min(s.radius,Math.min(w,h)/2);
        canvas.drawRoundRect(0,0,w,h,radius,radius,bg);
        WidgetStyle.Spacing edges=s.spacing(w,h,0);
        float paddingHorizontal=edges.horizontal,paddingVertical=edges.vertical,innerW=edges.innerWidth,innerH=edges.innerHeight;
        float fontScale=Math.max(.5f,Math.min(3,c.getResources().getConfiguration().fontScale));
        LinkedHashSet<String> warnings=new LinkedHashSet<>();List<LineRow> rows=new ArrayList<>();
        if(edges.paddingAdjusted)warnings.add("1×1에서도 내용을 표시할 수 있도록 내부 여백을 줄였어요.");
        long now=System.currentTimeMillis();boolean valid=usage!=null&&!usage.expired(now);TimeZone zone=TimeZone.getDefault();
        String[] texts={valid?usage.percent():"—%",s.resetDate.format(usage==null?0:usage.resetsAt,zone,"초기화"),
            s.lastDate.format(usage==null?0:usage.fetchedAt/1000,zone,"조회"),"ChatGPT"};
        for(int id:s.order){
            WidgetStyle.Row spec=s.rows[id];if(!spec.enabled)continue;
            if(id==WidgetStyle.BRAND&&s.brandMode==0)continue;
            String text=texts[id];
            // A selected timestamp with no source is shown as a dash, never the current time.
            if((id==WidgetStyle.RESET||id==WidgetStyle.LAST)&&text.isEmpty()){
                WidgetStyle.DateSpec d=id==WidgetStyle.RESET?s.resetDate:s.lastDate;
                // Turning every element off is an explicit, supported "hide" choice.
                if(!d.hasElements())continue;
                text=d.label?(id==WidgetStyle.RESET?"초기화 —":"조회 —"):"—";
            }
            LineRow row=new LineRow();row.id=id;row.spec=spec;row.paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);
            row.paint.setColor(spec.color);row.paint.setTextSize(spec.sizeSp*fontScale);row.paint.setTypeface(font(c,spec.font,spec.bold,warnings));
            if(id==WidgetStyle.BRAND&&(s.brandMode==2||s.brandMode==3)){
                try{row.logo=c.getDrawable(R.drawable.chatgpt_logo).mutate();row.hasLogo=true;
                    row.logo.setTint(luminance(s.background)>.52f?Color.BLACK:Color.WHITE);
                    // The official vector already includes its original clear space.
                    row.logoSize=s.brandLogoSizeSp*fontScale;row.logoSlot=row.logoSize;
                }catch(Exception e){warnings.add("로고를 불러오지 못해 ChatGPT 문구로 표시했어요.");}
            }
            if(id==WidgetStyle.BRAND&&s.brandMode==2&&row.hasLogo)text="";
            row.lines=text.isEmpty()?new String[0]:text.split("\n",-1);row.measure();
            if(s.autoFit&&row.width>innerW){
                for(int pass=0;pass<4&&row.width>innerW;pass++)row.scale(Math.max(.001f,(innerW-.05f)/row.width));
                warnings.add("좁은 폭에 맞게 일부 글자·로고를 줄였어요.");
            }
            rows.add(row);
        }
        if(rows.isEmpty())warnings.add("현재 표시하도록 선택한 행이 없어요.");
        WidgetStyle.Spacing spacing=s.spacing(w,h,rows.size());
        float gap=spacing.gap,total=gap*Math.max(0,rows.size()-1);for(LineRow r:rows)total+=r.height;
        if(spacing.gapAdjusted)warnings.add("행 사이 간격이 내용을 가리지 않도록 간격을 줄였어요.");
        if(s.autoFit&&total>innerH){float factor=Math.max(.01f,(innerH-gap*Math.max(0,rows.size()-1))/Math.max(1,total-gap*Math.max(0,rows.size()-1)));
            for(LineRow r:rows)r.scale(factor);warnings.add("높이에 맞게 글자·로고를 줄였어요. 더 크게 보려면 행을 줄이거나 위젯을 키워 주세요.");}
        float[] heights=new float[rows.size()],offsets=new float[rows.size()];boolean overflow=false;
        for(int i=0;i<rows.size();i++){LineRow r=rows.get(i);heights[i]=r.height;offsets[i]=r.spec.offsetY;if(r.width>innerW+.2f)overflow=true;if(r.paint.getTextSize()<6&&r.lines.length>0)warnings.add("1×1에서 일부 글자가 매우 작아요. 행을 줄이거나 크기를 조절해 주세요.");}
        WidgetStyle.Placement placement=WidgetStyle.place(h,paddingVertical,heights,offsets,gap);overflow|=placement.overflow;
        if(placement.adjusted)warnings.add("화면 경계와 행 겹침을 피하도록 세로 위치를 조정했어요.");
        if(overflow){
            LinkedHashSet<String> prioritized=new LinkedHashSet<>();
            prioritized.add("선택한 크기의 공간을 넘어요. 자동 맞춤을 켜거나 글자·행을 줄여 주세요.");prioritized.addAll(warnings);warnings=prioritized;
        }
        canvas.save();canvas.clipRect(0,0,w,h);
        for(int i=0;i<rows.size();i++)drawRow(canvas,rows.get(i),placement.tops[i],paddingHorizontal,innerW);
        canvas.restore();
        if(s.feedbackEnabled)drawFeedback(canvas,feedbackState,w,h);
        String access=(valid?"주간 잔여량 "+texts[0]:"주간 잔여량 확인 필요")+". "+Display.reset(usage)+". "+Display.last(usage);
        if(!"none".equals(feedbackState))access+=". "+feedbackDescription(feedbackState);
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
            catch(Exception e){warnings.add("선택한 글꼴을 불러오지 못해 기본 글꼴로 표시했어요.");}}
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
    static String feedbackDescription(String state){
        if("success".equals(state))return "새 사용량 조회 성공";if("error".equals(state))return "조회 실패, 앱에서 확인";
        if("running".equals(state))return "사용량 조회 중";if("waiting".equals(state))return "새로고침 접수, 실행 대기";
        if("skipped".equals(state))return "중복 요청 또는 재시도 대기";return "";
    }
    private static float luminance(int color){return (Color.red(color)*.2126f+Color.green(color)*.7152f+Color.blue(color)*.0722f)/255f;}
    private static String join(Set<String> values){StringBuilder out=new StringBuilder();for(String s:values){if(out.length()>0)out.append('\n');out.append(s);}return out.toString();}
    private WidgetRenderer(){}
}
