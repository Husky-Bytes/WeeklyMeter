package dev.yerin.weeklymeter;

/** Preview sizing in physical pixels; viewport limits never shrink the image. */
final class PreviewGeometry {
    static int pixels(float dp,float density){
        if(!Float.isFinite(dp)||dp<=0||!Float.isFinite(density)||density<=0)return 1;
        return Math.max(1,Math.round(dp*density));
    }
    static int viewportHeight(int available,int chrome,int wanted){
        int room=Math.max(1,available),reserved=Math.max(1,room/2);
        return Math.max(1,Math.min(Math.max(1,wanted),room-Math.max(0,chrome)-reserved));
    }
    static int pan(int requested,int content,int viewport){return Math.max(0,Math.min(Math.max(0,content-Math.max(0,viewport)),requested));}
    private PreviewGeometry(){}
}
