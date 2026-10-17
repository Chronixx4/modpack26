"""Belohnungen pro Kapitel: passende Items der jeweiligen Mod plus mehr XP.

POOLS[kapitel] = Liste (id, anzahl), sortiert von frueh nach spaet. Quests am Anfang eines
Kapitels bekommen Items vom Listenanfang, Quests am Ende die vom Listenende. Nur Items, die in
den Quests derselben Mod schon vorkommen (also verifiziert sind), plus Vanilla-Items.
Kapitel ohne Pool bekommen nur die XP-Anhebung.

anwenden(kapitel_liste, tabellen) schreibt die Belohnungen der Quests um und haengt
pro Kapitel und Stufe eine eigene Loot-Kiste an die Tabellen (z. B. 'create_1_loot2').
"""
import hashlib
import re

XP = {1: 3, 2: 5, 3: 8}                      # frueher 1/2/3 Level
KISTE_XP = {'loot1': 5, 'loot2': 8, 'loot3': 12, 'loot4': 20}
# Ausschnitt des Pools pro Kiste (von, bis) und Mengenfaktor
KISTE_FENSTER = {'loot1': (0.0, 0.5, 1), 'loot2': (0.2, 0.75, 1.5), 'loot3': (0.45, 1.0, 2), 'loot4': (0.65, 1.0, 3)}

P = {}


def pool(kap, text):
    liste = []
    for zeile in text.strip().splitlines():
        for e in zeile.split(','):
            e = e.strip()
            if e:
                i, n = e.rsplit(' ', 1)
                liste.append((i if ':' in i else 'minecraft:' + i, int(n)))
    P[kap] = liste


pool('willkommen', 'torch 32, bread 16, cooked_beef 12, iron_ingot 8, experience_bottle 8, golden_carrot 8')
pool('erste_schritte', '''coal 16, copper_ingot 16, iron_ingot 12, gold_ingot 6, redstone 16, lapis_lazuli 8,
    experience_bottle 8, obsidian 8, diamond 3, ender_pearl 4, golden_apple 2, netherite_scrap 2, netherite_ingot 1''')
pool('lager', '''pipez:item_pipe 8, storagedrawers:upgrade_template 4, sophisticatedbackpacks:upgrade_base 2,
    sophisticatedstorage:upgrade_base 2, pipez:fluid_pipe 8, pipez:energy_pipe 8, pipez:basic_upgrade 2,
    sophisticatedstorage:hopper_upgrade 1, sophisticatedbackpacks:pickup_upgrade 1, sophisticatedbackpacks:feeding_upgrade 1,
    storagedrawers:iron_storage_upgrade 1, sophisticatedbackpacks:magnet_upgrade 1, storagedrawers:diamond_storage_upgrade 1''')
pool('essen', '''farmersdelight:tomato 8, farmersdelight:onion 8, farmersdelight:rice 16, aquaculture:worm 16, tide:bait 16,
    farmersdelight:cooked_rice 8, farmersdelight:beef_stew 4, farmersdelight:hamburger 4, aquaculture:fish_fillet_cooked 12,
    farmersdelight:pasta_with_meatballs 4, garnished:nut_mix 4, create_factory:waffle 4, aquaculture:sushi 6,
    farmersdelight:roast_chicken_block 1, minecraft:golden_apple 2''')
pool('create_1', '''create:andesite_alloy 16, create:shaft 16, create:cogwheel 8, create:iron_sheet 8, create:copper_sheet 8,
    create:zinc_ingot 8, create:fluid_pipe 8, create:andesite_casing 4, create:brass_ingot 8, create:copper_casing 4,
    create:electron_tube 4, create:brass_casing 4, create:large_cogwheel 4, create:golden_sheet 8''')
pool('create_2', '''create:brass_ingot 16, create:cardboard 16, create:brass_funnel 4, create:track 16, create:sturdy_sheet 4,
    create:redstone_link 2, create:railway_casing 2, create:smart_chute 2, create:chain_conveyor 4, create:super_glue 1,
    create:precision_mechanism 2, create:stock_link 1, create:display_link 1, create:mechanical_arm 1, create:packager 2''')
pool('create_3', '''immersiveengineering:wire_copper 16, createaddition:copper_spool 4, create_new_age:overcharged_iron 4,
    mekanism:ingot_tin 8, mekanism:ingot_bronze 8, immersiveengineering:ingot_steel 8, createbigcannons:powder_charge 8,
    createaddition:capacitor 2, createmetallurgy:tungsten_ingot 4, create_new_age:thorium 4, aeronautics:levitite 4,
    createbigcannons:solid_shot 4, createdieselgenerators:diesel_bucket 1, createteleporters:quantum_mechanism 1,
    create_new_age:overcharged_gold 4''')
pool('tfmg', '''tfmg:cast_iron_ingot 8, tfmg:coal_coke 16, tfmg:fireclay_ball 16, tfmg:limesand 16, tfmg:fireproof_bricks 16,
    immersiveengineering:ingot_steel 8, immersiveengineering:ingot_lead 8, immersiveengineering:ingot_nickel 8,
    tfmg:copper_spool 4, immersiveengineering:ingot_aluminum 8, tfmg:plastic_sheet 4, tfmg:rubber_sheet 4,
    tfmg:silicon_ingot 4, tfmg:magnet 2, tfmg:steel_mechanism 2, tfmg:circuit_board 2, tfmg:diesel_bucket 1''')
pool('ae2', '''ae2:certus_quartz_crystal 16, ae2:silicon 8, ae2:charged_certus_quartz_crystal 8, ae2:fluix_crystal 8,
    ae2:blank_pattern 8, ae2:logic_processor 4, ae2:calculation_processor 4, ae2:engineering_processor 4,
    ae2:item_storage_cell_1k 1, ae2:energy_cell 1, ae2:fluix_pearl 2, ae2:item_storage_cell_4k 1,
    ae2:item_storage_cell_16k 1, ae2:dense_energy_cell 1, ae2:item_storage_cell_64k 1''')
pool('ae2_addons', '''appflux:insulating_resin 8, appflux:redstone_crystal 8, extendedae:entro_crystal 4, megacells:sky_steel_ingot 8,
    megacells:sky_bronze_ingot 8, extendedae:concurrent_processor 2, megacells:accumulation_processor 2,
    advanced_ae:quantum_alloy 2, advanced_ae:quantum_processor 2, megacells:item_storage_cell_1m 1''')
pool('refined_storage', '''refinedstorage:quartz_enriched_iron 8, refinedstorage:silicon 8, refinedstorage:cable 16,
    refinedstorage:basic_processor 4, refinedstorage:1k_storage_disk 1, refinedstorage:improved_processor 4,
    refinedstorage:16k_storage_disk 1, refinedstorage:advanced_processor 2, refinedstorage:64k_storage_disk 1''')
pool('mekanism', '''mekanism:ingot_osmium 8, mekanism:alloy_infused 8, mekanism:basic_control_circuit 4, mekanism:bio_fuel 16,
    mekanism:advanced_control_circuit 2, mekanism:alloy_reinforced 4, mekanism:ingot_refined_obsidian 4,
    mekanism:ingot_refined_glowstone 4, mekanism:hdpe_sheet 4, mekanism:upgrade_speed 1, mekanism:upgrade_energy 1,
    mekanism:elite_control_circuit 2, mekanism:yellow_cake_uranium 4, mekanism:alloy_atomic 2,
    mekanism:ultimate_control_circuit 1''')
pool('immersive', '''immersiveengineering:coal_coke 16, immersiveengineering:ingot_electrum 8, immersiveengineering:ingot_constantan 8,
    immersiveengineering:wirecoil_copper 8, immersiveengineering:connector_lv 4, immersiveengineering:ingot_steel 8,
    immersiveengineering:capacitor_lv 1, immersiveengineering:wirecoil_electrum 8, immersiveengineering:bullet_casull 8,
    immersiveengineering:wirecoil_steel 8, immersiveengineering:capacitor_mv 1''')
pool('powah_reaktoren', '''powah:dielectric_paste 16, powah:uraninite 8, bigreactors:graphite_ingot 8, bigreactors:yellorium_ingot 8,
    powah:energizing_orb 1, powah:steel_energized 4, bigreactors:cyanite_ingot 4, powah:crystal_blazing 2,
    powah:crystal_niotic 2, powah:aerial_pearl 1, powah:crystal_spirited 2, powah:ender_core 1, powah:crystal_nitro 1''')
pool('industrial_foregoing', '''industrialforegoing:iron_gear 4, industrialforegoing:dryrubber 8, industrialforegoing:plastic 8,
    industrialforegoing:gold_gear 4, industrialforegoing:range_addon_tier_1 1, industrialforegoing:speed_addon_tier_1 1,
    industrialforegoing:efficiency_addon_tier_1 1, industrialforegoing:diamond_gear 2, industrialforegoing:pink_slime_bucket 1''')
pool('enderio', '''enderio:silicon 8, enderio:grains_of_infinity 16, enderio:conductive_alloy_ingot 8,
    enderio:redstone_alloy_ingot 8, enderio:energetic_alloy_ingot 4, enderio:vibrant_alloy_ingot 4,
    enderio:pulsating_alloy_ingot 4, enderio:soularium_ingot 4, enderio:dark_steel_ingot 4, enderio:pulsating_crystal 2,
    enderio:vibrant_crystal 2, enderio:end_steel_ingot 2, enderio:ender_crystal 1''')
pool('oritech', '''immersiveengineering:ingot_nickel 8, oritech:fluxite 8, oritech:plastic_sheet 8, oritech:platinum_ingot 4,
    oritech:magnetic_coil 2, oritech:enderic_compound 4, oritech:energite_ingot 4, oritech:processing_unit 2,
    oritech:duratium_ingot 4, oritech:superconductor 2, oritech:prometheum_ingot 2''')
pool('kleine_technik', '''actuallyadditions:black_quartz 8, actuallyadditions:canola_seeds 8, actuallyadditions:restonia_crystal 4,
    actuallyadditions:palis_crystal 4, actuallyadditions:enori_crystal 4, actuallyadditions:void_crystal 4,
    integrateddynamics:crystalized_menril_chunk 8, ironjetpacks:strap 4, ironjetpacks:basic_coil 2,
    ironjetpacks:thruster 2, ironjetpacks:advanced_coil 2, actuallyadditions:empowered_restonia_crystal 2,
    ironjetpacks:elite_coil 1''')
pool('draconic', '''draconicevolution:draconium_dust 8, draconicevolution:draconium_ingot 4, draconicevolution:draconium_core 2,
    draconicevolution:wyvern_core 1, draconicevolution:awakened_draconium_ingot 2, draconicevolution:awakened_core 1,
    draconicevolution:chaos_shard 1, draconicevolution:chaotic_core 1''')
pool('ars_nouveau', '''ars_nouveau:source_gem 16, ars_nouveau:magebloom_fiber 16, ars_nouveau:blank_parchment 16,
    ars_nouveau:archwood_planks 32, ars_nouveau:fire_essence 4, ars_nouveau:water_essence 4, ars_nouveau:earth_essence 4,
    ars_nouveau:air_essence 4, ars_nouveau:blank_thread 4, ars_nouveau:manipulation_essence 4,
    ars_nouveau:abjuration_essence 4, ars_nouveau:conjuration_essence 4, ars_nouveau:source_gem 32''')
pool('ars_addons', '''ars_nouveau:source_gem 16, ars_nouveau:magebloom_fiber 16, ars_nouveau:blank_thread 4, ars_nouveau:fire_essence 4,
    ars_nouveau:water_essence 4, ars_nouveau:earth_essence 4, ars_nouveau:air_essence 4, ars_nouveau:abjuration_essence 4,
    ars_nouveau:conjuration_essence 4, ars_technica:calibrated_precision_mechanism 1, ars_nouveau:source_gem 32''')
pool('irons_spells', '''irons_spellbooks:arcane_essence 8, irons_spellbooks:common_ink 8, irons_spellbooks:blank_rune 4,
    irons_spellbooks:uncommon_ink 6, irons_spellbooks:magic_cloth 4, irons_spellbooks:mithril_scrap 4,
    irons_spellbooks:cinder_essence 4, irons_spellbooks:lightning_bottle 4, irons_jewelry:ruby 2,
    irons_jewelry:sapphire 2, irons_jewelry:topaz 2, irons_spellbooks:rare_ink 4, irons_spellbooks:arcane_ingot 2,
    irons_spellbooks:epic_ink 2, irons_spellbooks:legendary_ink 1, irons_spellbooks:upgrade_orb 1''')
pool('occultism', '''occultism:datura_seeds 8, occultism:otherstone 16, occultism:chalk_white 4, immersiveengineering:ingot_silver 8,
    occultism:spirit_attuned_gem 4, occultism:purified_ink 4, occultism:awakened_feather 2, occultism:iesnium_ingot 4,
    occultism:fragile_soul_gem 2, occultism:afrit_essence 2, occultism:marid_essence 2''')
pool('evilcraft', 'evilcraft:dark_gem 8, evilcraft:hardened_blood_shard 4, evilcraft:dark_power_gem 2')
pool('goety', '''goety:ectoplasm 8, goety:magic_emerald 4, goety:dark_fabric 4, goety:cursed_ingot 4, goety:magic_fabric 4,
    goety:spirit_fabric 4, goety:dark_ingot 4, goety:unholy_blood 4, goety:soul_emerald 2, goety:void_shard 2,
    goety:soul_ruby 1''')
pool('forbidden_hexerei', '''hexerei:sage_seed 8, hexerei:mandrake_root 8, hexerei:belladonna_flowers 8, hexerei:mugwort_flowers 8,
    forbidden_arcanus:arcane_crystal_dust 16, forbidden_arcanus:arcane_crystal 8, hexerei:dried_sage_bundle 4,
    forbidden_arcanus:mundabitur_dust 8, forbidden_arcanus:deorum_ingot 4, forbidden_arcanus:obsidiansteel_ingot 2''')
pool('apotheosis', '''minecraft:lapis_lazuli 32, apotheosis:gem_dust 16, apothic_enchanting:scrap_tome 4,
    apotheosis:mysterious_scrap_metal 4, apotheosis:timeworn_fabric 4, apotheosis:luminous_crystal_shard 4,
    apothic_enchanting:infused_breath 2, apotheosis:gem_fused_slate 1, apotheosis:sigil_of_socketing 1,
    apotheosis:sigil_of_enhancement 1, apotheosis:godforged_pearl 1''')
pool('mystical_agriculture', '''mysticalagriculture:inferium_essence 32, mysticalagriculture:inferium_seeds 8,
    mysticalagriculture:mystical_fertilizer 8, mysticalagriculture:iron_seeds 4, mysticalagriculture:prosperity_shard 16,
    mysticalagriculture:prudentium_essence 24, mysticalagriculture:soulium_dust 8, mysticalagriculture:diamond_seeds 2,
    mysticalagriculture:tertium_essence 16, mysticalagriculture:prosperity_ingot 4, mysticalagriculture:imperium_essence 16,
    mysticalagriculture:supremium_essence 8, mysticalagradditions:insanium_essence 4''')
pool('bienen', '''minecraft:honeycomb 16, productivebees:honey_treat 8, productivebees:bee_cage 4, the_bumblezone:pollen_puff 8,
    the_bumblezone:bee_bread 8, the_bumblezone:honey_crystal_shards 8, the_bumblezone:royal_jelly_bottle 2,
    the_bumblezone:essence_of_the_bees 1''')
pool('dimensionen', '''minecraft:ender_pearl 8, minecraft:blaze_rod 8, aether:ambrosium_shard 16, undergarden:cloggrum_ingot 8,
    betternether:cincinnasite_ingot 8, alexscaves:amber 8, enderscape:nebulite 8, aether:zanite_gemstone 8,
    deep_aether:skyjade 8, betterend:terminite_ingot 8, ad_astra:desh_ingot 8, alexscaves:sea_glass_shards 8,
    undergarden:froststeel_ingot 4, betternether:nether_ruby 4, alexscaves:uranium_shard 4, enderscape:shadoline_ingot 4,
    ad_astra:ostrum_ingot 6, alexscaves:scarlet_neodymium_ingot 4, alexscaves:azure_neodymium_ingot 4,
    undergarden:utherium_crystal 4, ad_astra:calorite_ingot 4, betterend:aeternium_ingot 2, undergarden:regalium_crystal 2''')
pool('twilight', '''twilightforest:ironwood_ingot 8, twilightforest:steeleaf_ingot 8, twilightforest:meef_stroganoff 8,
    twilightforest:naga_scale 8, twilightforest:carminite 8, twilightforest:ice_bomb 8, twilightforest:fiery_ingot 4,
    twilightforest:knightmetal_ingot 6, twilightforest:fiery_blood 2, twilightforest:magic_map 1''')
pool('strukturen', '''minecraft:emerald 8, minecraft:gold_ingot 8, minecraft:experience_bottle 8, minecraft:name_tag 2,
    minecraft:ender_pearl 4, minecraft:diamond 3, minecraft:saddle 1, minecraft:golden_apple 2''')
pool('bosse', '''minecraft:experience_bottle 16, minecraft:blaze_rod 8, minecraft:golden_apple 4, minecraft:diamond 4,
    born_in_chaos_v1:dark_metal_ingot 4, minecraft:ender_eye 4, minecraft:netherite_scrap 2, cataclysm:ignitium_ingot 2,
    minecraft:shulker_shell 4, cataclysm:witherite_ingot 2, cataclysm:cursium_ingot 2, minecraft:totem_of_undying 1,
    minecraft:nether_star 1''')
pool('waffen', '''minecraft:iron_ingot 16, minecraft:arrow 64, minecraft:experience_bottle 16, minecraft:gold_ingot 8,
    minecraft:diamond 4, minecraft:golden_apple 2, simplyswords:runic_tablet 1, minecraft:netherite_scrap 2''')
pool('hostility', 'minecraft:experience_bottle 16, l2hostility:bottle_of_sanity 1, minecraft:golden_apple 4, l2hostility:chaos_ingot 1, l2hostility:miracle_ingot 1')
pool('allthemodium', '''allthemodium:raw_allthemodium 4, allthemodium:allthemodium_ingot 2, allthemodium:vibranium_ingot 2,
    allthemodium:allthemodium_upgrade_smithing_template 1''')
pool('finale', '''minecraft:experience_bottle 64, minecraft:golden_apple 8, minecraft:netherite_ingot 4,
    mysticalagradditions:insanium_essence 16, minecraft:nether_star 2, minecraft:enchanted_golden_apple 2''')


def _h(*teile):
    return int(hashlib.sha256('/'.join(teile).encode()).hexdigest()[:8], 16)


def _kistenstufe(r):
    return r if isinstance(r, str) and r in KISTE_XP else None


def anwenden(kapitel_liste, tabellen):
    basis = {t['key']: t for t in tabellen}
    neu = []
    benutzt = set()
    for k in kapitel_liste:
        pl = P.get(k['datei'])
        n = len(k['quests'])
        for idx, q in enumerate(k['quests']):
            alt = q['belohnung'] if isinstance(q['belohnung'], list) else [q['belohnung']]
            res = []
            hat_item = any(isinstance(r, tuple) for r in alt)
            for r in alt:
                if isinstance(r, int):
                    res.append(XP.get(r, r * 3))
                elif _kistenstufe(r):
                    if not any(isinstance(x, int) for x in alt):
                        res.append(KISTE_XP[r])
                    if pl:
                        benutzt.add((k['datei'], r))
                        res.append(f"{k['datei']}_{r}")
                    else:
                        res.append(r)
                else:
                    res.append(r)
            # normale Quests: zusaetzlich ein Item aus dem Kapitel-Pool
            if pl and not hat_item and not any(_kistenstufe(r) for r in alt) and any(isinstance(r, int) for r in alt):
                stufe = max(r for r in alt if isinstance(r, int))
                pos = idx / max(n - 1, 1)
                mitte = int(round(pos * (len(pl) - 1)))
                i = min(max(mitte + _h(k['datei'], q['key']) % 3 - 1, 0), len(pl) - 1)
                item, anz = pl[i]
                if stufe == 1 and anz >= 4:
                    anz = max(anz // 2, 2)
                elif stufe == 3 and anz >= 4:
                    anz = min(anz * 3 // 2, 64)
                res.append(('item', item, anz))
            q['belohnung'] = res
    for datei, stufe in sorted(benutzt):
        pl = P[datei]
        von, bis, faktor = KISTE_FENSTER[stufe]
        a = int(len(pl) * von)
        b = max(int(round(len(pl) * bis)), a + 1)
        fenster = pl[a:b]
        b0 = basis[stufe]
        items = []
        for item, anz in fenster:
            m = min(int(anz * faktor), 64) if anz >= 4 else anz
            items.append((item, m, 10))
        items += [(i, c, 2) for i, c, g in b0['items'][:3]]   # etwas Allgemeines dazu
        titel = next(kp['titel'] for kp in kapitel_liste if kp['datei'] == datei)
        neu.append({'key': f'{datei}_{stufe}', 'titel': b0['titel'] + ' (' + re.sub('&.', '', titel) + ')',
                    'farbe': b0['farbe'], 'glow': b0.get('glow', False), 'items': items})
    tabellen.extend(neu)
