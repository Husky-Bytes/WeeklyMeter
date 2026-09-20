package android.graphics;
public final class Rect {
    public final int left,top,right,bottom;
    public Rect(int left,int top,int right,int bottom){this.left=left;this.top=top;this.right=right;this.bottom=bottom;}
    public int width(){return right-left;} public int height(){return bottom-top;}
    @Override public boolean equals(Object other){if(!(other instanceof Rect))return false;Rect r=(Rect)other;return left==r.left&&top==r.top&&right==r.right&&bottom==r.bottom;}
    @Override public int hashCode(){return left*31+top*17+right*7+bottom;}
}
