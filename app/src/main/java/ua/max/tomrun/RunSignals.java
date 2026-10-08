package ua.max.tomrun;
/** Conservative detector for the cyan SAVE ME panel in provided gameplay. Unknown menus need manual marking. */
public final class RunSignals {
 private int votes;
 public void reset(){votes=0;}
 public boolean observe(int[] p,int w,int h){boolean visible=isRevivePanel(p,w,h);votes=visible?votes+1:0;return votes>=3;}
 public static boolean isRevivePanel(int[] p,int w,int h){
  if(w<20||h<40||p.length!=w*h)return false;
  int cyan=0,total=0,yellow=0,yn=0;for(int y=(int)(h*.39);y<h*.63;y+=2)for(int x=(int)(w*.17);x<w*.82;x+=2){int c=p[y*w+x],r=(c>>16)&255,g=(c>>8)&255,b=c&255;total++;if(g>155&&b>155&&g-r>15&&b-r>15&&Math.abs(g-b)<50)cyan++;}
  for(int y=(int)(h*.52);y<h*.58;y+=2)for(int x=(int)(w*.38);x<w*.64;x+=2){int c=p[y*w+x],r=(c>>16)&255,g=(c>>8)&255,b=c&255;yn++;if(r>190&&g>115&&b<110)yellow++;}
  return total>0&&yn>0&&cyan>(total*.48)&&yellow>(yn*.25);
 }
}
