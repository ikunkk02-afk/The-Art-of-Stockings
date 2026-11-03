"""Original dye-neutral cloth atlases and item art for the Blockbench variants."""
from pathlib import Path
from PIL import Image
import math, json

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/theartofstockings'
TEX=ASSETS/'textures'
ART=ROOT/'art/materials'
ART.mkdir(exist_ok=True)
base=Image.open(TEX/'entity/stockings.png').convert('RGBA')
sheen=Image.open(TEX/'entity/stockings_sheen.png').convert('RGBA')
for name in ['gradient_stockings','pantyhose','fishnet_stockings','glow_stockings']:
    cloth=base.copy(); shine=sheen.copy()
    if name=='fishnet_stockings':
        for y in range(32,184):
            for x in range(192):
                # Wrap-safe diagonal strands with genuinely empty diamond holes.
                a=(x+y)%12; b=(x-y)%12
                distance=min(a,12-a,b,12-b)
                alpha=238 if distance==0 else 105 if distance==1 else 0
                cloth.putpixel((x,y),(246,246,246,alpha))
                p=shine.getpixel((x,y));shine.putpixel((x,y),(*p[:3],round(p[3]*alpha/255)))
    if name=='glow_stockings':
        for y in range(200):
            for x in range(192):
                p=cloth.getpixel((x,y))
                if p[3]:cloth.putpixel((x,y),(255,255,255,max(165,p[3])))
    if name=='pantyhose':
        # Hip atlas in unused right-hand strip; separate dense waistband.
        for y in range(184):
            for x in range(192,256):
                alpha=220 if y>=176 else 115+round(3*math.sin((x+y)*math.pi/2))
                cloth.putpixel((x,y),(245,245,245,alpha))
                shine.putpixel((x,y),(250,246,242,6 if y<176 else 2))
    cloth.save(TEX/f'entity/{name}.png');shine.save(TEX/f'entity/{name}_sheen.png')
    preview=cloth.copy()
    for y in range(256):
        if name=='gradient_stockings':
            t=max(0,min(1,(y-32)/168)) if y>=32 else 0
            c=tuple(round(a*(1-t)+b*t) for a,b in zip((225,112,181),(90,166,244)))
        else:c={'pantyhose':(107,92,137),'fishnet_stockings':(70,63,79),'glow_stockings':(88,230,221)}[name]
        for x in range(256):
            p=cloth.getpixel((x,y));preview.putpixel((x,y),(*(round(p[k]*c[k]/255) for k in range(3)),p[3]))
    preview.save(ART/f'{name}_preview.png')

for name in ['gradient_stockings','pantyhose','fishnet_stockings','glow_stockings']:
    model={'parent':'minecraft:item/generated','textures':{'layer0':f'theartofstockings:item/{name}'}}
    if name=='gradient_stockings':
        model['textures']['layer1']='theartofstockings:item/gradient_stockings_bottom'
    if name=='glow_stockings':
        model['textures']['layer1']='theartofstockings:item/glow_stockings_sparkle'
    (ASSETS/f'models/item/{name}.json').write_text(json.dumps(model,indent=2)+'\n')

(ASSETS/'models/item/palette.json').write_text(json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':'theartofstockings:item/palette'}},indent=2)+'\n')
data=ROOT/'src/main/resources/data'
(data/'minecraft/tags/item/dyeable.json').write_text(json.dumps({'replace':False,'values':[f'theartofstockings:{s}' for s in ['stockings','pantyhose','fishnet_stockings','glow_stockings']]},indent=2)+'\n')
(data/'theartofstockings/recipe/stockings_style.json').write_text(json.dumps({'type':'theartofstockings:stockings_style','category':'misc'},indent=2)+'\n')
(data/'theartofstockings/recipe/palette.json').write_text(json.dumps({'type':'minecraft:crafting_shapeless','category':'misc','ingredients':[{'item':f'minecraft:{i}'} for i in ['paper','stick','red_dye','green_dye','blue_dye']],'result':{'id':'theartofstockings:palette','count':1}},indent=2)+'\n')
print('Generated four neutral cloth atlases, Blockbench preview textures, item models and recipes.')
