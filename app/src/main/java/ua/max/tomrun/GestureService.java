package ua.max.tomrun;
import android.accessibilityservice.*;import android.view.accessibility.*;import android.graphics.*;import android.os.*;import android.view.*;import android.widget.*;
public final class GestureService extends AccessibilityService {
 public static final String GAME="com.outfit7.talkingtomgoldrun";
 public static volatile GestureService instance;
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
 public void showPanel(){new Handler(getMainLooper()).post(()->{if(panel!=null)return;panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(8,4,8,4);panel.setBackgroundColor(0xdd18202c);
  status=new TextView(this);status.setTextColor(0xffffffff);status.setTextSize(10);status.setText("Перегляд · авто вимкнено");panel.addView(status);
  LinearLayout row=new LinearLayout(this);auto=new Button(this);auto.setText("Авто");auto.setTextSize(10);row.addView(auto);mode=new Button(this);mode.setText("Режим: авто");mode.setTextSize(10);row.addView(mode);Button stop=new Button(this);stop.setText("Стоп");stop.setTextSize(10);row.addView(stop);panel.addView(row);
  LinearLayout tests=new LinearLayout(this);for(Planner.Action a:new Planner.Action[]{Planner.Action.LEFT,Planner.Action.RIGHT,Planner.Action.JUMP,Planner.Action.SLIDE}){Button t=new Button(this);t.setText("Тест "+a);t.setTextSize(9);tests.addView(t,new LinearLayout.LayoutParams(0,80,1));t.setOnClickListener(v->test(a));}panel.addView(tests);
  auto.setOnClickListener(v->{CaptureService c=CaptureService.instance;if(c!=null){if(c.enabled)c.pause();else if(inGame())c.enable();else status.setText("Спочатку відкрий гру");}});
  mode.setOnClickListener(v->{CaptureService c=CaptureService.instance;if(c!=null){c.cycleMode();mode.setText(c.modeLabel());}});stop.setOnClickListener(v->{CaptureService c=CaptureService.instance;if(c!=null)c.stopSelf();});
  WindowManager.LayoutParams lp=new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,android.graphics.PixelFormat.TRANSLUCENT);lp.gravity=Gravity.BOTTOM;
  getSystemService(WindowManager.class).addView(panel,lp);
 });}
 public void update(String s,boolean enabled){new Handler(getMainLooper()).post(()->{if(status!=null)status.setText(s);if(auto!=null)auto.setText(enabled?"Пауза":"Авто");});}
 public void hidePanel(){new Handler(getMainLooper()).post(()->{if(panel!=null){getSystemService(WindowManager.class).removeView(panel);panel=null;status=null;auto=null;mode=null;}});}
 @Override public void onInterrupt(){CaptureService c=CaptureService.instance;if(c!=null)c.pause();busy=false;}
 @Override public void onDestroy(){onInterrupt();hidePanel();instance=null;super.onDestroy();}
}
