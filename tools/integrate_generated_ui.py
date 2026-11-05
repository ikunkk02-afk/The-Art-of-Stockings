"""Mechanically resize the five built-in imagegen masters for Minecraft UI slots."""
from pathlib import Path
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
MASTERS=ROOT/'art/generated/0.9.0'
ASSETS=ROOT/'src/main/resources/assets/theartofstockings'

def icon(name,destination,size,padding=1):
    source=Image.open(MASTERS/f'{name}.png').convert('RGBA')
    box=source.getchannel('A').point(lambda value:255 if value>=96 else 0).getbbox()
    if not box: raise ValueError(f'{name} has no visible pixels')
    cropped=source.crop(box)
    available=(size[0]-padding*2,size[1]-padding*2)
    factor=min(available[0]/cropped.width,available[1]/cropped.height)
    final=cropped.resize((max(1,round(cropped.width*factor)),max(1,round(cropped.height*factor))),Image.Resampling.NEAREST)
    canvas=Image.new('RGBA',size)
    canvas.alpha_composite(final,((size[0]-final.width)//2,(size[1]-final.height)//2))
    destination.parent.mkdir(parents=True,exist_ok=True);canvas.save(destination)

icon('stockings_slot',ASSETS/'textures/slot/stockings.png',(16,16))
icon('charm_effect',ASSETS/'textures/mob_effect/charm.png',(18,18))
icon('foot_odor_effect',ASSETS/'textures/mob_effect/foot_odor.png',(18,18))
progress=Image.open(MASTERS/'dressing_progress.png').convert('RGBA')
progress=progress.crop(progress.getchannel('A').point(lambda value:255 if value>=96 else 0).getbbox()).resize((112,14),Image.Resampling.NEAREST)
(ASSETS/'textures/gui').mkdir(parents=True,exist_ok=True)
progress.save(ASSETS/'textures/gui/dressing_progress.png')
logo=Image.open(MASTERS/'mod_icon.png').convert('RGBA').resize((512,512),Image.Resampling.LANCZOS)
logo.save(ROOT/'src/main/resources/theartofstockings-icon.png')

# A large nearest-neighbor preview makes the actual in-game pixels easy to inspect.
preview=Image.new('RGBA',(640,256),'#302936')
logo.resize((256,256),Image.Resampling.LANCZOS).save(MASTERS/'mod_icon_256.png')
preview.alpha_composite(logo.resize((256,256),Image.Resampling.LANCZOS),(0,0))
for name,path,x in [
    ('slot',ASSETS/'textures/slot/stockings.png',272),
    ('charm',ASSETS/'textures/mob_effect/charm.png',392),
    ('odor',ASSETS/'textures/mob_effect/foot_odor.png',512),
]:
    preview.alpha_composite(Image.open(path).convert('RGBA').resize((108,108),Image.Resampling.NEAREST),(x,16))
preview.alpha_composite(progress.resize((336,42),Image.Resampling.NEAREST),(282,176))
preview.save(MASTERS/'runtime_ui_preview.png')
print('Integrated generated mod icon, accessory slot, effect icons and dressing progress frame.')
