package ua.max.tomrun;
import java.util.ArrayDeque;
/** Worker-thread episode lifecycle. Retain recent actions until their outcome is known. */
public final class LearningSession {
 public final OnlineDqn agent=new OnlineDqn(FrameHistory.INPUTS,71);
 private final FrameHistory history=new FrameHistory();private final RunSignals signals=new RunSignals();
 private byte[] previous;private int action,mask=31;private long actionAt,started;private boolean active,training=true;
 private static final class Step {byte[] s,n;int a,mask;float reward;Step(byte[] s,int a,float r,byte[] n,int m){this.s=s;this.a=a;reward=r;this.n=n;mask=m;}}
 private final ArrayDeque<Step> tail=new ArrayDeque<>();
 public float lastLoss,lastDeathQBefore,lastDeathQAfter;public long lastPenaltySamples;
 public void setTraining(boolean value){if(active)throw new IllegalStateException("Pause first");training=value;}
 public boolean training(){return training;}
 public void start(long now){previous=null;tail.clear();history.reset();signals.reset();started=now;active=true;}
 public void suspend(){previous=null;tail.clear();active=false;history.reset();signals.reset();}
 public boolean active(){return active;}
 public int actionMask(int mode){return mode==1||mode==3?7:31;}
 public byte[] observe(int[] pixels,int w,int h,int mode,long now){
  byte[] state=history.observe(pixels,w,h,mode);mask=actionMask(mode);
  if(previous!=null){if(training){float reward=(float)Math.min(.025,Math.max(0,now-actionAt)/1000.0*.05);tail.addLast(new Step(previous,action,reward,state,mask));if(tail.size()>4){Step t=tail.removeFirst();agent.remember(t.s,t.a,t.reward,t.n,false,t.mask);}lastLoss=agent.train(4);}previous=null;}
  return state;
 }
 public int choose(byte[] state){return agent.choose(state,mask,training);}
 public void retainCompleted(byte[] state,int chosen,long now){if(!active)return;previous=state.clone();action=chosen;actionAt=now;}
 public boolean deathVisible(int[] p,int w,int h,long now){return active&&signals.observe(p,w,h);}
 public boolean suspectedDeath(){return active&&signals.pending();}
 public void death(long now){
  if(!active)return;lastPenaltySamples=0;
  if(training){
   if(previous!=null)tail.addLast(new Step(previous,action,0,previous,mask));
   if(!tail.isEmpty()){
    Step latest=tail.peekLast();latest.reward=-1;byte[] lastState=latest.s;int lastAction=latest.a;lastDeathQBefore=agent.values(lastState)[lastAction];float result=0;
    while(!tail.isEmpty()){Step t=tail.removeLast();result=t.reward+.98f*result;agent.remember(t.s,t.a,result,t.n,true,t.mask);}
    long before=agent.terminalSamples;lastLoss=agent.train(32);lastPenaltySamples=agent.terminalSamples-before;lastDeathQAfter=agent.values(lastState)[lastAction];
   }
  }
  agent.finishEpisode(Math.max(0,now-started)/1000.0,training);suspend();
 }
 public String status(){return (training?"Тренування":"Перевірка без випадкових рухів")+" · спроби "+agent.episodes+" · перевірки "+agent.evaluationEpisodes+" · досвід "+agent.transitions+"\nПам’ять поразок "+agent.failureMemorySize()+" · штрафів у навчанні "+agent.terminalSamples+" · останній штраф ×"+lastPenaltySamples+" · випадковість "+(training?Math.round(agent.epsilon()*100):0)+"%\nРекорд тренування "+Math.round(agent.bestSeconds)+" с · перевірки "+Math.round(agent.bestEvaluationSeconds)+" с";}
}
