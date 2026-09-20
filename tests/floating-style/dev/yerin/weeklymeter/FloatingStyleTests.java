package dev.yerin.weeklymeter;

import android.content.Context;
import java.util.*;

public final class FloatingStyleTests {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static void eq(Object expected,Object actual,String label){check(Objects.equals(expected,actual),label+": expected="+expected+", actual="+actual);}
    private static Context.MemoryPreferences prefs(Context c,String name){return (Context.MemoryPreferences)c.getSharedPreferences(name,Context.MODE_PRIVATE);}
    private static void dates(WidgetStyle.DateSpec expected,WidgetStyle.DateSpec actual,String label){
        eq(expected.year,actual.year,label+" year");eq(expected.month,actual.month,label+" month");eq(expected.day,actual.day,label+" day");eq(expected.weekday,actual.weekday,label+" weekday");
        eq(expected.hour,actual.hour,label+" hour");eq(expected.minute,actual.minute,label+" minute");eq(expected.second,actual.second,label+" second");eq(expected.ampm,actual.ampm,label+" ampm");
        eq(expected.use24h,actual.use24h,label+" use24h");eq(expected.leadingZero,actual.leadingZero,label+" leading zero");eq(expected.twoLines,actual.twoLines,label+" lines");eq(expected.label,actual.label,label+" label");eq(expected.separator,actual.separator,label+" separator");
    }
    private static void styles(WidgetStyle expected,WidgetStyle actual,String label){
        eq(expected.background,actual.background,label+" background");eq(expected.opacity,actual.opacity,label+" opacity");eq(expected.radius,actual.radius,label+" radius");eq(expected.autoFit,actual.autoFit,label+" autofit");
        eq(expected.overallOpacity,actual.overallOpacity,label+" overall opacity");
        eq(expected.automaticPadding,actual.automaticPadding,label+" automatic padding");eq(expected.paddingHorizontalDp,actual.paddingHorizontalDp,label+" horizontal padding");eq(expected.paddingVerticalDp,actual.paddingVerticalDp,label+" vertical padding");eq(expected.rowGapDp,actual.rowGapDp,label+" gap");
        eq(expected.feedbackEnabled,actual.feedbackEnabled,label+" feedback");eq(expected.feedbackDurationMs,actual.feedbackDurationMs,label+" feedback duration");eq(expected.brandMode,actual.brandMode,label+" brand mode");eq(expected.brandLogoSizeSp,actual.brandLogoSizeSp,label+" logo size");
        check(Arrays.equals(expected.order,actual.order),label+" order");
        for(int i=0;i<4;i++){WidgetStyle.Row a=expected.rows[i],b=actual.rows[i];eq(a.enabled,b.enabled,label+" enabled "+i);eq(a.font,b.font,label+" font "+i);eq(a.sizeSp,b.sizeSp,label+" size "+i);eq(a.color,b.color,label+" color "+i);eq(a.bold,b.bold,label+" bold "+i);eq(a.alignment,b.alignment,label+" alignment "+i);eq(a.offsetY,b.offsetY,label+" offset "+i);}
        dates(expected.resetDate,actual.resetDate,label+" reset");dates(expected.lastDate,actual.lastDate,label+" last");
    }
    private static WidgetStyle customized(int seed){
        WidgetStyle s=WidgetStyle.defaults();s.background=0xff103050+seed;s.opacity=seed*7%101;s.overallOpacity=seed*13%101;s.radius=seed%33;s.autoFit=seed%2==0;s.automaticPadding=seed%3==0;
        s.paddingHorizontalDp=seed%32+.5f;s.paddingVerticalDp=(seed*3)%32+.5f;s.rowGapDp=seed%16+.5f;s.feedbackEnabled=seed%3!=0;s.feedbackDurationMs=100+(seed*700)%9900;s.brandMode=seed%4;s.brandLogoSizeSp=6+(seed*5)%58+.5f;
        for(int i=0;i<4;i++){WidgetStyle.Row r=s.rows[i];r.enabled=(seed+i)%2==0;r.font=(seed+i)%5;r.sizeSp=6+(seed+i*3)%90+.5f;r.color=0xff795230+seed+i;r.bold=(seed+i)%3==0;r.alignment=(seed+i)%3;r.offsetY=(seed+i*11)%100-50;}
        s.order=new int[]{(seed+3)%4,(seed+2)%4,(seed+1)%4,seed%4};
        for(WidgetStyle.DateSpec d:new WidgetStyle.DateSpec[]{s.resetDate,s.lastDate}){
            d.year=(seed&1)!=0;d.month=(seed&2)!=0;d.day=(seed&4)!=0;d.weekday=(seed&8)!=0;d.hour=(seed&1)==0;d.minute=(seed&2)==0;d.second=(seed&4)==0;d.ampm=(seed&8)==0;
            d.use24h=seed%2==0;d.leadingZero=seed%3==0;d.twoLines=seed%4==0;d.label=seed%5==0;d.separator=seed%4;seed++;
        }
        s.normalize();return s;
    }
    private static void defaultsAndIsolation(){
        Context c=new Context();styles(WidgetStyle.defaults(),FloatingPreferences.style(c),"floating defaults");
        eq(128,FloatingPreferences.widthDp(c),"default width");eq(96,FloatingPreferences.heightDp(c),"default height");
        eq(0,prefs(c,"floating_style").writes,"style reads do not write");eq(0,prefs(c,"floating_widget").writes,"dimension reads do not write");
        WidgetStyle home=customized(3),floating=customized(12);WidgetAppearance.save(c,"widget_style",home);styles(WidgetStyle.defaults(),FloatingPreferences.style(c),"no home copy");
        prefs(c,"credentials").values.put("sentinel","untouched");prefs(c,"usage").values.put("sentinel",42);
        FloatingPreferences.prefs(c).edit().putBoolean("enabled",true).putInt("x",12).apply();
        FloatingPreferences.saveStyle(c,floating);FloatingPreferences.saveSize(c,178,133);
        styles(home,WidgetAppearance.load(c,"widget_style"),"floating save keeps home");styles(floating,FloatingPreferences.style(c),"independent floating style");
        WidgetAppearance.save(c,"widget_style",customized(7));styles(floating,FloatingPreferences.style(c),"home save keeps floating");
        eq(178,FloatingPreferences.widthDp(c),"style saves keep size");eq(133,FloatingPreferences.heightDp(c),"style saves keep height");eq(true,FloatingPreferences.prefs(c).getBoolean("enabled",false),"size save keeps enabled");eq(12,FloatingPreferences.prefs(c).getInt("x",0),"size save keeps position");
        eq("untouched",prefs(c,"credentials").values.get("sentinel"),"credentials unchanged");eq(42,prefs(c,"usage").values.get("sentinel"),"cache unchanged");
        FloatingPreferences.saveStyle(c,WidgetStyle.defaults());FloatingPreferences.saveSize(c,128,96);styles(customized(7),WidgetAppearance.load(c,"widget_style"),"floating reset keeps home");
        WidgetStyle loaded=FloatingPreferences.style(c);loaded.rows[0].font=4;loaded.order[0]=3;styles(WidgetStyle.defaults(),FloatingPreferences.style(c),"load returns independent object");
    }
    private static void roundTrips(){
        Context c=new Context();for(int seed=0;seed<16;seed++){
            WidgetStyle value=customized(seed);FloatingPreferences.saveStyle(c,value);styles(value,FloatingPreferences.style(c),"full roundtrip "+seed);
            check(prefs(c,"floating_style").values.size()==3,"codec writes exactly style and feedback keys");
        }
        WidgetStyle unnormalized=customized(8);unnormalized.opacity=900;unnormalized.rows[0].sizeSp=1000;FloatingPreferences.saveStyle(c,unnormalized);
        eq(900,unnormalized.opacity,"save does not mutate original opacity");eq(1000f,unnormalized.rows[0].sizeSp,"save does not mutate original row");eq(100,FloatingPreferences.style(c).opacity,"saved opacity normalized");eq(96f,FloatingPreferences.style(c).rows[0].sizeSp,"saved row normalized");
    }
    private static void legacyMigration(){
        for(int format=0;format<4;format++)for(int size=0;size<3;size++)for(int align=0;align<2;align++){
            Context c=new Context();Context.MemoryPreferences p=prefs(c,"widget_style");p.edit().putInt("background",0xff246810).putInt("opacity",67).putInt("radius",9).putInt("text",0xff019876).putInt("reset",0xfff08023).putBoolean("show_reset",false).putBoolean("bold",false).putInt("alignment",align).putInt("text_size",size).putInt("date_format",format).putBoolean("feedback_enabled",false).putInt("feedback_duration_ms",3700).apply();
            WidgetStyle expected=WidgetStyle.defaults();expected.background=0xff246810;expected.opacity=67;expected.radius=9;expected.rows[0].color=0xff019876;expected.rows[1].color=0xfff08023;expected.rows[1].enabled=false;expected.rows[0].bold=false;expected.rows[0].sizeSp=size==0?27:size==2?37.5f:32;
            for(WidgetStyle.Row row:expected.rows)row.alignment=align==1?0:1;
            if(format==1)expected.resetDate.weekday=true;if(format==2){expected.resetDate.separator=1;expected.resetDate.use24h=false;expected.resetDate.ampm=true;}if(format==3){expected.resetDate.year=true;expected.resetDate.leadingZero=true;}
            expected.feedbackEnabled=false;expected.feedbackDurationMs=3700;styles(expected,WidgetAppearance.load(c,"widget_style"),"legacy "+format+"/"+size+"/"+align);eq(1,p.writes,"migration read does not write");
            styles(WidgetStyle.defaults(),FloatingPreferences.style(c),"legacy home does not migrate into floating");
        }
    }
    private static void malformedData(){
        String[] invalid={"{", "null", "[]", "{\"rows\":null,\"order\":[9,9,-1]}","{\"opacity\":\"wrong\",\"radius\":9223372036854775807}"};
        Context c=new Context();Context.MemoryPreferences p=prefs(c,"floating_style");for(String value:invalid){p.values.clear();p.values.put("style_v3",value);WidgetStyle loaded=FloatingPreferences.style(c);eq(88,loaded.opacity,"bad input default opacity");check(Arrays.equals(new int[]{0,1,2,3},loaded.order),"bad order normalized");}
        p.values.clear();p.values.put("style_v3",true);styles(WidgetStyle.defaults(),FloatingPreferences.style(c),"wrong style storage type");
        p.values.clear();p.values.put("feedback_duration_ms","wrong");styles(WidgetStyle.defaults(),FloatingPreferences.style(c),"wrong feedback storage type");
        p.values.clear();p.failRead=true;styles(WidgetStyle.defaults(),FloatingPreferences.style(c),"failed reads default");p.failRead=false;
        p.values.clear();p.values.put("style_v3",String.join("",Collections.nCopies(65537,"x")));p.values.put("opacity",24);eq(24,FloatingPreferences.style(c).opacity,"oversized data keeps legacy migration");
        p.values.clear();p.values.put("style_v3","{\"rows\":[{\"size\":1000,\"font\":99,\"offset_y\":-999}],\"opacity\":-9,\"order\":[3,3,0,99],\"reset_date\":{\"year\":true,\"month\":false}}");
        WidgetStyle s=FloatingPreferences.style(c);eq(96f,s.rows[0].sizeSp,"malformed size bounded");eq(4,s.rows[0].font,"malformed font bounded");eq(-50f,s.rows[0].offsetY,"offset bounded");eq(0,s.opacity,"opacity bounded");check(Arrays.equals(new int[]{3,0,1,2},s.order),"order deduplicated");eq(true,s.resetDate.year,"partial date loaded");eq(false,s.resetDate.month,"partial date loaded false");eq(true,s.resetDate.hour,"missing date part default retained");
    }
    private static void dimensions(){
        Context c=new Context();int[] values={Integer.MIN_VALUE,-1000,-1,0,47,48,49,95,96,127,128,299,300,301,359,360,361,1000,Integer.MAX_VALUE};
        for(int width:values)for(int height:values){FloatingPreferences.saveSize(c,width,height);eq(Math.max(48,Math.min(360,width)),FloatingPreferences.widthDp(c),"width save bound");eq(Math.max(48,Math.min(300,height)),FloatingPreferences.heightDp(c),"height save bound");}
        Context.MemoryPreferences p=prefs(c,"floating_widget");for(int value:values){p.values.put("width_dp",value);p.values.put("height_dp",value);eq(Math.max(48,Math.min(360,value)),FloatingPreferences.widthDp(c),"width read bound");eq(Math.max(48,Math.min(300,value)),FloatingPreferences.heightDp(c),"height read bound");}
        p.values.put("width_dp","128");p.values.put("height_dp",true);eq(128,FloatingPreferences.widthDp(c),"bad width type fallback");eq(96,FloatingPreferences.heightDp(c),"bad height type fallback");
        p.failRead=true;eq(128,FloatingPreferences.widthDp(c),"failed width read fallback");eq(96,FloatingPreferences.heightDp(c),"failed height read fallback");
    }
    private static void overallOpacity(){
        Context c=new Context();WidgetStyle home=WidgetStyle.defaults(),floating=WidgetStyle.defaults();home.opacity=61;floating.opacity=23;
        for(int value:new int[]{Integer.MIN_VALUE,-1,0,1,37,50,99,100,101,Integer.MAX_VALUE}){
            home.overallOpacity=value;floating.overallOpacity=100-Math.max(0,Math.min(100,value));
            WidgetAppearance.save(c,"widget_style",home);FloatingPreferences.saveStyle(c,floating);
            WidgetStyle actualHome=WidgetAppearance.load(c,"widget_style"),actualFloating=FloatingPreferences.style(c);
            eq(Math.max(0,Math.min(100,value)),actualHome.overallOpacity,"home overall save clamp");eq(floating.overallOpacity,actualFloating.overallOpacity,"floating overall isolated");
            eq(61,actualHome.opacity,"home background opacity unchanged");eq(23,actualFloating.opacity,"floating background opacity unchanged");eq(value,home.overallOpacity,"save does not normalize caller overall opacity");
        }
        for(String name:new String[]{"widget_style","floating_style"}){
            Context.MemoryPreferences p=prefs(c,name);p.values.clear();p.values.put("style_v3","{\"version\":3,\"opacity\":42,\"rows\":[{\"font\":2}]}");WidgetStyle old=WidgetAppearance.load(c,name);
            eq(100,old.overallOpacity,"old style_v3 missing overall opacity defaults to 100");eq(42,old.opacity,"old background preserved");eq(2,old.rows[0].font,"old typography preserved");
            for(String value:new String[]{"null","true","\"bad\"","9223372036854775807"}){
                p.values.put("style_v3","{\"opacity\":42,\"overall_opacity\":"+value+"}");eq(100,WidgetAppearance.load(c,name).overallOpacity,"invalid overall value safe fallback");
            }
            for(int value:new int[]{-500,0,50,100,500}){p.values.put("style_v3","{\"overall_opacity\":"+value+"}");eq(Math.max(0,Math.min(100,value)),WidgetAppearance.load(c,name).overallOpacity,"loaded overall opacity bound");}
        }
        home.overallOpacity=15;floating.overallOpacity=79;WidgetAppearance.save(c,"widget_style",home);FloatingPreferences.saveStyle(c,floating);
        FloatingPreferences.saveStyle(c,WidgetStyle.defaults());eq(15,WidgetAppearance.load(c,"widget_style").overallOpacity,"floating reset leaves home opacity");eq(100,FloatingPreferences.style(c).overallOpacity,"floating reset restores visible default");
        FloatingPreferences.saveStyle(c,floating);WidgetAppearance.save(c,"widget_style",WidgetStyle.defaults());eq(79,FloatingPreferences.style(c).overallOpacity,"home reset leaves floating opacity");eq(100,WidgetAppearance.load(c,"widget_style").overallOpacity,"home reset restores visible default");
    }
    public static void main(String[] args){defaultsAndIsolation();roundTrips();legacyMigration();malformedData();dimensions();overallOpacity();System.out.println("PASS: "+checks+" floating style/persistence checks (JVM preference doubles; no device UI test)");}
}
