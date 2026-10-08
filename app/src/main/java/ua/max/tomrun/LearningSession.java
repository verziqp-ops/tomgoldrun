package ua.max.tomrun;
/** Worker-thread episode lifecycle. Only completed gestures enter replay. */
public final class LearningSession {
 public final OnlineDqn agent=new OnlineDqn(FrameHistory.INPUTS,71);
 private final FrameHistory history=new FrameHistory();private final RunSignals signals=new RunSignals();
 private byte[] previous;private int action,mask=31;private long actionAt,started;private boolean active;
 public void start(long now){previous=null;lastState=null;history.reset();signals.reset();started=now;active=true;}
 public void suspend(){previous=null;lastState=null;active=false;history.reset();signals.reset();}
 public boolean active(){return active;}
 public int actionMask(int mode){return mode==1||mode==3?7:31;}
 public byte[] observe(int[] pixels,int w,int h,int mode,long now){
  byte[] state=history.observe(pixels,w,h,mode);mask=actionMask(mode);
  if(previous!=null){float reward=(float)Math.min(.025,Math.max(0,now-actionAt)/1000.0*.05);agent.remember(previous,action,reward,state,false,mask);previous=null;agent.train(4);}
  return state;
 }
 public int choose(byte[] state){return agent.choose(state,mask,true);}
 public void completed(byte[] state,int chosen,long now){if(!active)return;previous=state.clone();action=chosen;actionAt=now;}
 public boolean deathVisible(int[] p,int w,int h,long now){return active&&now-started>2000&&signals.observe(p,w,h);}
 public void death(long now){if(!active)return;if(previous!=null)agent.remember(previous,action,-1,previous,true,mask);else if(lastState!=null)agent.remember(lastState,lastAction,-1,lastState,true,mask);agent.train(16);agent.finishEpisode(Math.max(0,now-started)/1000.0);suspend();lastState=null;}
 private byte[] lastState;private int lastAction;
 public void retainCompleted(byte[] state,int chosen,long now){completed(state,chosen,now);lastState=state.clone();lastAction=chosen;}
 public String status(){return "Навчання · спроби "+agent.episodes+" · досвід "+agent.transitions+" · оновлення "+agent.updates+" · дослідження "+Math.round(agent.epsilon()*100)+"% · рекорд "+Math.round(agent.bestSeconds)+" с";}
}
