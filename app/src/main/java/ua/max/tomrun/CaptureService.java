package ua.max.tomrun;
import android.app.*;import android.content.*;import android.content.pm.ServiceInfo;import android.graphics.*;import android.hardware.display.*;import android.media.*;import android.media.projection.*;import android.os.*;import android.view.*;import java.nio.*;
public final class CaptureService extends Service {
 public static volatile CaptureService instance;public volatile boolean enabled;
 private MediaProjection projection;private VirtualDisplay display;private ImageReader reader;private HandlerThread thread;private Handler worker,main;
 private VisionEngine vision;private final Planner planner=new Planner();private long lastFrame;private volatile int override=-1;private boolean closing;private int frameW,frameH;
 public void pause(){enabled=false;GestureService g=GestureService.instance;if(g!=null)g.update("Перегляд · авто вимкнено",false);}
 public void enable(){if(worker==null)return;worker.post(()->{planner.reset();vision.reset();enabled=true;});}
 public void cycleMode(){override=(override+2)%5-1;}
 public String modeLabel(){return override<0?"Режим: авто":"Режим: "+Planner.Mode.values()[override];}
 @Override public void onCreate(){super.onCreate();instance=this;main=new Handler(getMainLooper());}
 @Override public int onStartCommand(Intent intent,int flags,int id){
  if(intent==null){stopSelf();return START_NOT_STICKY;}if("STOP".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
  if(projection!=null)return START_NOT_STICKY;
  NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("pilot","Tom Run Pilot",NotificationManager.IMPORTANCE_LOW));
  PendingIntent stop=PendingIntent.getService(this,0,new Intent(this,CaptureService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE);
  Notification n=new Notification.Builder(this,"pilot").setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("Tom Run Pilot · Prototype").setContentText("Екран аналізується локально. Авто вмикається на панелі.").setOngoing(true).addAction(new Notification.Action.Builder(null,"Стоп",stop).build()).build();
  startForeground(7,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
  try{
   vision=new VisionEngine(this);thread=new HandlerThread("pilot-vision");thread.start();worker=new Handler(thread.getLooper());
   Rect screen=getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();frameW=180;frameH=Math.round((float)screen.height()/screen.width()*frameW);
   reader=ImageReader.newInstance(frameW,frameH,PixelFormat.RGBA_8888,2);reader.setOnImageAvailableListener(this::frame,worker);
   Intent consent=Build.VERSION.SDK_INT>=33?intent.getParcelableExtra("consent",Intent.class):intent.getParcelableExtra("consent");
   projection=getSystemService(MediaProjectionManager.class).getMediaProjection(intent.getIntExtra("code",Activity.RESULT_CANCELED),consent);
   projection.registerCallback(new MediaProjection.Callback(){@Override public void onStop(){stopSelf();}@Override public void onCapturedContentResize(int w,int h){if(w>h){pause();stopSelf();}}},main);
   display=projection.createVirtualDisplay("TomRunPilot",frameW,frameH,getResources().getDisplayMetrics().densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,worker);
   GestureService g=GestureService.instance;if(g!=null)g.showPanel();
  }catch(Exception e){android.widget.Toast.makeText(this,"Не вдалося почати: "+e.getClass().getSimpleName(),android.widget.Toast.LENGTH_LONG).show();stopSelf();}
  return START_NOT_STICKY;
 }
 private void frame(ImageReader source){
  Image image=null;Bitmap bitmap=null;
  try{image=source.acquireLatestImage();if(image==null)return;long now=SystemClock.elapsedRealtime();if(now-lastFrame<120)return;lastFrame=now;
   GestureService g=GestureService.instance;if(g==null||!g.inGame()){pause();return;}
   Image.Plane plane=image.getPlanes()[0];ByteBuffer b=plane.getBuffer();int[] pixels=new int[frameW*frameH];int row=plane.getRowStride(),pixel=plane.getPixelStride();
   for(int y=0;y<frameH;y++)for(int x=0;x<frameW;x++){int i=y*row+x*pixel;int r=b.get(i)&255,gg=b.get(i+1)&255,bb=b.get(i+2)&255;pixels[y*frameW+x]=0xff000000|(r<<16)|(gg<<8)|bb;}
   image.close();image=null;bitmap=Bitmap.createBitmap(pixels,frameW,frameH,Bitmap.Config.ARGB_8888);
   VisionEngine.Result result=vision.analyze(bitmap,now);planner.observeLane(result.playerLane);
   Planner.Mode mode=override<0?result.mode:Planner.Mode.values()[override];Planner.Decision decision=planner.decide(result.objects,mode,now);
   long spent=SystemClock.elapsedRealtime()-now;
   g.update((enabled?"АВТО":"Перегляд")+" · "+mode+" · "+spent+" мс\n"+result.debug+" · "+decision.reason,enabled);
   if(enabled&&spent<250&&decision.action!=Planner.Action.NONE){long measured=now;main.post(()->{if(enabled&&SystemClock.elapsedRealtime()-measured<250&&GestureService.instance!=null)GestureService.instance.swipe(decision.action,()->worker.post(()->planner.committed(decision.action,SystemClock.elapsedRealtime())));});}
  }catch(Exception e){enabled=false;GestureService g=GestureService.instance;if(g!=null)g.update("Пауза: "+e.getClass().getSimpleName(),false);}
  finally{if(image!=null)image.close();if(bitmap!=null)bitmap.recycle();}
 }
 @Override public void onDestroy(){enabled=false;instance=null;if(!closing){closing=true;if(reader!=null)reader.setOnImageAvailableListener(null,null);if(display!=null)display.release();if(projection!=null)projection.stop();if(reader!=null)reader.close();if(thread!=null)thread.quitSafely();}GestureService g=GestureService.instance;if(g!=null)g.hidePanel();super.onDestroy();}
 @Override public IBinder onBind(Intent i){return null;}
}
