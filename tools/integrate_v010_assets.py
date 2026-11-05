"""Integrate generated inventory/HUD art and extend the project's own cloth model."""
from pathlib import Path
from PIL import Image
import json
import shutil

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/theartofstockings'
ART = ROOT / 'art/generated/0.10.0'
ART.mkdir(parents=True, exist_ok=True)
GENERATED = Path(r'C:\Users\shouy\.codex\generated_images\01a1ab7a-a839-7ce3-891d-ad380deb6369')
icons = [('exec-599e20dd-1c07-4c0b-8642-004c358020b3.png', 'bodystocking', 32, 'item'),
         ('exec-1ff48115-dbef-496d-a220-23f43de4b63e.png', 'excitement', 18, 'mob_effect')]
for source, name, size, kind in icons:
    shutil.copy2(GENERATED / source, ART / f'{name}-original.png')
    image = Image.open(ART / f'{name}-original.png').convert('RGBA')
    image = image.crop(image.getchannel('A').getbbox())
    image.thumbnail((size-2, size-2), Image.Resampling.NEAREST)
    output = Image.new('RGBA', (size, size))
    output.alpha_composite(image, ((size-image.width)//2, (size-image.height)//2))
    output.save(ASSETS / f'textures/{kind}/{name}.png')

def write(path, content):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(content, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')

model = json.loads((ASSETS / 'geo/pantyhose.geo.json').read_text(encoding='utf-8'))
geometry = model['minecraft:geometry'][0]
geometry['description']['identifier'] = 'geometry.bodystocking'
for bone in geometry['bones']:
    if bone['name'] == 'tights_hips':
        bone['cubes'] = bone['cubes'][:1]
        bone['cubes'][0]['size'][1] = 12
write(ASSETS / 'geo/bodystocking.geo.json', model)
for suffix in ['', '_sheen']:
    shutil.copy2(ASSETS / f'textures/entity/pantyhose{suffix}.png', ASSETS / f'textures/entity/bodystocking{suffix}.png')
write(ASSETS / 'models/item/bodystocking.json', {'parent':'minecraft:item/generated','textures':{'layer0':'theartofstockings:item/bodystocking'}})
for relative in ['minecraft/tags/item/dyeable.json', 'curios/tags/item/stockings.json', 'accessories/tags/item/stockings.json']:
    path = ROOT / 'src/main/resources/data' / relative
    data = json.loads(path.read_text(encoding='utf-8'))
    if 'theartofstockings:bodystocking' not in data['values']:
        data['values'].append('theartofstockings:bodystocking')
    write(path, data)

common = {
 'gui.theartofstockings.palette.depth': ('颜色深度：%s%%', 'Color depth: %s%%'),
 'gui.theartofstockings.palette.depth_hint': ('0% 不额外加深颜色；提高深度会加深颜色、增加遮盖度并减弱高光。', '0% adds no extra darkening; increase for darker color, more coverage and softer sheen.'),
 'gui.theartofstockings.palette.opacity_hint': ('0–75% 保留透肤；75–100% 逐渐增强遮盖。100% 面料不透肤，仍保留网孔和全透明图案。', '0–75% stays sheer; 75–100% increases coverage. At 100%, fabric is opaque while mesh holes and transparent artwork remain open.'),
 'tooltip.theartofstockings.depth': ('颜色深度：%s%%', 'Color depth: %s%%'),
 'tooltip.theartofstockings.wear_parts': ('潜行右键：选择双腿、双手或头部穿戴', 'Crouch-use: choose legs, hands or head'),
 'tooltip.theartofstockings.bodystocking': ('覆盖双腿、躯干、双臂和头部；仍可与盔甲共存', 'Covers legs, torso, arms and head; works with armor'),
 'item.theartofstockings.bodystocking': ('全身丝袜', 'Bodystocking'),
 'gui.theartofstockings.wear.equip': ('选择丝袜穿戴部位', 'Choose clothing location'),
 'gui.theartofstockings.wear.remove': ('选择要脱下的丝袜', 'Choose stockings to remove'),
 'gui.theartofstockings.wear.legs': ('双腿', 'Legs'),
 'gui.theartofstockings.wear.hands': ('双手', 'Hands'),
 'gui.theartofstockings.wear.head': ('头部', 'Head'),
 'gui.theartofstockings.wear.full_body': ('全身', 'Full body'),
 'effect.theartofstockings.excitement': ('兴奋', 'Excitement'),
 'tooltip.theartofstockings.worn_before': ('曾被玩家穿过 · 放在盔甲架上可闻袜', 'Previously worn by a player; sniff it on an armor stand'),
 'message.theartofstockings.excitement': ('丝袜的气味让你精神振奋！', 'The stockings scent leaves you feeling energized!'),
 'message.theartofstockings.already_wearing': ('这个部位已经穿着丝袜了。', 'This clothing location is already occupied.'),
}
for i, locale in enumerate(['zh_cn', 'en_us']):
    path = ASSETS / f'lang/{locale}.json'
    data = json.loads(path.read_text(encoding='utf-8'))
    data.update({key: value[i] for key,value in common.items()})
    write(path,data)

# A nearest-neighbor sheet for inspecting the actual tiny sprites.
preview=Image.new('RGBA',(400,210),'#34323C')
for x,(_,name,size,kind) in zip([25,235],icons):
    im=Image.open(ASSETS / f'textures/{kind}/{name}.png')
    im=im.resize((size*5,size*5),Image.Resampling.NEAREST)
    preview.alpha_composite(im,(x,20))
preview.save(ART / 'runtime-icons.png')
write(ART / 'prompts.json', {
 'tool':'built-in image_gen',
 'bodystocking':'Create a single Minecraft inventory item sprite: a folded full-body nylon bodystocking garment with long narrow legs, torso, long sleeves and a small hood, shown alone as a recognizable empty clothing item, no person or body inside. Vanilla Minecraft 32x32 pixel-art aesthetic, chunky crisp square pixels, grayscale white and gray material intended for runtime dye tinting, modest simple all-covering garment silhouette, transparent background, no text, no border, no shadow outside the sprite. Place the sprite centered with wide clear transparent margins, balanced square canvas. The result will be downsampled to 32x32; prioritize bold readable silhouette.',
 'excitement':'Create one small Minecraft beneficial status-effect HUD icon for Excitement / energetic mood. A vivid warm pink five-point star with a pale peach center and two tiny magenta energy sparks. Bold crisp blocky pixel art, vanilla Minecraft effect icon style, extremely simple silhouette readable at 18x18 pixels. Transparent background, no lettering, no frame, no gradient background, no people, no garments, no hearts. Center the star on a square canvas with generous transparent margin. Use only about 6 warm pink/cream colors and hard pixel edges; this will be downsampled to 18x18.'})
print('Integrated 0.10.0 assets and translations.')
