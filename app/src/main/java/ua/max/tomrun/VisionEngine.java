package ua.max.tomrun;
import android.content.Context;import android.graphics.*;import java.io.*;import java.util.*;
/** Experimental multiscale RGB template matching; NOT a trained general detector. */
public final class VisionEngine {
 private static final int NX=6,NY=8,N=NX*NY*3;
 static final class Template {String name,kind;float aspect,minY,maxY,threshold;float[] vector;int[] sizes;}
 static final class Match {Template t;float x,y,score,w,h;Match(Template t,float x,float y,float s,float w,float h){this.t=t;this.x=x;this.y=y;score=s;this.w=w;this.h=h;}}
 static final class Track {float y;long time;int hits;}
 public static final class Result {public List<Planner.ObjectInfo> objects=new ArrayList<>();public Planner.Mode mode=Planner.Mode.GROUND;public int playerLane=-1;public String debug="";}
 private final List<Template> templates=new ArrayList<>();private final Map<String,Track> tracks=new HashMap<>();
 private final Map<String,Long> markers=new HashMap<>();private Planner.Mode stable=Planner.Mode.GROUND,candidate=Planner.Mode.GROUND;private int modeVotes;
 public VisionEngine(Context c)throws IOException {
  try(BufferedReader r=new BufferedReader(new InputStreamReader(c.getAssets().open("templates/index.tsv")))){
   String line;while((line=r.readLine())!=null){if(line.startsWith("#")||line.isBlank())continue;String[] a=line.split("\t");Template t=new Template();t.name=a[0];t.kind=a[1];t.minY=Float.parseFloat(a[2]);t.maxY=Float.parseFloat(a[3]);t.threshold=Float.parseFloat(a[4]);String[] ss=a[5].split(",");t.sizes=new int[ss.length];for(int i=0;i<ss.length;i++)t.sizes[i]=Integer.parseInt(ss[i]);
    Bitmap b;try(InputStream in=c.getAssets().open("templates/"+t.name+".png")){b=BitmapFactory.decodeStream(in);}if(b==null)throw new IOException("Invalid template "+t.name);t.aspect=(float)b.getHeight()/b.getWidth();int[] pixels=new int[b.getWidth()*b.getHeight()];b.getPixels(pixels,0,b.getWidth(),0,0,b.getWidth(),b.getHeight());t.vector=sample(pixels,b.getWidth(),0,0,b.getWidth(),b.getHeight());b.recycle();templates.add(t);
   }
  }
 }
 private static float[] sample(int[] p,int stride,int x,int y,int w,int h){float[] v=new float[RgbMatcher.N];RgbMatcher.sample(p,stride,x,y,w,h,v);return v;}
 private List<Match> find(int[] pixels,int w,int h,Template t){List<Match> out=new ArrayList<>();for(RgbMatcher.Hit hit:RgbMatcher.search(pixels,w,h,t.vector,t.aspect,t.minY,t.maxY,t.sizes,t.threshold))out.add(new Match(t,(hit.x+hit.w*.5f)/w,(hit.y+hit.h*.5f)/h,hit.score,(float)hit.w/w,(float)hit.h/h));return out;}
 private int lane(float x,float y){float spread=.07f+.28f*Math.max(0,Math.min(1,(y-.18f)/.55f));if(x<.5f-spread/2)return 0;if(x>.5f+spread/2)return 2;return 1;}
 public void reset(){tracks.clear();markers.clear();stable=Planner.Mode.GROUND;candidate=stable;modeVotes=0;}
 public Result analyze(Bitmap bitmap,long now){Result result=new Result();int w=bitmap.getWidth(),h=bitmap.getHeight();int[] pixels=new int[w*h];bitmap.getPixels(pixels,0,w,0,0,w,h);List<Match> all=new ArrayList<>();
  for(Template t:templates)all.addAll(find(pixels,w,h,t));all.sort((a,b)->Float.compare(b.score,a.score));
  for(Match m:all)if(m.t.kind.equals("BOSS")||m.t.kind.equals("AIR"))markers.put(m.t.kind,now);
  boolean boss=now-markers.getOrDefault("BOSS",-10000L)<800,air=now-markers.getOrDefault("AIR",-10000L)<800;
  Planner.Mode detected=boss?(air?Planner.Mode.BOSS_AIR:Planner.Mode.BOSS_GROUND):(air?Planner.Mode.FLIGHT:Planner.Mode.GROUND);
  if(detected==candidate)modeVotes++;else{candidate=detected;modeVotes=1;}if(modeVotes>=2)stable=candidate;result.mode=stable;
  Set<String> seen=new HashSet<>();
  for(Match m:all){String kind=m.t.kind;if(kind.equals("BOSS")||kind.equals("AIR"))continue;if(kind.equals("PLAYER")){if(result.playerLane<0)result.playerLane=lane(m.x,m.y);continue;}
   int l=lane(m.x,m.y);String key=kind+":"+l;if(!seen.add(key))continue;
   Track track=tracks.get(key);float ttc=Float.POSITIVE_INFINITY;if(track!=null&&now-track.time<1200&&Math.abs(m.y-track.y)<.22f){float vel=(m.y-track.y)/Math.max(.001f,(now-track.time)/1000f);if(vel>.025f)ttc=Math.max(0,(.67f-m.y)/vel);track.hits++;}else{track=new Track();track.hits=1;}
   track.y=m.y;track.time=now;tracks.put(key,track);
   if(track.hits>=2||m.score>=.92f)result.objects.add(new Planner.ObjectInfo(kind,l,m.y,m.score,ttc));
  }
  tracks.entrySet().removeIf(e->now-e.getValue().time>1200);
  StringBuilder details=new StringBuilder();for(Planner.ObjectInfo o:result.objects){if(details.length()>120)break;details.append(o.kind).append(":").append(o.lane+1).append(" ");}result.debug="об’єктів: "+result.objects.size()+" · доріжка: "+(result.playerLane+1)+" · "+details;
  return result;
 }
}
