import ua.max.tomrun.RgbMatcher;
import java.awt.image.BufferedImage;import java.awt.Graphics2D;import javax.imageio.ImageIO;import java.nio.file.*;import java.util.*;
/** Source-frame smoke test only. It does NOT measure generalization to unseen video. */
public class TemplateAudit {
 public static void main(String[] args)throws Exception{
  Path assets=Path.of(args[0]),frames=Path.of(args[1]);
  Map<String,Integer> frame=Map.of("gold",1,"blue",7,"bomb",9,"car_red",5,"tall_x",8,"duck_bar",19,"barrel_air",13,"boss_truck",9,"jetpack",13,"portal_city",6);
  for(String line:Files.readAllLines(assets.resolve("index.tsv"))){if(line.startsWith("#")||line.isBlank())continue;String[] s=line.split("\t");if(!frame.containsKey(s[0]))continue;
   BufferedImage template=ImageIO.read(assets.resolve(s[0]+".png").toFile()),src=ImageIO.read(frames.resolve(String.format("s%02d.jpg",frame.get(s[0]))).toFile());
   int w=180,h=390;BufferedImage resized=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);Graphics2D g=resized.createGraphics();g.drawImage(src,0,0,w,h,null);g.dispose();
   int[] p=resized.getRGB(0,0,w,h,null,0,w),tp=template.getRGB(0,0,template.getWidth(),template.getHeight(),null,0,template.getWidth());
   float[] tv=new float[RgbMatcher.N],patch=new float[RgbMatcher.N];RgbMatcher.sample(tp,template.getWidth(),0,0,template.getWidth(),template.getHeight(),tv);
   float best=-1;int bx=0,by=0;
   for(String size:s[5].split(",")){int sw=Integer.parseInt(size),sh=Math.round(sw*(float)template.getHeight()/template.getWidth());if(sw>=w||sh>=h)continue;int ymin=Math.max(0,(int)(h*Float.parseFloat(s[2]))-sh/2),ymax=Math.min(h-sh,(int)(h*Float.parseFloat(s[3]))-sh/2);
    for(int y=ymin;y<=ymax;y+=2)for(int x=0;x<=w-sw;x+=2){RgbMatcher.sample(p,w,x,y,sw,sh,patch);float score=RgbMatcher.score(tv,patch);if(score>best){best=score;bx=x+sw/2;by=y+sh/2;}}
   }
   System.out.printf(Locale.ROOT,"%s %.3f >= %s: %s (%.2f,%.2f)%n",s[0],best,s[4],best>=Float.parseFloat(s[4])?"PASS":"MISS",bx/(float)w,by/(float)h);
  }
 }
}
