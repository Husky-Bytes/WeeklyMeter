package dev.yerin.weeklymeter;

import java.util.*;

/** Pure selection model for launcher-reported content sizes, not a guessed grid size. */
final class HomeWidgetPreviewSizes {
    static final class Size {
        final int widgetId;final float widthDp,heightDp;final boolean estimated;
        Size(int widgetId,float widthDp,float heightDp,boolean estimated){this.widgetId=widgetId;this.widthDp=widthDp;this.heightDp=heightDp;this.estimated=estimated;}
        String key(){return widgetId+":"+widthDp+":"+heightDp+":"+estimated;}
    }
    static List<Size> host(int id,float[][] sizes,float minWidth,float minHeight,float maxWidth,float maxHeight,boolean landscape){
        List<Size> found=new ArrayList<>();
        if(sizes!=null)for(float[] size:sizes){if(size!=null&&size.length>=2)add(found,id,size[0],size[1]);if(found.size()==4)break;}
        float portraitWidth=minWidth,portraitHeight=valid(maxHeight)?maxHeight:minHeight;
        float landscapeWidth=valid(maxWidth)?maxWidth:minWidth,landscapeHeight=minHeight;
        float currentWidth=landscape?landscapeWidth:portraitWidth,currentHeight=landscape?landscapeHeight:portraitHeight;
        if(found.isEmpty()){
            add(found,id,currentWidth,currentHeight);
            add(found,id,landscape?portraitWidth:landscapeWidth,landscape?portraitHeight:landscapeHeight);
        }else if(valid(currentWidth)&&valid(currentHeight)){
            // Host size lists have no guaranteed order. Prefer the legacy pair matching
            // this Activity orientation, but retain every renderer-supported variant.
            int best=0;double score=Double.MAX_VALUE;
            for(int i=0;i<found.size();i++){Size s=found.get(i);double next=Math.abs(s.widthDp-currentWidth)+Math.abs(s.heightDp-currentHeight);if(next<score){score=next;best=i;}}
            if(best>0)found.add(0,found.remove(best));
        }
        return found;
    }
    static List<Size> estimates(){return Arrays.asList(new Size(-1,64,88,true),new Size(-1,138,88,true));}
    private static boolean valid(float value){return Float.isFinite(value)&&value>0&&value<=1200;}
    private static void add(List<Size> result,int id,float width,float height){
        if(!valid(width)||!valid(height))return;
        for(Size s:result)if(s.widthDp==width&&s.heightDp==height)return;
        result.add(new Size(id,width,height,false));
    }
    private HomeWidgetPreviewSizes(){}
}
