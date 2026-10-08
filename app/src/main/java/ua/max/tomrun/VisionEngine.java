package ua.max.tomrun;
import android.content.Context;import android.graphics.*;import java.io.*;import java.util.*;
/** Hybrid prototype: learned vehicle patches plus item templates. */
public final class VisionEngine {
 private static final int NX=6,NY=8,N=NX*NY*3;
 static final class Template {String name,kind;float aspect,minY,maxY,threshold;float[] vector;int[] sizes;}
 static final class Match {Template t;float x,y,score,w,h;Match(Template t,float x,float y,float s,float w,float h){this.t=t;this.x=x;this.y=y;score=s;this.w=w;this.h=h;}}
 static final class Track {float y;long time;int hits;}
 public static final class Box {public final String kind;public final RectF rect;public final float confidence;public final boolean confirmed;public Box(String k,RectF r,float c,boolean ok){kind=k;rect=r;confidence=c;confirmed=ok;}}
 public static final class Result {public List<Box> boxes=new ArrayList<>();public List<Planner.ObjectInfo> objects=new ArrayList<>();public Planner.Mode mode=Planner.Mode.GROUND;public int playerLane=-1;public String debug="";}
 private final VehicleModel vehicles;
 private final List<Template> templates=new ArrayList<>();private final Map<String,Track> tracks=new HashMap<>();
 private final Map<String,Long> markers=new HashMap<>();private Planner.Mode stable=Planner.Mode.GROUND,candidate=Planner.Mode.GROUND;private int modeVotes;
 public VisionEngine(Context c)throws IOException {
  vehicles=new VehicleModel(c.getAssets().open("vehicle.bin"));
  try(BufferedReader r=new BufferedReader(new InputStreamReader(c.getAssets().open("templates/index.tsv")))){
   String line;while((line=r.readLine())!=null){if(line.startsWith("#")||line.isBlank())continue;String[] a=line.split("\t");Template t=new Template();t.name=a[0];t.kind=a[1];t.minY=Float.parseFloat(a[2]);t.maxY=Float.parseFloat(a[3]);t.threshold=Float.parseFloat(a[4]);String[] ss=a[5].split(",");t.sizes=new int[ss.length];for(int i=0;i<ss.length;i++)t.sizes[i]=Integer.parseInt(ss[i]);
    Bitmap b;try(InputStream in=c.getAssets().open("templates/"+t.name+".png")){b=BitmapFactory.decodeStream(in);}if(b==null)throw new IOException("Invalid template "+t.name);t.aspect=(float)b.getHeight()/b.getWidth();int[] pixels=new int[b.getWidth()*b.getHeight()];b.getPixels(pixels,0,b.getWidth(),0,0,b.getWidth(),b.getHeight());t.vector=sample(pixels,b.getWidth(),0,0,b.getWidth(),b.getHeight());b.recycle();if((!t.kind.equals("BLOCK")||t.name.equals("tall_x"))&&!t.kind.equals("AIR"))templates.add(t);
   }
  }
 }
 private static float[] sample(int[] p,int stride,int x,int y,int w,int h){float[] v=new float[RgbMatcher.N];RgbMatcher.sample(p,stride,x,y,w,h,v);return v;}
 private List<Match> find(int[] pixels,int w,int h,Template t){List<Match> out=new ArrayList<>();for(RgbMatcher.Hit hit:RgbMatcher.search(pixels,w,h,t.vector,t.aspect,t.minY,t.maxY,t.sizes,t.threshold))out.add(new Match(t,(hit.x+hit.w*.5f)/w,(hit.y+hit.h*.5f)/h,hit.score,(float)hit.w/w,(float)hit.h/h));return out;}
 private Box box(Match m,boolean ok){return new Box(m.t.kind,new RectF(Math.max(0,m.x-m.w/2),Math.max(0,m.y-m.h/2),Math.min(1,m.x+m.w/2),Math.min(1,m.y+m.h/2)),m.score,ok);}
 private int lane(float x,float y){return SceneGeometry.lane(x,y);}
 public void reset(){tracks.clear();markers.clear();stable=Planner.Mode.GROUND;candidate=stable;modeVotes=0;}
 public Result analyze(Bitmap bitmap,long now){Result result=new Result();int w=bitmap.getWidth(),h=bitmap.getHeight();int[] pixels=new int[w*h];bitmap.getPixels(pixels,0,w,0,0,w,h);List<Match> all=new ArrayList<>();
  for(Template t:templates)for(Match m:find(pixels,w,h,t)){
   // Reward items must lie inside the perspective road; trees and HUD are excluded.
   float spread=.07f+.28f*Math.max(0,Math.min(1,(m.y-.18f)/.55f));
   if((t.kind.equals("GOLD")||t.kind.equals("BLUE"))&&Math.abs(m.x-.5f)>spread*1.45f)continue;
   all.add(m);
  }
  Template vehicle=new Template();vehicle.kind="BLOCK";
  for(VehicleModel.Hit hit:vehicles.detect(pixels,w,h))all.add(new Match(vehicle,(hit.x+hit.w*.5f)/w,(hit.y+hit.h*.5f)/h,hit.score,(float)hit.w/w,(float)hit.h/h));
  all.sort((a,b)->Float.compare(b.score,a.score));
  for(Match m:all)if(m.t.kind.equals("BOSS")||m.t.kind.equals("AIR")){markers.put(m.t.kind,now);result.boxes.add(box(m,true));}
  boolean boss=now-markers.getOrDefault("BOSS",-10000L)<800,air=now-markers.getOrDefault("AIR",-10000L)<800;
  Planner.Mode detected=boss?(air?Planner.Mode.BOSS_AIR:Planner.Mode.BOSS_GROUND):(air?Planner.Mode.FLIGHT:Planner.Mode.GROUND);
  if(detected==candidate)modeVotes++;else{candidate=detected;modeVotes=1;}if(modeVotes>=2)stable=candidate;result.mode=stable;
  int[] orange=new int[3];
  for(int y=(int)(h*.61f);y<h*.77f;y++)for(int x=(int)(w*.12f);x<w*.88f;x++){int p=pixels[y*w+x],r=(p>>16)&255,g=(p>>8)&255,b=p&255;if(r>170&&g>95&&g<210&&b<105&&r>g*1.12f)orange[lane((float)x/w,(float)y/h)]++;}
  int best=1;for(int l=0;l<3;l++)if(orange[l]>orange[best])best=l;int runner=0;for(int l=0;l<3;l++)if(l!=best)runner=Math.max(runner,orange[l]);if(orange[best]>55&&orange[best]>runner*1.7f)result.playerLane=best;
  // A specific barrier type overrides a generic vehicle patch at the same position.
  Set<Match> generic=new HashSet<>();
  for(Match m:all)if(m.t.kind.equals("BLOCK"))for(Match typed:all)if((typed.t.kind.equals("JUMP")||typed.t.kind.equals("SLIDE"))&&typed.score>=.88f&&SceneGeometry.overlap(m.x,m.y,m.w,m.h,typed.x,typed.y,typed.w,typed.h)>.55f){generic.add(m);break;}
  Map<String,Match> selected=new LinkedHashMap<>();
  for(Match m:all){String kind=m.t.kind;if(generic.contains(m)||kind.equals("BOSS")||kind.equals("AIR"))continue;
   if(kind.equals("PLAYER")){if(result.playerLane<0){result.playerLane=lane(m.x,m.y);result.boxes.add(box(m,true));}continue;}
   for(int l:SceneGeometry.lanes(kind,m.x,m.y,m.w,m.h)){String key=kind+":"+l;Match previous=selected.get(key);
    if(previous==null||(SceneGeometry.hazard(kind)&&SceneGeometry.anchor(kind,m.y,m.h)>SceneGeometry.anchor(kind,previous.y,previous.h)))selected.put(key,m);
   }
  }
  Set<Match> drawn=new HashSet<>();
  for(Map.Entry<String,Match> entry:selected.entrySet()){String key=entry.getKey();Match m=entry.getValue();String kind=m.t.kind;int l=Integer.parseInt(key.substring(key.indexOf(':')+1));float position=SceneGeometry.anchor(kind,m.y,m.h);
   Track track=tracks.get(key);float ttc=Float.POSITIVE_INFINITY;if(track!=null&&now-track.time<1200&&Math.abs(position-track.y)<.22f){float vel=(position-track.y)/Math.max(.001f,(now-track.time)/1000f);if(vel>.025f)ttc=Math.max(0,(.76f-position)/vel);track.hits++;}else{track=new Track();track.hits=1;}
   track.y=position;track.time=now;tracks.put(key,track);boolean confirmed=track.hits>=2||m.score>=.92f;
   if(drawn.add(m))result.boxes.add(box(m,confirmed));
   if(confirmed)result.objects.add(new Planner.ObjectInfo(kind,l,position,m.score,ttc));
  }
  tracks.entrySet().removeIf(e->now-e.getValue().time>1200);
  StringBuilder details=new StringBuilder();for(Planner.ObjectInfo o:result.objects){if(details.length()>120)break;details.append(o.kind).append(":").append(o.lane+1).append(" ");}result.debug="об’єктів: "+result.objects.size()+" · доріжка: "+(result.playerLane+1)+" · "+details;
  return result;
 }
}
