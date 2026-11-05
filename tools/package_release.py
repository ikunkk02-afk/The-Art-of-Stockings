"""Package the locally verified mod, user templates and complete editable project."""
from pathlib import Path
import hashlib
import json
import re
import shutil
import subprocess
from zipfile import ZipFile, ZIP_DEFLATED

ROOT = Path(__file__).resolve().parents[1]
version = re.search(r'^mod_version=(.+)$', (ROOT / 'gradle.properties').read_text(encoding='utf-8'), re.M)[1].strip()
name = f'theartofstockings-{version}'
jar = ROOT / f'build/libs/{name}.jar'
output = ROOT / 'deliverables'
output.mkdir(exist_ok=True)
styles = ['stockings', 'gradient_stockings', 'pantyhose', 'fishnet_stockings', 'glow_stockings',
          'striped_stockings', 'cat_paw_stockings', 'matte_stockings', 'fox_stockings', 'custom_stockings', 'bodystocking']

with ZipFile(jar) as archive:
    assert archive.testzip() is None
    files = set(archive.namelist())
    assert not any('/test/' in p or p.startswith(('top/theillusivec4/', 'io/wispforest/')) for p in files)
    assert not any('StockingsRenderingRegression' in p for p in files)
    for required in ['com/shouyun/theartofstockings/client/StockingsPaintScreen.class',
                     'com/shouyun/theartofstockings/client/StockingsCanvas.class',
                     'com/shouyun/theartofstockings/PaletteEdits.class',
                     'assets/theartofstockings/textures/entity/matte_stockings_s.png',
                     'assets/theartofstockings/textures/entity/matte_stockings_n.png',
                     'com/shouyun/theartofstockings/Charm.class',
                     'com/shouyun/theartofstockings/CharmAi.class',
                     'com/shouyun/theartofstockings/Excitement.class',
                     'com/shouyun/theartofstockings/WearSlot.class',
                     'com/shouyun/theartofstockings/client/WearScreen.class',
                     'com/shouyun/theartofstockings/client/FabricTextureCache.class',
                     'com/shouyun/theartofstockings/mixin/PlayerHandMixin.class',
                     'assets/theartofstockings/textures/mob_effect/excitement.png',
                     'assets/theartofstockings/textures/mob_effect/charm.png',
                     'assets/theartofstockings/textures/gui/dressing_progress.png',
                     'assets/minecraft/atlases/blocks.json',
                     'theartofstockings-icon.png']:
        assert required in files, required
    metadata = archive.read('META-INF/neoforge.mods.toml').decode('utf-8')
    assert f'version="{version}"' in metadata and '${' not in metadata
    assert '0.12.15.6-beta.1' in metadata
    assert 'logoFile="theartofstockings-icon.png"' in metadata
    for style in styles:
        prefix = 'assets/theartofstockings/'
        for path in [f'geo/{style}.geo.json', f'models/item/{style}.json', f'textures/entity/{style}.png']:
            assert prefix + path in files, path
        model = json.loads(archive.read(prefix + f'models/item/{style}.json'))
        for texture in model['textures'].values():
            namespace, path = texture.split(':', 1)
            assert f'assets/{namespace}/textures/{path}.png' in files, texture
    for locale in ['zh_cn', 'en_us']:
        language = json.loads(archive.read(f'assets/theartofstockings/lang/{locale}.json'))
        assert all(f'item.theartofstockings.{style}' in language for style in styles)
        assert all('gui.theartofstockings.paint.' + key in language for key in ['title','brush','eraser','fill','undo','redo','done'])
        assert all(key in language for key in ['effect.theartofstockings.charm','hud.theartofstockings.equipping','hud.theartofstockings.removing'])
        assert all(key in language for key in ['effect.theartofstockings.excitement','gui.theartofstockings.palette.depth',
                                               'gui.theartofstockings.wear.hands','gui.theartofstockings.wear.head'])
    assert 'PlayerHandMixin' in json.loads(archive.read('theartofstockings.mixins.json'))['client']

validation = []
render_log = (ROOT / 'build/validation-render.log').read_text(encoding='utf-8', errors='replace')
render_passed = re.search(r'Passed (\d+) actual stockings mesh scenarios', render_log)
assert render_passed and 'BUILD SUCCESSFUL' in render_log
validation.append(f'Render buffer regression: {render_passed[1]} mesh scenarios passed')
assert 'Passed PNG import, large NBT, painting, undo/redo, alpha and matte material regressions.' in render_log
validation.append('PNG import, NBT, canvas tools and matte material regressions passed')
assert 'Passed color-depth, raised-paw, upper-body, slim-arm and new-icon regressions.' in render_log
validation.append('Color depth, raised paw pads, hands/head meshes and new icons passed')
if version in {'0.10.1','0.10.2','0.10.3','0.11.0'}:
    assert 'Passed full-opacity coverage on all 11 production textures' in render_log
    validation.append('Full opacity covers all 11 fabrics, retains holes and RGB, and reduces sheen')
if version in {'0.10.2','0.10.3','0.11.0'}:
    assert 'Passed upper-garment pattern colors, paw overlays, crown-to-neck clipping and reversed head removal regressions.' in render_log
    validation.append('Upper patterns, paw overlays, crown-to-neck head covering and reversed removal passed')
for profile in ['base', 'curios', 'accessories', 'both']:
    log = (ROOT / f'build/validation-{profile}.log').read_text(encoding='utf-8', errors='replace')
    passed = re.search(r'All (\d+) required tests passed', log)
    assert passed and 'BUILD SUCCESSFUL' in log and 'required tests failed' not in log, profile
    if version == '0.10.3':
        assert int(passed[1]) >= 56, profile
    if version == '0.11.0':
        assert int(passed[1]) >= 65, profile
    validation.append(f'{profile}: {passed[1]} GameTests passed')

mod_output = output / jar.name
shutil.copy2(jar, mod_output)
icon_output = output / f'{name}-icon.png'
shutil.copy2(ROOT/'src/main/resources/theartofstockings-icon.png',icon_output)
test_pack = output / f'丝袜艺术-{version}-测试包.zip'
with ZipFile(test_pack, 'w', ZIP_DEFLATED) as archive:
    archive.write(mod_output, f'mods/{jar.name}')
    archive.write(ROOT / f'docs/RELEASE-{version}.md', '安装与合成说明.md')
    archive.write(ROOT / 'README.md', '完整使用与合成说明.md')
    archive.write(ROOT / 'docs/TESTING.md', '开发验证记录.md')
    if version in {'0.10.2','0.10.3','0.11.0'}:
        preview_output=output/f'{name}-图案与方向预览.png'
        shutil.copy2(ROOT/'build/upper-garment-preview.png',preview_output)
        archive.write(preview_output,'图案与头套方向预览.png')
    archive.write(icon_output,'模组图标.png')
    archive.write(ROOT/'art/generated/0.9.0/runtime_ui_preview.png','界面图标预览.png')
    archive.write(ROOT/'art/generated/0.9.0/prompts.json','图标生成提示词.json')
    archive.write(ROOT/'art/generated/0.10.0/runtime-icons.png','全身丝袜与兴奋图标预览.png')
    archive.write(ROOT/'art/generated/0.10.0/prompts.json','新增图标生成提示词.json')
    for file in sorted((ROOT / 'docs/custom-textures').iterdir()):
        if file.is_file():
            archive.write(file, '自定义纹理/' + file.name)
    archive.writestr('自动验证结果.txt', '\n'.join(validation) + '\n')
    archive.writestr('JAR-SHA256.txt', hashlib.sha256(mod_output.read_bytes()).hexdigest() + '  ' + jar.name + '\n')

source_pack = output / f'{name}-project-source.zip'
source_files = subprocess.check_output(['git', 'ls-files', '-co', '--exclude-standard', '-z'], cwd=ROOT).decode('utf-8').split('\0')
with ZipFile(source_pack, 'w', ZIP_DEFLATED) as archive:
    for relative in sorted(set(source_files)):
        if not relative or relative.startswith(('deliverables/', 'build/', 'run/', 'logs/', '.gradle/')):
            continue
        file = ROOT / relative
        if file.is_file():
            archive.write(file, f'{name}-project/' + relative)

artifacts = [mod_output, test_pack, source_pack]
checksums = output / f'SHA256SUMS-{version}.txt'
checksums.write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest() + '  ' + p.name + '\n' for p in [*artifacts,icon_output]), encoding='utf-8')
for file in artifacts:
    with ZipFile(file) as archive:
        assert archive.testzip() is None, file
    print(f'{file} ({file.stat().st_size:,} bytes)')
print('\n'.join(validation))
