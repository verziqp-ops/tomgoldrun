import ua.max.tomrun.*;import java.io.*;import java.nio.file.*;import java.util.*;
public class LearningTests {
 static int checks;static void check(boolean ok,String m){checks++;if(!ok)throw new AssertionError(m);}
 public static void main(String[] args)throws Exception{
  OnlineDqn q=new OnlineDqn(12,71);byte[] a=new byte[12],b=new byte[12];Arrays.fill(a,(byte)25);Arrays.fill(b,(byte)230);
  for(int i=0;i<2500;i++){byte[] s=i%2==0?a:b;int action=i%5;q.remember(s,action,action==(i%2==0?1:2)?1:-1,s,true,31);q.train(4);}
  check(q.choose(a,31,false)==1,"Learns action for dark state");check(q.choose(b,31,false)==2,"Learns different action for light state");
  for(int i=0;i<100;i++)check(q.choose(a,7,true)<3,"Flight excludes vertical actions");
  File file=Files.createTempFile("dqn-test",".bin").toFile();q.finishEpisode(8.2);q.save(file);OnlineDqn restored=new OnlineDqn(12,19);restored.load(file);
  check(restored.episodes==1&&restored.bestSeconds==8.2,"Episode persisted");check(restored.transitions==q.transitions&&restored.updates==q.updates&&restored.memorySize()==q.memorySize(),"Training and replay persisted");check(restored.choose(a,31,false)==1&&restored.choose(b,31,false)==2,"Learned decisions survive restart");
  byte[] bytes=Files.readAllBytes(file.toPath());Files.write(file.toPath(),Arrays.copyOf(bytes,80));try{restored.load(file);throw new AssertionError("Accepted truncated model");}catch(IOException expected){}check(restored.choose(a,31,false)==1,"Failed load does not damage active model");file.delete();
  FrameHistory history=new FrameHistory();int[] dark=new int[100*200];Arrays.fill(dark,0xff000000);byte[] first=history.observe(dark,100,200,0);int[] light=dark.clone();Arrays.fill(light,0xffffffff);byte[] second=history.observe(light,100,200,1);
  check(first.length==FrameHistory.INPUTS,"Frame input shape");check((second[0]&255)==0&&(second[20*28*3]&255)==255,"Ordered frame history preserves motion");check((second[FrameHistory.INPUTS-3]&255)==255,"Mode input");
  LearningSession session=new LearningSession();session.start(100);byte[] s=session.observe(dark,100,200,0,100);session.retainCompleted(s,1,100);session.observe(light,100,200,0,300);check(session.agent.transitions==1,"Completed action recorded");session.death(400);check(session.agent.episodes==1&&session.agent.transitions==2&&!session.active(),"Death adds terminal experience");session.start(500);session.death(600);check(session.agent.transitions==2,"New episode does not penalize previous episode action");session.start(700);s=session.observe(dark,100,200,0,700);session.choose(s);session.observe(light,100,200,0,900);check(session.agent.transitions==2,"Uncompleted gesture not recorded");session.suspend();check(session.agent.episodes==2,"Pause not counted as death");
  int w=100,h=200;int[] panel=new int[w*h];Arrays.fill(panel,0xff222222);for(int y=78;y<126;y++)for(int x=17;x<82;x++)panel[y*w+x]=0xffaadddf;for(int y=104;y<116;y++)for(int x=38;x<64;x++)panel[y*w+x]=0xffffbb20;
  RunSignals signals=new RunSignals();check(!signals.observe(panel,w,h)&&!signals.observe(panel,w,h)&&signals.observe(panel,w,h),"Death needs three consistent frames");check(!signals.observe(dark,w,h),"Scene clears death votes");
  session.start(1000);check(!session.deathVisible(panel,w,h,1100)&&session.suspectedDeath(),"Early death candidate freezes decisions before terminal label");
  System.out.println("Learning: "+checks+" checks passed. Toy-state learning only; gameplay skill untested.");
 }
}
