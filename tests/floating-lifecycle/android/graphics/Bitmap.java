package android.graphics;

/** Pixel fixture only; Android rendering itself is covered by the separate runtime harness. */
public final class Bitmap {
    private final int width,height;
    private final int[] pixels;
    public boolean recycled;
    public int reads;
    public Bitmap(int width,int height,int[] pixels){this.width=width;this.height=height;this.pixels=pixels.clone();}
    public int getWidth(){return width;}
    public int getHeight(){return height;}
    public void getPixels(int[] target,int offset,int stride,int x,int y,int w,int h){
        if(recycled)throw new IllegalStateException("Reading a recycled bitmap");
        if(x<0||y<0||w<0||h<0||x+w>width||y+h>height)throw new IllegalArgumentException("Invalid pixel rectangle");
        reads++;
        for(int row=0;row<h;row++)System.arraycopy(pixels,(y+row)*width+x,target,offset+row*stride,w);
    }
    public void recycle(){if(recycled)throw new IllegalStateException("Bitmap recycled twice");recycled=true;}
}
