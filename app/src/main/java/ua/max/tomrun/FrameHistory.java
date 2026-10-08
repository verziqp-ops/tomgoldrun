package ua.max.tomrun;
/** Grayscale game area, four frames. UI/HUD and bottom overlay excluded. */
public final class FrameHistory {
 public static final int WIDTH=20,HEIGHT=28,FRAMES=4,INPUTS=WIDTH*HEIGHT*FRAMES+4;
 private final byte[][] frames=new byte[FRAMES][];private int count,index;
 public void reset(){count=0;index=0;}
 public byte[] observe(int[] pixels,int w,int h,int mode){
  if(pixels.length!=w*h||w<1||h<1||mode<0||mode>3)throw new IllegalArgumentException();byte[] f=new byte[WIDTH*HEIGHT];
  for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++){
   int x0=x*w/WIDTH,x1=Math.max(x0+1,(x+1)*w/WIDTH);int y0=(int)(h*(.20+.55*y/HEIGHT)),y1=Math.max(y0+1,(int)(h*(.20+.55*(y+1)/HEIGHT)));long sum=0;int n=0;
   for(int yy=y0;yy<Math.min(h,y1);yy++)for(int xx=x0;xx<Math.min(w,x1);xx++){int p=pixels[yy*w+xx];sum+=(77*((p>>16)&255)+150*((p>>8)&255)+29*(p&255))>>8;n++;}f[y*WIDTH+x]=(byte)(sum/Math.max(1,n));
  }if(count==0){for(int i=0;i<FRAMES;i++)frames[i]=f.clone();count=FRAMES;}frames[index]=f;index=(index+1)%FRAMES;
  byte[] result=new byte[INPUTS];for(int i=0;i<FRAMES;i++)System.arraycopy(frames[(index+i)%FRAMES],0,result,i*f.length,f.length);result[WIDTH*HEIGHT*FRAMES+mode]=(byte)255;return result;
 }
}
