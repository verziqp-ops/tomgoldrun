package ua.max.tomrun;
import android.app.*;import android.content.*;import android.content.pm.ServiceInfo;import android.graphics.*;import android.hardware.display.*;import android.media.*;import android.media.projection.*;import android.os.*;import android.view.*;import java.nio.*;
public final class CaptureService extends Service {
 public static volatile CaptureService instance;public volatile boolean enabled;private final java.util.concurrent.atomic.AtomicLong commandGeneration=new java.util.concurrent.atomic.AtomicLong();
 private MediaProjection projection;private VirtualDisplay display;private ImageReader reader;private HandlerThread thread;private Handler worker,main;
 private Bitmap latestFrame;private volatile VisionEngine.Result latestResult;private String latestInfo="";private int latestLane;private Planner.Action latestAction=Planner.Action.NONE;
 private final LearningSession learning=new LearningSession();public volatile boolean learningMode;private long lastSave;private java.io.File learningFile;private String saveStatus="";
 private VisionEngine vision;private final Planner planner=new Planner();private long lastFrame;private boolean gesturePending;private long pendingSince;private volatile int override=-1;private volatile boolean closing;private int frameW,frameH;
 public void pause(){enabled=false;if(worker!=null)worker.post(()->{learning.suspend();saveLearning();});commandGeneration.incrementAndGet();GestureService g=GestureService.instance;if(g!=null)g.update("Перегляд · авто вимкнено",false);}
 public void enable(){if(worker==null)return;worker.post(()->{commandGeneration.incrementAndGet();gesturePending=false;planner.reset();vision.reset();if(learningMode)learning.start(SystemClock.elapsedRealtime());enabled=true;});}
 public void toggleLearning(){pause();if(worker!=null)worker.post(()->{learningMode=!learningMode;GestureService g=GestureService.instance;if(g!=null)g.update((learningMode?"Навчання увімкнено. Почни гру вручну й натисни Авто.":"Звичайний режим. Навчання збережено.")+"\n"+learning.status()+saveStatus,false);});}
 public void markDeath(){if(worker!=null)worker.post(()->{if(!learningMode||!learning.active())return;enabled=false;commandGeneration.incrementAndGet();gesturePending=false;learning.death(SystemClock.elapsedRealtime());saveLearning();GestureService g=GestureService.instance;if(g!=null)g.update("Зіткнення записане. Почни новий забіг вручну, потім Авто.\n"+learning.status()+saveStatus,false);});}
 private void saveLearning(){if(learningFile==null||(learning.agent.decisions==0&&learning.agent.transitions==0))return;try{learning.agent.save(learningFile);saveStatus=" · збережено";lastSave=SystemClock.elapsedRealtime();}catch(java.io.IOException e){saveStatus=" · ПОМИЛКА збереження: "+e.getClass().getSimpleName();}}
 public void cycleMode(){override=(override+2)%5-1;}
 public String modeLabel(){return override<0?"Режим: авто":"Режим: "+Planner.Mode.values()[override];}
 @Override public void onCreate(){super.onCreate();instance=this;main=new Handler(getMainLooper());learningFile=new java.io.File(getFilesDir(),"online-dqn-v1.bin");}
 @Override public int onStartCommand(Intent intent,int flags,int id){
  if(intent==null){stopSelf();return START_NOT_STICKY;}if("STOP".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
  if(projection!=null)return START_NOT_STICKY;
  NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("pilot","Tom Run Pilot",NotificationManager.IMPORTANCE_LOW));
  PendingIntent stop=PendingIntent.getService(this,0,new Intent(this,CaptureService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE);
  Notification n=new Notification.Builder(this,"pilot").setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("Tom Run Pilot · Prototype").setContentText("Екран аналізується локально. Авто вмикається на панелі.").setOngoing(true).addAction(new Notification.Action.Builder(null,"Стоп",stop).build()).build();
  startForeground(7,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
  try{
   vision=new VisionEngine(this);thread=new HandlerThread("pilot-vision");thread.start();worker=new Handler(thread.getLooper());worker.post(()->{if(learningFile.exists())try{learning.agent.load(learningFile);saveStatus=" · модель завантажено";}catch(java.io.IOException e){saveStatus=" · не вдалося завантажити модель";}});
   Rect screen=getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();frameW=180;frameH=Math.round((float)screen.height()/screen.width()*frameW);
   reader=ImageReader.newInstance(frameW,frameH,PixelFormat.RGBA_8888,2);reader.setOnImageAvailableListener(this::frame,worker);
   Intent consent=Build.VERSION.SDK_INT>=33?intent.getParcelableExtra("consent",Intent.class):intent.getParcelableExtra("consent");
   projection=getSystemService(MediaProjectionManager.class).getMediaProjection(intent.getIntExtra("code",Activity.RESULT_CANCELED),consent);
   projection.registerCallback(new MediaProjection.Callback(){@Override public void onStop(){stopSelf();}@Override public void onCapturedContentResize(int w,int h){if(w>h){pause();stopSelf();return;}if(worker!=null)worker.post(()->resizeCapture(w,h));}},main);
   display=projection.createVirtualDisplay("TomRunPilot",frameW,frameH,getResources().getDisplayMetrics().densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,worker);
   GestureService g=GestureService.instance;if(g!=null)g.showPanel();
  }catch(Exception e){android.widget.Toast.makeText(this,"Не вдалося почати: "+e.getClass().getSimpleName(),android.widget.Toast.LENGTH_LONG).show();stopSelf();}
  return START_NOT_STICKY;
 }
 private void resizeCapture(int width,int height){
  if(width<=0||height<=0||display==null||closing)return;int targetHeight=Math.round(180f*height/width);if(targetHeight==frameH)return;
  ImageReader previous=reader;ImageReader next=ImageReader.newInstance(180,targetHeight,PixelFormat.RGBA_8888,2);next.setOnImageAvailableListener(this::frame,worker);
  display.resize(180,targetHeight,getResources().getDisplayMetrics().densityDpi);display.setSurface(next.getSurface());reader=next;frameW=180;frameH=targetHeight;previous.setOnImageAvailableListener(null,null);previous.close();vision.reset();
 }
 private void frame(ImageReader source){
  Image image=null;Bitmap bitmap=null;
  try{image=source.acquireLatestImage();if(image==null)return;if(source!=reader)return;long now=SystemClock.elapsedRealtime();if(now-lastFrame<(learningMode?160:60))return;lastFrame=now;
   GestureService g=GestureService.instance;if(g==null){pause();return;}if(!g.inGame()){if(enabled)pause();enabled=false;g.update("Гра не визначена активною\n"+g.diagnostic(),false);return;}
   Image.Plane plane=image.getPlanes()[0];ByteBuffer b=plane.getBuffer();int[] pixels=new int[frameW*frameH];int row=plane.getRowStride(),pixel=plane.getPixelStride();
   for(int y=0;y<frameH;y++)for(int x=0;x<frameW;x++){int i=y*row+x*pixel;int r=b.get(i)&255,gg=b.get(i+1)&255,bb=b.get(i+2)&255;pixels[y*frameW+x]=0xff000000|(r<<16)|(gg<<8)|bb;}
   image.close();image=null;bitmap=Bitmap.createBitmap(pixels,frameW,frameH,Bitmap.Config.ARGB_8888);
   if(learningMode){learningFrame(pixels,bitmap,now,g);return;}
   VisionEngine.Result result=vision.analyze(bitmap,now);planner.observeLane(result.playerLane,SystemClock.elapsedRealtime());
   if(gesturePending&&now-pendingSince>900){gesturePending=false;commandGeneration.incrementAndGet();}
   Planner.Mode mode=override<0?result.mode:Planner.Mode.values()[override];Planner.Decision decision=gesturePending?new Planner.Decision(Planner.Action.NONE,"Очікування завершення жесту"):planner.decide(result.objects,mode,now);
   long spent=SystemClock.elapsedRealtime()-now;long finished=SystemClock.elapsedRealtime();
   if(latestFrame!=null)latestFrame.recycle();latestFrame=bitmap.copy(Bitmap.Config.ARGB_8888,false);latestResult=result;latestLane=planner.lane;latestAction=decision.action;latestInfo=(enabled?"АВТО":"Перегляд")+" · "+mode+" · "+spent+" мс · "+decision.reason;g.showDetections(result,planner.lane,decision.action);
   g.update((enabled?"АВТО":"Перегляд")+" · "+mode+" · "+spent+" мс\n"+result.debug+" · "+decision.reason+" · "+decision.action+"\n"+g.diagnostic(),enabled);
   if(enabled&&!gesturePending&&decision.action!=Planner.Action.NONE){gesturePending=true;long generation=commandGeneration.incrementAndGet();pendingSince=now;main.post(()->dispatchStep(decision,decision.steps,now,generation));}
  }catch(Exception e){enabled=false;GestureService g=GestureService.instance;if(g!=null)g.update("Пауза: "+e.getClass().getSimpleName(),false);}
  finally{if(image!=null)image.close();if(bitmap!=null)bitmap.recycle();}
 }
 private void learningFrame(int[] pixels,Bitmap bitmap,long now,GestureService g){
  g.hideDetections();if(latestFrame!=null)latestFrame.recycle();latestFrame=bitmap.copy(Bitmap.Config.ARGB_8888,false);latestResult=null;
  if(!enabled||!learning.active()){latestInfo="Навчання · пауза";g.update("Почни забіг вручну й натисни Авто.\n"+learning.status()+saveStatus,false);return;}
  if(learning.deathVisible(pixels,frameW,frameH,now)){enabled=false;commandGeneration.incrementAndGet();gesturePending=false;learning.death(now);saveLearning();g.update("Виявлено SAVE ME. Поразку записано. Новий забіг вручну → Авто.\n"+learning.status()+saveStatus,false);return;}
  if(learning.suspectedDeath()){g.update("Перевірка екрана поразки · жести призупинені\n"+learning.status(),true);return;}
  if(gesturePending){if(now-pendingSince>900){gesturePending=false;commandGeneration.incrementAndGet();learning.suspend();enabled=false;g.update("Навчання зупинено: жест не завершився",false);}return;}
  int selectedMode=override<0?0:override;byte[] state=learning.observe(pixels,frameW,frameH,selectedMode,now);int chosen=learning.choose(state);Planner.Action action=Planner.Action.values()[chosen];latestAction=action;latestInfo=learning.status();
  if(action==Planner.Action.NONE)learning.retainCompleted(state,chosen,now);
  else{gesturePending=true;pendingSince=now;long generation=commandGeneration.incrementAndGet();main.post(()->{
   if(!enabled||closing||generation!=commandGeneration.get()||SystemClock.elapsedRealtime()-now>350){finishCommand(generation);return;}
   boolean sent=g.swipe(action,()->worker.post(()->{if(enabled&&generation==commandGeneration.get()){learning.retainCompleted(state,chosen,SystemClock.elapsedRealtime());gesturePending=false;}}),()->finishCommand(generation));if(!sent)finishCommand(generation);
  });}
  if(now-lastSave>15000)saveLearning();long elapsed=SystemClock.elapsedRealtime()-now;g.update(learning.status()+"\n"+action+" · "+elapsed+" мс · лише винагорода за виживання"+saveStatus,true);
 }
 private void finishCommand(long generation){worker.post(()->{if(commandGeneration.get()==generation)gesturePending=false;});}
 private void dispatchStep(Planner.Decision decision,int remaining,long observedAt,long generation){
  GestureService service=GestureService.instance;
  if(!enabled||closing||generation!=commandGeneration.get()||service==null||SystemClock.elapsedRealtime()-observedAt>550){finishCommand(generation);return;}
  if(remaining<decision.steps&&latestResult!=null){Planner.Mode mode=override<0?latestResult.mode:Planner.Mode.values()[override];if(!planner.canContinueRoute(latestResult.objects,decision.targetLane,mode)){finishCommand(generation);return;}}
  boolean sent=service.swipe(decision.action,()->{
   worker.post(()->{if(commandGeneration.get()==generation)planner.committed(decision.action,SystemClock.elapsedRealtime());});
   if(remaining>1)main.postDelayed(()->dispatchStep(decision,remaining-1,observedAt,generation),60);
   else finishCommand(generation);
  },()->finishCommand(generation));
  if(!sent)finishCommand(generation);
 }
 public void saveDebugShot(){if(worker==null)return;worker.post(()->{
  if(latestFrame==null||latestResult==null){main.post(()->android.widget.Toast.makeText(this,"Ще немає кадру гри",android.widget.Toast.LENGTH_SHORT).show());return;}
  android.net.Uri uri=null;Bitmap shot=null;
  try{
   int width=720,height=Math.round((float)latestFrame.getHeight()/latestFrame.getWidth()*width);shot=Bitmap.createBitmap(width,height+100,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(shot);canvas.drawColor(Color.BLACK);canvas.drawBitmap(latestFrame,null,new Rect(0,0,width,height),null);DetectionView.draw(canvas,width,height,latestResult.boxes,latestLane,latestAction.name());Paint text=new Paint(Paint.ANTI_ALIAS_FLAG);text.setColor(Color.WHITE);text.setTextSize(18);canvas.drawText(latestInfo,10,height+25,text);canvas.drawText(latestResult.debug,10,height+50,text);
   ContentValues values=new ContentValues();values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"TomPilot-"+System.currentTimeMillis()+".png");values.put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/TomRunPilot");values.put(android.provider.MediaStore.Images.Media.IS_PENDING,1);uri=getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new java.io.IOException("MediaStore insert failed");
   try(java.io.OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null||!shot.compress(Bitmap.CompressFormat.PNG,100,out))throw new java.io.IOException("PNG save failed");}
   values.clear();values.put(android.provider.MediaStore.Images.Media.IS_PENDING,0);getContentResolver().update(uri,values,null,null);main.post(()->android.widget.Toast.makeText(this,"Фото з рамками збережене в Pictures/TomRunPilot",android.widget.Toast.LENGTH_LONG).show());
  }catch(Exception e){if(uri!=null)getContentResolver().delete(uri,null,null);main.post(()->android.widget.Toast.makeText(this,"Не вдалося зберегти фото",android.widget.Toast.LENGTH_LONG).show());}finally{if(shot!=null)shot.recycle();}
 });}
 @Override public void onDestroy(){enabled=false;commandGeneration.incrementAndGet();instance=null;if(worker!=null)worker.post(()->{learning.suspend();saveLearning();if(latestFrame!=null){latestFrame.recycle();latestFrame=null;}});if(!closing){closing=true;if(reader!=null)reader.setOnImageAvailableListener(null,null);if(display!=null)display.release();if(projection!=null)projection.stop();if(reader!=null)reader.close();if(thread!=null)thread.quitSafely();}GestureService g=GestureService.instance;if(g!=null)g.hidePanel();super.onDestroy();}
 @Override public IBinder onBind(Intent i){return null;}
}
