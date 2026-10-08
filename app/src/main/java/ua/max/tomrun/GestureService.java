package ua.max.tomrun;
import android.accessibilityservice.*;import android.view.accessibility.*;import android.graphics.*;import android.os.*;import android.view.*;import android.widget.*;
public final class GestureService extends AccessibilityService {
 public static final String GAME="com.outfit7.talkingtomgoldrun";
 public static volatile GestureService instance;
 private DetectionView detections;private boolean boxesVisible=true;
 private volatile boolean busy;private volatile String active="";private LinearLayout panel;private TextView status;private Button auto,mode;private volatile String gestureStatus="Ще немає жестів";
 @Override protected void onServiceConnected(){instance=this;refreshActive();}
 private void refreshActive(){
  // Unity games may expose no node tree. Preserve the foreground package supplied by window events.
  for(AccessibilityWindowInfo w:getWindows())if(w.getType()==AccessibilityWindowInfo.TYPE_APPLICATION&&(w.isActive()||w.isFocused())){
   AccessibilityNodeInfo r=w.getRoot();if(r!=null){CharSequence pkg=r.getPackageName();if(pkg!=null)active=pkg.toString();r.recycle();return;}
  }
  AccessibilityNodeInfo r=getRootInActiveWindow();if(r!=null){CharSequence p=r.getPackageName();if(p!=null&&!p.toString().equals(getPackageName()))active=p.toString();r.recycle();}
 }
 @Override public void onAccessibilityEvent(AccessibilityEvent e){if(e.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED){
  String pkg=e.getPackageName()==null?"":e.getPackageName().toString();String cls=e.getClassName()==null?"":e.getClassName().toString();
  if(!pkg.isEmpty()&&(!pkg.equals(getPackageName())||cls.contains("MainActivity")))active=pkg;
  refreshActive();CaptureService c=CaptureService.instance;if(c!=null&&!GAME.equals(active))c.pause();
 }}
 public boolean inGame(){refreshActive();return GAME.equals(active);}
 public String diagnostic(){return "Активний: "+(active.isEmpty()?"невідомо":active)+" · "+gestureStatus;}
 public void test(Planner.Action a){CaptureService c=CaptureService.instance;if(c!=null)c.pause();boolean ok=swipe(a,()->update("Тест виконано: "+a+"\n"+diagnostic(),false));update((ok?"Тест відправлено: ":"Тест не відправлено: ")+a+"\n"+diagnostic(),false);}
 public boolean swipe(Planner.Action a,Runnable success){if(busy){gestureStatus="Жест ще виконується";return false;}if(!inGame()){gestureStatus="Гра не визначена активною";return false;}if(a==Planner.Action.NONE)return false;
  Rect r=getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();float x=r.width()*.5f,y=r.height()*.75f;
  float dx=0,dy=0;switch(a){case LEFT:dx=-r.width()*.28f;break;case RIGHT:dx=r.width()*.28f;break;case JUMP:dy=-r.height()*.20f;break;case SLIDE:dy=r.height()*.16f;break;default:return false;}
  Path p=new Path();p.moveTo(x,y);p.lineTo(x+dx,y+dy);GestureDescription g=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,85)).build();busy=true;
  boolean accepted=dispatchGesture(g,new GestureResultCallback(){@Override public void onCompleted(GestureDescription g){busy=false;gestureStatus="Виконано: "+a;success.run();}@Override public void onCancelled(GestureDescription g){busy=false;gestureStatus="Android скасував жест";}},new Handler(getMainLooper()));
  if(!accepted){busy=false;gestureStatus="Android відхилив жест";}else gestureStatus="Відправлено: "+a;return accepted;
 }
 public void showPanel(){new Handler(getMainLooper()).post(()->{if(panel!=null)return;
  WindowManager wm=getSystemService(WindowManager.class);detections=new DetectionView(this);
  WindowManager.LayoutParams debugParams=new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);debugParams.gravity=Gravity.TOP|Gravity.LEFT;wm.addView(detections,debugParams);
  panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(4,2,4,2);panel.setBackgroundColor(0xcc18202c);
  LinearLayout compact=new LinearLayout(this);auto=new Button(this);auto.setText("Авто");auto.setTextSize(10);compact.addView(auto,new LinearLayout.LayoutParams(0,100,1));Button expand=new Button(this);expand.setText("Панель ▾");expand.setTextSize(10);compact.addView(expand,new LinearLayout.LayoutParams(0,100,1));Button stop=new Button(this);stop.setText("Стоп");stop.setTextSize(10);compact.addView(stop,new LinearLayout.LayoutParams(0,100,1));panel.addView(compact);
  LinearLayout details=new LinearLayout(this);details.setOrientation(1);details.setVisibility(View.GONE);panel.addView(details);
  status=new TextView(this);status.setTextColor(0xffffffff);status.setTextSize(10);status.setText("Перегляд · авто вимкнено");details.addView(status);
  LinearLayout options=new LinearLayout(this);mode=new Button(this);mode.setText("Режим: авто");mode.setTextSize(10);options.addView(mode,new LinearLayout.LayoutParams(0,100,1));Button boxes=new Button(this);boxes.setText("Рамки: так");boxes.setTextSize(10);options.addView(boxes,new LinearLayout.LayoutParams(0,100,1));Button photo=new Button(this);photo.setText("Фото");photo.setTextSize(10);options.addView(photo,new LinearLayout.LayoutParams(0,100,1));details.addView(options);
  LinearLayout tests=new LinearLayout(this);for(Planner.Action a:new Planner.Action[]{Planner.Action.LEFT,Planner.Action.RIGHT,Planner.Action.JUMP,Planner.Action.SLIDE}){Button t=new Button(this);t.setText("Тест "+a);t.setTextSize(9);tests.addView(t,new LinearLayout.LayoutParams(0,80,1));t.setOnClickListener(v->test(a));}details.addView(tests);
  expand.setOnClickListener(v->{boolean open=details.getVisibility()!=View.VISIBLE;details.setVisibility(open?View.VISIBLE:View.GONE);expand.setText(open?"Панель ▴":"Панель ▾");});
  boxes.setOnClickListener(v->{boxesVisible=!boxesVisible;detections.setVisibility(boxesVisible?View.VISIBLE:View.GONE);boxes.setText(boxesVisible?"Рамки: так":"Рамки: ні");});
  photo.setOnClickListener(v->{CaptureService c=CaptureService.instance;if(c!=null)c.saveDebugShot();});
  auto.setOnClickListener(v->{CaptureService c=CaptureService.instance;if(c!=null){if(c.enabled)c.pause();else if(inGame())c.enable();else status.setText("Спочатку відкрий гру");}});
  mode.setOnClickListener(v->{CaptureService c=CaptureService.instance;if(c!=null){c.cycleMode();mode.setText(c.modeLabel());}});stop.setOnClickListener(v->{CaptureService c=CaptureService.instance;if(c!=null)c.stopSelf();});
  WindowManager.LayoutParams lp=new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);lp.gravity=Gravity.BOTTOM;wm.addView(panel,lp);
 });}
 public void showDetections(VisionEngine.Result r,int lane,Planner.Action action){new Handler(getMainLooper()).post(()->{if(detections!=null){detections.set(r,lane,action);detections.setVisibility(boxesVisible&&GAME.equals(active)?View.VISIBLE:View.GONE);}});}
 public void update(String s,boolean enabled){new Handler(getMainLooper()).post(()->{if(detections!=null&&!GAME.equals(active))detections.setVisibility(View.GONE);if(status!=null)status.setText(s);if(auto!=null)auto.setText(enabled?"Пауза":"Авто");});}
 public void hidePanel(){new Handler(getMainLooper()).post(()->{if(detections!=null){getSystemService(WindowManager.class).removeView(detections);detections=null;}if(panel!=null){getSystemService(WindowManager.class).removeView(panel);panel=null;status=null;auto=null;mode=null;}});}
 @Override public void onInterrupt(){CaptureService c=CaptureService.instance;if(c!=null)c.pause();busy=false;}
 @Override public void onDestroy(){onInterrupt();hidePanel();instance=null;super.onDestroy();}
}
