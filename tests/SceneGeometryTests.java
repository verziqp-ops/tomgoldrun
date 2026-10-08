import ua.max.tomrun.SceneGeometry;import java.util.Arrays;
public class SceneGeometryTests {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 public static void main(String[] args){
  check(Math.abs(SceneGeometry.anchor("BLOCK",.4f,.2f)-.5f)<.00001f,"vehicle uses bottom edge");
  check(SceneGeometry.anchor("GOLD",.4f,.2f)==.4f,"reward keeps its centre");
  check(Arrays.equals(SceneGeometry.lanes("SLIDE",.5f,.4f,.7f,.05f),new int[]{0,1,2}),"wide crossbar covers all lanes");
  check(Arrays.equals(SceneGeometry.lanes("SLIDE",.5f,.4f,.1f,.05f),new int[]{1}),"narrow crossbar only blocks middle");
  check(SceneGeometry.lanes("JUMP",.2f,.5f,.12f,.1f)[0]==0,"left low obstacle stays in left lane");
  check(SceneGeometry.overlap(.5f,.4f,.2f,.2f,.5f,.4f,.1f,.1f)>.99f,"contained barrier can override generic vehicle");
  check(SceneGeometry.overlap(.2f,.4f,.1f,.1f,.8f,.4f,.1f,.1f)==0,"separate objects do not override one another");
  System.out.println("PASS 7 scene geometry checks");
 }
}
