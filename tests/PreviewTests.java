package dev.yerin.weeklymeter;

import java.util.*;

/** Pure physical-size/viewport/host-option checks, not Android layout instrumentation. */
public final class PreviewTests {
    private static int count;
    private static void check(boolean result,String title){count++;if(!result)throw new AssertionError(title);}
    private static void eq(int expected,int actual,String title){check(expected==actual,title+" expected "+expected+" actual "+actual);}
    public static void main(String[] args){
        for(float density:new float[]{.75f,1,1.5f,2,2.625f,3,3.5f,4,8,10})for(float width:new float[]{48,64,88,96,128,138,160,240,300,360,411.5f,1200}){
            int exact=Math.round(width*density);eq(exact,PreviewGeometry.pixels(width,density),"selected dp maps to exact display pixels");
            for(int available:new int[]{120,240,340,500,900}){
                int viewport=PreviewGeometry.viewportHeight(available,80,exact);
                check(viewport>=1&&viewport<=exact,"viewport bounded without changing image size");
                eq(exact,PreviewGeometry.pixels(width,density),"small viewport never feeds a scale factor into image sizing");
            }
        }
        eq(1,PreviewGeometry.pixels(Float.NaN,3),"bad size safe");eq(1,PreviewGeometry.pixels(128,Float.NaN),"bad density safe");eq(1,PreviewGeometry.pixels(-1,3),"negative size safe");eq(1,PreviewGeometry.pixels(128,0),"zero density safe");
        for(int available:new int[]{1,80,120,240,340,500,900,1800})for(int chrome:new int[]{0,32,48,80,120,200})for(int requested:new int[]{1,48,96,128,240,300,900}){
            int result=PreviewGeometry.viewportHeight(available,chrome,requested);check(result>=1&&result<=requested,"viewport never grows or vanishes");
            if(available-chrome-available/2>=1)check(available-chrome-result>=available/2,"editing retains at least half of available height when chrome fits");
        }
        eq(0,PreviewGeometry.pan(-100,900,300),"negative pan clamped");eq(600,PreviewGeometry.pan(9999,900,300),"large pan clamped");eq(123,PreviewGeometry.pan(123,900,300),"valid pan preserved");eq(0,PreviewGeometry.pan(20,100,300),"small content centered without pan");
        List<HomeWidgetPreviewSizes.Size> sizes=HomeWidgetPreviewSizes.host(7,null,64,88,138,160,false);
        eq(2,sizes.size(),"legacy portrait and landscape variants available");check(sizes.get(0).widthDp==64&&sizes.get(0).heightDp==160&&!sizes.get(0).estimated,"portrait uses min width and max height");check(sizes.get(1).widthDp==138&&sizes.get(1).heightDp==88,"landscape alternative retained");
        sizes=HomeWidgetPreviewSizes.host(7,null,64,88,138,160,true);check(sizes.get(0).widthDp==138&&sizes.get(0).heightDp==88,"landscape orientation prefers max width min height");
        sizes=HomeWidgetPreviewSizes.host(7,new float[][]{{200,80},{90,170},{150.5f,110.5f}},90,80,200,170,false);
        eq(3,sizes.size(),"all reported size variants kept");check(sizes.get(0).widthDp==90&&sizes.get(0).heightDp==170,"host list defaults to orientation-matching pair");
        check(sizes.get(2).widthDp==150.5f&&sizes.get(2).heightDp==110.5f,"fractional host dp is not rounded before rendering");
        for(HomeWidgetPreviewSizes.Size size:sizes)check(size.widgetId==7&&!size.estimated,"reported size identifies installed widget, not estimated sample");
        String key=sizes.get(0).key();check(key.equals(HomeWidgetPreviewSizes.host(7,new float[][]{{90,170}},0,0,0,0,false).get(0).key()),"selection identity survives reread");
        check(!key.equals(HomeWidgetPreviewSizes.host(8,new float[][]{{90,170}},0,0,0,0,false).get(0).key()),"same dimensions from different widgets remain distinct");
        sizes=HomeWidgetPreviewSizes.host(7,new float[][]{null,{}, {1},{Float.NaN,80},{80,Float.POSITIVE_INFINITY},{0,80},{-1,80},{2000,80},{64,88},{64,88},{80,64},{100,100},{200,200},{300,300}},0,0,0,0,false);
        eq(4,sizes.size(),"invalid/duplicate sizes skipped and renderer variant limit respected");check(sizes.get(0).widthDp==64&&sizes.get(3).widthDp==200,"first four valid distinct variants match renderer selection");
        sizes=HomeWidgetPreviewSizes.host(7,new float[][]{{0,0}},64,88,0,0,false);eq(1,sizes.size(),"invalid size list falls back to available legacy bounds without duplication");
        check(HomeWidgetPreviewSizes.host(7,null,0,0,0,0,false).isEmpty(),"missing options do not invent a real installed size");
        sizes=HomeWidgetPreviewSizes.estimates();eq(2,sizes.size(),"two estimates only when caller needs fallback");
        check(sizes.get(0).estimated&&sizes.get(0).widthDp==64&&sizes.get(0).heightDp==88,"1x1 fallback explicitly estimated");check(sizes.get(1).estimated&&sizes.get(1).widthDp==138&&sizes.get(1).heightDp==88,"2x1 fallback explicitly estimated");
        System.out.println("PASS: "+count+" full-size preview geometry/host-option checks (pure Java; no Android view runtime)");
    }
}
