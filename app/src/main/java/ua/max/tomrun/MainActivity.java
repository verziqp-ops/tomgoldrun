package ua.max.tomrun;
import android.app.*;import android.os.*;import android.content.*;import android.media.projection.*;import android.provider.Settings;import android.graphics.Color;import android.widget.*;
public final class MainActivity extends Activity {
 @Override public void onCreate(Bundle s){super.onCreate(s);
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(40,60,40,24);root.setBackgroundColor(Color.rgb(19,23,31));
  TextView title=new TextView(this);title.setText("Tom Run Pilot\nПрототип 0.6");title.setTextSize(27);title.setTextColor(Color.WHITE);root.addView(title);
  TextView text=new TextView(this);text.setText("Виправлено рішення про стрибок і пригинання. Рамки мають показувати тип: транспорт, низька перешкода або перекладина. Це ще не універсальний ШІ. Кнопка Фото зберігає кадр з рамками без другого захоплення екрана.\n\nЕкспериментальне розпізнавання за твоїм відео. Це ще не перевірений автопілот.\n\n1. Увімкни спеціальні можливості для Tom Run Pilot.\n2. Запусти захоплення й обери саме гру.\n3. Відкрий гру та почни забіг вручну.\n4. Спершу перевір розпізнавання в режимі Перегляд. Потім натисни Увімкнути авто. Зелений напис АВТО означає, що керування працює.\n\nЗолото, сині предмети й бомбочки; арки обходити. У польоті без стрибків. Політ обирай вручну на панелі. Рамки краще вмикати лише якщо захоплюєш саме гру; інакше вони потрапляють у розпізнавання.\n\nНемає реклами, інтернет-дозволу чи надсилання екрана. Автопілот не перезапускає гру після поразки.");text.setTextSize(16);text.setTextColor(Color.LTGRAY);text.setPadding(0,30,0,30);root.addView(text);
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
