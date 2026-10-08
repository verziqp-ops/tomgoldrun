import ua.max.tomrun.Planner;
import java.util.*;
public class PlannerTests {
 static int cases=0;
 static Planner.ObjectInfo o(String k,int l,float y){return new Planner.ObjectInfo(k,l,y,.95f,Float.POSITIVE_INFINITY);}
 static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);cases++;}
 public static void main(String[] args){
  Planner p=new Planner();
  check(p.decide(List.of(o("GOLD",0,.5f)),Planner.Mode.GROUND,1000).action==Planner.Action.LEFT,"a single gold target can trigger a lane change");
  check(p.decide(List.of(o("BLOCK",1,.41f)),Planner.Mode.GROUND,1000).action!=Planner.Action.NONE,"earlier obstacle dodge");
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
  check(p.decide(List.of(o("BLOCK",2,.5f)),Planner.Mode.GROUND,1050).action==Planner.Action.NONE,"gesture cooldown");
  p.observeLane(0);check(p.lane==2,"lane observation debounced");p.observeLane(0);check(p.lane==0,"observed lane corrects drift");
  p.reset();check(p.lane==1,"reset clears lane");
  check(p.decide(List.of(new Planner.ObjectInfo("BLOCK",1,.35f,.95f,.3f)),Planner.Mode.GROUND,1000).action!=Planner.Action.NONE,"approaching object causes early dodge");
  for(Planner.Mode m:List.of(Planner.Mode.FLIGHT,Planner.Mode.BOSS_AIR))for(String kind:List.of("JUMP","SLIDE","BLOCK","BARREL","PORTAL")){
   Planner.Action a=p.decide(List.of(o(kind,1,.6f),o("BLOCK",0,.6f),o("BLOCK",2,.6f)),m,1000).action;
   check(a!=Planner.Action.JUMP&&a!=Planner.Action.SLIDE,"air invariant "+m+kind);
  }
  p.reset();
  check(p.decide(List.of(o("JUMP",1,.55f)),Planner.Mode.GROUND,2000).action==Planner.Action.JUMP,"jump is allowed with empty side lanes");
  check(p.decide(List.of(o("SLIDE",1,.55f)),Planner.Mode.GROUND,2000).action==Planner.Action.SLIDE,"slide is allowed with empty side lanes");
  check(p.decide(List.of(o("JUMP",1,.43f)),Planner.Mode.GROUND,2000).action==Planner.Action.NONE,"wait instead of jumping too early");
  check(p.decide(List.of(new Planner.ObjectInfo("SLIDE",1,.43f,.95f,.4f)),Planner.Mode.GROUND,2000).action==Planner.Action.SLIDE,"approach speed can trigger earlier slide");
  p.committed(Planner.Action.JUMP,2000);
  check(p.decide(List.of(o("JUMP",1,.60f)),Planner.Mode.GROUND,2250).action==Planner.Action.NONE,"do not repeatedly jump for one obstacle");
  check(p.decide(List.of(o("SLIDE",1,.60f)),Planner.Mode.GROUND,2250).action==Planner.Action.SLIDE,"a different vertical hazard is still actionable");
  p.reset();p.committed(Planner.Action.RIGHT,3000);p.observeLane(1,3100);p.observeLane(1,3200);
  check(p.lane==2,"stale player position does not undo completed lane change");
  p.observeLane(1,3500);p.observeLane(1,3600);check(p.lane==1,"fresh lane observations can correct drift after manoeuvre");
  p.reset();check(p.decide(List.of(o("BLOCK",1,.65f),o("BLOCK",0,.43f),o("BLOCK",2,.63f)),Planner.Mode.GROUND,4000).action==Planner.Action.LEFT,"escape into a lane with more clearance");
  check(p.decide(List.of(o("BLOCK",1,.65f),o("BLOCK",0,.63f),o("BLOCK",2,.63f)),Planner.Mode.GROUND,4000).action==Planner.Action.NONE,"do not dodge into equally imminent vehicles");
  p.reset();p.lane=0;Planner.Decision route=p.decide(List.of(o("BLOCK",0,.44f),o("BLOCK",1,.44f)),Planner.Mode.GROUND,5000);
  check(route.action==Planner.Action.RIGHT&&route.steps==2&&route.targetLane==2,"early two-lane escape through intermediate lane");
  p.lane=2;route=p.decide(List.of(o("BLOCK",2,.44f),o("BLOCK",1,.44f)),Planner.Mode.GROUND,5000);
  check(route.action==Planner.Action.LEFT&&route.steps==2&&route.targetLane==0,"two-lane escape is symmetric");
  p.lane=0;check(p.decide(List.of(o("BLOCK",0,.66f),o("BLOCK",1,.66f)),Planner.Mode.GROUND,5000).steps==0,"do not cross an imminent middle obstacle");
  check(p.decide(List.of(o("BLOCK",0,.44f),o("BLOCK",1,.44f),o("PORTAL",2,.3f)),Planner.Mode.GROUND,5000).steps!=2,"two-lane escape must not enter a portal");
  check(!p.canContinueRoute(List.of(o("BLOCK",2,.6f)),2,Planner.Mode.GROUND),"new destination obstacle cancels second step");
  check(p.canContinueRoute(List.of(o("BLOCK",2,.6f)),2,Planner.Mode.FLIGHT),"flight ignores ground vehicle during route check");
  check(!p.canContinueRoute(List.of(o("PORTAL",2,.3f)),2,Planner.Mode.FLIGHT),"flight route still excludes portals");
  p.lane=1;check(p.decide(List.of(o("RAMP",1,.55f)),Planner.Mode.GROUND,5000).action==Planner.Action.NONE,"stay on current walkable ramp");
  System.out.println("PASS "+cases+" planner checks");
 }
}
