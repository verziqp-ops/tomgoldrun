package ua.max.tomrun;
import java.io.*;import java.nio.file.*;import java.util.*;
/** Small Double DQN trained locally from raw frame history. No obstacle templates. */
public final class OnlineDqn {
 public static final int ACTIONS=5,HIDDEN=24,CAPACITY=1024;
 private final int inputs;private final Random random;private Network policy,target;
 private final Transition[] replay=new Transition[CAPACITY];private int count,cursor;private final Transition[] failures=new Transition[64];private int failureCount,failureCursor;public long decisions,updates,episodes,transitions,terminalSamples,evaluationEpisodes;public double bestSeconds,bestEvaluationSeconds;
 private static final class Transition {byte[] s,n;int a,mask;float reward;boolean done;Transition(byte[] s,int a,float r,byte[] n,boolean d,int m){this.s=s.clone();this.n=n.clone();this.a=a;reward=r;done=d;mask=m;}}
 private final class Network {
  float[] w=new float[inputs*HIDDEN],b=new float[HIDDEN],out=new float[HIDDEN*ACTIONS],bias=new float[ACTIONS];
  Network(boolean init){if(init){float scale=(float)Math.sqrt(6.0/(inputs+HIDDEN));for(int i=0;i<w.length;i++)w[i]=(random.nextFloat()*2-1)*scale;for(int i=0;i<out.length;i++)out[i]=(random.nextFloat()*2-1)*.25f;}}
  float[] hidden(byte[] s){float[] h=new float[HIDDEN];for(int j=0;j<HIDDEN;j++){float z=b[j];int base=j*inputs;for(int i=0;i<inputs;i++)z+=w[base+i]*((s[i]&255)/255f-.5f);h[j]=(float)Math.tanh(z);}return h;}
  float[] values(float[] h){float[] q=bias.clone();for(int a=0;a<ACTIONS;a++)for(int j=0;j<HIDDEN;j++)q[a]+=out[a*HIDDEN+j]*h[j];return q;}
  void copyFrom(Network source){w=source.w.clone();b=source.b.clone();out=source.out.clone();bias=source.bias.clone();}
  void write(DataOutputStream d)throws IOException{for(float[] v:new float[][]{w,b,out,bias})for(float x:v)d.writeFloat(x);}
  void read(DataInputStream d)throws IOException{for(float[] v:new float[][]{w,b,out,bias})for(int i=0;i<v.length;i++){float x=d.readFloat();if(!Float.isFinite(x))throw new IOException("Non-finite weights");v[i]=x;}}
 }
 public OnlineDqn(int inputs,long seed){if(inputs<1||inputs>10000)throw new IllegalArgumentException();this.inputs=inputs;random=new Random(seed);policy=new Network(true);target=new Network(false);target.copyFrom(policy);}
 private void check(byte[] s){if(s.length!=inputs)throw new IllegalArgumentException("State shape");}
 public double epsilon(){return Math.max(.05,.35*Math.exp(-decisions/5000.0));}
 private int best(float[] q,int mask){int best=-1;for(int a=0;a<ACTIONS;a++)if((mask&(1<<a))!=0&&(best<0||q[a]>q[best]))best=a;if(best<0)throw new IllegalArgumentException("Empty mask");return best;}
 public int choose(byte[] s,int mask,boolean explore){check(s);if((mask&31)==0)throw new IllegalArgumentException("Empty mask");int a;if(explore&&random.nextDouble()<epsilon()){int n=Integer.bitCount(mask&31),k=random.nextInt(n);a=0;for(;a<ACTIONS;a++)if((mask&(1<<a))!=0&&k--==0)break;}else a=best(policy.values(policy.hidden(s)),mask);if(explore)decisions++;return a;}
 public void remember(byte[] s,int a,float reward,byte[] n,boolean done,int nextMask){check(s);check(n);if(a<0||a>=ACTIONS||!Float.isFinite(reward)||(nextMask&31)==0)throw new IllegalArgumentException();Transition t=new Transition(s,a,reward,n,done,nextMask&31);replay[cursor]=t;if(done){failures[failureCursor]=t;failureCursor=(failureCursor+1)%failures.length;failureCount=Math.min(failures.length,failureCount+1);}cursor=(cursor+1)%CAPACITY;count=Math.min(CAPACITY,count+1);transitions++;}
 public float train(int batch){if(count<24&&failureCount==0)return 0;float loss=0;for(int sample=0;sample<batch;sample++){
  Transition t=failureCount>0&&sample%4==0?failures[random.nextInt(failureCount)]:replay[random.nextInt(count)];if(t.done)terminalSamples++;float[] h=policy.hidden(t.s),q=policy.values(h);float goal=t.reward;
  if(!t.done){int a=best(policy.values(policy.hidden(t.n)),t.mask);goal+=.98f*target.values(target.hidden(t.n))[a];}
  float error=q[t.a]-goal,derivative=Math.max(-1,Math.min(1,error));loss+=Math.abs(error)<1?.5f*error*error:Math.abs(error)-.5f;
  float rate=.003f;int base=t.a*HIDDEN;float[] old=new float[HIDDEN];System.arraycopy(policy.out,base,old,0,HIDDEN);
  policy.bias[t.a]-=rate*derivative;for(int j=0;j<HIDDEN;j++){policy.out[base+j]-=rate*derivative*h[j];float dh=Math.max(-.2f,Math.min(.2f,derivative*old[j]*(1-h[j]*h[j])));policy.b[j]-=rate*dh;int offset=j*inputs;for(int i=0;i<inputs;i++)policy.w[offset+i]-=rate*dh*((t.s[i]&255)/255f-.5f);}
 }updates++;if(updates%100==0)target.copyFrom(policy);return loss/Math.max(1,batch);}
 public void finishEpisode(double seconds){finishEpisode(seconds,true);}
 public void finishEpisode(double seconds,boolean training){if(training){episodes++;bestSeconds=Math.max(bestSeconds,seconds);}else{evaluationEpisodes++;bestEvaluationSeconds=Math.max(bestEvaluationSeconds,seconds);}}
 public int failureMemorySize(){return failureCount;}
 public float[] values(byte[] s){check(s);return policy.values(policy.hidden(s));}
 public int memorySize(){return count;}
 public void save(File file)throws IOException{
  File tmp=new File(file.getPath()+".tmp");try(FileOutputStream f=new FileOutputStream(tmp);DataOutputStream d=new DataOutputStream(new BufferedOutputStream(f))){d.writeInt(0x54524451);d.writeInt(2);d.writeInt(inputs);d.writeLong(decisions);d.writeLong(updates);d.writeLong(episodes);d.writeLong(transitions);d.writeDouble(bestSeconds);d.writeLong(terminalSamples);d.writeLong(evaluationEpisodes);d.writeDouble(bestEvaluationSeconds);policy.write(d);target.write(d);d.writeInt(count);for(int i=0;i<count;i++){Transition t=replay[i];d.write(t.s);d.writeByte(t.a);d.writeFloat(t.reward);d.write(t.n);d.writeBoolean(t.done);d.writeByte(t.mask);}d.writeInt(failureCount);for(int i=0;i<failureCount;i++){Transition t=failures[i];d.write(t.s);d.writeByte(t.a);d.writeFloat(t.reward);d.write(t.n);d.writeBoolean(t.done);d.writeByte(t.mask);}d.flush();f.getFD().sync();}
  try{Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}
 }
 private Transition readTransition(DataInputStream d)throws IOException{byte[] state=new byte[inputs],next=new byte[inputs];d.readFully(state);int a=d.readUnsignedByte();float r=d.readFloat();d.readFully(next);boolean done=d.readBoolean();int m=d.readUnsignedByte();if(a>=ACTIONS||!Float.isFinite(r)||m==0||m>31)throw new IOException("Failure transition");return new Transition(state,a,r,next,done,m);}
 public void load(File file)throws IOException{
  OnlineDqn fresh=new OnlineDqn(inputs,1);try(DataInputStream d=new DataInputStream(new BufferedInputStream(new FileInputStream(file)))){
   if(d.readInt()!=0x54524451)throw new IOException("Checkpoint magic");int version=d.readInt();if((version!=1&&version!=2)||d.readInt()!=inputs)throw new IOException("Checkpoint format");fresh.decisions=d.readLong();fresh.updates=d.readLong();fresh.episodes=d.readLong();fresh.transitions=d.readLong();fresh.bestSeconds=d.readDouble();if(version>=2){fresh.terminalSamples=d.readLong();fresh.evaluationEpisodes=d.readLong();fresh.bestEvaluationSeconds=d.readDouble();if(fresh.terminalSamples<0||fresh.evaluationEpisodes<0||!Double.isFinite(fresh.bestEvaluationSeconds)||fresh.bestEvaluationSeconds<0)throw new IOException("Evaluation stats");}if(fresh.decisions<0||fresh.updates<0||fresh.episodes<0||fresh.transitions<0||!Double.isFinite(fresh.bestSeconds)||fresh.bestSeconds<0)throw new IOException("Stats");fresh.policy.read(d);fresh.target.read(d);int n=d.readInt();if(n<0||n>CAPACITY)throw new IOException("Replay size");for(int i=0;i<n;i++){byte[] s=new byte[inputs],next=new byte[inputs];d.readFully(s);int a=d.readUnsignedByte();float r=d.readFloat();d.readFully(next);boolean done=d.readBoolean();int mask=d.readUnsignedByte();try{fresh.remember(s,a,r,next,done,mask);}catch(IllegalArgumentException e){throw new IOException("Replay data",e);}}fresh.transitions-=n;if(version>=2){fresh.failureCount=0;fresh.failureCursor=0;int nf=d.readInt();if(nf<0||nf>64)throw new IOException("Failure size");for(int i=0;i<nf;i++){Transition t=fresh.readTransition(d);if(!t.done)throw new IOException("Nonterminal failure");fresh.failures[i]=t;}fresh.failureCount=nf;fresh.failureCursor=nf%64;}if(d.read()!=-1)throw new IOException("Trailing data");
  }policy=fresh.policy;target=fresh.target;System.arraycopy(fresh.replay,0,replay,0,CAPACITY);count=fresh.count;cursor=fresh.cursor;decisions=fresh.decisions;updates=fresh.updates;episodes=fresh.episodes;transitions=fresh.transitions;bestSeconds=fresh.bestSeconds;terminalSamples=fresh.terminalSamples;evaluationEpisodes=fresh.evaluationEpisodes;bestEvaluationSeconds=fresh.bestEvaluationSeconds;System.arraycopy(fresh.failures,0,failures,0,64);failureCount=fresh.failureCount;failureCursor=fresh.failureCursor;
 }
}
