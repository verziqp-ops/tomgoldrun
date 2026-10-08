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

 /** Same centered RGB correlation, computed without normalizing every search window. */
 public static float scorePatch(int[] pixels,int stride,int x,int y,int w,int h,float[] template){
  float sum=0,squares=0,dot=0;int i=0;
  for(int yy=0;yy<NY;yy++){int row=(y+Math.min(h-1,(2*yy+1)*h/(2*NY)))*stride+x;
   for(int xx=0;xx<NX;xx++){int c=pixels[row+Math.min(w-1,(2*xx+1)*w/(2*NX))];float r=(c>>16)&255,g=(c>>8)&255,b=c&255;
    sum+=r+g+b;squares+=r*r+g*g+b*b;dot+=template[i++]*r+template[i++]*g+template[i++]*b;
   }
  }
  float variance=squares-sum*sum/N;return variance<1?0:dot/(float)Math.sqrt(variance);
 }
 public static final class Hit {public final int x,y,w,h;public final float score;Hit(int x,int y,int w,int h,float s){this.x=x;this.y=y;this.w=w;this.h=h;score=s;}}
 public static java.util.List<Hit> search(int[] pixels,int width,int height,float[] template,float aspect,float minY,float maxY,int[] sizes,float threshold){
  java.util.List<Hit> seeds=new java.util.ArrayList<>();float[] patch=new float[N];
  for(int sw:sizes){int sh=Math.round(sw*aspect);if(sw>=width||sh>=height)continue;int lo=Math.max(0,(int)(height*minY)-sh/2),hi=Math.min(height-sh,(int)(height*maxY)-sh/2);
   for(int y=lo;y<=hi;y+=4)for(int x=0;x<=width-sw;x+=4){float value=scorePatch(pixels,width,x,y,sw,sh,template);if(value<.5f)continue;
    int index=0;while(index<seeds.size()&&seeds.get(index).score>value)index++;if(index<24){seeds.add(index,new Hit(x,y,sw,sh,value));if(seeds.size()>24)seeds.remove(24);}
   }
  }
  java.util.List<Hit> hits=new java.util.ArrayList<>();
  for(Hit seed:seeds){Hit best=seed;int lo=Math.max(0,(int)(height*minY)-seed.h/2),hi=Math.min(height-seed.h,(int)(height*maxY)-seed.h/2);
   for(int y=Math.max(lo,seed.y-4);y<=Math.min(hi,seed.y+4);y+=1)for(int x=Math.max(0,seed.x-4);x<=Math.min(width-seed.w,seed.x+4);x+=1){float value=scorePatch(pixels,width,x,y,seed.w,seed.h,template);if(value>best.score)best=new Hit(x,y,seed.w,seed.h,value);}
   if(best.score>=threshold)hits.add(best);
  }
  hits.sort((a,b)->Float.compare(b.score,a.score));java.util.List<Hit> kept=new java.util.ArrayList<>();
  for(Hit hit:hits){boolean duplicate=false;for(Hit k:kept)if(Math.abs((k.x+k.w*.5f)-(hit.x+hit.w*.5f))<Math.max(k.w,hit.w)*.65f&&Math.abs((k.y+k.h*.5f)-(hit.y+hit.h*.5f))<Math.max(k.h,hit.h)*.65f){duplicate=true;break;}if(!duplicate)kept.add(hit);if(kept.size()>=4)break;}
  return kept;
 }
}
