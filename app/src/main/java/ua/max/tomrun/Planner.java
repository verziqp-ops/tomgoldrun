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
  public final Action action; public final String reason;
  Decision(Action a,String r){action=a;reason=r;}
 }
 public int lane=1; private long lastAction=-10000; private int observedLane=-1, laneVotes;
 public void reset(){lane=1;lastAction=-10000;observedLane=-1;laneVotes=0;}
 public void observeLane(int l){if(l<0||l>2)return; if(observedLane==l)laneVotes++;else{observedLane=l;laneVotes=1;} if(laneVotes>=2)lane=l;}
 public void committed(Action a,long now){lastAction=now;if(a==Action.LEFT)lane=Math.max(0,lane-1);if(a==Action.RIGHT)lane=Math.min(2,lane+1);}
 private boolean danger(String k){return k.equals("BLOCK")||k.equals("JUMP")||k.equals("SLIDE")||k.equals("BARREL")||k.equals("PORTAL");}
 private boolean airborne(Mode m){return m==Mode.FLIGHT||m==Mode.BOSS_AIR;}
 public Decision decide(List<ObjectInfo> objects,Mode mode,long now){
  if(now-lastAction<160)return new Decision(Action.NONE,"Очікування завершення маневру");
  float[] risk=new float[3],reward=new float[3]; ObjectInfo urgent=null;
  for(ObjectInfo o:objects){
   if(o.confidence<.83f||o.lane<0||o.lane>2)continue;
   if(mode==Mode.FLIGHT&&danger(o.kind)&&!o.kind.equals("PORTAL"))continue;
   if(airborne(mode)&&!o.kind.equals("BARREL")&&!o.kind.equals("PORTAL")&&danger(o.kind))continue;
   if(danger(o.kind)){
    float r=o.kind.equals("PORTAL")?30:((o.y>.40f||o.ttc<.95f)?100:15);
    risk[o.lane]+=r;
    if(o.lane==lane&&(o.y>.40f||o.ttc<.95f||o.kind.equals("PORTAL"))) {
     if(urgent==null||o.y>urgent.y)urgent=o;
    }
   }else{
    float bonus=o.kind.equals("BOMB")?(mode==Mode.BOSS_AIR||mode==Mode.BOSS_GROUND?12:0):o.kind.equals("BLUE")?5:o.kind.equals("GOLD")?1:0;
    if(o.y>.20f&&o.y<.65f)reward[o.lane]+=bonus*(.5f+o.y);
   }
  }
  if(urgent!=null){
   int target=lane;float best=Float.MAX_VALUE;
   // Only one adjacent lane per decision; do not jump across a dangerous middle lane.
   for(int l=Math.max(0,lane-1);l<=Math.min(2,lane+1);l++)if(l!=lane&&risk[l]<best){target=l;best=risk[l];}
   if(target!=lane&&best<risk[lane]&&best<30)return new Decision(target<lane?Action.LEFT:Action.RIGHT,"Обхід "+urgent.kind);
   if(!airborne(mode)&&(urgent.kind.equals("JUMP")||urgent.kind.equals("BARREL")))return new Decision(Action.JUMP,"Низька перешкода");
   if(!airborne(mode)&&urgent.kind.equals("SLIDE"))return new Decision(Action.SLIDE,"Прохід під перешкодою");
   return new Decision(Action.NONE,"Немає впевненого безпечного маневру");
  }
  int bestLane=lane;float best=reward[lane]-risk[lane];
  for(int l=Math.max(0,lane-1);l<=Math.min(2,lane+1);l++)if(risk[l]==0&&reward[l]-risk[l]>best+.30f){bestLane=l;best=reward[l];}
  return new Decision(bestLane==lane?Action.NONE:bestLane<lane?Action.LEFT:Action.RIGHT,bestLane==lane?"Утримання доріжки":"Збирання предметів");
 }
}
