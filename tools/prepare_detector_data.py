"""Prepare complete gameplay images and manually annotated hazard boxes.
Use: python tools/prepare_detector_data.py UPLOAD_DIR FRAMES_DIR OUTPUT_DIR
No image-template matching or pseudo-labels are used for detector training.
Classes: solid obstacle, walkable ramp, low jump hurdle, duck bar, portal, barrel.
Annotations are approximate; the small dataset is explicitly experimental.
"""
from pathlib import Path
from PIL import Image
import json,sys,zipfile
u,f,out=map(Path,sys.argv[1:]);out.mkdir(parents=True,exist_ok=True)
classes=['BACKGROUND','BLOCK','RAMP','JUMP','SLIDE','PORTAL','BARREL']
# x0,y0,x1,y1 in original pixels. Every listed image contains full gameplay.
a={
's01.jpg':[('RAMP',52,168,190,422),('BLOCK',199,126,260,184)],
's02.jpg':[],
's03.jpg':[('BLOCK',192,128,227,151),('BLOCK',196,113,216,132),('BLOCK',131,122,154,151),('JUMP',155,130,194,143)],
's04.jpg':[],
's05.jpg':[('BLOCK',93,274,187,359),('BLOCK',183,175,237,251),('BLOCK',298,202,360,409),('BLOCK',133,204,177,242)],
's06.jpg':[('BLOCK',0,323,64,398),('PORTAL',73,104,149,275)],
's07.jpg':[],
's08.jpg':[('BLOCK',182,203,247,358)],
's09.jpg':[],
's10.jpg':[('BLOCK',195,203,245,356)],
's11.jpg':[('BARREL',80,601,238,708),('BLOCK',95,123,137,237)],
's12.jpg':[('BARREL',64,249,96,294)],
's13.jpg':[('BARREL',102,242,158,294)],
's14.jpg':[('BARREL',44,249,88,306),('BARREL',209,218,252,266)],
's15.jpg':[('BARREL',0,304,64,374)],
's16.jpg':[],
's17.jpg':[('BLOCK',18,329,144,458),('BLOCK',163,176,204,223),('JUMP',182,288,260,372),('JUMP',177,215,211,250),('PORTAL',94,136,134,218)],
's18.jpg':[('BLOCK',20,193,89,298),('BLOCK',107,177,131,220),('BLOCK',129,172,163,228),('BLOCK',168,177,207,241),('JUMP',178,239,239,324)],
's19.jpg':[('SLIDE',54,257,304,313),('SLIDE',116,176,151,242),('SLIDE',193,174,235,246),('BLOCK',156,175,194,240)],
's20.jpg':[],
'01-1000013088.png':[('BLOCK',25,350,266,577),('BLOCK',335,350,416,451)],
'02-1000013087.png':[('BLOCK',177,350,330,602),('RAMP',333,350,600,913),('BLOCK',20,390,130,738)],
'03-1000013085.png':[],
'04-1000013086.png':[],
'05-1000013084.png':[('BLOCK',275,640,437,882)],
'01-1000013094.png':[('SLIDE',103,899,605,1270)],
'01-1000013098.png':[('RAMP',0,362,426,1250),('BLOCK',435,520,720,1250)],
'02-1000013097.png':[('BLOCK',327,811,720,1180),('RAMP',238,340,340,513),('RAMP',160,344,240,486)],
'03-1000013099.png':[('RAMP',403,380,720,1260),('BLOCK',0,516,409,1120)],
'04-1000013096.png':[('BLOCK',170,578,577,1268),('BLOCK',0,426,175,1220),('SLIDE',298,375,417,576),('SLIDE',428,375,546,577),('SLIDE',174,375,284,577)]
}
# These images are excluded from training and are never used for threshold tuning.
validation={'s06.jpg','s13.jpg','02-1000013097.png'}
records=[]
for name,boxes in a.items():
 path=(f if name.startswith('s') else u)/name
 im=Image.open(path).convert('RGB');crop=min(im.height,1280 if im.width==720 else 640);im=im.crop((0,0,im.width,crop));sx=320/im.width;sy=320/im.height
 im=im.resize((320,320),Image.Resampling.BILINEAR);dest=name.rsplit('.',1)[0]+'.jpg';im.save(out/dest,quality=90)
 clipped=[]
 for kind,x0,y0,x1,y1 in boxes:
  x0=max(0,x0);y0=max(0,y0);x1=min(im.width/sx,x1);y1=min(crop,y1)
  if x1>x0 and y1>y0:clipped.append({'label':classes.index(kind),'box':[x0*sx,y0*sy,x1*sx,y1*sy]})
 records.append({'file':dest,'split':'validation' if name in validation else 'train','objects':clipped})
(out/'annotations.json').write_text(json.dumps({'classes':classes,'images':records},indent=2)+'\n')
print('Prepared',len(records),'images,',sum(len(r['objects']) for r in records),'boxes; held out:',sorted(validation))
