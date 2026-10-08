"""Train a tiny patch classifier with NumPy; no cloud or runtime dependency.
Usage: python tools/train_vehicle.py UPLOAD_DIR VIDEO_FRAMES_DIR
Annotations are pixel boxes. Photo 05 is reserved for development checks, never training.
This is a prototype on one player's captures, not a general detector.
"""
import sys, json, struct
from pathlib import Path
import numpy as np
from PIL import Image
rng=np.random.default_rng(51)
root=Path(__file__).resolve().parents[1]
u,f=map(Path,sys.argv[1:])
annotations=[
 (u/'01-1000013088.png',[(25,350,266,577),(335,350,416,451)]),
 (u/'02-1000013087.png',[(177,350,330,602),(333,350,600,913)]),
 (u/'03-1000013085.png',[]),
 (u/'04-1000013086.png',[]),
 (f/'s05.jpg',[(95,275,187,356),(183,174,235,252)]),
 (f/'s06.jpg',[(0,322,63,395)]),
 (f/'s17.jpg',[(18,329,144,458),(163,176,204,223)]),
]
def feature(p,x,y,w,h):
 # 4x6 RGB cell means and horizontal+vertical contrast, matching Java exactly.
 cells=[]
 for cy in range(6):
  for cx in range(4):
   a=p[y+cy*h//6:y+(cy+1)*h//6,x+cx*w//4:x+(cx+1)*w//4]
   cells.append(a.mean(axis=(0,1))/255)
 c=np.array(cells).reshape(6,4,3)
 gray=c.mean(axis=2)
 edge=np.zeros((6,4));edge[:,:-1]+=abs(gray[:,1:]-gray[:,:-1]);edge[:-1]+=abs(gray[1:]-gray[:-1])
 return np.concatenate([c.ravel()*2-1,edge.ravel()*2]).astype('float32')
X=[];Y=[]
for path,boxes in annotations:
 im=Image.open(path).convert('RGB');scale=180/im.width;p=np.array(im.resize((180,round(im.height*scale)),Image.Resampling.BILINEAR));H,W=p.shape[:2]
 boxes=[tuple(round(v*scale) for v in b) for b in boxes]
 for x1,y1,x2,y2 in boxes:
  for n in range(220):
   w=max(8,round((x2-x1)*rng.uniform(.88,1.12)));h=max(12,round((y2-y1)*rng.uniform(.88,1.12)))
   x=int(np.clip(x1+rng.integers(-3,4),0,W-w));y=int(np.clip(y1+rng.integers(-3,4),0,H-h))
   v=feature(p,x,y,w,h);v[:72]=np.clip((v[:72]+1)*rng.uniform(.7,1.2)+rng.uniform(-.1,.1)-1,-1,1);X.append(v);Y.append(1)
 for n in range(2200):
  w=int(rng.integers(10,70));h=int(min(H*.35,w*rng.uniform(.8,2.2)));x=int(rng.integers(0,W-w));y=int(rng.integers(int(H*.16),int(H*.7)))
  if y+h>=H:continue
  # Ignore patches containing any significant annotated vehicle area.
  if any(max(0,min(x+w,b[2])-max(x,b[0]))*max(0,min(y+h,b[3])-max(y,b[1]))> .1*min(w*h,(b[2]-b[0])*(b[3]-b[1])) for b in boxes):continue
  X.append(feature(p,x,y,w,h));Y.append(0)
X=np.array(X);Y=np.array(Y,dtype='float32')[:,None]
A=rng.normal(0,.12,(96,24)).astype('float32');a=np.zeros((1,24),dtype='float32');B=rng.normal(0,.12,(24,1)).astype('float32');b=np.zeros((1,1),dtype='float32')
# Balanced batches prevent abundant background from dominating positives.
pos=np.flatnonzero(Y[:,0]==1);neg=np.flatnonzero(Y[:,0]==0)
for it in range(2200):
 ix=np.r_[rng.choice(pos,64),rng.choice(neg,64)];xx=X[ix];yy=Y[ix]
 z=np.tanh(xx@A+a);o=1/(1+np.exp(-np.clip(z@B+b,-25,25)));d=(o-yy)/128;dz=(d@B.T)*(1-z*z)
 rate=.08 if it<1500 else .025
 A-=rate*(xx.T@dz+.0001*A);a-=rate*dz.sum(0);B-=rate*(z.T@d+.0001*B);b-=rate*d.sum(0)
# Mine hard negatives from training-only roof/death images using runtime proposals.
for path,boxes in annotations:
 if path.name not in ['03-1000013085.png','04-1000013086.png']:continue
 im=Image.open(path).convert('RGB');p=np.array(im.resize((180,415),Image.Resampling.BILINEAR));H=390
 for width in [12,18,26,38,54,72]:
  for aspect in [.85,1.35,1.9]:
   height=round(width*aspect)
   for y in range(round(H*.20),int(H*.64)-height,6):
    cy=(y+height*.5)/H;spread=.07+.28*min(1,max(0,(cy-.18)/.55))
    for lane in range(3):
     center=round(180*(.5+(lane-1)*spread))
     for shift in [-4,0,4]:
      x=center-width//2+shift
      if x<0 or x+width>180:continue
      v=feature(p,x,y,width,height)
      o=float((1/(1+np.exp(-(np.tanh(v@A+a)@B+b)))).item())
      if o>.6:
       for j in range(5):X=np.vstack([X,v]);Y=np.vstack([Y,[[0]]])
neg=np.flatnonzero(Y[:,0]==0)
for it in range(1800):
 ix=np.r_[rng.choice(pos,64),rng.choice(neg,64)];xx=X[ix];yy=Y[ix]
 z=np.tanh(xx@A+a);o=1/(1+np.exp(-np.clip(z@B+b,-25,25)));d=(o-yy)/128;dz=(d@B.T)*(1-z*z)
 rate=.03
 A-=rate*(xx.T@dz+.0001*A);a-=rate*dz.sum(0);B-=rate*(z.T@d+.0001*B);b-=rate*d.sum(0)
model=root/'app/src/main/assets/vehicle.bin' 
with model.open('wb') as out:
 out.write(struct.pack('>iii',0x54524d4c,96,24))
 for v in [A,a,B,b]:out.write(v.astype('>f4').tobytes())
def score(v):return float((1/(1+np.exp(-(np.tanh(v@A+a)@B+b)))).item())
# Development photo withheld from training: nearest road-window proposal and explicit tree/HUD negatives.
p=np.array(Image.open(u/'05-1000013084.png').convert('RGB').resize((180,415),Image.Resampling.BILINEAR))
checks={'held_out_bus':(68,160,42,61),'tree_left':(0,50,24,100),'tree_right':(150,50,28,100),'hud_gold':(23,23,24,15),'empty_road':(62,240,32,44)}
report={k:round(score(feature(p,*v)),4) for k,v in checks.items()}
report.update(training_positive_patches=len(pos),training_negative_patches=len(neg),limitations='Only vehicle patch classification; one development photo withheld from training; no on-device gameplay validation.')
(root/'tools/vehicle-validation.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report))
