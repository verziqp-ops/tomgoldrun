package ua.max.tomrun;
/** Perspective approximations in image coordinates, not game-engine hitboxes. */
public final class SceneGeometry {
 private SceneGeometry(){}
 public static boolean hazard(String kind){return kind.equals("BLOCK")||kind.equals("JUMP")||kind.equals("SLIDE")||kind.equals("BARREL")||kind.equals("PORTAL");}
 public static float spread(float y){return .07f+.28f*Math.max(0,Math.min(1,(y-.18f)/.55f));}
 public static int lane(float x,float y){float s=spread(y);return x<.5f-s/2?0:x>.5f+s/2?2:1;}
 public static float anchor(String kind,float y,float height){return hazard(kind)?Math.min(1,y+height/2):y;}
 public static int[] lanes(String kind,float x,float y,float width,float height){float foot=anchor(kind,y,height);if(!kind.equals("SLIDE"))return new int[]{lane(x,foot)};
  int[] out=new int[3];int n=0;float s=spread(foot);for(int l=0;l<3;l++){float center=.5f+(l-1)*s;if(center>=x-width/2&&center<=x+width/2)out[n++]=l;}return n==0?new int[]{lane(x,foot)}:java.util.Arrays.copyOf(out,n);
 }
 public static float overlap(float ax,float ay,float aw,float ah,float bx,float by,float bw,float bh){float w=Math.max(0,Math.min(ax+aw/2,bx+bw/2)-Math.max(ax-aw/2,bx-bw/2)),h=Math.max(0,Math.min(ay+ah/2,by+bh/2)-Math.max(ay-ah/2,by-bh/2));return w*h/Math.max(.000001f,Math.min(aw*ah,bw*bh));}
}
