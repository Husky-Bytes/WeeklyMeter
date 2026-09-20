package dev.yerin.weeklymeter;

import java.util.*;

/** Non-secret appearance model; independent from Android and credential storage. */
final class WidgetStyle {
    static final int PERCENT=0,RESET=1,LAST=2,BRAND=3;
    static final int DEFAULT_BACKGROUND=0xff14181f,DEFAULT_RESET=0xffb8c1cf;
    int background=DEFAULT_BACKGROUND,opacity=88,overallOpacity=100,radius=18;
    boolean autoFit=true,feedbackEnabled=true;
    boolean automaticPadding=true;
    float paddingHorizontalDp=5f,paddingVerticalDp=5f,rowGapDp=3f;
    int feedbackDurationMs=1000,brandMode=1;
    float brandLogoSizeSp=16;
    Row[] rows=new Row[4];
    int[] order={PERCENT,RESET,LAST,BRAND};
    DateSpec resetDate=new DateSpec(),lastDate=new DateSpec();
    WidgetStyle(){
        rows[PERCENT]=new Row(true,0,32,0xffffffff,true,1,0);
        rows[RESET]=new Row(true,0,11,DEFAULT_RESET,false,1,0);
        rows[LAST]=new Row(false,0,10,0xff9aa8b9,false,1,0);
        rows[BRAND]=new Row(false,0,11,0xffb8c1cf,false,1,0);
        lastDate.month=false;lastDate.day=false;lastDate.second=true;
    }
    static WidgetStyle defaults(){return new WidgetStyle();}
    WidgetStyle copy(){
        WidgetStyle out=new WidgetStyle();out.background=background;out.opacity=opacity;out.overallOpacity=overallOpacity;out.radius=radius;
        out.autoFit=autoFit;out.feedbackEnabled=feedbackEnabled;out.feedbackDurationMs=feedbackDurationMs;
        out.automaticPadding=automaticPadding;out.paddingHorizontalDp=paddingHorizontalDp;out.paddingVerticalDp=paddingVerticalDp;out.rowGapDp=rowGapDp;
        out.brandMode=brandMode;out.brandLogoSizeSp=brandLogoSizeSp;
        for(int i=0;i<4;i++)out.rows[i]=rows[i].copy();
        out.order=order.clone();out.resetDate=resetDate.copy();out.lastDate=lastDate.copy();return out;
    }
    void normalize(){
        background|=0xff000000;opacity=clamp(opacity,0,100);overallOpacity=clamp(overallOpacity,0,100);radius=clamp(radius,0,32);
        feedbackDurationMs=clamp(Math.round(feedbackDurationMs/100f)*100,100,10000);
        brandMode=clamp(brandMode,0,3);brandLogoSizeSp=half(bound(brandLogoSizeSp,6,64,16));
        paddingHorizontalDp=half(bound(paddingHorizontalDp,0,32,5));paddingVerticalDp=half(bound(paddingVerticalDp,0,32,5));rowGapDp=half(bound(rowGapDp,0,16,3));
        Row[] validRows=new Row[4];WidgetStyle baseline=defaults();
        for(int i=0;i<4;i++){validRows[i]=rows!=null&&i<rows.length&&rows[i]!=null?rows[i]:baseline.rows[i];validRows[i].normalize();}
        rows=validRows;
        int[] cleaned=new int[4];boolean[] seen=new boolean[4];int index=0;
        if(order!=null)for(int id:order)if(id>=0&&id<4&&!seen[id]){seen[id]=true;cleaned[index++]=id;}
        for(int id=0;id<4;id++)if(!seen[id])cleaned[index++]=id;
        order=cleaned;
        if(resetDate==null)resetDate=new DateSpec();if(lastDate==null)lastDate=baseline.lastDate;
        resetDate.normalize();lastDate.normalize();
    }
    int backgroundArgb(){return (Math.round(opacity*255/100f)<<24)|(background&0xffffff);}
    /** Applied once to the finished composition, never multiplied into individual rows. */
    int overallAlpha(){return Math.round(clamp(overallOpacity,0,100)*255/100f);}
    static int parseRgb(String input){
        String value=input==null?"":input.trim();if(value.startsWith("#"))value=value.substring(1);
        if(!value.matches("[0-9a-fA-F]{6}"))throw new IllegalArgumentException("색상을 #RRGGBB 형식으로 입력해 주세요.");
        return 0xff000000|Integer.parseInt(value,16);
    }
    static String rgb(int color){return String.format(Locale.ROOT,"#%06X",color&0xffffff);}
    static float dimension(float n){return !Float.isFinite(n)||n<=0?64:Math.max(24,Math.min(1200,n));}
    static int bitmapPixelBudget(int screenWidth,int screenHeight,int variants){
        // RemoteViews' aggregate bitmap limit is based on physical display area.
        // Keep 20% below its 1.5-screen allowance, including the largest variant set.
        long pixels=(long)(screenWidth>0?screenWidth:320)*(screenHeight>0?screenHeight:640);
        return Math.max(1,(int)Math.min(220000,Math.floor(pixels*1.2/clamp(variants,1,4))));
    }
    static String rowName(int id){return rowName(id,Locale.KOREAN);}
    static String rowName(int id,Locale locale){return (korean(locale)?new String[]{"퍼센트","초기화 시각","마지막 성공 조회","ChatGPT"}:new String[]{"Percentage","Reset time","Last successful refresh","ChatGPT"})[clamp(id,0,3)];}
    private static boolean korean(Locale locale){return locale!=null&&"ko".equals(locale.getLanguage());}
    /** Resolved internal spacing only; launcher-reserved cell margins are not changed. */
    static final class Spacing {
        final float horizontal,vertical,gap,innerWidth,innerHeight;
        final boolean paddingAdjusted,gapAdjusted;
        Spacing(float horizontal,float vertical,float gap,float width,float height,boolean paddingAdjusted,boolean gapAdjusted){
            this.horizontal=horizontal;this.vertical=vertical;this.gap=gap;this.innerWidth=width;this.innerHeight=height;
            this.paddingAdjusted=paddingAdjusted;this.gapAdjusted=gapAdjusted;
        }
    }
    Spacing spacing(float requestedWidth,float requestedHeight,int visibleRows){
        float width=dimension(requestedWidth),height=dimension(requestedHeight);
        if(automaticPadding){
            float pad=Math.min(10,Math.max(3,Math.min(width,height)*.07f));
            return new Spacing(pad,pad,Math.min(4,Math.max(1,height*.025f)),width-pad*2,height-pad*2,false,false);
        }
        float wantedHorizontal=half(bound(paddingHorizontalDp,0,32,5)),wantedVertical=half(bound(paddingVerticalDp,0,32,5));
        // Keep a small but real content area even for 1x1 + maximum padding.
        // Requested preferences remain untouched; the preview reports the adjustment.
        float horizontal=Math.min(wantedHorizontal,Math.max(0,(width-12)/2));
        float vertical=Math.min(wantedVertical,Math.max(0,(height-12)/2));
        float innerWidth=width-horizontal*2,innerHeight=height-vertical*2;
        float wantedGap=half(bound(rowGapDp,0,16,3)),gap=wantedGap;int rows=clamp(visibleRows,0,4);
        // Reserve >=3 dp per visible row so the gaps cannot consume the whole area.
        // The normal auto-fit process still measures/scales the actual font heights.
        if(rows>1)gap=Math.min(gap,Math.max(0,(innerHeight-rows*3)/(rows-1)));
        return new Spacing(horizontal,vertical,gap,innerWidth,innerHeight,
            horizontal<wantedHorizontal-.001f||vertical<wantedVertical-.001f,gap<wantedGap-.001f);
    }
    static final class Row {
        boolean enabled,bold;int font,color,alignment;float sizeSp,offsetY;
        Row(boolean enabled,int font,float sizeSp,int color,boolean bold,int alignment,float offsetY){
            this.enabled=enabled;this.font=font;this.sizeSp=sizeSp;this.color=color;this.bold=bold;this.alignment=alignment;this.offsetY=offsetY;
        }
        Row(){this(true,0,12,0xffffffff,false,1,0);}
        Row copy(){return new Row(enabled,font,sizeSp,color,bold,alignment,offsetY);}
        void normalize(){font=clamp(font,0,4);sizeSp=half(bound(sizeSp,6,96,12));color|=0xff000000;alignment=clamp(alignment,0,2);offsetY=bound(offsetY,-50,50,0);}
    }
    static final class DateSpec {
        boolean year=false,month=true,day=true,weekday=false,hour=true,minute=true,second=false;
        boolean use24h=true,ampm=false,leadingZero=false,twoLines=false,label=false;
        int separator=0;
        DateSpec copy(){
            DateSpec d=new DateSpec();d.year=year;d.month=month;d.day=day;d.weekday=weekday;d.hour=hour;d.minute=minute;d.second=second;
            d.use24h=use24h;d.ampm=ampm;d.leadingZero=leadingZero;d.twoLines=twoLines;d.label=label;d.separator=separator;return d;
        }
        void normalize(){separator=clamp(separator,0,3);}
        boolean hasElements(){return year||month||day||weekday||hour||minute||second||ampm;}
        String format(long epochSeconds,TimeZone zone,String labelText){
            return format(epochSeconds,zone,labelText,Locale.KOREAN);
        }
        String format(long epochSeconds,TimeZone zone,String labelText,Locale locale){
            if(epochSeconds<=0||epochSeconds>100_000_000_000L||!hasElements())return "";
            boolean ko=korean(locale);Calendar c=Calendar.getInstance(zone==null?TimeZone.getDefault():zone,ko?Locale.KOREA:Locale.ENGLISH);c.setTimeInMillis(epochSeconds*1000);
            List<String> dates=new ArrayList<>(),times=new ArrayList<>();
            if(year)dates.add(String.format(Locale.ROOT,"%04d",c.get(Calendar.YEAR))+(separator==3&&ko?"년":""));
            if(month)dates.add(separator==3&&!ko?new java.text.DateFormatSymbols(Locale.ENGLISH).getShortMonths()[c.get(Calendar.MONTH)]:number(c.get(Calendar.MONTH)+1,leadingZero)+(separator==3?"월":""));
            if(day)dates.add(number(c.get(Calendar.DAY_OF_MONTH),leadingZero)+(separator==3&&ko?"일":""));
            String[] separators={".","/","-"," "};String date=join(dates,separators[clamp(separator,0,3)]);
            if(weekday){String[] weekdays=ko?new String[]{"일","월","화","수","목","금","토"}:new String[]{"Sun","Mon","Tue","Wed","Thu","Fri","Sat"};date+=(date.isEmpty()?"":" ")+"("+weekdays[c.get(Calendar.DAY_OF_WEEK)-1]+")";}
            int h=c.get(Calendar.HOUR_OF_DAY);if(!use24h)h=h%12==0?12:h%12;
            if(hour)times.add(number(h,leadingZero));
            // Once hours are present, minutes/seconds have clock-style zero padding.
            // Standalone minute or second elements honor the leading-zero toggle.
            if(minute)times.add(number(c.get(Calendar.MINUTE),leadingZero||hour));
            if(second)times.add(number(c.get(Calendar.SECOND),leadingZero||hour||minute));
            String time=join(times,":");
            if(ampm){String marker=c.get(Calendar.AM_PM)==Calendar.AM?(ko?"오전":"AM"):(ko?"오후":"PM");time=ko?marker+(time.isEmpty()?"":" "+time):time+(time.isEmpty()?"":" ")+marker;}
            String result=date+(date.isEmpty()||time.isEmpty()?"":twoLines?"\n":" ")+time;
            String prefix=labelText==null?"":labelText.trim();
            return label&&!result.isEmpty()&&!prefix.isEmpty()?prefix+" "+result:result;
        }
        private static String number(int n,boolean zero){return zero?String.format(Locale.ROOT,"%02d",n):Integer.toString(n);}
        private static String join(List<String> parts,String delimiter){StringBuilder b=new StringBuilder();for(String part:parts){if(b.length()>0)b.append(delimiter);b.append(part);}return b.toString();}
    }
    /** Stable pure layout planning shared by tests and the bitmap renderer. */
    static final class Placement {
        final float[] tops;final boolean adjusted,overflow;
        Placement(float[] tops,boolean adjusted,boolean overflow){this.tops=tops;this.adjusted=adjusted;this.overflow=overflow;}
    }
    static Placement place(float viewportHeight,float padding,float[] heights,float[] offsets,float gap){
        float inner=Math.max(1,viewportHeight-padding*2),total=gap*Math.max(0,heights.length-1);
        for(float h:heights)total+=Math.max(0,h);
        boolean overflow=total>inner+.01f,adjusted=false;float[] tops=new float[heights.length];
        float base=padding+Math.max(0,(inner-total)/2),previous=padding-gap,remaining=total;
        for(int i=0;i<heights.length;i++){
            remaining-=heights[i];float requested=base+offsets[i]*viewportHeight/100;
            float low=previous+gap,high=viewportHeight-padding-heights[i]-remaining;
            float top=overflow?Math.max(low,requested):Math.max(low,Math.min(high,requested));
            if(Math.abs(top-requested)>.5f)adjusted=true;tops[i]=top;previous=top+heights[i];base+=heights[i]+gap;remaining-=gap;
        }
        return new Placement(tops,adjusted,overflow);
    }
    private static int clamp(int n,int min,int max){return Math.max(min,Math.min(max,n));}
    private static float bound(float n,float min,float max,float fallback){return !Float.isFinite(n)?fallback:Math.max(min,Math.min(max,n));}
    private static float half(float n){return Math.round(n*2)/2f;}
}
