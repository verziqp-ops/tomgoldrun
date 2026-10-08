"""Track manually seeded boxes +/-1 s in 4-fps gameplay video.
Tracker proposals are training annotations, NOT a runtime detector. Visual QA is required.
Usage: expand_detector_data.py DATA_DIR ORIGINAL_KEY_FRAMES FOUR_FPS_FRAMES
"""
import json,sys
from pathlib import Path
import cv2,numpy as np
from PIL import Image
out,keys,sequence=map(Path,sys.argv[1:]);meta=json.loads((out/'annotations.json').read_text());files=sorted(sequence.glob('*.jpg'));frames=[cv2.imread(str(p)) for p in files];small=np.array([cv2.resize(im,(36,78)).astype('float32').ravel() for im in frames])
matched={}
for r in meta['images']:
 if not r['file'].startswith('s'):continue
 reference=cv2.imread(str(keys/r['file']));v=cv2.resize(reference,(36,78)).astype('float32').ravel();matched[r['file']]=int(np.argmin(np.mean((small-v)**2,axis=1)))
blocked={i for r in meta['images'] if r['split']=='validation' and r['file'] in matched for i in range(max(0,matched[r['file']]-8),min(len(files),matched[r['file']]+9))}
extra=[]
for r in list(meta['images']):
 if r['split']!='train' or r['file'] not in matched:continue
 start=matched[r['file']]
 for direction in [-1,1]:
  trackers=[]
  for o in r['objects']:
   x0,y0,x1,y1=o['box'];box=(round(x0*360/320),round(y0*640/320),round((x1-x0)*360/320),round((y1-y0)*640/320));t=cv2.TrackerCSRT_create();t.init(frames[start],box);trackers.append((o['label'],t))
  for distance in range(1,5):
   ix=start+direction*distance
   if ix<0 or ix>=len(files) or ix in blocked:break
   objects=[]
   for label,t in trackers:
    ok,(x,y,w,h)=t.update(frames[ix]);x0=max(0,x);y0=max(0,y);x1=min(360,x+w);y1=min(640,y+h)
    if ok and x1-x0>5 and y1-y0>5:objects.append({'label':label,'box':[x0*320/360,y0*320/640,x1*320/360,y1*320/640]})
   # Do not turn a failed tracking frame into a background-only negative.
   if r['objects'] and len(objects)!=len(r['objects']):continue
   name=f"track_{r['file'][:-4]}_{ix:04d}.jpg";im=Image.fromarray(cv2.cvtColor(frames[ix][:640],cv2.COLOR_BGR2RGB)).resize((320,320),Image.Resampling.BILINEAR);im.save(out/name,quality=90);extra.append({'file':name,'split':'train','objects':objects,'tracked':True})
meta['images']+=extra;meta['annotation_note']='Manual keyframes plus +/-1s CSRT training proposals, reviewed with a contact sheet; not engine hitboxes.';(out/'annotations.json').write_text(json.dumps(meta,indent=2)+'\n')
# Preview tracker proposals for manual quality review, one per anchor and direction.
show=[]
for r in extra:
 if not any(r['file'].startswith(f'track_{s}_') for s in ['s01','s05','s08','s17','s18','s19']):continue
 if any(x['file'].split('_')[1]==r['file'].split('_')[1] for x in show):continue
 show.append(r)
canvas=Image.new('RGB',(320*3,320*2))
for n,r in enumerate(show[:6]):
 im=cv2.imread(str(out/r['file']))
 for o in r['objects']:
  x0,y0,x1,y1=map(round,o['box']);cv2.rectangle(im,(x0,y0),(x1,y1),(0,255,0),2);cv2.putText(im,meta['classes'][o['label']],(x0,max(10,y0)),cv2.FONT_HERSHEY_SIMPLEX,.35,(0,255,0),1)
 canvas.paste(Image.fromarray(cv2.cvtColor(im,cv2.COLOR_BGR2RGB)),((n%3)*320,(n//3)*320))
canvas.save('/tmp/tom-tracked-qa.jpg');print('Added',len(extra),'tracked frames; total',len(meta['images']),flush=True)
