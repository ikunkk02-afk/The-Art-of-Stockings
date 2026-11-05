# 0.9.0 generated interface art

Generated with the built-in image generation tool for this project: mod icon, empty stockings accessory slot, Charm and Foot Odor status icons, and dressing progress frame. No external artwork or code is used.

The exact generation prompts are saved in `prompts.json`. Master images are preserved here; runtime textures are mechanically resized from those masters with alpha preserved.

## Masters

- [Mod icon](mod_icon.png)
- [Stockings accessory slot](stockings_slot.png)
- [Charm status icon](charm_effect.png)
- [Foot Odor status icon](foot_odor_effect.png)
- [Dressing progress frame](dressing_progress.png)
- [Actual-size UI preview, enlarged with nearest-neighbor sampling](runtime_ui_preview.png)
- [Exact prompt set](prompts.json)

The final mod-list icon is `src/main/resources/theartofstockings-icon.png`. Runtime sprites are under `src/main/resources/assets/theartofstockings/textures/slot/`, `textures/mob_effect/` and `textures/gui/`. Regenerate those game-sized files with `python tools/integrate_generated_ui.py` from the project root.
