package com.shouyun.theartofstockings.client;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;

/** Pixel editing independent of the window and GPU; ARGB pixels preserve fabric transparency. */
public final class StockingsCanvas {
    public static final int SIZE=256;
    public enum Region {
        FRONT(0,32,48,168),BACK(96,32,48,168),LEFT(144,32,48,168),RIGHT(48,32,48,168),
        SOLE(0,208,48,48),CUFF(0,0,192,20),ALL(0,0,256,256);
        public final int x,y,w,h;
        Region(int x,int y,int w,int h) { this.x=x;this.y=y;this.w=w;this.h=h; }
        public boolean contains(int px,int py) { return px>=x && px<x+w && py>=y && py<y+h; }
    }
    private final BufferedImage image=new BufferedImage(SIZE,SIZE,BufferedImage.TYPE_INT_ARGB);
    private final int[] original;
    private final ArrayDeque<int[]> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    public StockingsCanvas(BufferedImage source) {
        var graphics=image.createGraphics();
        try {
            graphics.setComposite(java.awt.AlphaComposite.Src);
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.drawImage(source,0,0,SIZE,SIZE,null);
        } finally { graphics.dispose(); }
        original=pixels();
    }
    public BufferedImage image() { return image; }
    public int sample(int x,int y) { return image.getRGB(x,y); }
    private int[] pixels() { return image.getRGB(0,0,SIZE,SIZE,null,0,SIZE); }
    private void pixels(int[] data) { image.setRGB(0,0,SIZE,SIZE,data,0,SIZE); }
    public void beginStroke() { undo.addLast(pixels());while(undo.size()>20)undo.removeFirst();redo.clear(); }
    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }
    public void undo() { if(canUndo()) { redo.addLast(pixels());pixels(undo.removeLast()); } }
    public void redo() { if(canRedo()) { undo.addLast(pixels());pixels(redo.removeLast()); } }
    public void restore() { beginStroke();pixels(original); }
    public void clear(Region region) {
        beginStroke();
        for(int y=region.y;y<region.y+region.h;y++)for(int x=region.x;x<region.x+region.w;x++)image.setRGB(x,y,0);
    }
    private static int blend(int below,int above) {
        int a=above>>>24;if(a==255)return above;if(a==0)return below;
        int ba=below>>>24,out=a+(ba*(255-a)+127)/255;
        int result=out<<24;
        for(int shift:new int[]{16,8,0}) {
            int n=((above>>>shift)&255)*a+(((below>>>shift)&255)*ba*(255-a)+127)/255;
            result|=Math.clamp((n+out/2)/out,0,255)<<shift;
        }
        return result;
    }
    private void stamp(int cx,int cy,int size,int color,boolean erase,Region region) {
        int x0=cx-size/2,y0=cy-size/2;
        for(int y=y0;y<y0+size;y++)for(int x=x0;x<x0+size;x++)if(region.contains(x,y))
            image.setRGB(x,y,erase?0:blend(image.getRGB(x,y),color));
    }
    public void line(int x0,int y0,int x1,int y1,int size,int color,boolean erase,Region region) {
        size=Math.clamp(size,1,16);
        int dx=Math.abs(x1-x0),dy=-Math.abs(y1-y0),sx=x0<x1?1:-1,sy=y0<y1?1:-1,error=dx+dy;
        while(true) {
            stamp(x0,y0,size,color,erase,region);
            if(x0==x1 && y0==y1)break;
            int twice=2*error;
            if(twice>=dy) { error+=dy;x0+=sx; }
            if(twice<=dx) { error+=dx;y0+=sy; }
        }
    }
    public void fill(int x,int y,int color,Region region) {
        if(!region.contains(x,y) || image.getRGB(x,y)==color)return;
        beginStroke();int target=image.getRGB(x,y);
        var queue=new ArrayDeque<Integer>();queue.add(y*SIZE+x);image.setRGB(x,y,color);
        while(!queue.isEmpty()) {
            int position=queue.removeFirst(),px=position%SIZE,py=position/SIZE;
            for(int i=0;i<4;i++) {
                int nx=px+(i==0?1:i==1?-1:0),ny=py+(i==2?1:i==3?-1:0);
                if(region.contains(nx,ny) && image.getRGB(nx,ny)==target) { image.setRGB(nx,ny,color);queue.add(ny*SIZE+nx); }
            }
        }
    }
}
