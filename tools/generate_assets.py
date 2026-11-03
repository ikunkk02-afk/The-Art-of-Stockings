"""Original nylon materials for the Blockbench-authored v2 model (Pillow only)."""
from pathlib import Path
from PIL import Image
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
print('Generated 256px fine-knit nylon and separate sheen.')
