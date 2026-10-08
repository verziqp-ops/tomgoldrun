import ua.max.tomrun.RgbMatcher;
import java.util.*;
public class MatcherTests {
 public static void main(String[] args){
  int[] pixels=new int[60*80];int[] brighter=new int[60*80];
  Random random=new Random(17);
  for(int i=0;i<pixels.length;i++){int r=random.nextInt(150),g=random.nextInt(150),b=random.nextInt(150);pixels[i]=0xff000000|(r<<16)|(g<<8)|b;brighter[i]=0xff000000|((r+30)<<16)|((g+30)<<8)|(b+30);}
  float[] a=new float[RgbMatcher.N],b=new float[RgbMatcher.N];
  RgbMatcher.sample(pixels,60,0,0,60,80,a);RgbMatcher.sample(brighter,60,0,0,60,80,b);
  if(Math.abs(RgbMatcher.score(a,b)-1)>1e-5)throw new AssertionError("brightness invariance");
  Arrays.fill(pixels,0xff555555);RgbMatcher.sample(pixels,60,0,0,60,80,b);
  if(RgbMatcher.score(a,b)!=0)throw new AssertionError("uniform patch must not match");
  // Test all configured small/edge windows for out-of-bounds access and finite scores.
  for(int w=1;w<=60;w++)for(int h=1;h<=80;h++){
   RgbMatcher.sample(brighter,60,60-w,80-h,w,h,b);
   if(!Float.isFinite(RgbMatcher.score(a,b)))throw new AssertionError("finite score");
  }
  System.out.println("PASS matcher invariance, uniform rejection, 4800 edge windows");
 }
}
