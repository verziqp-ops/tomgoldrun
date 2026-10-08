package ua.max.tomrun;
import java.util.*;
/** Pure decision logic. Detections must use normalized coordinates, lane 0..2. */
public final class Planner {
 public enum Mode { GROUND, FLIGHT, BOSS_GROUND, BOSS_AIR }
 public enum Action { NONE, LEFT, RIGHT, JUMP, SLIDE }
 public static final class ObjectInfo {
  public final String kind; public final int lane; public final float y, confidence, ttc;
  public ObjectInfo(String k,int l,float y,float c,float t){kind=k;lane=l;this.y=y;confidence=c;ttc=t;}
 }
 public static final class Decision {
  public final Action action; public final String reason;public final int steps;public final int targetLane;
  Decision(Action a,String r){this(a,r,a==Action.NONE?0:1,-1);}
  Decision(Action a,String r,int n,int target){action=a;reason=r;steps=n;targetLane=target;}
 }
 public int lane=1; private long lastAction=-10000; private int observedLane=-1, laneVotes;private Action lastVertical=Action.NONE;private long lastVerticalAt=-10000;private int lastVerticalLane=-1;
 public void reset(){lane=1;lastAction=-10000;observedLane=-1;laneVotes=0;lastVertical=Action.NONE;lastVerticalAt=-10000;lastVerticalLane=-1;}
 public void observeLane(int l){if(l<0||l>2)return; if(observedLane==l)laneVotes++;else{observedLane=l;laneVotes=1;} if(laneVotes>=2)lane=l;}
 public void observeLane(int l,long now){if(now-lastAction<400)return;observeLane(l);}
 public void committed(Action a,long now){lastAction=now;observedLane=-1;laneVotes=0;if(a==Action.JUMP||a==Action.SLIDE){lastVertical=a;lastVerticalAt=now;lastVerticalLane=lane;}if(a==Action.LEFT)lane=Math.max(0,lane-1);if(a==Action.RIGHT)lane=Math.min(2,lane+1);}
 public boolean canContinueRoute(List<ObjectInfo> objects,int target,Mode mode){for(ObjectInfo o:objects){if(o.confidence<.83f||o.lane!=target||!danger(o.kind))continue;if(airborne(mode)&&!o.kind.equals("BARREL")&&!o.kind.equals("PORTAL"))continue;if(o.kind.equals("PORTAL")||o.y>=.48f||o.ttc<=.45f)return false;}return true;}
 private boolean danger(String k){return k.equals("BLOCK")||k.equals("JUMP")||k.equals("SLIDE")||k.equals("BARREL")||k.equals("PORTAL");}
 private boolean airborne(Mode m){return m==Mode.FLIGHT||m==Mode.BOSS_AIR;}
 public Decision decide(List<ObjectInfo> objects,Mode mode,long now){
  if(now-lastAction<80)return new Decision(Action.NONE,"Очікування завершення маневру");
  float[] risk=new float[3],reward=new float[3],nearest=new float[3];float[] arrival={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY};boolean[] portal=new boolean[3];ObjectInfo urgent=null;
  for(ObjectInfo o:objects){
   if(o.confidence<.83f||o.lane<0||o.lane>2)continue;
   if(mode==Mode.FLIGHT&&danger(o.kind)&&!o.kind.equals("PORTAL"))continue;
   if(airborne(mode)&&!o.kind.equals("BARREL")&&!o.kind.equals("PORTAL")&&danger(o.kind))continue;
   if(danger(o.kind)){
    float r=o.kind.equals("PORTAL")?30:((o.y>.40f||o.ttc<.95f)?100:15);
    risk[o.lane]+=r;nearest[o.lane]=Math.max(nearest[o.lane],o.y);arrival[o.lane]=Math.min(arrival[o.lane],o.ttc);portal[o.lane]|=o.kind.equals("PORTAL");
    if(o.lane==lane&&(o.y>.30f||o.ttc<1.25f||o.kind.equals("PORTAL"))) {
     if(urgent==null||o.y>urgent.y)urgent=o;
    }
   }else{
    float bonus=o.kind.equals("RAMP")?3:o.kind.equals("BOMB")?(mode==Mode.BOSS_AIR||mode==Mode.BOSS_GROUND?12:0):o.kind.equals("BLUE")?5:o.kind.equals("GOLD")?1:0;
    if(o.y>.20f&&o.y<.65f)reward[o.lane]+=bonus*(.5f+o.y);
   }
  }
  if(urgent!=null){
   boolean jump=urgent.kind.equals("JUMP")||urgent.kind.equals("BARREL");boolean slide=urgent.kind.equals("SLIDE");
   if(!airborne(mode)&&(jump||slide)){
    Action vertical=jump?Action.JUMP:Action.SLIDE;
    if(lastVertical==vertical&&lastVerticalLane==lane&&now-lastVerticalAt<650)return new Decision(Action.NONE,"Маневр уже виконано · очікування проходу перешкоди");
    if(urgent.y>=.50f||urgent.ttc<=.65f)return new Decision(vertical,jump?"Стрибок через низьку перешкоду":"Пригинання під перекладиною");
    return new Decision(Action.NONE,"Наближення · очікування моменту "+vertical);
   }
   // Start a two-lane escape early, while the middle obstacle still has clearance.
   if((lane==0||lane==2)&&!portal[1]&&nearest[1]<.55f&&arrival[1]>.45f){
    int destination=2-lane;
    if(risk[destination]==0&&risk[1]>=30)return new Decision(destination>lane?Action.RIGHT:Action.LEFT,"Ранній обхід через дві смуги",2,destination);
   }
   int target=lane;float best=Float.MAX_VALUE;
   // An adjacent move may be followed by a preplanned second move.
   for(int l=Math.max(0,lane-1);l<=Math.min(2,lane+1);l++)if(l!=lane&&!portal[l]&&(risk[l]<30||(nearest[l]<urgent.y-.12f&&arrival[l]>.75f))&&risk[l]<best){target=l;best=risk[l];}
   if(target!=lane)return new Decision(target<lane?Action.LEFT:Action.RIGHT,"Обхід "+urgent.kind,1,target);
   return new Decision(Action.NONE,"Немає впевненого безпечного маневру");
  }
  int bestLane=lane;float best=reward[lane]-risk[lane];
  for(int l=Math.max(0,lane-1);l<=Math.min(2,lane+1);l++)if(risk[l]==0&&reward[l]-risk[l]>best+.30f){bestLane=l;best=reward[l];}
  return new Decision(bestLane==lane?Action.NONE:bestLane<lane?Action.LEFT:Action.RIGHT,bestLane==lane?"Утримання доріжки":"Збирання предметів");
 }
}
