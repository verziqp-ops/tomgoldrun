package ua.max.tomrun;
import java.io.*;import java.util.*;
/** Small NumPy-trained MLP. Scores are classifier outputs, not calibrated probabilities. */
public final class VehicleModel {
 private final float[] weights=new float[96*24],bias=new float[24],output=new float[24];private final float outBias;
 public VehicleModel(InputStream input)throws IOException{try(DataInputStream in=new DataInputStream(input)){if(in.readInt()!=0x54524d4c||in.readInt()!=96||in.readInt()!=24)throw new IOException("Invalid vehicle model");for(int i=0;i<weights.length;i++)weights[i]=in.readFloat();for(int i=0;i<24;i++)bias[i]=in.readFloat();for(int i=0;i<24;i++)output[i]=in.readFloat();outBias=in.readFloat();}}
 public float score(int[] pixels,int stride,int x,int y,int w,int h){float[] v=new float[96];float[] gray=new float[24];
  for(int cy=0;cy<6;cy++)for(int cx=0;cx<4;cx++){int cell=cy*4+cx,n=0;float r=0,g=0,b=0;for(int yy=y+cy*h/6;yy<y+(cy+1)*h/6;yy++)for(int xx=x+cx*w/4;xx<x+(cx+1)*w/4;xx++){int p=pixels[yy*stride+xx];r+=(p>>16)&255;g+=(p>>8)&255;b+=p&255;n++;}if(n==0)return 0;r/=n*255f;g/=n*255f;b/=n*255f;v[cell*3]=r*2-1;v[cell*3+1]=g*2-1;v[cell*3+2]=b*2-1;gray[cell]=(r+g+b)/3;}
  for(int cy=0;cy<6;cy++)for(int cx=0;cx<4;cx++){int k=cy*4+cx;float edge=0;if(cx<3)edge+=Math.abs(gray[k+1]-gray[k]);if(cy<5)edge+=Math.abs(gray[k+4]-gray[k]);v[72+k]=edge*2;}
  float logit=outBias;for(int j=0;j<24;j++){float z=bias[j];for(int i=0;i<96;i++)z+=v[i]*weights[i*24+j];logit+=(float)Math.tanh(z)*output[j];}return (float)(1/(1+Math.exp(-logit)));
 }
 public static final class Hit {public final int x,y,w,h;public final float score;Hit(int x,int y,int w,int h,float s){this.x=x;this.y=y;this.w=w;this.h=h;score=s;}}
 public List<Hit> detect(int[] p,int w,int h){List<Hit> candidates=new ArrayList<>();
  for(int width:new int[]{12,18,26,38,54,72})for(float aspect:new float[]{.85f,1.35f,1.9f}){int height=Math.round(width*aspect);for(int y=Math.round(h*.20f);y+height<h*.64f;y+=6){float centerY=(y+height*.5f)/h,spread=.07f+.28f*Math.min(1,Math.max(0,(centerY-.18f)/.55f));for(int lane=0;lane<3;lane++){int center=Math.round(w*(.5f+(lane-1)*spread));for(int shift:new int[]{-4,0,4}){int x=center-width/2+shift;if(x<0||x+width>w)continue;float s=score(p,w,x,y,width,height);if(s>=.99f)candidates.add(new Hit(x,y,width,height,s));}}}}
  candidates.sort((a,b)->Float.compare(b.score,a.score));List<Hit> out=new ArrayList<>();for(Hit a:candidates){boolean overlap=false;for(Hit b:out){int area=Math.max(0,Math.min(a.x+a.w,b.x+b.w)-Math.max(a.x,b.x))*Math.max(0,Math.min(a.y+a.h,b.y+b.h)-Math.max(a.y,b.y));if(area>.35f*Math.min(a.w*a.h,b.w*b.h)){overlap=true;break;}}if(!overlap){out.add(a);if(out.size()==6)break;}}return out;
 }
}
