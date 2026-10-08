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
 private List<Match> find(int[] pixels,int w,int h,Template t){List<Match> candidates=new ArrayList<>();float[] patch=new float[RgbMatcher.N];for(int sw:t.sizes){int sh=Math.round(sw*t.aspect);if(sw>=w||sh>=h)continue;int ymin=Math.max(0,(int)(h*t.minY)-sh/2),ymax=Math.min(h-sh,(int)(h*t.maxY)-sh/2);for(int y=ymin;y<=ymax;y+=2)for(int x=0;x<=w-sw;x+=2){RgbMatcher.sample(pixels,w,x,y,sw,sh,patch);float s=RgbMatcher.score(t.vector,patch);if(s>=t.threshold)candidates.add(new Match(t,(x+sw*.5f)/w,(y+sh*.5f)/h,s,(float)sw/w,(float)sh/h));}}
  candidates.sort((a,b)->Float.compare(b.score,a.score));List<Match> keep=new ArrayList<>();for(Match m:candidates){boolean duplicate=false;for(Match k:keep)if(Math.abs(k.x-m.x)<Math.max(k.w,m.w)*.65f&&Math.abs(k.y-m.y)<Math.max(k.h,m.h)*.65f){duplicate=true;break;}if(!duplicate)keep.add(m);if(keep.size()>=4)break;}return keep;
 }
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
   Track track=tracks.get(key);float ttc=Float.POSITIVE_INFINITY;if(track!=null&&now-track.time<350&&Math.abs(m.y-track.y)<.22f){float vel=(m.y-track.y)/Math.max(.001f,(now-track.time)/1000f);if(vel>.025f)ttc=Math.max(0,(.67f-m.y)/vel);track.hits++;}else{track=new Track();track.hits=1;}
   track.y=m.y;track.time=now;tracks.put(key,track);
   if(track.hits>=2)result.objects.add(new Planner.ObjectInfo(kind,l,m.y,m.score,ttc));
  }
  tracks.entrySet().removeIf(e->now-e.getValue().time>500);
  result.debug=stable+" · об’єктів: "+result.objects.size()+" · доріжка: "+(result.playerLane+1);
  return result;
 }
}
