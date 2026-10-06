"""Loot-Kisten fuer Belohnungen: 'loot1' (gewoehnlich) bis 'loot4' (legendaer).
items: (id, anzahl, gewicht)
"""
TABELLEN = [
    {'key': 'loot1', 'titel': '&fGewöhnliche Kiste', 'farbe': 0xB0B0B0, 'items': [
        ('minecraft:iron_ingot', 8, 10), ('minecraft:copper_ingot', 16, 8), ('minecraft:gold_ingot', 4, 6),
        ('minecraft:coal', 16, 8), ('minecraft:redstone', 16, 8), ('minecraft:lapis_lazuli', 8, 5),
        ('minecraft:bread', 16, 8), ('minecraft:cooked_beef', 12, 6), ('minecraft:torch', 32, 6),
        ('minecraft:oak_log', 32, 6), ('minecraft:arrow', 32, 4), ('minecraft:bone_meal', 16, 5),
        ('create:andesite_alloy', 16, 6), ('minecraft:experience_bottle', 8, 4), ('waystones:warp_dust', 4, 3),
    ]},
    {'key': 'loot2', 'titel': '&aSeltene Kiste', 'farbe': 0x55FF55, 'items': [
        ('minecraft:diamond', 3, 6), ('minecraft:emerald', 6, 6), ('minecraft:gold_ingot', 12, 8),
        ('minecraft:iron_block', 4, 8), ('create:brass_ingot', 16, 6), ('minecraft:ender_pearl', 8, 6),
        ('minecraft:golden_carrot', 16, 6), ('minecraft:experience_bottle', 16, 6), ('minecraft:name_tag', 2, 3),
        ('mekanism:ingot_osmium', 16, 6), ('ae2:certus_quartz_crystal', 16, 6), ('minecraft:obsidian', 16, 5),
        ('mysticalagriculture:inferium_essence', 32, 6), ('ars_nouveau:source_gem', 8, 5), ('minecraft:saddle', 1, 2),
    ]},
    {'key': 'loot3', 'titel': '&5Epische Kiste', 'farbe': 0xAA00FF, 'glow': True, 'items': [
        ('minecraft:diamond_block', 2, 6), ('minecraft:netherite_scrap', 2, 5), ('minecraft:emerald_block', 3, 5),
        ('minecraft:ender_eye', 8, 5), ('minecraft:golden_apple', 4, 5), ('minecraft:experience_bottle', 32, 6),
        ('minecraft:shulker_shell', 4, 4), ('minecraft:blaze_rod', 8, 5), ('mekanism:alloy_infused', 16, 5),
        ('ae2:fluix_crystal', 16, 5), ('mysticalagriculture:prudentium_essence', 32, 5), ('minecraft:totem_of_undying', 1, 2),
        ('ars_nouveau:source_gem_block', 4, 4),
    ]},
    {'key': 'loot4', 'titel': '&6Legendäre Kiste', 'farbe': 0xFFAA00, 'glow': True, 'items': [
        ('minecraft:netherite_ingot', 2, 5), ('minecraft:enchanted_golden_apple', 1, 4), ('minecraft:nether_star', 1, 3),
        ('minecraft:elytra', 1, 2), ('minecraft:diamond_block', 8, 5), ('minecraft:totem_of_undying', 2, 4),
        ('minecraft:experience_bottle', 64, 5), ('mekanism:alloy_atomic', 8, 4), ('mysticalagriculture:imperium_essence', 32, 4),
        ('minecraft:dragon_breath', 8, 3),
    ]},
]
