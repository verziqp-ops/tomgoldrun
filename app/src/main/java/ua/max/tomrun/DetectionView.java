package ua.max.tomrun;
import android.content.Context;import android.graphics.*;import android.view.View;import java.util.*;
/** Diagnostic image bounds, not collision geometry from the game engine. */
public final class DetectionView extends View {
 private java.util.List<VisionEngine.Box> boxes=java.util.Collections.emptyList();private int lane=1;private String action="NONE";
 public DetectionView(Context context){super(context);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
 public void set(VisionEngine.Result result,int l,Planner.Action a){boxes=new ArrayList<>(result.boxes);lane=l;action=a.name();invalidate();}
 @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);draw(canvas,getWidth(),getHeight(),boxes,lane,action);}
 public static int color(String kind){switch(kind){case "GOLD":return 0xffffd740;case "BLUE":return 0xff00e5ff;case "BOMB":return 0xffff9100;case "PORTAL":return 0xffe040fb;case "PLAYER":return 0xff69f0ae;case "BOSS":case "AIR":return 0xffb388ff;default:return 0xffff5252;}}
 public static String name(String kind){switch(kind){case "GOLD":return "Золото";case "BLUE":return "Сині";case "BOMB":return "Бомбочка";case "PORTAL":return "Арка: обхід";case "PLAYER":return "Том";case "BLOCK":return "Перешкода";case "JUMP":return "Стрибок";case "SLIDE":return "Підкат";case "BARREL":return "Бочка";case "BOSS":return "Бос";case "AIR":return "Політ";default:return kind;}}
 public static void draw(Canvas c,int w,int h,java.util.List<VisionEngine.Box> boxes,int lane,String action){
  Paint line=new Paint(Paint.ANTI_ALIAS_FLAG);line.setStyle(Paint.Style.STROKE);line.setStrokeWidth(Math.max(1.5f,w*.003f));
  Paint text=new Paint(Paint.ANTI_ALIAS_FLAG);text.setTextSize(Math.max(10,w*.026f));text.setTypeface(Typeface.DEFAULT_BOLD);
  Paint bg=new Paint();bg.setColor(0xbb10141c);
  for(VisionEngine.Box box:boxes){RectF r=new RectF(box.rect.left*w,box.rect.top*h,box.rect.right*w,box.rect.bottom*h);line.setColor(box.confirmed?color(box.kind):0xffaaaaaa);line.setPathEffect(box.confirmed?null:new DashPathEffect(new float[]{5,4},0));c.drawRect(r,line);
   String label=name(box.kind)+" "+Math.round(box.confidence*100)+"%"+(box.confirmed?"":" ?");text.setColor(line.getColor());float ty=Math.max(text.getTextSize()+4,r.top-5),tx=Math.min(r.left,Math.max(0,w-text.measureText(label)-6));c.drawRect(tx,ty-text.getTextSize()-2,tx+text.measureText(label)+6,ty+4,bg);c.drawText(label,tx+3,ty,text);
  }
  text.setColor(0xffffffff);String info="Доріжка "+(lane+1)+" → "+action;float y=h*.115f;c.drawRect(4,y-text.getTextSize()-3,text.measureText(info)+14,y+6,bg);c.drawText(info,8,y,text);
 }
}
