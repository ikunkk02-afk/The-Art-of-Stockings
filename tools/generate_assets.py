"""Original nylon materials for the Blockbench-authored v2 model (Pillow only)."""
from pathlib import Path
from PIL import Image, ImageDraw
import math
root=Path(__file__).resolve().parents[1]
r=root/'src/main/resources/assets/theartofstockings/textures'
(r/'entity').mkdir(parents=True,exist_ok=True)
base=Image.new('RGBA',(256,256));sheen=Image.new('RGBA',(256,256))
for face in range(4):
 for v in range(200):
  for i in range(48):
   x=face*48+i
   across=(i+.5)/48
   # Fine diagonal knit, no coarse checkerboard or opaque blocky lace.
   thread=math.sin((i*1.37+v)*math.pi)*2.3+math.cos(v*math.pi)*1.4
   edge=abs(across-.5)*2
   alpha=95+edge**3*19+thread
   shade=248
   if v<16: # dense doubled welt, with two satin borders
    alpha=214+thread*1.5;shade=239
    if v in (0,1,13,14,15):alpha=240;shade=222
    if 4<=v<=10:alpha=201+thread
   elif v<20:alpha=238;shade=226
   elif v<32:alpha=0
   elif v>=184: # reinforced toes and heel, not an opaque shoe
    alpha=153+thread;shade=238
    if face==2:alpha+=16
   elif v>164 and face in (1,3):alpha+=12*(v-164)/20
   base.putpixel((x,v),(shade,shade,shade,max(0,min(255,round(alpha)))))
   # Untinted nylon reflection; restrained, non-emissive, narrow front highlight.
   highlight=math.exp(-((across-.31)/.17)**2)
   strength=(18 if face==0 else 8 if face in (1,3) else 3)*highlight
   if v<20:strength*=.45
   if 20<=v<32:strength=0
   sheen.putpixel((x,v),(250,246,242,round(strength)))
# End-cap area, kept off the large face strips.
for y in range(208,256):
 for x in range(48):base.putpixel((x,y),(240,240,240,130))
base.save(r/'entity/stockings.png');sheen.save(r/'entity/stockings_sheen.png')
# Inventory icon: soft, shaded fabric with compact folded welts and a paired silhouette.
item=Image.new('RGBA',(64,64));d=ImageDraw.Draw(item)
for dx,dy in [(8,5),(32,10)]:
 shape=[(dx+2,dy),(dx+17,dy+1),(dx+15,dy+30),(dx+12,dy+39),(dx+6,dy+47),(dx-1,dy+47),(dx-4,dy+43),(dx+3,dy+33)]
 mask=Image.new('L',(64,64));ImageDraw.Draw(mask).polygon(shape,fill=255)
 for y in range(64):
  for x in range(64):
   if mask.getpixel((x,y)):
    value=round(172+65*math.exp(-((x-dx-8)/5)**2)+3*math.sin(y*2.2))
    item.putpixel((x,y),(value,value,value,255))
 d.polygon([(dx+2,dy),(dx+17,dy+1),(dx+17,dy+5),(dx+2,dy+4)],fill=(226,226,226,255))
 d.line([(dx+3,dy+1),(dx+16,dy+2)],fill=(254,254,254,255),width=1)
(r/'item').mkdir(exist_ok=True);item.save(r/'item/stockings.png')
print('Generated 256px fine-knit nylon, separate sheen and 64px icon.')
