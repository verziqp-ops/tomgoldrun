package ua.max.tomrun;
import android.app.*;import android.os.*;import android.content.*;import android.media.projection.*;import android.provider.Settings;import android.graphics.Color;import android.widget.*;
public final class MainActivity extends Activity {
 @Override public void onCreate(Bundle s){super.onCreate(s);
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(40,60,40,24);root.setBackgroundColor(Color.rgb(19,23,31));
  TextView title=new TextView(this);title.setText("Tom Run Pilot\nПрототип 0.8 · Навчання");title.setTextSize(27);title.setTextColor(Color.WHITE);root.addView(title);
  TextView text=new TextView(this);text.setText("Додано окремий режим навчання нейромережі на телефоні. Вона аналізує чотири послідовні кадри, пробує рухи та оновлює модель із досвіду. Спочатку вона не вміє грати й часто врізатиметься. Досвід і модель зберігаються в додатку між запусками. Видалення додатка очищає навчання.\n\n1. Увімкни спеціальні можливості.\n2. Почни захоплення: обери саме гру.\n3. На панелі натисни Навчання: перемкнути.\n4. Почни забіг вручну, потім натисни Увімкнути авто.\n5. Після поразки натисни Зіткнення, якщо додаток не визначив SAVE ME. Почни новий забіг вручну й знову натисни Авто. Пауза не рахується поразкою.\n\nНавчання наразі винагороджує виживання; збір золота й уникнення арок окремо ще не оцінюються. Результат тривалих забігів не перевірений. Перезапуск гри ручний. У польоті обери FLIGHT або BOSS_AIR на панелі — тоді стрибки й пригинання заблоковані.\n\nЗвичайний режим залишено окремо. Рамки під час навчання вимикаються. Немає реклами, надсилання екрана чи інтернет-дозволу.");text.setTextSize(16);text.setTextColor(Color.LTGRAY);text.setPadding(0,30,0,30);root.addView(text);
  button(root,"1 · Спеціальні можливості",()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
  button(root,"2 · Захоплення екрана",()->{
   if(GestureService.instance==null){Toast.makeText(this,"Спочатку увімкни службу спеціальних можливостей",Toast.LENGTH_LONG).show();return;}
   MediaProjectionManager m=getSystemService(MediaProjectionManager.class);
   Intent capture=Build.VERSION.SDK_INT>=34?m.createScreenCaptureIntent(MediaProjectionConfig.createConfigForUserChoice()):m.createScreenCaptureIntent();
   startActivityForResult(capture,41);
  });
  button(root,"Відкрити Talking Tom Gold Run",()->{Intent i=getPackageManager().getLaunchIntentForPackage(GestureService.GAME);if(i!=null)startActivity(i);else Toast.makeText(this,"Гру не знайдено",Toast.LENGTH_SHORT).show();});
  button(root,"Зупинити",()->stopService(new Intent(this,CaptureService.class)));
  ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},42);
 }
 private void button(LinearLayout l,String label,Runnable action){Button b=new Button(this);b.setText(label);b.setOnClickListener(v->action.run());l.addView(b);}
 @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(r==41&&c==RESULT_OK&&d!=null){Intent i=new Intent(this,CaptureService.class);i.putExtra("code",c);i.putExtra("consent",d);startForegroundService(i);}}
}
