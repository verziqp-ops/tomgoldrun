import ua.max.tomrun.Planner;
import java.util.*;
public class PlannerTests {
 static int cases=0;
 static Planner.ObjectInfo o(String k,int l,float y){return new Planner.ObjectInfo(k,l,y,.95f,Float.POSITIVE_INFINITY);}
 static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);cases++;}
 public static void main(String[] args){
  Planner p=new Planner();
  check(p.decide(List.of(o("BOMB",0,.5f)),Planner.Mode.BOSS_GROUND,1000).action==Planner.Action.LEFT,"boss collects bomb");
  check(p.decide(List.of(o("BOMB",0,.5f),o("BLOCK",0,.5f)),Planner.Mode.BOSS_GROUND,1000).action==Planner.Action.NONE,"bomb behind blocker skipped");
  check(p.decide(List.of(o("PORTAL",1,.3f),o("GOLD",1,.5f)),Planner.Mode.GROUND,1000).action!=Planner.Action.NONE,"portal avoided despite gold");
  check(p.decide(List.of(o("PORTAL",1,.3f)),Planner.Mode.FLIGHT,1000).action!=Planner.Action.NONE,"flight also avoids portals");
  check(p.decide(List.of(o("BARREL",1,.5f)),Planner.Mode.BOSS_AIR,1000).action==Planner.Action.LEFT,"air boss dodges barrel");
  check(p.decide(List.of(o("JUMP",1,.5f),o("BLOCK",0,.5f),o("BLOCK",2,.5f)),Planner.Mode.GROUND,1000).action==Planner.Action.JUMP,"ground jumps when side lanes blocked");
  check(p.decide(List.of(o("SLIDE",1,.5f),o("BLOCK",0,.5f),o("BLOCK",2,.5f)),Planner.Mode.GROUND,1000).action==Planner.Action.SLIDE,"ground slides when side lanes blocked");
  check(p.decide(List.of(o("SLIDE",1,.5f)),Planner.Mode.BOSS_AIR,1000).action==Planner.Action.NONE,"no slide in air");
  check(p.decide(List.of(o("JUMP",1,.5f)),Planner.Mode.FLIGHT,1000).action==Planner.Action.NONE,"no jump in flight");
  p.lane=0;check(p.decide(List.of(o("BLUE",2,.5f),o("BLOCK",1,.3f)),Planner.Mode.GROUND,1000).action==Planner.Action.NONE,"does not cross blocked middle lane");
  p.lane=1;check(p.decide(List.of(o("BLUE",2,.5f)),Planner.Mode.GROUND,1000).action==Planner.Action.RIGHT,"blue collected");
  check(p.decide(List.of(new Planner.ObjectInfo("GOLD",0,.5f,.5f,0)),Planner.Mode.GROUND,1000).action==Planner.Action.NONE,"low confidence ignored");
  p.committed(Planner.Action.RIGHT,1000);check(p.lane==2,"lane updated only on completed gesture");
  check(p.decide(List.of(o("BLOCK",2,.5f)),Planner.Mode.GROUND,1100).action==Planner.Action.NONE,"gesture cooldown");
  p.observeLane(0);check(p.lane==2,"lane observation debounced");p.observeLane(0);check(p.lane==0,"observed lane corrects drift");
  p.reset();check(p.lane==1,"reset clears lane");
  check(p.decide(List.of(new Planner.ObjectInfo("BLOCK",1,.35f,.95f,.3f)),Planner.Mode.GROUND,1000).action!=Planner.Action.NONE,"approaching object causes early dodge");
  for(Planner.Mode m:List.of(Planner.Mode.FLIGHT,Planner.Mode.BOSS_AIR))for(String kind:List.of("JUMP","SLIDE","BLOCK","BARREL","PORTAL")){
   Planner.Action a=p.decide(List.of(o(kind,1,.6f),o("BLOCK",0,.6f),o("BLOCK",2,.6f)),m,1000).action;
   check(a!=Planner.Action.JUMP&&a!=Planner.Action.SLIDE,"air invariant "+m+kind);
  }
  System.out.println("PASS "+cases+" planner checks");
 }
}
