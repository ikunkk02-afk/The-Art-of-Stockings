"""Original 0.7 tailoring assets, derived from this project's own cloth and UVs."""
from pathlib import Path
from PIL import Image, ImageDraw
import copy
import json

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/theartofstockings'
DATA = ROOT / 'src/main/resources/data'
NAMES = ['striped_stockings', 'cat_paw_stockings', 'matte_stockings', 'fox_stockings', 'custom_stockings']
base = Image.open(ASSETS / 'textures/entity/stockings.png').convert('RGBA')
sheen = Image.open(ASSETS / 'textures/entity/stockings_sheen.png').convert('RGBA')
geometry = json.loads((ASSETS / 'geo/stockings.geo.json').read_text(encoding='utf-8'))

for name in NAMES:
    model = copy.deepcopy(geometry)
    model['minecraft:geometry'][0]['description']['identifier'] = 'geometry.' + name
    if name == 'striped_stockings':
        # Each stripe is a separate face interval, so vertex colors never interpolate across a stripe.
        for bone in model['minecraft:geometry'][0]['bones']:
            if not bone['name'].endswith('_pull'):
                continue
            parts = []
            for cube in bone.get('cubes', []):
                low = cube['origin'][1]
                high = low + cube['size'][1]
                cuts = [low] + [i * 1.3 for i in range(1, 9) if low < i * 1.3 < high] + [high]
                for a, b in zip(cuts, cuts[1:]):
                    part = copy.deepcopy(cube)
                    part['origin'][1] = a
                    part['size'][1] = b - a
                    for face in ['north', 'east', 'south', 'west']:
                        uv = part['uv'][face]
                        original = cube['uv'][face]
                        uv['uv'][1] = original['uv'][1] + original['uv_size'][1] * (high - b) / (high - low)
                        uv['uv_size'][1] = original['uv_size'][1] * (b - a) / (high - low)
                    parts.append(part)
            bone['cubes'] = parts
    (ASSETS / f'geo/{name}.geo.json').write_text(json.dumps(model, indent=2) + '\n', encoding='utf-8')
    cloth = base.copy()
    if name in ['striped_stockings', 'fox_stockings']:
        for y in range(256):
            for x in range(256):
                r, g, b, alpha = cloth.getpixel((x, y))
                if alpha:
                    cloth.putpixel((x, y), (r, g, b, 232 if y < 20 else max(205, alpha)))
    if name in ['matte_stockings', 'custom_stockings']:
        for y in range(256):
            for x in range(256):
                r, g, b, alpha = cloth.getpixel((x, y))
                if name == 'matte_stockings' and alpha:
                    # A dense, low-contrast knitted fabric rather than the classic sheer base.
                    shade = 242 + (3 if (x + y) % 4 < 2 else 0)
                    cloth.putpixel((x, y), (shade, shade, shade, 232 if y < 20 else 216))
                else:
                    cloth.putpixel((x, y), (255, 255, 255, alpha))
    cloth.save(ASSETS / f'textures/entity/{name}.png')
    shine = Image.new('RGBA', (256, 256)) if name in ['matte_stockings', 'custom_stockings'] else sheen.copy()
    if name in ['striped_stockings', 'fox_stockings']:
        shine.putalpha(shine.getchannel('A').point(lambda value: round(value * .4)))
    shine.save(ASSETS / f'textures/entity/{name}_sheen.png')

# LabPBR: zero smoothness, low dielectric reflectance, porous cloth, no emission.
Image.new('RGBA', (256, 256), (0, 5, 38, 255)).save(ASSETS / 'textures/entity/matte_stockings_s.png')
Image.new('RGBA', (256, 256), (128, 128, 255, 255)).save(ASSETS / 'textures/entity/matte_stockings_n.png')

paw = Image.new('RGBA', (256, 256))
d = ImageDraw.Draw(paw)
# Sole UV is [48,256] with a negative 48x48 extent. All four toes are inside that island.
d.ellipse((13, 229, 35, 247), fill=(255, 255, 255, 255))
for box in [(5, 218, 15, 230), (15, 212, 25, 225), (27, 212, 37, 225), (37, 219, 47, 231)]:
    d.ellipse(box, fill=(255, 255, 255, 255))
paw.save(ASSETS / 'textures/entity/cat_paw_overlay.png')

sprite = Image.open(ASSETS / 'textures/item/stockings.png').convert('RGBA')
for name in NAMES:
    primary = sprite.copy()
    detail = Image.new('RGBA', sprite.size)
    for y in range(32):
        for x in range(32):
            pixel = sprite.getpixel((x, y))
            secondary = (name == 'striped_stockings' and y // 3 % 2 == 0) or (name == 'fox_stockings' and y >= 20)
            if secondary:
                detail.putpixel((x, y), pixel)
                primary.putpixel((x, y), (0, 0, 0, 0))
            elif name == 'matte_stockings' and pixel[3]:
                primary.putpixel((x, y), (180, 180, 180, pixel[3]))
    if name == 'cat_paw_stockings':
        draw = ImageDraw.Draw(detail)
        for dx, dy in [(0, 0), (10, 3)]:
            draw.ellipse((6 + dx, 21 + dy, 10 + dx, 24 + dy), fill='white')
            for px, py in [(5, 19), (7, 18), (9, 18), (11, 19)]:
                draw.point((px + dx, py + dy), fill='white')
    if name == 'custom_stockings':
        # A small artist's mark identifies the editable item in an inventory.
        draw = ImageDraw.Draw(primary)
        for box, color in [((20, 4, 22, 6), '#EF95B7'), ((24, 8, 26, 10), '#81C8E6'), ((20, 12, 22, 14), '#F1CA76')]:
            draw.rectangle(box, fill=color)
    primary.save(ASSETS / f'textures/item/{name}.png')
    textures = {'layer0': f'theartofstockings:item/{name}'}
    if name in ['striped_stockings', 'cat_paw_stockings', 'fox_stockings']:
        detail.save(ASSETS / f'textures/item/{name}_detail.png')
        textures['layer1'] = f'theartofstockings:item/{name}_detail'
    (ASSETS / f'models/item/{name}.json').write_text(json.dumps({'parent': 'minecraft:item/generated', 'textures': textures}, indent=2) + '\n')

all_names = ['stockings', 'gradient_stockings', 'pantyhose', 'fishnet_stockings', 'glow_stockings'] + NAMES
def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

# Two-color items have a custom dye recipe; the others can keep vanilla dye mixing.
write_json(DATA / 'minecraft/tags/item/dyeable.json', {'replace': False, 'values': ['theartofstockings:' + s for s in ['stockings', 'pantyhose', 'fishnet_stockings', 'glow_stockings', 'matte_stockings', 'custom_stockings']]})
write_json(DATA / 'curios/tags/item/stockings.json', {'replace': False, 'values': ['theartofstockings:' + s for s in all_names]})
write_json(DATA / 'accessories/tags/item/stockings.json', {'replace': False, 'values': ['theartofstockings:' + s for s in all_names]})
no_accessories = [{'type': 'neoforge:not', 'value': {'type': 'neoforge:mod_loaded', 'modid': 'accessories'}}]
write_json(DATA / 'theartofstockings/curios/slots/stockings.json', {'size': 1, 'order': 180, 'icon': 'theartofstockings:slot/stockings', 'add_cosmetic': False, 'render_toggle': False, 'neoforge:conditions': no_accessories})
write_json(DATA / 'theartofstockings/curios/entities/stockings.json', {'entities': ['minecraft:player'], 'slots': ['stockings'], 'neoforge:conditions': no_accessories})
write_json(DATA / 'theartofstockings/accessories/slot/stockings.json', {'amount': 1, 'operation': 'SET', 'order': 180, 'icon': 'theartofstockings:slot/stockings', 'validators': ['accessories:tag']})
write_json(DATA / 'theartofstockings/accessories/entity/stockings.json', {'entities': ['minecraft:player'], 'slots': ['stockings']})
(ASSETS / 'textures/slot').mkdir(exist_ok=True)
icon = sprite.resize((16, 16), Image.Resampling.NEAREST)
icon.save(ASSETS / 'textures/slot/stockings.png')

languages = {
    'zh_cn': {
        'item.theartofstockings.striped_stockings': '条纹丝袜', 'item.theartofstockings.cat_paw_stockings': '猫肉垫丝袜',
        'item.theartofstockings.matte_stockings': '哑光丝袜', 'item.theartofstockings.fox_stockings': '狐狸丝袜',
        'item.theartofstockings.custom_stockings': '自定义丝袜', 'curios.identifier.stockings': '丝袜', 'accessories.slot.stockings': '丝袜',
        'tooltip.theartofstockings.two_colors': '调色板可分别设置底色与图案色', 'tooltip.theartofstockings.matte': '柔和哑光面料，无额外高光层',
        'tooltip.theartofstockings.custom': '调色板可导入 PNG，纹理随物品保存与联机同步', 'tooltip.theartofstockings.opacity': '材质不透明度：%s%%',
        'tooltip.theartofstockings.exclusive': '独立穿戴层 / 饰品栏 · 无护甲值 · 可与护腿共存',
        'gui.theartofstockings.palette.opacity': '不透明度：%s%%', 'gui.theartofstockings.palette.import': '导入 PNG',
        'gui.theartofstockings.palette.clear': '清除纹理', 'gui.theartofstockings.palette.template': '导出绘制模板',
        'gui.theartofstockings.palette.imported': 'PNG 已导入，可拖入替换', 'gui.theartofstockings.palette.import_error': '需 64/128/256 方形 PNG，≤10KB',
        'gui.theartofstockings.palette.template_error': '无法导出模板', 'gui.theartofstockings.palette.base': '底色', 'gui.theartofstockings.palette.detail': '图案色',
        'gui.theartofstockings.palette.apply': '应用', 'message.theartofstockings.palette_applied': '丝袜外观已保存',
    },
    'en_us': {
        'item.theartofstockings.striped_stockings': 'Striped Stockings', 'item.theartofstockings.cat_paw_stockings': 'Cat Paw Stockings',
        'item.theartofstockings.matte_stockings': 'Matte Stockings', 'item.theartofstockings.fox_stockings': 'Fox Stockings',
        'item.theartofstockings.custom_stockings': 'Custom Stockings', 'curios.identifier.stockings': 'Stockings', 'accessories.slot.stockings': 'Stockings',
        'tooltip.theartofstockings.two_colors': 'Edit fabric and pattern colors separately with a palette',
        'tooltip.theartofstockings.matte': 'Soft matte fabric without an additional sheen layer',
        'tooltip.theartofstockings.custom': 'Import a PNG with a palette; saved and synced with the item', 'tooltip.theartofstockings.opacity': 'Fabric opacity: %s%%',
        'tooltip.theartofstockings.exclusive': 'Clothing / accessory slot · No armor points · Works with leggings',
        'gui.theartofstockings.palette.opacity': 'Opacity: %s%%', 'gui.theartofstockings.palette.import': 'Import PNG',
        'gui.theartofstockings.palette.clear': 'Clear PNG', 'gui.theartofstockings.palette.template': 'Export painting template',
        'gui.theartofstockings.palette.imported': 'PNG imported; drop a file to replace', 'gui.theartofstockings.palette.import_error': '64/128/256 square PNG, max 10KB',
        'gui.theartofstockings.palette.template_error': 'Could not export template', 'gui.theartofstockings.palette.base': 'Fabric', 'gui.theartofstockings.palette.detail': 'Pattern',
        'gui.theartofstockings.palette.apply': 'Apply', 'message.theartofstockings.palette_applied': 'Stockings appearance saved',
    }
}
languages['zh_cn'].update({
    'effect.theartofstockings.charm': '魅惑',
    'tooltip.theartofstockings.charm': '穿着腿部/全身丝袜：魅惑周围生灵，村民交易优惠 20%',
    'hud.theartofstockings.equipping': '正在穿上 · %s%%',
    'hud.theartofstockings.removing': '正在脱下 · %s%%',
    'gui.theartofstockings.palette.paint': '游戏内绘画', 'gui.theartofstockings.palette.template_short': '导出模板',
    'gui.theartofstockings.palette.painted': '绘画已完成，点应用保存',
    'gui.theartofstockings.palette.imported': 'PNG 已导入并转换为 256×256',
    'gui.theartofstockings.palette.import_error': '无法导入，详见聊天提示',
    'gui.theartofstockings.palette.import_too_large': '图片太大：文件需小于 16 MiB，像素尺寸不超过 4096×4096。',
    'gui.theartofstockings.palette.import_not_png': '所选文件不是 PNG 图片，请在绘图软件中另存为 PNG。',
    'gui.theartofstockings.palette.import_not_square': '图片不是正方形；请保持导出模板的正方形画布。',
    'gui.theartofstockings.palette.import_dimensions': '图片尺寸超出范围：仅支持最大 4096×4096 的方形 PNG。',
    'gui.theartofstockings.palette.import_damaged': 'PNG 图片已损坏或无法读取，请重新导出 PNG。',
    'gui.theartofstockings.palette.import_unreadable': '无法读取文件，请检查文件位置和访问权限。',
    'gui.theartofstockings.palette.saving': '正在上传并保存图案…',
    'gui.theartofstockings.palette.save_failed': '保存失败：丝袜已变化或编辑已失效，请重新打开调色板。绘画可先在画板中导出 PNG。',
    'gui.theartofstockings.palette.save_timeout': '服务器尚未确认保存，请稍后重试或重新打开调色板。',
    'tooltip.theartofstockings.matte': '厚实哑光织物 · 无高光层 · 最大粗糙度材质',
    'tooltip.theartofstockings.custom': '调色板可在游戏内绘画或导入 PNG，图案随物品同步',
})
languages['en_us'].update({
    'effect.theartofstockings.charm': 'Charm',
    'tooltip.theartofstockings.charm': 'Wear stockings on your legs or a bodystocking: charm nearby creatures and get 20% off villager trades',
    'hud.theartofstockings.equipping': 'Putting on · %s%%',
    'hud.theartofstockings.removing': 'Taking off · %s%%',
    'gui.theartofstockings.palette.paint': 'Paint in game', 'gui.theartofstockings.palette.template_short': 'Template',
    'gui.theartofstockings.palette.painted': 'Drawing ready; Apply to save',
    'gui.theartofstockings.palette.imported': 'PNG imported and resized to 256x256',
    'gui.theartofstockings.palette.import_error': 'Import failed; see chat for details',
    'gui.theartofstockings.palette.import_too_large': 'Image too large: maximum 16 MiB and 4096x4096 pixels.',
    'gui.theartofstockings.palette.import_not_png': 'The selected file is not PNG. Export it as a PNG image.',
    'gui.theartofstockings.palette.import_not_square': 'The image must be square. Keep the square template canvas.',
    'gui.theartofstockings.palette.import_dimensions': 'Image dimensions exceed the 4096x4096 limit.',
    'gui.theartofstockings.palette.import_damaged': 'The PNG is damaged or unsupported. Export the image again.',
    'gui.theartofstockings.palette.import_unreadable': 'Cannot read the file. Check its location and permissions.',
    'gui.theartofstockings.palette.saving': 'Uploading and saving artwork...',
    'gui.theartofstockings.palette.save_failed': 'Save failed: the item or edit session changed. Reopen the palette. You can export the drawing first.',
    'gui.theartofstockings.palette.save_timeout': 'The server has not confirmed saving. Retry or reopen the palette.',
    'tooltip.theartofstockings.matte': 'Dense matte fabric; no sheen layer; full-roughness material',
    'tooltip.theartofstockings.custom': 'Paint in game or import a PNG with a palette; artwork syncs with the item',
})
paint_zh = {'title':'丝袜绘画板','hint':'左键绘画，右键取色；完成后在调色板点“应用”保存',
    'front':'正面','back':'背面','left':'左侧','right':'右侧','sole':'脚底','cuff':'袜口','all':'整张纹理',
    'brush':'画笔','eraser':'橡皮','fill':'填色','picker':'取色','color':'颜色', 'size':'笔刷大小：%s',
    'alpha':'画笔不透明度：%s%%','undo':'撤销','redo':'重做','clear':'清空此面','restore':'还原画布',
    'done':'完成绘画','export':'导出 PNG','exported':'图案已导出到配置文件夹',
    'load_failed':'原图无法读取，已打开空白画布','save_failed':'无法保存图案，请重试'}
paint_en = {'title':'Stockings Paint Studio','hint':'Left: paint; Right: pick color. Apply in the palette to save.',
    'front':'Front','back':'Back','left':'Left','right':'Right','sole':'Sole','cuff':'Cuff','all':'Full atlas',
    'brush':'Brush','eraser':'Eraser','fill':'Fill','picker':'Pick','color':'Color','size':'Brush size: %s',
    'alpha':'Brush opacity: %s%%','undo':'Undo','redo':'Redo','clear':'Clear face','restore':'Restore',
    'done':'Finish drawing','export':'Export PNG','exported':'Drawing exported to the config folder',
    'load_failed':'Image could not be read; opened a blank canvas','save_failed':'Could not save artwork; try again'}
for locale, words in [('zh_cn',paint_zh),('en_us',paint_en)]:
    languages[locale].update({'gui.theartofstockings.paint.'+key:value for key,value in words.items()})

for locale, additions in languages.items():
    path = ASSETS / f'lang/{locale}.json'
    content = json.loads(path.read_text(encoding='utf-8'))
    content.update(additions)
    write_json(path, content)

# Ship an immediately editable template and a separate UV guide, outside the runtime assets.
docs = ROOT / 'docs/custom-textures'
docs.mkdir(parents=True, exist_ok=True)
Image.open(ASSETS / 'textures/entity/custom_stockings.png').save(docs / 'custom_stockings_template.png')
guide = Image.new('RGBA', (256, 256), '#33333B')
draw = ImageDraw.Draw(guide)
for i, label in enumerate(['FRONT', 'SIDE R', 'BACK', 'SIDE L']):
    x = i * 48
    draw.rectangle((x, 32, x + 47, 199), fill=['#DD809D', '#E6B06E', '#77BCAF', '#8CA5D3'][i])
    draw.text((x + 2, 65), label, fill='black')
    draw.rectangle((x, 0, x + 47, 19), fill='#DDD8C9')
draw.rectangle((0, 208, 47, 255), fill='#D9B7E9')
draw.text((4, 227), 'SOLE', fill='black')
guide.save(docs / 'uv_guide.png')
print('Generated five original styles, paw overlay, native accessory slots, localization and PNG templates.')
