package dev.yerin.weeklymeter;

import java.time.Instant;
import java.util.*;

/** Pure settings/date/layout planning checks, not Android Canvas or launcher tests. */
public final class WidgetStyleTests {
    private static int passed;
    private static void check(boolean result,String title){if(!result)throw new AssertionError(title);passed++;}
    private static WidgetStyle.DateSpec empty(){WidgetStyle.DateSpec d=new WidgetStyle.DateSpec();d.year=d.month=d.day=d.weekday=d.hour=d.minute=d.second=d.ampm=false;return d;}
    private static float bottom(WidgetStyle.Placement p,float[] heights){return p.tops.length==0?0:p.tops[p.tops.length-1]+heights[heights.length-1];}
    public static void main(String[] args){
        WidgetStyle s=WidgetStyle.defaults();long date=Instant.parse("2026-09-15T09:30:07Z").getEpochSecond();TimeZone seoul=TimeZone.getTimeZone("Asia/Seoul");
        check(s.rows[0].enabled&&s.rows[1].enabled&&!s.rows[2].enabled&&!s.rows[3].enabled,"default remains percent/reset only");
        check(s.feedbackEnabled&&s.feedbackDurationMs==1000,"short feedback default");
        check(s.backgroundArgb()==0xe014181f,"default background alpha");
        s.opacity=0;check(s.backgroundArgb()==0x0014181f,"fully transparent background");s.opacity=100;check(s.backgroundArgb()==0xff14181f,"opaque background");
        s.opacity=50;check(s.backgroundArgb()==0x8014181f,"half opacity");
        check(WidgetStyle.parseRgb("#aBc123")==0xffabc123,"hex parse");check(WidgetStyle.parseRgb(" 123456 ")==0xff123456,"bare trimmed hex");check(WidgetStyle.rgb(0x0012abcd).equals("#12ABCD"),"canonical hex");
        for(String input:new String[]{"","#fff","#12345678","#zzzzzz","#12345g",null,"000000\nanything","0x123456"}){
            boolean rejected=false;try{WidgetStyle.parseRgb(input);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"invalid hex rejected");
        }
        check(s.resetDate.format(date,seoul,"초기화").equals("9.15 18:30"),"default reset formatting");
        check(s.lastDate.format(date,seoul,"조회").equals("18:30:07"),"last successful fetch time-only default");
        check(s.resetDate.format(0,seoul,"초기화").isEmpty(),"missing date not replaced by current time");
        check(s.resetDate.format(-1,seoul,"").isEmpty(),"negative epoch");
        check(s.resetDate.format(Long.MAX_VALUE,seoul,"").isEmpty(),"epoch overflow");
        WidgetStyle.DateSpec d=empty();check(!d.hasElements()&&d.format(date,seoul,"").isEmpty(),"empty composition");
        d.hour=true;check(d.format(date,seoul,"").equals("18"),"hour only without forced minutes/date");
        d=empty();d.minute=true;check(d.format(date,seoul,"").equals("30"),"minute only");
        d=empty();d.second=true;check(d.format(date,seoul,"").equals("7"),"second only");d.leadingZero=true;check(d.format(date,seoul,"").equals("07"),"standalone second leading zero");
        d=empty();d.ampm=true;check(d.format(date,seoul,"").equals("오후"),"AM/PM standalone");
        d=empty();d.year=true;check(d.format(date,seoul,"").equals("2026"),"year only");
        d=empty();d.month=true;check(d.format(date,seoul,"").equals("9"),"month only");
        d=empty();d.day=true;check(d.format(date,seoul,"").equals("15"),"day only");
        d=empty();d.weekday=true;check(d.format(date,seoul,"").equals("(화)"),"weekday only");
        d=empty();d.hour=d.minute=true;check(d.format(date,seoul,"").equals("18:30"),"time only no date");
        d.use24h=false;d.ampm=true;check(d.format(date,seoul,"").equals("오후 6:30"),"12-hour AM/PM");d.ampm=false;check(d.format(date,seoul,"").equals("6:30"),"12-hour AM/PM optional");
        d.leadingZero=true;check(d.format(date,seoul,"").equals("06:30"),"12-hour leading zero");
        long midnight=Instant.parse("2026-09-14T15:05:02Z").getEpochSecond();
        check(d.format(midnight,seoul,"").equals("12:05"),"12-hour midnight");d.use24h=true;check(d.format(midnight,seoul,"").equals("00:05"),"24-hour midnight");
        d=empty();d.month=d.day=true;check(d.format(date,seoul,"").equals("9.15"),"date only no time");
        d.year=true;d.separator=1;check(d.format(date,seoul,"").equals("2026/9/15"),"slash date");
        d.separator=2;d.leadingZero=true;check(d.format(date,seoul,"").equals("2026-09-15"),"dash date leading zero");
        d.separator=3;check(d.format(date,seoul,"").equals("2026년 09월 15일"),"Korean date units");
        d.weekday=d.hour=d.minute=true;d.twoLines=true;d.label=true;
        check(d.format(date,seoul,"초기화").equals("초기화 2026년 09월 15일 (화)\n18:30"),"label and two groups");
        d.year=d.month=d.day=d.weekday=false;check(d.format(date,seoul,"조회").equals("조회 18:30"),"time only no empty line even twoLines");
        d.hour=d.minute=false;d.day=true;check(d.format(date,seoul,"조회").equals("조회 15일"),"date only no empty time line");
        WidgetStyle.DateSpec defaults=new WidgetStyle.DateSpec();
        check(defaults.format(date,TimeZone.getTimeZone("America/New_York"),"").equals("9.15 5:30"),"device timezone respected");
        check(defaults.format(Instant.parse("2026-03-08T06:59:00Z").getEpochSecond(),TimeZone.getTimeZone("America/New_York"),"").equals("3.8 1:59"),"DST before");
        check(defaults.format(Instant.parse("2026-03-08T07:00:00Z").getEpochSecond(),TimeZone.getTimeZone("America/New_York"),"").equals("3.8 3:00"),"DST after");
        // Every independent year/month/day/weekday/hour/minute/second selection,
        // including the entirely empty and time-only/date-only combinations.
        for(int mask=0;mask<128;mask++){
            WidgetStyle.DateSpec each=empty();each.year=(mask&1)!=0;each.month=(mask&2)!=0;each.day=(mask&4)!=0;each.weekday=(mask&8)!=0;
            each.hour=(mask&16)!=0;each.minute=(mask&32)!=0;each.second=(mask&64)!=0;each.twoLines=true;
            String result=each.format(date,seoul,"");
            check(!result.startsWith(" ")&&!result.endsWith(" ")&&!result.startsWith("\n")&&!result.endsWith("\n")&&!result.contains("\n\n")&&(mask==0)==result.isEmpty(),"independent composition without empty pieces");
        }
        WidgetStyle clone=s.copy();clone.rows[0].sizeSp=73;clone.order[0]=3;clone.resetDate.year=true;
        check(s.rows[0].sizeSp!=73&&s.order[0]==0&&!s.resetDate.year,"deep copy");
        s.opacity=999;s.radius=-5;s.brandMode=99;s.brandLogoSizeSp=Float.NaN;s.feedbackDurationMs=1164;
        s.rows[0].sizeSp=Float.POSITIVE_INFINITY;s.rows[1].sizeSp=14.3f;s.rows[2].sizeSp=999;s.rows[3].offsetY=-999;
        s.rows[0].font=-9;s.rows[1].font=99;s.rows[0].alignment=99;s.rows[2].color=0x00123456;s.order=new int[]{3,3,-1,2,8};s.normalize();
        check(s.opacity==100&&s.radius==0&&s.brandMode==3&&s.brandLogoSizeSp==16,"global bounds");
        check(s.feedbackDurationMs==1200,"feedback 100ms precision");
        check(s.rows[0].sizeSp==12&&s.rows[1].sizeSp==14.5f&&s.rows[2].sizeSp==96,"independent row size bounds and half step");
        check(s.rows[3].offsetY==-50&&s.rows[0].font==0&&s.rows[1].font==4&&s.rows[0].alignment==2,"position/font/alignment bounds");
        check(s.rows[2].color==0xff123456,"row text opaque independent of background");
        check(Arrays.equals(s.order,new int[]{3,2,0,1}),"row order deduplicated preserves first occurrence");
        s.feedbackDurationMs=-999;s.normalize();check(s.feedbackDurationMs==100,"feedback minimum");s.feedbackDurationMs=Integer.MAX_VALUE;s.normalize();check(s.feedbackDurationMs==10000,"feedback maximum");
        s.rows=null;s.order=null;s.resetDate=null;s.lastDate=null;s.normalize();check(s.rows.length==4&&s.order.length==4&&s.resetDate!=null&&s.lastDate!=null,"corrupt local model safe reset");
        check(WidgetStyle.dimension(Float.NaN)==64&&WidgetStyle.dimension(Float.POSITIVE_INFINITY)==64&&WidgetStyle.dimension(0)==64&&WidgetStyle.dimension(-1)==64,"invalid host dimensions safe");
        check(WidgetStyle.dimension(2)==24&&WidgetStyle.dimension(9000)==1200,"host bounds");
        check(WidgetStyle.bitmapPixelBudget(320,640,4)==61440,"small display aggregate bitmap budget");
        check(WidgetStyle.bitmapPixelBudget(1440,3120,4)==220000,"S25 bitmap budget cap");
        check(WidgetStyle.bitmapPixelBudget(0,0,4)==61440,"unknown display uses conservative budget");
        for(int[] screen:new int[][]{{240,320},{320,640},{480,800},{1080,2400},{1440,3120}})
            check((long)WidgetStyle.bitmapPixelBudget(screen[0],screen[1],4)*4<=(long)screen[0]*screen[1]*1.2,"all four bitmap variants below platform memory allowance");
        for(int viewport:new int[]{32,40,51,64,88,102,200}){
            for(float[] offsets:new float[][]{{0,0,0,0},{-50,-50,-50,-50},{50,50,50,50},{50,-50,50,-50}}){
                float[] heights={viewport*.18f,viewport*.12f,viewport*.12f,viewport*.12f};float padding=3,gap=1;
                WidgetStyle.Placement place=WidgetStyle.place(viewport,padding,heights,offsets,gap);
                boolean okay=!place.overflow&&place.tops[0]>=padding-.01f&&bottom(place,heights)<=viewport-padding+.01f;
                for(int i=1;i<4;i++)okay&=place.tops[i]>=place.tops[i-1]+heights[i-1]+gap-.01f;
                check(okay,"four-row safe ordered placement within 1x1/2x1 height");
            }
        }
        check(WidgetStyle.place(40,3,new float[]{30,20},new float[]{0,0},2).overflow,"oversized explicit font layout warns");
        check(WidgetStyle.place(88,4,new float[]{20,12},new float[]{50,-50},2).adjusted,"conflicting Y offsets clamp and warn");
        check(WidgetStyle.place(88,4,new float[0],new float[0],2).tops.length==0,"all rows optional safe");
        WidgetStyle padded=WidgetStyle.defaults();
        check(padded.automaticPadding&&padded.paddingHorizontalDp==5&&padded.paddingVerticalDp==5&&padded.rowGapDp==3,"padding defaults preserve old automatic layout");
        padded.automaticPadding=false;padded.paddingHorizontalDp=2.5f;padded.paddingVerticalDp=7.5f;padded.rowGapDp=4.5f;
        WidgetStyle paddedCopy=padded.copy();paddedCopy.paddingHorizontalDp=9;
        check(!paddedCopy.automaticPadding&&paddedCopy.paddingVerticalDp==7.5f&&paddedCopy.rowGapDp==4.5f&&padded.paddingHorizontalDp==2.5f,"padding globals copied independently");
        WidgetStyle.Spacing resolved=padded.spacing(138,88,4);
        check(resolved.horizontal==2.5f&&resolved.vertical==7.5f&&resolved.gap==4.5f&&!resolved.paddingAdjusted&&!resolved.gapAdjusted,"ordinary custom H/V padding and gap used exactly");
        check(resolved.innerWidth==133&&resolved.innerHeight==73,"horizontal and vertical content dimensions independent");
        padded.paddingHorizontalDp=0;padded.paddingVerticalDp=0;padded.rowGapDp=0;resolved=padded.spacing(64,88,4);
        check(resolved.horizontal==0&&resolved.vertical==0&&resolved.gap==0&&resolved.innerWidth==64&&resolved.innerHeight==88,"zero internal padding and row gap stay zero");
        padded.paddingHorizontalDp=32;padded.paddingVerticalDp=32;padded.rowGapDp=16;resolved=padded.spacing(32,32,4);
        check(resolved.horizontal==10&&resolved.vertical==10&&resolved.innerWidth==12&&resolved.innerHeight==12&&resolved.gap==0,"extreme 1x1 settings preserve real positive content");
        check(resolved.paddingAdjusted&&resolved.gapAdjusted&&padded.paddingHorizontalDp==32&&padded.paddingVerticalDp==32&&padded.rowGapDp==16,"runtime clamps warned without changing requested settings");
        padded.paddingHorizontalDp=Float.NaN;padded.paddingVerticalDp=Float.POSITIVE_INFINITY;padded.rowGapDp=Float.NEGATIVE_INFINITY;padded.normalize();
        check(padded.paddingHorizontalDp==5&&padded.paddingVerticalDp==5&&padded.rowGapDp==3,"nonfinite spacing falls back safely");
        padded.paddingHorizontalDp=-4;padded.paddingVerticalDp=99;padded.rowGapDp=99;padded.normalize();
        check(padded.paddingHorizontalDp==0&&padded.paddingVerticalDp==32&&padded.rowGapDp==16,"spacing bounds normalized");
        padded.paddingHorizontalDp=3.3f;padded.paddingVerticalDp=7.7f;padded.rowGapDp=4.3f;padded.normalize();
        check(padded.paddingHorizontalDp==3.5f&&padded.paddingVerticalDp==7.5f&&padded.rowGapDp==4.5f,"spacing half-dp precision");
        for(float[] viewport:new float[][]{{24,24},{32,32},{40,40},{57,102},{64,88},{127,51},{138,88},{300,300}}){
            padded.automaticPadding=true;padded.paddingHorizontalDp=32;padded.paddingVerticalDp=0;padded.rowGapDp=16;
            resolved=padded.spacing(viewport[0],viewport[1],4);float oldPad=Math.min(10,Math.max(3,Math.min(viewport[0],viewport[1])*.07f));
            check(resolved.horizontal==oldPad&&resolved.vertical==oldPad&&resolved.gap==Math.min(4,Math.max(1,viewport[1]*.025f))&&!resolved.paddingAdjusted&&!resolved.gapAdjusted,"automatic spacing exactly preserves existing sizing");
            padded.automaticPadding=false;
            for(float[] values:new float[][]{{0,0,0},{5,5,3},{32,0,16},{0,32,16},{32,32,16}}){
                padded.paddingHorizontalDp=values[0];padded.paddingVerticalDp=values[1];padded.rowGapDp=values[2];
                for(int count=1;count<=4;count++)for(boolean fit:new boolean[]{false,true}){
                    padded.autoFit=fit;resolved=padded.spacing(viewport[0],viewport[1],count);
                    float contentHeight=resolved.innerHeight-resolved.gap*(count-1);float[] rows=new float[count],offsets=new float[count];
                    Arrays.fill(rows,contentHeight/count);for(int i=0;i<count;i++)offsets[i]=i%2==0?50:-50;
                    WidgetStyle.Placement place=WidgetStyle.place(viewport[1],resolved.vertical,rows,offsets,resolved.gap);
                    boolean inside=resolved.innerWidth>=12&&resolved.innerHeight>=12&&contentHeight>=count*3-.01f&&!place.overflow&&place.tops[0]>=resolved.vertical-.01f&&bottom(place,rows)<=viewport[1]-resolved.vertical+.01f;
                    for(int i=1;i<count;i++)inside&=place.tops[i]>=place.tops[i-1]+rows[i-1]+resolved.gap-.01f;
                    check(inside,"small/resized manual padding and gap leave ordered positive content with either auto-fit setting");
                }
            }
        }
        resolved=padded.spacing(Float.NaN,-9,4);check(Float.isFinite(resolved.innerWidth)&&Float.isFinite(resolved.innerHeight)&&resolved.innerWidth>0&&resolved.innerHeight>0,"invalid host sizes remain safe under custom padding");
        System.out.println("PASS: "+passed+" widget style/date/layout checks (pure Java; no Android Canvas or launcher runtime test).");
    }
}
