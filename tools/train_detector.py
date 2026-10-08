"""Fine-tune pretrained TorchVision SSDlite on gameplay hazards; export ONNX.
Requires torch==2.5.1, torchvision==0.20.1, onnx==1.17.0, Pillow, NumPy.
CPU training, no cloud jobs. python train_detector.py DATA_DIR OUTPUT_DIR [EPOCHS]
Validation images are isolated. Report precision and recall at IoU>=0.5 and score>=0.5.
A trained model is not automatically approved for autopilot use.
"""
import sys,json,time,random,math
from pathlib import Path
import numpy as np
from PIL import Image,ImageEnhance
import torch,torchvision
from torchvision.models.detection import ssdlite320_mobilenet_v3_large,SSDLite320_MobileNet_V3_Large_Weights
from torchvision.models.detection.ssdlite import SSDLiteClassificationHead
from torchvision.models.detection._utils import retrieve_out_channels
from functools import partial
from torch import nn
random.seed(71);np.random.seed(71);torch.manual_seed(71);torch.set_num_threads(4)
data,out=map(Path,sys.argv[1:3]);epochs=int(sys.argv[3]) if len(sys.argv)>3 else 55;out.mkdir(parents=True,exist_ok=True)
meta=json.loads((data/'annotations.json').read_text());classes=meta['classes']
model=ssdlite320_mobilenet_v3_large(weights=SSDLite320_MobileNet_V3_Large_Weights.DEFAULT)
channels=retrieve_out_channels(model.backbone,(320,320))
model.head.classification_head=SSDLiteClassificationHead(channels,model.anchor_generator.num_anchors_per_location(),len(classes),partial(nn.BatchNorm2d,eps=.001,momentum=.03))
model.score_thresh=.15;model.detections_per_img=30
for parameter in model.backbone.parameters():parameter.requires_grad=False
for parameter in model.backbone.features[-1].parameters():parameter.requires_grad=True
optimizer=torch.optim.AdamW([{'params':model.head.parameters(),'lr':.001},{'params':[p for p in model.backbone.parameters() if p.requires_grad],'lr':.00002}],weight_decay=.0001)
def load(record,aug=False):
 im=Image.open(data/record['file']).convert('RGB');boxes=torch.tensor([o['box'] for o in record['objects']],dtype=torch.float32).reshape(-1,4);labels=torch.tensor([o['label'] for o in record['objects']],dtype=torch.int64)
 if aug:
  im=ImageEnhance.Brightness(im).enhance(random.uniform(.75,1.25));im=ImageEnhance.Contrast(im).enhance(random.uniform(.8,1.2))
  # Change hue to prevent a red-car-only classifier; affine transform all boxes.
  hsv=np.array(im.convert('HSV'));hsv[:,:,0]=(hsv[:,:,0].astype('int16')+random.randint(-60,60))%256;im=Image.fromarray(hsv.astype('uint8'),'HSV').convert('RGB')
  scale=random.uniform(.7,1.2);dx=random.uniform(-28,28)+(320-320*scale)/2;dy=random.uniform(-28,28)+(320-320*scale)/2
  im=im.transform((320,320),Image.Transform.AFFINE,(1/scale,0,-dx/scale,0,1/scale,-dy/scale),Image.Resampling.BILINEAR)
  if len(boxes):
   boxes[:,[0,2]]=boxes[:,[0,2]]*scale+dx;boxes[:,[1,3]]=boxes[:,[1,3]]*scale+dy;boxes.clamp_(0,320);keep=(boxes[:,2]-boxes[:,0]>3)&(boxes[:,3]-boxes[:,1]>3);boxes=boxes[keep];labels=labels[keep]
  if random.random()<.5:
   im=im.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
   if len(boxes):boxes[:,[0,2]]=320-boxes[:,[2,0]]
 return torchvision.transforms.functional.to_tensor(im),{'boxes':boxes,'labels':labels}
train=[r for r in meta['images'] if r['split']=='train'];val=[r for r in meta['images'] if r['split']=='validation'];start=time.monotonic()
for epoch in range(epochs):
 model.train();model.backbone.eval();random.shuffle(train);loss_total=0;steps=0
 for i in range(0,len(train),4):
  batch=[load(r,True) for r in train[i:i+4]]
  if len(batch)<2:batch.append(load(train[0],True))
  losses=model([x[0] for x in batch],[x[1] for x in batch]);loss=sum(losses.values())
  if not torch.isfinite(loss):raise RuntimeError('Non-finite loss')
  optimizer.zero_grad();loss.backward();torch.nn.utils.clip_grad_norm_(model.parameters(),5);optimizer.step();loss_total+=loss.item();steps+=1
 if epoch==20:
  for g in optimizer.param_groups:g['lr']*=.25
 if epoch%5==0 or epoch==epochs-1:print(json.dumps({'epoch':epoch+1,'loss':round(loss_total/steps,4),'seconds':round(time.monotonic()-start)}),flush=True)
model.eval();report={'classes':classes,'train_images':len(train),'validation_images':len(val),'score_threshold':.5,'iou_threshold':.5,'per_class':{},'images':[]}
counts={k:[0,0,0] for k in classes[1:]}
with torch.no_grad():
 for record in val:
  image,target=load(record);prediction=model([image])[0];keep=prediction['scores']>=.5;pred={k:v[keep] for k,v in prediction.items()};used=set();hits=[]
  for box,label,score in zip(pred['boxes'],pred['labels'],pred['scores']):
   k=classes[int(label)];indices=[i for i,l in enumerate(target['labels']) if int(l)==int(label) and i not in used]
   best=-1;overlap=0
   for i in indices:
    iou=float(torchvision.ops.box_iou(box[None],target['boxes'][i:i+1])[0,0])
    if iou>overlap:best=i;overlap=iou
   matched=overlap>=.5
   if matched:used.add(best);counts[k][0]+=1
   else:counts[k][1]+=1
   hits.append({'class':k,'score':round(float(score),3),'box':[round(float(x),1) for x in box],'matched':matched})
  for i,label in enumerate(target['labels']):
   if i not in used:counts[classes[int(label)]][2]+=1
  report['images'].append({'file':record['file'],'detections':hits})
for k,(tp,fp,fn) in counts.items():report['per_class'][k]={'tp':tp,'fp':fp,'fn':fn,'precision':round(tp/max(1,tp+fp),3),'recall':round(tp/max(1,tp+fn),3)}
report['limitations']='Small single-video dataset and screenshots; no measured gameplay survival or device latency; unsupported classes have no validation positives.'
(out/'validation.json').write_text(json.dumps(report,indent=2)+'\n');print('VALIDATION',json.dumps(report['per_class']),flush=True)
torch.save(model.state_dict(),out/'detector.pt')
class Export(nn.Module):
 def __init__(self,m):super().__init__();self.model=m
 def forward(self,x):
  p=self.model([x[0]])[0];return p['boxes'],p['labels'],p['scores']
with torch.no_grad():torch.onnx.export(Export(model),torch.zeros(1,3,320,320),str(out/'detector.onnx'),input_names=['image'],output_names=['boxes','labels','scores'],opset_version=17)
print('EXPORT',str(out/'detector.onnx'),flush=True)
