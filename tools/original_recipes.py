# Transcription of the original Thaumcraft 6 ConfigRecipes (git show master:.../ConfigRecipes.java) into this port's
# recipe JSON. Run `python tools/original_recipes.py` to diff, `--write` to regenerate. Port-only recipes are left alone.
import json, os, sys
os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
OUT = 'src/main/resources/data/thaumcraft/recipe'
import re
IDS = {k.split('.')[2] for k in json.load(open('src/main/resources/assets/thaumcraft/lang/en_us.json', encoding='utf-8')) if re.match(r'^(item|block).thaumcraft.[a-z0-9_]+$', k)}
R = {}
SKIPPED = []

A = dict(AIR='aer', EARTH='terra', FIRE='ignis', WATER='aqua', ORDER='ordo', ENTROPY='perditio', VOID='vacuos', LIGHT='lux',
         MOTION='motus', COLD='gelum', CRYSTAL='vitreus', METAL='metallum', LIFE='victus', DEATH='mortuus', ENERGY='potentia',
         EXCHANGE='permutatio', MAGIC='praecantatio', AURA='auram', ALCHEMY='alkimia', FLUX='vitium', DARKNESS='tenebrae',
         ELDRITCH='alienis', FLIGHT='volatus', PLANT='herba', TOOL='instrumentum', CRAFT='fabrico', MECHANISM='machina',
         TRAP='vinculum', SOUL='spiritus', MIND='cognitio', SENSES='sensus', AVERSION='aversio', PROTECT='praemunio',
         DESIRE='desiderium', UNDEAD='exanimis', BEAST='bestia', MAN='humanus')
ALL_ASPECTS = list(A.values())

def asp(**kw):
    return {A[k]: v for k, v in kw.items()}

class Missing(Exception):
    pass

def item(i):
    if ':' not in i: i = 'thaumcraft:' + i
    if i.startswith('thaumcraft:') and i.split(':')[1] not in IDS:
        raise Missing(i)
    return i

def I(i): return {'item': item(i)}
def T(t): return {'tag': t}
def ANY(*items): return [I(i) for i in items]
def crystal(a, count=1):
    return {'type': 'neoforge:components', 'items': 'thaumcraft:crystal_essence', 'components': {'thaumcraft:aspects': {a: 1}}}
def phial(a):
    return {'type': 'neoforge:components', 'items': 'thaumcraft:phial', 'components': {'thaumcraft:aspects': {a: 10}}}

OD = {
    'plateBrass': I('plate_brass'), 'plateIron': I('plate_iron'), 'plateThaumium': I('plate_thaumium'), 'plateVoid': I('plate_void'),
    'stickWood': T('c:rods/wooden'), 'ingotIron': T('c:ingots/iron'), 'ingotGold': T('c:ingots/gold'), 'ingotBrass': I('ingot_brass'),
    'ingotThaumium': I('ingot_thaumium'), 'ingotVoid': I('ingot_void'), 'gemQuartz': T('c:gems/quartz'), 'gemDiamond': T('c:gems/diamond'),
    'gemAmber': I('amber'), 'leather': T('c:leathers'), 'string': T('c:strings'), 'nitor': T('thaumcraft:nitor'),
    'paneGlass': T('c:glass_panes'), 'blockGlass': T('c:glass_blocks'), 'slabWood': T('minecraft:wooden_slabs'),
    'plankWood': T('minecraft:planks'), 'trapdoorWood': T('minecraft:wooden_trapdoors'), 'nuggetBrass': I('nugget_brass'),
    'nuggetQuicksilver': I('nugget_quicksilver'), 'nuggetQuartz': I('nugget_quartz'), 'nuggetThaumium': I('nugget_thaumium'),
    'nuggetGold': T('c:nuggets/gold'), 'dustRedstone': T('c:dusts/redstone'), 'blockRedstone': T('c:storage_blocks/redstone'),
    'dustGlowstone': T('c:dusts/glowstone'), 'stone': T('c:stones'), 'cobblestone': T('c:cobblestones'),
    'workbench': I('minecraft:crafting_table'), 'dyeRed': T('c:dyes/red'), 'dyeBlack': T('c:dyes/black'), 'slimeball': T('c:slime_balls'),
    'oreIron': T('c:ores/iron'), 'oreGold': T('c:ores/gold'), 'oreCinnabar': I('ore_cinnabar'), 'oreCopper': T('c:ores/copper'),
    'oreTin': T('c:ores/tin'), 'oreSilver': T('c:ores/silver'), 'oreLead': T('c:ores/lead'),
}

def ing(x):
    if isinstance(x, (dict, list)): return x
    if x in OD: return OD[x]
    return I(x)

def result(i, count=1, components=None):
    r = {'id': item(i)}
    if count != 1: r['count'] = count
    if components: r['components'] = components
    return r

def add(name, recipe_fn):
    try:
        R[name] = recipe_fn()
    except Missing as e:
        SKIPPED.append((name, str(e)))

def key_of(k):
    return {c: ing(v) for c, v in k.items()}

def arcane(name, research, vis, crystals, out, pattern, key, count=1, components=None):
    def f():
        d = {'type': 'thaumcraft:arcane_shaped', 'research': research, 'vis': vis}
        if crystals: d['crystals'] = crystals
        d['pattern'] = pattern
        d['key'] = key_of(key)
        d['result'] = result(out, count, components)
        return d
    add(name, f)

def arcane_sl(name, research, vis, crystals, out, ings, count=1, components=None):
    def f():
        d = {'type': 'thaumcraft:arcane_shapeless', 'research': research, 'vis': vis}
        if crystals: d['crystals'] = crystals
        d['ingredients'] = [ing(i) for i in ings]
        d['result'] = result(out, count, components)
        return d
    add(name, f)

def crucible(name, research, out, catalyst, aspects, count=1, components=None):
    add(name, lambda: {'type': 'thaumcraft:crucible', 'research': research, 'catalyst': ing(catalyst), 'aspects': aspects,
                       'result': result(out, count, components)})

def infusion(name, research, out, inst, aspects, central, comps, components=None):
    add(name, lambda: {'type': 'thaumcraft:infusion', 'research': research, 'instability': inst, 'aspects': aspects,
                       'central': ing(central), 'components': [ing(c) for c in comps], 'result': result(out, 1, components)})

def shaped(name, out, pattern, key, count=1, group=None):
    def f():
        d = {'type': 'minecraft:crafting_shaped', 'category': 'misc'}
        if group: d['group'] = group
        d['pattern'] = pattern
        d['key'] = key_of(key)
        d['result'] = result(out, count)
        return d
    add(name, f)

def shapeless(name, out, ings, count=1, group=None):
    def f():
        d = {'type': 'minecraft:crafting_shapeless', 'category': 'misc'}
        if group: d['group'] = group
        d['ingredients'] = [ing(i) for i in ings]
        d['result'] = result(out, count)
        return d
    add(name, f)

COLOURS = ['white', 'orange', 'magenta', 'lightblue', 'yellow', 'lime', 'pink', 'gray', 'silver', 'cyan', 'purple', 'blue', 'brown', 'green', 'red', 'black']
VANILLA_COLOUR = {'lightblue': 'light_blue', 'silver': 'light_gray'}
def vc(c): return VANILLA_COLOUR.get(c, c)

# ---------------------------------------------------------------- crucible (initializeAlchemyRecipes + postAspects)
for a in ALL_ASPECTS:
    crucible('crystal_essence_' + a, 'BASEALCHEMY', 'crystal_essence', 'nuggetQuartz', {a: 2}, components={'thaumcraft:aspects': {a: 1}})
crucible('nitor_yellow', 'UNLOCKALCHEMY@3', 'nitor_yellow', 'dustGlowstone', asp(ENERGY=10, FIRE=10, LIGHT=10))
for c in COLOURS:
    shapeless('nitor_' + c, 'nitor_' + c, [T('c:dyes/' + vc(c)), 'nitor'], group='thaumcraft:nitorgroup')
crucible('alumentum', 'ALUMENTUM', 'alumentum', ANY('minecraft:coal', 'minecraft:charcoal'), asp(ENERGY=10, FIRE=10, ENTROPY=5))
crucible('brassingot', 'METALLURGY@1', 'ingot_brass', 'ingotIron', asp(TOOL=5))
crucible('ingot_thaumium', 'METALLURGY@2', 'ingot_thaumium', 'ingotIron', asp(MAGIC=5, EARTH=5))
crucible('ingot_void', 'BASEELDRITCH', 'ingot_void', 'void_seed', asp(METAL=10, FLUX=5))
crucible('tallow', 'HEDGEALCHEMY@1', 'tallow', 'minecraft:rotten_flesh', asp(FIRE=1))
crucible('hedge_leather', 'HEDGEALCHEMY@1', 'minecraft:leather', 'minecraft:rotten_flesh', asp(AIR=3, BEAST=3))
# the original's blank foci are this port's focus_blank_1/2/3 (focus_1..3 here are the port's own fixed-chain foci)
crucible('focus_blank_1', 'UNLOCKAUROMANCY', 'focus_blank_1', crystal('ordo'), asp(CRYSTAL=20, MAGIC=10, AURA=5))
for metal, cluster in [('iron', 'cluster_iron'), ('gold', 'cluster_gold'), ('cinnabar', 'cluster_cinnabar'), ('copper', 'cluster_copper'),
                       ('tin', 'cluster_tin'), ('silver', 'cluster_silver'), ('lead', 'cluster_lead')]:
    crucible(cluster, 'METALPURIFICATION', cluster, 'ore' + metal.capitalize(), asp(METAL=5, ORDER=5))
    if metal in ('tin', 'silver', 'lead') and cluster in R:
        # ModConfig.found*Ore: only when some mod supplies that ore
        R[cluster]['neoforge:conditions'] = [{'type': 'neoforge:not', 'value': {'type': 'neoforge:tag_empty', 'tag': 'c:ores/' + metal}}]
crucible('liquid_death', 'LIQUIDDEATH', 'bucket_death', 'minecraft:bucket', asp(DEATH=100, ALCHEMY=20, ENTROPY=50))
crucible('bottle_taint', 'BOTTLETAINT', 'bottle_taint', phial('vitium'), asp(FLUX=30, WATER=30))
crucible('bath_salts', 'BATHSALTS', 'bath_salts', 'salis_mundus', asp(MIND=40, AIR=40, ORDER=40, LIFE=40))
crucible('sanity_soap', 'SANESOAP', 'sanity_soap', 'flesh_block', asp(MIND=75, ELDRITCH=50, ORDER=75, LIFE=50))
crucible('seal_pickup', 'SEALCOLLECT', 'seal_pickup', 'seal_blank', asp(DESIRE=10))
crucible('seal_pickup_advanced', 'SEALCOLLECT&&MINDBIOTHAUMIC', 'seal_pickup_advanced', 'seal_pickup', asp(SENSES=10, MIND=10))
crucible('seal_fill', 'SEALSTORE', 'seal_fill', 'seal_blank', asp(AVERSION=10))
crucible('seal_fill_advanced', 'SEALSTORE&&MINDBIOTHAUMIC', 'seal_fill_advanced', 'seal_fill', asp(SENSES=10, MIND=10))
crucible('seal_empty', 'SEALEMPTY', 'seal_empty', 'seal_blank', asp(VOID=10))
crucible('seal_empty_advanced', 'SEALEMPTY&&MINDBIOTHAUMIC', 'seal_empty_advanced', 'seal_empty', asp(SENSES=10, MIND=10))
crucible('seal_provider', 'SEALPROVIDE', 'seal_provider', 'seal_empty_advanced', asp(EXCHANGE=10, DESIRE=10))
crucible('seal_stock', 'SEALSTOCK', 'seal_stock', 'seal_fill', asp(MIND=10, DESIRE=10))
crucible('seal_guard', 'SEALGUARD', 'seal_guard', 'seal_blank', asp(AVERSION=20, PROTECT=20))
crucible('seal_guard_advanced', 'SEALGUARD&&MINDBIOTHAUMIC', 'seal_guard_advanced', 'seal_guard', asp(SENSES=20, MIND=20))
crucible('seal_lumber', 'SEALLUMBER', 'seal_lumber', 'seal_breaker', asp(PLANT=40, SENSES=20))
crucible('seal_use', 'SEALUSE', 'seal_use', 'seal_blank', asp(CRAFT=20, SENSES=10, MIND=20))
crucible('seal_breaker_advanced', 'SEALBREAK&&MINDBIOTHAUMIC', 'seal_breaker_advanced', 'seal_breaker', asp(SENSES=10, MIND=10, TOOL=20))
crucible('everfull_urn', 'EVERFULLURN', 'everfull_urn', 'minecraft:flower_pot', asp(WATER=30, CRAFT=10, EARTH=10))
# postAspects: the aspects of the output item, or of output minus catalyst - worked out from the port's aspect table at
# generation time is not possible here, so the values are the original's own ConfigAspects registrations (see hedge note)
# postAspects: each hedge recipe costs the aspects of its output (less the catalyst's, for the transmutations), as
# registered by the original's ConfigAspects - the same values this port's aspect table carries.
crucible('hedge_gunpowder', 'HEDGEALCHEMY@2', 'minecraft:gunpowder', 'minecraft:gunpowder', asp(FIRE=10, ENTROPY=10, ALCHEMY=5), count=2)
crucible('hedge_slime', 'HEDGEALCHEMY@2', 'minecraft:slime_ball', 'minecraft:slime_ball', asp(WATER=5, LIFE=5, ALCHEMY=1), count=2)
crucible('hedge_glowstone', 'HEDGEALCHEMY@2', 'minecraft:glowstone_dust', 'dustGlowstone', asp(SENSES=5, LIGHT=10), count=2)
crucible('hedge_dye', 'HEDGEALCHEMY@2', 'minecraft:ink_sac', 'minecraft:ink_sac', asp(WATER=2, BEAST=2), count=2)
crucible('hedge_clay', 'HEDGEALCHEMY@3', 'minecraft:clay_ball', 'minecraft:dirt', asp(WATER=5))
crucible('hedge_string', 'HEDGEALCHEMY@3', 'minecraft:string', 'minecraft:wheat', asp(BEAST=5, CRAFT=1))
crucible('hedge_web', 'HEDGEALCHEMY@3', 'minecraft:cobweb', 'minecraft:string', asp(TRAP=5))
crucible('hedge_lava', 'HEDGEALCHEMY@3', 'minecraft:lava_bucket', 'minecraft:bucket', asp(FIRE=15, EARTH=5))

# ---------------------------------------------------------------- arcane
arcane('mechanism_simple', 'BASEARTIFICE', 10, asp(FIRE=1, WATER=1), 'mechanism_simple', [' B ', 'ISI', ' B '], {'B': 'plateBrass', 'I': 'plateIron', 'S': 'stickWood'})
arcane('mechanism_complex', 'BASEARTIFICE', 50, asp(FIRE=1, WATER=1), 'mechanism_complex', [' M ', 'TQT', ' M '], {'T': 'plateThaumium', 'Q': 'minecraft:piston', 'M': 'mechanism_simple'})
arcane_sl('vis_resonator', 'UNLOCKAUROMANCY@2', 50, asp(AIR=1, WATER=1), 'vis_resonator', ['plateIron', 'gemQuartz'])
arcane_sl('activator_rail', 'FIRSTSTEPS', 10, None, 'activator_rail', ['minecraft:activator_rail'])
arcane('thaumometer', 'FIRSTSTEPS@2', 20, asp(AIR=1, EARTH=1, WATER=1, FIRE=1, ORDER=1, ENTROPY=1), 'thaumometer', [' I ', 'IGI', ' I '], {'I': 'ingotGold', 'G': 'minecraft:glass_pane'})
arcane('sanity_checker', 'WARP', 20, asp(ORDER=1, ENTROPY=1), 'sanity_checker', ['BN ', 'M N', 'BN '], {'N': 'nuggetBrass', 'B': 'brain', 'M': 'mirrored_glass'})
arcane('recharge_pedestal', 'RECHARGEPEDESTAL', 100, asp(AIR=1, ORDER=1), 'recharge_pedestal', [' R ', 'DID', 'SSS'], {'I': 'ingotGold', 'D': 'gemDiamond', 'R': 'vis_resonator', 'S': 'stone'})
arcane('arcane_workbench_charger', 'WORKBENCHCHARGER', 200, asp(AIR=2, ORDER=2), 'arcane_workbench_charger', [' R ', 'W W', 'I I'], {'I': 'ingotIron', 'R': 'vis_resonator', 'W': 'plank_greatwood'})
arcane('focal_manipulator', 'BASEAUROMANCY@2', 100, asp(EARTH=1, WATER=1), 'focal_manipulator', ['ISI', 'BRB', 'GTG'], {'S': 'stone_arcane_slab', 'T': 'table_stone', 'R': 'vis_resonator', 'B': 'stone_arcane', 'G': 'ingotGold', 'I': 'plateIron'})
arcane('caster_basic', 'UNLOCKAUROMANCY@2', 100, asp(AIR=1, EARTH=1, WATER=1, FIRE=1, ORDER=1, ENTROPY=1), 'caster_basic', ['III', 'LRL', 'LTL'], {'T': 'thaumometer', 'R': 'vis_resonator', 'L': 'leather', 'I': 'ingotIron'})
arcane('fabric', 'UNLOCKINFUSION', 5, None, 'fabric', [' S ', 'SCS', ' S '], {'S': 'string', 'C': T('minecraft:wool')})
arcane('cloth_chest', 'UNLOCKINFUSION', 100, None, 'cloth_chest', ['I I', 'III', 'III'], {'I': 'fabric'})
arcane('cloth_legs', 'UNLOCKINFUSION', 100, None, 'cloth_legs', ['III', 'I I', 'I I'], {'I': 'fabric'})
arcane('cloth_boots', 'UNLOCKINFUSION', 100, None, 'cloth_boots', ['I I', 'I I'], {'I': 'fabric'})
arcane('goggles', 'UNLOCKARTIFICE', 50, None, 'goggles', ['LGL', 'L L', 'TGT'], {'T': 'thaumometer', 'G': 'ingotBrass', 'L': 'leather'})
arcane_sl('seal_blank', 'CONTROLSEALS', 20, asp(AIR=1), 'seal_blank', ['minecraft:clay_ball', 'tallow', 'dyeRed', 'nitor'], count=3)
arcane('module_vision', 'GOLEMVISION', 50, asp(WATER=1), 'module_vision', ['B B', 'E E', 'PGP'], {'B': 'minecraft:glass_bottle', 'E': 'minecraft:fermented_spider_eye', 'P': 'plateBrass', 'G': 'mechanism_simple'})
arcane('module_aggression', 'SEALGUARD', 50, asp(FIRE=1), 'module_aggression', [' R ', 'RTR', 'PGP'], {'R': 'paneGlass', 'T': 'minecraft:blaze_powder', 'P': 'plateBrass', 'G': 'mechanism_simple'})
arcane_sl('mirrored_glass', 'BASEARTIFICE', 50, asp(WATER=1, ORDER=1), 'mirrored_glass', ['quicksilver', 'paneGlass'])
arcane('spa', 'ARCANESPA', 50, asp(WATER=1), 'spa', ['QIQ', 'SJS', 'SPS'], {'P': 'mechanism_simple', 'J': 'jar_normal', 'S': 'stone_arcane', 'Q': 'minecraft:quartz_block', 'I': 'minecraft:iron_bars'})
arcane('tube', 'TUBES', 10, None, 'tube', [' Q ', 'IGI', ' B '], {'I': 'plateIron', 'B': 'nuggetBrass', 'G': 'blockGlass', 'Q': 'nuggetQuicksilver'}, count=8)
arcane('resonator', 'TUBES', 50, None, 'resonator', ['I I', 'INI', ' S '], {'I': 'plateIron', 'N': 'minecraft:quartz', 'S': 'stickWood'})
arcane_sl('tube_valve', 'TUBES', 10, None, 'tube_valve', ['tube', 'minecraft:lever'])
arcane_sl('tube_filter', 'TUBES', 10, None, 'tube_filter', ['tube', 'filter'])
arcane_sl('tube_restrict', 'TUBES', 10, asp(EARTH=1), 'tube_restrict', ['tube'])
arcane_sl('tube_oneway', 'TUBES', 10, asp(WATER=1), 'tube_oneway', ['tube'])
arcane('tube_buffer', 'TUBES', 25, None, 'tube_buffer', ['PVP', 'TWT', 'PRP'], {'T': 'tube', 'V': 'tube_valve', 'W': 'plateIron', 'R': 'tube_restrict', 'P': 'phial'})
arcane('jar_normal', 'WARDEDJARS', 5, None, 'jar_normal', ['GWG', 'G G', 'GGG'], {'W': 'slabWood', 'G': 'paneGlass'})
arcane('jar_void', 'WARDEDJARS', 50, asp(ENTROPY=1), 'jar_void', ['J'], {'J': 'jar_normal'})
for i, c in enumerate(COLOURS):
    arcane('banner_' + c, 'BASEINFUSION', 10, None, 'banner_' + c, ['WS', 'WS', 'WB'], {'W': 'minecraft:' + vc(c) + '_wool', 'S': 'stickWood', 'B': 'slabWood'})
arcane('paving_stone_barrier', 'PAVINGSTONES', 50, asp(FIRE=1, ORDER=1), 'paving_stone_barrier', ['SS', 'SS'], {'S': 'stone_arcane_brick'}, count=4)
arcane('paving_stone_travel', 'PAVINGSTONES', 50, asp(AIR=1, EARTH=1), 'paving_stone_travel', ['SS', 'SS'], {'S': 'stone_arcane_brick'}, count=4)
arcane('lamp_arcane', 'ARCANELAMP', 50, asp(AIR=1, FIRE=1), 'lamp_arcane', [' I ', 'IAI', ' I '], {'A': 'amber_block', 'I': 'plateIron'})
arcane('levitator', 'LEVITATOR', 35, asp(AIR=1), 'levitator', ['WIW', 'BNB', 'WGW'], {'I': 'plateThaumium', 'N': 'nitor', 'W': 'plankWood', 'B': 'plateIron', 'G': 'mechanism_simple'})
arcane('redstone_relay', 'REDSTONERELAY', 10, asp(ORDER=1), 'redstone_relay', ['   ', 'TGT', 'SSS'], {'T': 'minecraft:redstone_torch', 'G': 'mechanism_simple', 'S': 'minecraft:smooth_stone_slab'})
arcane('arcane_ear', 'ARCANEEAR', 15, asp(AIR=1), 'arcane_ear', ['P P', ' G ', 'WRW'], {'W': 'slabWood', 'R': 'minecraft:redstone', 'G': 'mechanism_simple', 'P': 'plateBrass'})
shapeless('arcane_ear_toggle', 'arcane_ear_toggle', ['arcane_ear', 'minecraft:lever'])
arcane('infusion_matrix', 'INFUSION@2', 150, asp(AIR=1, EARTH=1, WATER=1, FIRE=1, ORDER=1, ENTROPY=1), 'infusion_matrix', ['S S', ' N ', 'S S'], {'S': 'stone_arcane_brick', 'N': 'nitor'})
arcane('matrix_speed', 'INFUSIONBOOST', 500, asp(AIR=1, ORDER=1), 'matrix_speed', ['SNS', 'NGN', 'SNS'], {'S': 'stone_arcane', 'N': 'nitor', 'G': 'minecraft:diamond_block'})
arcane('matrix_cost', 'INFUSIONBOOST', 500, asp(AIR=1, WATER=1, ENTROPY=1), 'matrix_cost', ['SAS', 'AGA', 'SAS'], {'S': 'stone_arcane', 'A': 'alumentum', 'G': 'minecraft:diamond_block'})
arcane('pedestal', 'INFUSION', 10, None, 'pedestal', ['SSS', ' B ', 'SSS'], {'S': 'stone_arcane_slab', 'B': 'stone_arcane'})
arcane('pedestal_ancient', 'INFUSIONANCIENT', 150, None, 'pedestal_ancient', ['SSS', ' B ', 'SSS'], {'S': 'slab_ancient', 'B': 'stone_ancient'})
arcane('pedestal_eldritch', 'INFUSIONELDRITCH', 150, None, 'pedestal_eldritch', ['SSS', ' B ', 'SSS'], {'S': 'slab_eldritch', 'B': 'stone_eldritch_tile'})
arcane('focus_pouch', 'FOCUSPOUCH', 25, None, 'focus_pouch', ['LGL', 'LBL', 'LLL'], {'B': 'girdle_mundane', 'L': 'leather', 'G': 'minecraft:gold_ingot'})
arcane('dioptra', 'DIOPTRA', 50, asp(AIR=1, WATER=1), 'dioptra', ['APA', 'IGI', 'AAA'], {'A': 'stone_arcane', 'G': 'thaumometer', 'P': 'vis_resonator', 'I': 'plateIron'})
arcane('hungry_chest', 'HUNGRYCHEST', 15, asp(EARTH=1, WATER=1), 'hungry_chest', ['WTW', 'W W', 'WWW'], {'W': 'plank_greatwood', 'T': 'trapdoorWood'})
arcane('filter', 'BASEALCHEMY', 15, asp(WATER=1), 'filter', ['GWG'], {'G': 'minecraft:gold_ingot', 'W': 'plank_silverwood'}, count=2)
arcane('morphic_resonator', 'BASEALCHEMY', 50, asp(AIR=1, FIRE=1), 'morphic_resonator', [' G ', 'BSB', ' G '], {'G': 'paneGlass', 'B': 'plateBrass', 'S': 'nugget_rareearth'})
arcane('alembic', 'ESSENTIASMELTER', 50, asp(WATER=1), 'alembic', ['WFW', 'SBS', 'WFW'], {'W': 'plank_greatwood', 'B': 'minecraft:bucket', 'F': 'filter', 'S': 'plateBrass'})
arcane('smelter', 'ESSENTIASMELTER@2', 50, asp(FIRE=1), 'smelter', ['BCB', 'SFS', 'SSS'], {'C': 'crucible', 'F': 'minecraft:furnace', 'S': 'cobblestone', 'B': 'plateBrass'})
arcane('essentia_smelter_thaumium', 'ESSENTIASMELTERTHAUMIUM', 250, asp(FIRE=2), 'smelter_thaumium', ['BFB', 'IGI', 'III'], {'F': 'smelter', 'G': 'metal_alchemical', 'I': 'plateThaumium', 'B': 'plateBrass'})
arcane('essentia_smelter_void', 'ESSENTIASMELTERVOID', 750, asp(FIRE=3), 'smelter_void', ['BFB', 'IGI', 'III'], {'F': 'smelter', 'G': 'metal_alchemical_advanced', 'I': 'plateVoid', 'B': 'plateBrass'})
arcane('metal_alchemical', 'TUBES', 75, asp(WATER=1, ORDER=1, ENTROPY=1), 'metal_alchemical', ['IVI', 'TWT', 'IVI'], {'W': 'plank_greatwood', 'V': 'tube_valve', 'T': 'tube', 'I': 'plateIron'}, count=2)
arcane('metal_alchemical_advanced', 'ESSENTIASMELTERVOID@1', 200, asp(EARTH=1, FIRE=1), 'metal_alchemical_advanced', [' A ', 'VPV', ' A '], {'A': 'metal_alchemical', 'V': 'plateVoid', 'P': 'primordial_pearl'})
arcane('potion_sprayer', 'POTIONSPRAYER', 75, asp(WATER=1, FIRE=1), 'potion_sprayer', ['BDB', 'IAI', 'ICI'], {'B': 'plateBrass', 'I': 'plateIron', 'A': 'minecraft:brewing_stand', 'D': 'minecraft:dispenser', 'C': 'metal_alchemical'})
arcane('smelter_aux', 'IMPROVEDSMELTING', 100, asp(AIR=1, EARTH=1), 'smelter_aux', ['WTW', 'RGR', 'IBI'], {'W': 'plank_greatwood', 'B': 'bellows', 'R': 'plateBrass', 'T': 'tube_filter', 'I': 'plateIron', 'G': 'metal_alchemical'})
arcane('smelter_vent', 'IMPROVEDSMELTING2', 150, asp(AIR=1), 'smelter_vent', ['IBI', 'MGF', 'IBI'], {'I': 'plateIron', 'B': 'plateBrass', 'F': 'filter', 'M': 'filter', 'G': 'metal_alchemical'})
arcane('essentia_input', 'ESSENTIATRANSPORT', 100, asp(AIR=1, WATER=1), 'essentia_input', ['   ', 'BQB', 'IGI'], {'I': 'plateIron', 'B': 'plateBrass', 'Q': 'minecraft:dispenser', 'G': 'metal_alchemical'})
arcane('essentia_output', 'ESSENTIATRANSPORT', 100, asp(AIR=1, WATER=1), 'essentia_output', ['   ', 'BQB', 'IGI'], {'I': 'plateIron', 'B': 'plateBrass', 'Q': 'minecraft:hopper', 'G': 'metal_alchemical'})
arcane('bellows', 'BELLOWS', 25, asp(AIR=1), 'bellows', ['WW ', 'LLI', 'WW '], {'W': 'plankWood', 'I': 'ingotIron', 'L': 'leather'})
arcane('centrifuge', 'CENTRIFUGE', 100, asp(ORDER=1, ENTROPY=1), 'centrifuge', [' T ', 'RCP', ' T '], {'T': 'tube', 'P': 'mechanism_simple', 'R': 'morphic_resonator', 'C': 'metal_alchemical'})
arcane('brain_box', 'THAUMATORIUM', 50, asp(EARTH=1, ORDER=1), 'brain_box', ['IAI', 'ABA', 'IAI'], {'B': 'mind_clockwork', 'A': 'gemAmber', 'I': 'plateIron'})
arcane('mind_clockwork', 'MINDCLOCKWORK@2', 25, asp(FIRE=1, ORDER=1), 'mind_clockwork', [' P ', 'PGP', 'BCB'], {'G': 'mechanism_simple', 'B': 'plateBrass', 'P': 'paneGlass', 'C': 'minecraft:comparator'})
arcane('turret_placer_basic', 'BASICTURRET', 100, asp(AIR=1), 'turret_placer_basic', ['BGI', 'WMW', 'S S'], {'G': 'mechanism_simple', 'I': 'plateIron', 'S': 'stickWood', 'M': 'mind_clockwork', 'B': 'minecraft:bow', 'W': 'plank_greatwood'})
arcane('turret_placer_advanced', 'ADVANCEDTURRET', 150, asp(AIR=2), 'turret_placer_advanced', ['PMP', 'PTP', '   '], {'T': 'turret_placer_basic', 'P': 'plateIron', 'M': 'mind_biothaumic'})
arcane('pattern_crafter', 'ARCANEPATTERNCRAFTER', 50, asp(EARTH=1, WATER=1, ORDER=1), 'pattern_crafter', ['VH ', 'GCG', ' W '], {'H': 'minecraft:hopper', 'W': 'plank_greatwood', 'G': 'mechanism_simple', 'V': 'vis_resonator', 'C': 'workbench'})
arcane('grapple_gun_tip', 'GRAPPLEGUN', 25, asp(EARTH=1), 'grapple_gun_tip', ['BRB', 'RHR', 'BRB'], {'B': 'plateBrass', 'R': 'nugget_rareearth', 'H': 'minecraft:tripwire_hook'})
arcane('grapple_gun_spool', 'GRAPPLEGUN', 25, asp(WATER=1), 'grapple_gun_spool', ['SHS', 'SGS', 'SSS'], {'G': 'mechanism_simple', 'S': 'string', 'H': 'minecraft:tripwire_hook'})
arcane('grapple_gun', 'GRAPPLEGUN', 75, asp(AIR=1, FIRE=1), 'grapple_gun', ['  S', 'TII', ' BW'], {'B': 'plateBrass', 'I': 'plateIron', 'T': 'grapple_gun_tip', 'W': 'plankWood', 'S': 'grapple_gun_spool'})
arcane('vis_battery', 'VISBATTERY', 50, asp(AIR=2, EARTH=2, WATER=2, FIRE=2, ORDER=2, ENTROPY=2), 'vis_battery', ['SSS', 'SRS', 'SSS'], {'R': 'vis_resonator', 'S': 'stone_arcane_slab'})
arcane('vis_generator', 'VISGENERATOR', 25, asp(FIRE=1, ORDER=1), 'vis_generator', ['WSW', 'EPE', 'WRW'], {'R': 'vis_resonator', 'E': 'nugget_rareearth', 'S': 'dustRedstone', 'P': 'minecraft:piston', 'W': 'plankWood'})
arcane('condenser', 'FLUXCLEANUP', 500, asp(AIR=5, WATER=5, ENTROPY=5), 'condenser', ['BCB', 'WMW', 'BTB'], {'T': 'tube', 'C': 'morphic_resonator', 'W': 'plankWood', 'M': 'mechanism_complex', 'B': 'plateBrass'})
arcane('condenser_lattice', 'FLUXCLEANUP', 100, asp(EARTH=3, AIR=3), 'condenser_lattice', ['QTQ', 'QFQ', 'QTQ'], {'T': 'plateThaumium', 'F': 'filter', 'Q': 'gemQuartz'})
arcane('stabilizer', 'INFUSIONSTABLE', 250, asp(EARTH=1, WATER=1, ENTROPY=1), 'stabilizer', ['SRS', 'BVB', 'IMI'], {'R': 'blockRedstone', 'S': 'stone_arcane_slab', 'B': 'stone_arcane', 'M': 'mechanism_complex', 'V': 'vis_resonator', 'I': 'inlay'})
arcane_sl('inlay', 'INFUSIONSTABLE', 25, asp(WATER=1), 'inlay', ['dustRedstone', 'ingotGold'], count=2)

# ---------------------------------------------------------------- infusion
def inf_crystal(name, out, a, inst):
    add(name, lambda: {'type': 'thaumcraft:infusion_crystal', 'research': 'CRYSTALFARMER', 'instability': inst,
                       'aspects': {a: 10, 'vitreus': 10, 'vinculum': 5}, 'centralAspect': a,
                       'components': [I('minecraft:wheat_seeds'), I('salis_mundus')], 'result': result(out)})
infusion('seal_harvest', 'SEALHARVEST', 'seal_harvest', 0, asp(PLANT=10, SENSES=10, MAN=10), 'seal_blank',
         ['minecraft:wheat_seeds', 'minecraft:pumpkin_seeds', 'minecraft:melon_seeds', 'minecraft:beetroot_seeds', 'minecraft:sugar_cane', 'minecraft:cactus'])
infusion('seal_butcher', 'SEALBUTCHER', 'seal_butcher', 0, asp(BEAST=10, SENSES=10, MAN=10), 'seal_guard',
         ['leather', T('minecraft:wool'), 'minecraft:rabbit_hide', 'minecraft:porkchop', 'minecraft:mutton', 'minecraft:beef'])
infusion('seal_breaker', 'SEALBREAK', 'seal_breaker', 1, asp(TOOL=10, ENTROPY=10, MAN=10), 'seal_blank',
         ['minecraft:golden_axe', 'minecraft:golden_pickaxe', 'minecraft:golden_shovel'])
for n, out, a, inst in [('air', 'crystal_aer', 'aer', 0), ('fire', 'crystal_ignis', 'ignis', 0), ('water', 'crystal_aqua', 'aqua', 0),
                        ('earth', 'crystal_terra', 'terra', 0), ('order', 'crystal_ordo', 'ordo', 0), ('entropy', 'crystal_perditio', 'perditio', 0),
                        ('flux', 'crystal_vitium', 'vitium', 4)]:
    inf_crystal('infusion_crystal_cluster_' + n, out, a, inst)
infusion('infusion_focus_blank_2', 'FOCUSADVANCED@1', 'focus_blank_2', 3, asp(MAGIC=25, ORDER=50), 'focus_blank_1', ['quicksilver', 'gemDiamond', 'quicksilver', 'minecraft:ender_pearl'])
infusion('infusion_focus_blank_3', 'FOCUSGREATER@1', 'focus_blank_3', 5, asp(MAGIC=25, ORDER=50, VOID=100), 'focus_blank_2', ['quicksilver', 'primordial_pearl', 'quicksilver', 'minecraft:nether_star'])
infusion('jar_brain', 'JARBRAIN', 'jar_brain', 4, asp(MIND=25, SENSES=25, UNDEAD=25), 'jar_normal', ['brain', 'minecraft:spider_eye', 'minecraft:water_bucket', 'minecraft:spider_eye'])
infusion('infusion_vis_amulet', 'VISAMULET', 'amulet_vis', 6, asp(AURA=50, ENERGY=100, VOID=50), 'amulet_mundane',
         ['vis_resonator', crystal('aer'), crystal('ignis'), crystal('aqua'), crystal('terra'), crystal('ordo')])
infusion('mirror', 'MIRROR', 'mirror', 1, asp(MOTION=25, DARKNESS=25, EXCHANGE=25), 'mirrored_glass', ['ingotGold', 'ingotGold', 'ingotGold', 'minecraft:ender_pearl'])
infusion('hand_mirror', 'MIRRORHAND', 'hand_mirror', 5, asp(TOOL=50, MOTION=50), 'mirror', ['stickWood', 'minecraft:compass', 'minecraft:map'])
infusion('mirror_essentia', 'MIRRORESSENTIA', 'mirror_essentia', 2, asp(MOTION=25, WATER=25, EXCHANGE=25), 'mirrored_glass', ['ingotIron', 'ingotIron', 'ingotIron', 'minecraft:ender_pearl'])
for tool, a1, a2, aspects in [('axe', 'aqua', 'aqua', asp(WATER=60, PLANT=30)), ('pick', 'ignis', 'ignis', asp(FIRE=30, METAL=30, SENSES=30)),
                              ('sword', 'aer', 'aer', asp(AIR=30, MOTION=30, AVERSION=30)), ('shovel', 'terra', 'terra', asp(EARTH=60, CRAFT=30)),
                              ('hoe', 'ordo', 'perditio', asp(ORDER=30, PLANT=30, ENTROPY=30))]:
    infusion('infusion_elemental_' + tool, 'ELEMENTALTOOLS', 'elemental_' + tool, 1, aspects, 'thaumium_' + tool,
             [crystal(a1), crystal(a2), 'nugget_rareearth', 'plank_greatwood'])

def inf_enchant(name, ench, aspects, catalyst, display=None):
    def f():
        d = {'type': 'thaumcraft:infusion_enchantment', 'research': 'INFUSIONENCHANTMENT', 'instability': 4, 'aspects': aspects,
             'components': [I('minecraft:enchanted_book'), ing(catalyst)], 'enchantment': ench}
        if display: d['display'] = {'id': display}
        return d
    add(name, f)
for key, ench, aspects, catalyst, disp in [
        ('burrowing', 'BURROWING', asp(SENSES=80, EARTH=150), 'minecraft:rabbit_foot', 'minecraft:wooden_pickaxe'),
        ('collector', 'COLLECTOR', asp(DESIRE=80, WATER=100), 'minecraft:lead', 'minecraft:stone_axe'),
        ('destructive', 'DESTRUCTIVE', asp(AVERSION=200, ENTROPY=250), 'minecraft:tnt', 'minecraft:stone_pickaxe'),
        ('refining', 'REFINING', asp(ORDER=80, EXCHANGE=60), 'salis_mundus', 'minecraft:iron_pickaxe'),
        ('sounding', 'SOUNDING', asp(SENSES=40, FIRE=60), 'minecraft:map', 'minecraft:golden_pickaxe'),
        ('arcing', 'ARCING', asp(ENERGY=40, AIR=60), 'minecraft:redstone_block', 'minecraft:wooden_sword'),
        ('essence', 'ESSENCE', asp(BEAST=40, FLUX=60), 'crystal_essence', 'minecraft:stone_sword'),
        ('lamplight', 'LAMPLIGHT', asp(LIGHT=80, AIR=20), 'nitor', 'minecraft:golden_pickaxe')]:
    inf_enchant('infusion_enchant_' + key, ench, aspects, catalyst)
    inf_enchant('infusion_enchant_' + key + '_fake', ench, aspects, catalyst, disp)
infusion('traveller_boots', 'BOOTSTRAVELLER', 'traveller_boots', 1, asp(FLIGHT=100, MOTION=100), 'minecraft:leather_boots',
         [crystal('aer'), crystal('aer'), 'fabric', 'fabric', 'minecraft:feather', T('minecraft:fishes')])
infusion('mind_biothaumic', 'MINDBIOTHAUMIC', 'mind_biothaumic', 4, asp(MIND=50, MECHANISM=25), 'mind_clockwork', ['brain', 'mechanism_complex'])
infusion('turret_placer_bore', 'ARCANEBORE', 'turret_placer_bore', 4, asp(ENERGY=25, EARTH=25, MECHANISM=100, VOID=25, MOTION=25), 'turret_placer_basic',
         ['plank_greatwood', 'plank_greatwood', 'mechanism_complex', 'plateBrass', 'minecraft:diamond_pickaxe', 'minecraft:diamond_shovel', 'morphic_resonator', 'nugget_rareearth'])
infusion('infusion_lamp_growth', 'LAMPGROWTH', 'lamp_growth', 4, asp(PLANT=20, LIGHT=15, LIFE=15, TOOL=15), 'lamp_arcane',
         ['minecraft:gold_ingot', 'minecraft:bone_meal', crystal('terra'), 'minecraft:gold_ingot', 'minecraft:bone_meal', crystal('terra')])
infusion('infusion_lamp_fertility', 'LAMPFERTILITY', 'lamp_fertility', 4, asp(BEAST=20, LIGHT=15, LIFE=15, DESIRE=15), 'lamp_arcane',
         ['minecraft:gold_ingot', 'minecraft:wheat', crystal('ignis'), 'minecraft:gold_ingot', 'minecraft:carrot', crystal('ignis')])
infusion('infusion_fortress_helm', 'ARMORFORTRESS', 'fortress_helm', 3, asp(METAL=50, PROTECT=20, ENERGY=25), 'thaumium_helm',
         ['plateThaumium', 'plateThaumium', 'minecraft:gold_ingot', 'minecraft:gold_ingot', 'minecraft:emerald'])
infusion('infusion_fortress_chest', 'ARMORFORTRESS', 'fortress_chest', 3, asp(METAL=50, PROTECT=30, ENERGY=25), 'thaumium_chest',
         ['plateThaumium', 'plateThaumium', 'plateThaumium', 'plateThaumium', 'minecraft:gold_ingot', 'leather'])
infusion('infusion_fortress_legs', 'ARMORFORTRESS', 'fortress_legs', 3, asp(METAL=50, PROTECT=25, ENERGY=25), 'thaumium_legs',
         ['plateThaumium', 'plateThaumium', 'plateThaumium', 'minecraft:gold_ingot', 'leather'])
infusion('infusion_void_robe_helm', 'VOIDROBEARMOR', 'void_robe_helm', 6, asp(METAL=25, SENSES=25, PROTECT=25, ENERGY=25, ELDRITCH=25, VOID=25), 'void_helm',
         ['goggles', 'fabric', 'fabric', 'salis_mundus', 'fabric', 'fabric'])
infusion('infusion_void_robe_chest', 'VOIDROBEARMOR', 'void_robe_chest', 6, asp(METAL=35, PROTECT=35, ENERGY=25, ELDRITCH=25, VOID=35), 'void_chest',
         ['cloth_chest', 'plateVoid', 'plateVoid', 'salis_mundus', 'fabric', 'leather'])
infusion('infusion_void_robe_legs', 'VOIDROBEARMOR', 'void_robe_legs', 6, asp(METAL=30, PROTECT=30, ENERGY=25, ELDRITCH=25, VOID=30), 'void_legs',
         ['cloth_legs', 'plateVoid', 'plateVoid', 'salis_mundus', 'fabric', 'leather'])
def modifier(name, comps_components, inst, aspects, comps):
    add(name, lambda: {'type': 'thaumcraft:infusion_modifier', 'research': 'FORTRESSMASK', 'instability': inst, 'aspects': aspects,
                       'central': I('fortress_helm'), 'components': [ing(c) for c in comps], 'result': result('fortress_helm', 1, comps_components)})
modifier('infusion_fortress_helm_goggles', {'thaumcraft:fortress_goggles': True}, 5, asp(SENSES=40, AURA=20, PROTECT=20), ['minecraft:slime_ball', 'goggles'])
modifier('infusion_fortress_mask_devil', {'thaumcraft:fortress_mask': 0}, 8, asp(MIND=80, LIFE=80, PROTECT=20),
         ['minecraft:ink_sac', 'plateIron', 'leather', 'shimmerleaf', 'brain', 'plateIron'])
modifier('infusion_fortress_mask_ghost', {'thaumcraft:fortress_mask': 1}, 8, asp(ENTROPY=80, DEATH=80, PROTECT=20),
         ['minecraft:bone_meal', 'plateIron', 'leather', 'minecraft:poisonous_potato', 'minecraft:wither_skeleton_skull', 'plateIron'])
modifier('infusion_fortress_mask_fiend', {'thaumcraft:fortress_mask': 2}, 8, asp(UNDEAD=80, LIFE=80, PROTECT=20),
         ['minecraft:red_dye', 'plateIron', 'leather', 'minecraft:ghast_tear', 'minecraft:milk_bucket', 'plateIron'])
infusion('primal_crusher', 'PRIMALCRUSHER', 'primal_crusher', 6, asp(EARTH=75, TOOL=75, ENTROPY=50, VOID=50, AVERSION=50, ELDRITCH=50, DESIRE=50), 'primordial_pearl',
         ['void_pick', 'void_shovel', 'elemental_pick', 'elemental_shovel'])
infusion('infusion_verdant_heart', 'VERDANTCHARMS', 'verdant_charm', 5, asp(LIFE=60, ORDER=30, PLANT=60), 'amulet_fancy',
         ['nugget_rareearth', crystal('victus'), 'minecraft:milk_bucket', crystal('herba')])
def inf_potion(name, out, aspects, first, c1, potion, c2):
    add(name, lambda: {'type': 'thaumcraft:infusion_potion', 'research': 'VERDANTCHARMS', 'instability': 5, 'aspects': aspects,
                       'central': I('verdant_charm'), 'components': [ing(first), crystal(c1), I('minecraft:potion'), crystal(c2)],
                       'result': result(out), 'potion': potion})
inf_potion('infusion_verdant_heart_life', 'verdant_charm_life', asp(LIFE=80, MAN=80), 'minecraft:golden_apple', 'victus', 'minecraft:strong_healing', 'humanus')
inf_potion('infusion_verdant_heart_sustain', 'verdant_charm_sustain', asp(DESIRE=80, AIR=80), 'triple_meat_treat', 'desiderium', 'minecraft:strong_regeneration', 'aer')
infusion('infusion_cloud_ring', 'CLOUDRING', 'cloud_ring', 1, asp(AIR=50), 'ring_mundane', [crystal('aer'), 'minecraft:feather'])
infusion('infusion_curiosity_band', 'CURIOSITYBAND', 'curiosity_band', 5, asp(MIND=150, VOID=50, TRAP=100), 'girdle_fancy',
         ['minecraft:emerald', 'minecraft:writable_book'] * 4)
infusion('infusion_charm_undying', 'CHARMUNDYING', 'charm_undying', 2, asp(LIFE=25), 'minecraft:totem_of_undying', ['plateBrass'])
infusion('infusion_causality_collapser', 'RIFTCLOSER', 'causality_collapser', 8, asp(ELDRITCH=50, FLUX=50), 'minecraft:tnt',
         ['morphic_resonator', 'minecraft:redstone_block', 'alumentum', 'nitor', 'vis_resonator', 'minecraft:redstone_block', 'alumentum', 'nitor'])
infusion('void_siphon', 'VOIDSIPHON', 'void_siphon', 7, asp(ELDRITCH=50, ENTROPY=50, VOID=100, CRAFT=50), 'metal_block_void',
         ['stone_arcane', 'stone_arcane', 'mechanism_complex', 'plateBrass', 'plateBrass', 'minecraft:nether_star'])
infusion('infusion_voidseer_pearl', 'VOIDSEERPEARL', 'voidseer_charm', 8, asp(MIND=150, VOID=150, MAGIC=100), 'amulet_fancy',
         ['brain', 'void_seed', 'brain', 'primordial_pearl'])

# ---------------------------------------------------------------- crafting (initializeNormalRecipes)
shaped('nugget_thaumium', 'nugget_thaumium', ['#'], {'#': 'ingot_thaumium'}, count=9)
shaped('nugget_brass', 'nugget_brass', ['#'], {'#': 'ingot_brass'}, count=9)
shaped('nugget_quartz', 'nugget_quartz', ['#'], {'#': 'minecraft:quartz'}, count=9)
shaped('nugget_quicksilver', 'nugget_quicksilver', ['#'], {'#': 'quicksilver'}, count=9)
shaped('ingot_thaumium_from_nuggets', 'ingot_thaumium', ['###', '###', '###'], {'#': 'nuggetThaumium'})
shaped('ingot_brass_from_nuggets', 'ingot_brass', ['###', '###', '###'], {'#': 'nuggetBrass'})
shaped('quicksilver_from_nuggets', 'quicksilver', ['###', '###', '###'], {'#': 'nuggetQuicksilver'})
shaped('metal_block_thaumium', 'metal_block_thaumium', ['###', '###', '###'], {'#': 'ingot_thaumium'}, group='thaumcraft:thaumium_stuff')
shaped('ingot_thaumium_from_block', 'ingot_thaumium', ['#'], {'#': 'metal_block_thaumium'}, count=9, group='thaumcraft:thaumium_stuff')
shaped('metal_block_void', 'metal_block_void', ['###', '###', '###'], {'#': 'ingot_void'}, group='thaumcraft:void_stuff')
shaped('ingot_void_from_block', 'ingot_void', ['#'], {'#': 'metal_block_void'}, count=9, group='thaumcraft:void_stuff')
shaped('metal_block_brass', 'metal_block_brass', ['###', '###', '###'], {'#': 'ingot_brass'}, group='thaumcraft:brass_stuff')
shaped('ingot_brass_from_block', 'ingot_brass', ['#'], {'#': 'metal_block_brass'}, count=9, group='thaumcraft:brass_stuff')
shaped('flesh_block', 'flesh_block', ['###', '###', '###'], {'#': 'minecraft:rotten_flesh'})
shaped('rotten_flesh_from_block', 'minecraft:rotten_flesh', ['#'], {'#': 'flesh_block'}, count=9)
shaped('amber_block', 'amber_block', ['##', '##'], {'#': 'gemAmber'})
shaped('amber_brick', 'amber_brick', ['##', '##'], {'#': 'amber_block'}, count=4)
shaped('amber_brick_to_block', 'amber_block', ['##', '##'], {'#': 'amber_brick'}, count=4)
shaped('amber_block_to_amber', 'amber', ['#'], {'#': 'amber_block'}, count=4)
shaped('plate_iron', 'plate_iron', ['BBB'], {'B': 'ingotIron'}, count=3)
shaped('plate_brass', 'plate_brass', ['BBB'], {'B': 'ingotBrass'}, count=3, group='thaumcraft:brass_stuff')
shaped('plate_thaumium', 'plate_thaumium', ['BBB'], {'B': 'ingotThaumium'}, count=3, group='thaumcraft:thaumium_stuff')
shaped('plate_void', 'plate_void', ['BBB'], {'B': 'ingotVoid'}, count=3, group='thaumcraft:void_stuff')
for metal in ['thaumium', 'void']:
    g = 'thaumcraft:%s_stuff' % metal
    ingot = 'ingot' + metal.capitalize()
    shaped(metal + '_helm', metal + '_helm', ['III', 'I I'], {'I': ingot}, group=g)
    shaped(metal + '_chest', metal + '_chest', ['I I', 'III', 'III'], {'I': ingot}, group=g)
    shaped(metal + '_legs', metal + '_legs', ['III', 'I I', 'I I'], {'I': ingot}, group=g)
    shaped(metal + '_boots', metal + '_boots', ['I I', 'I I'], {'I': ingot}, group=g)
    shaped(metal + '_shovel', metal + '_shovel', ['I', 'S', 'S'], {'I': ingot, 'S': 'stickWood'}, group=g)
    shaped(metal + '_pick', metal + '_pick', ['III', ' S ', ' S '], {'I': ingot, 'S': 'stickWood'}, group=g)
    shaped(metal + '_axe', metal + '_axe', ['II', 'SI', 'S '], {'I': ingot, 'S': 'stickWood'}, group=g)
    shaped(metal + '_hoe', metal + '_hoe', ['II', 'S ', 'S '], {'I': ingot, 'S': 'stickWood'}, group=g)
    shaped(metal + '_sword', metal + '_sword', ['I', 'I', 'S'], {'I': ingot, 'S': 'stickWood'}, group=g)
bg = 'thaumcraft:baubles_stuff'
shaped('amulet_mundane', 'amulet_mundane', [' S ', 'S S', ' I '], {'S': 'string', 'I': 'ingotBrass'}, group=bg)
shaped('ring_mundane', 'ring_mundane', ['NNN', 'N N', 'NNN'], {'N': 'nuggetBrass'}, group=bg)
shaped('girdle_mundane', 'girdle_mundane', [' L ', 'L L', ' I '], {'L': 'leather', 'I': 'ingotBrass'}, group=bg)
shaped('amulet_fancy', 'amulet_fancy', [' S ', 'SGS', ' I '], {'S': 'string', 'G': 'gemDiamond', 'I': 'ingotGold'}, group=bg)
shaped('ring_fancy', 'ring_fancy', ['NGN', 'N N', 'NNN'], {'G': 'gemDiamond', 'N': 'nuggetGold'}, group=bg)
shaped('girdle_fancy', 'girdle_fancy', [' L ', 'LGL', ' I '], {'L': 'leather', 'G': 'gemDiamond', 'I': 'ingotGold'}, group=bg)
shaped('shimmerleaf_to_quicksilver', 'quicksilver', ['#'], {'#': 'shimmerleaf'})
shaped('cinderpearl_to_blaze_powder', 'minecraft:blaze_powder', ['#'], {'#': 'cinderpearl'})
shapeless('label', 'label', ['dyeBlack', 'slimeball', 'minecraft:paper', 'minecraft:paper', 'minecraft:paper', 'minecraft:paper'], count=4, group='thaumcraft:jarlabels')
shaped('plank_greatwood', 'plank_greatwood', ['W'], {'W': 'log_greatwood'}, count=4)
shaped('plank_silverwood', 'plank_silverwood', ['W'], {'W': 'log_silverwood'}, count=4)
shaped('greatwood_stairs', 'greatwood_stairs', ['K  ', 'KK ', 'KKK'], {'K': 'plank_greatwood'}, count=4)
shaped('silverwood_stairs', 'silverwood_stairs', ['K  ', 'KK ', 'KKK'], {'K': 'plank_silverwood'}, count=4)
shaped('stone_arcane_stairs', 'stone_arcane_stairs', ['K  ', 'KK ', 'KKK'], {'K': 'stone_arcane'}, count=4)
shaped('stone_arcane_brick_stairs', 'stone_arcane_brick_stairs', ['K  ', 'KK ', 'KKK'], {'K': 'stone_arcane_brick'}, count=4)
shaped('stairs_ancient', 'stairs_ancient', ['K  ', 'KK ', 'KKK'], {'K': 'stone_ancient'}, count=4)
shaped('stone_arcane', 'stone_arcane', ['KKK', 'KCK', 'KKK'], {'K': 'stone', 'C': 'crystal_essence'}, count=9)
shaped('stone_arcane_brick', 'stone_arcane_brick', ['KK', 'KK'], {'K': 'stone_arcane'}, count=4)
shaped('greatwood_slab', 'greatwood_slab', ['KKK'], {'K': 'plank_greatwood'}, count=6)
shaped('silverwood_slab', 'silverwood_slab', ['KKK'], {'K': 'plank_silverwood'}, count=6)
shaped('stone_arcane_slab', 'stone_arcane_slab', ['KKK'], {'K': 'stone_arcane'}, count=6)
shaped('stone_arcane_brick_slab', 'stone_arcane_brick_slab', ['KKK'], {'K': 'stone_arcane_brick'}, count=6)
shaped('slab_ancient', 'slab_ancient', ['KKK'], {'K': 'stone_ancient'}, count=6)
shaped('slab_eldritch', 'slab_eldritch', ['KKK'], {'K': 'stone_eldritch_tile'}, count=6)
shaped('phial', 'phial', [' C ', 'G G', ' G '], {'G': 'blockGlass', 'C': 'minecraft:clay_ball'}, count=8)
shaped('table_wood', 'table_wood', ['SSS', 'W W'], {'S': 'slabWood', 'W': 'plankWood'})
shaped('table_stone', 'table_stone', ['SSS', 'W W'], {'S': 'minecraft:stone_slab', 'W': 'stone'})
shapeless('scribing_tools_craft1', 'scribing_tools', ['phial', 'minecraft:feather', 'dyeBlack'], group='thaumcraft:inkwell')
shapeless('scribing_tools_craft2', 'scribing_tools', ['minecraft:glass_bottle', 'minecraft:feather', 'dyeBlack'], group='thaumcraft:inkwell')
shaped('golem_bell', 'golem_bell', [' QQ', ' QQ', 'S  '], {'S': 'stickWood', 'Q': 'gemQuartz'})
shaped('candle_white', 'candle_white', [' S ', ' T ', ' T '], {'S': 'string', 'T': 'tallow'}, count=3, group='thaumcraft:tallowcandles')
for c in COLOURS:
    shapeless('candle_white_dye' if c == 'white' else 'candle_' + c, 'candle_' + c, [T('c:dyes/' + vc(c)), T('thaumcraft:candles')], group='thaumcraft:tallowcandles')
add('salis_mundus', lambda: {'type': 'thaumcraft:magic_dust', 'category': 'misc'})
def display(name, out, ings):
    add(name, lambda: {'type': 'thaumcraft:display_shapeless', 'category': 'misc', 'ingredients': [ing(i) for i in ings], 'result': result(out)})
display('salis_mundus_display', 'salis_mundus', ['minecraft:flint', 'minecraft:bowl', 'minecraft:redstone', 'crystal_essence', 'crystal_essence', 'crystal_essence'])
display('triple_meat_treat_display', 'triple_meat_treat', [T('thaumcraft:meat_chunks'), T('thaumcraft:meat_chunks'), T('thaumcraft:meat_chunks'), 'minecraft:sugar'])
shaped('jar_brace', 'jar_brace', ['NSN', 'S S', 'NSN'], {'N': 'nuggetBrass', 'S': 'stickWood'}, count=2)

if __name__ == '__main__':
    write = '--write' in sys.argv
    changed = new = same = 0
    for name, d in sorted(R.items()):
        path = os.path.join(OUT, name + '.json')
        text = json.dumps(d, indent=2) + '\n'
        if os.path.exists(path):
            old = json.load(open(path, encoding='utf-8'))
            if old == d:
                same += 1
                continue
            changed += 1
            print('CHANGED', name)
        else:
            new += 1
            print('NEW', name)
        if write:
            open(path, 'w', encoding='utf-8', newline='\n').write(text)
    print('same', same, 'changed', changed, 'new', new)
    for s in SKIPPED: print('SKIPPED', s)
    existing = {os.path.basename(p)[:-5] for p in os.listdir(OUT)}
    print('PORT-ONLY (not in original):', sorted(n[:-5] if n.endswith('.json') else n for n in existing - set(R)))
