package ua.max.tomrun;
/** Dependency-free numerical kernel shared by Android and JVM checks. */
public final class RgbMatcher {
 public static final int NX=6,NY=8,N=NX*NY*3;
 private RgbMatcher(){}
 public static void sample(int[] pixels,int stride,int x,int y,int w,int h,float[] out){
  int i=0;float mean=0;
  for(int yy=0;yy<NY;yy++)for(int xx=0;xx<NX;xx++){
   int c=pixels[(y+Math.min(h-1,(2*yy+1)*h/(2*NY)))*stride+x+Math.min(w-1,(2*xx+1)*w/(2*NX))];
   out[i++]=(c>>16)&255;out[i++]=(c>>8)&255;out[i++]=c&255;
  }
  for(float f:out)mean+=f;mean/=N;float norm=0;
  for(i=0;i<N;i++){out[i]-=mean;norm+=out[i]*out[i];}norm=(float)Math.sqrt(norm);
  if(norm<1){java.util.Arrays.fill(out,0);return;}
  for(i=0;i<N;i++)out[i]/=norm;
 }
 public static float score(float[] a,float[] b){float s=0;for(int i=0;i<N;i++)s+=a[i]*b[i];return s;}
}
