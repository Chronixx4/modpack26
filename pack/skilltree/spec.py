"""Spec des Chronicle-Skilltrees (Pufferfish's Skills). Vorlage: die klickbare Vorschau.

Effekt: E(label, einheit, faktor, attribut, operation)  -> Attribut-Reward
        S(label, einheit, faktor, name)                  -> Skript-Wert (Reward 'tag', gelesen von server_scripts/skills.js)
Wirkung = Stufenwert * faktor. Bei einheit '%' wird der Wert fuer das Attribut durch 100 geteilt.
Operationen: addition (Wert wird addiert), multiply_base (Wert * Grundwert), multiply_total.
Attribut-IDs mit 'pruefen=True' sind noch nicht gegen die Registry bestaetigt (siehe PLAN-0.9.16-skilltree.md).
"""
from collections import namedtuple

E = namedtuple('E', 'label einheit faktor attr op pruefen', defaults=(False,))
S = namedtuple('S', 'label einheit faktor name')

STUFEN = ['I', 'II', 'III', 'IV', 'V']
KOSTEN = [1, 1, 1, 2, 2]
GIPFEL_KOSTEN = 3

VA = 'minecraft:generic.'
PL = 'minecraft:player.'
IS = 'irons_spellbooks:'
AP = 'apothic_attributes:'
GO = 'goety:'
SP = 'spell_power:'
AN = 'ars_nouveau:ars_nouveau.perk.'

# Aeste: id, Name, Wurzel-Symbol, Winkel in Grad (wie in der Vorschau)
# wurzel/gipfel: (Name, Text, Symbol, [Effekte]); dort ist der Faktor der feste Wert
# linie: (Name, Symbol, Stufenwerte, Text, [Effekte])
AESTE = [
    dict(id='abenteuer', name='Abenteuer', winkel=-90,
         wurzel=('Aufbruch', 'Der Weg in die Welt beginnt.', 'minecraft:iron_sword',
                 [E('Maximales Leben (Vanilla)', '', 2, VA + 'max_health', 'addition')]),
         linien=[
             ('Kampfkunst', 'minecraft:diamond_sword', [3, 3, 4, 5, 6], 'Du teilst mehr Schaden aus, im Nah- und Fernkampf.',
              [E('Nahkampfschaden (Vanilla)', '%', 1, 'puffish_skills:player.melee_damage', 'multiply_base'),
               E('Fernkampfschaden (Ranged Weapon API)', '%', 1, 'ranged_weapon:damage', 'multiply_base', True)]),
             ('Zähigkeit', 'minecraft:golden_apple', [2, 2, 2, 4, 4], 'Du hältst mehr aus.',
              [E('Maximales Leben (Vanilla)', '', 1, VA + 'max_health', 'addition')]),
             ('Bossjäger', 'minecraft:wither_skeleton_skull', [4, 4, 5, 6, 8], 'Du triffst starke Gegner härter.',
              [S('Schaden gegen Bosse', '%', 1, 'bossdmg'),
               E('Rüstungsdurchdringung (Apothic)', '%', 0.5, AP + 'armor_pierce', 'addition', True)]),
             ('Wanderer', 'minecraft:leather_boots', [2, 2, 3, 3, 5], 'Du bewegst dich schneller, auch beim Zaubern.',
              [E('Lauftempo (Vanilla)', '%', 1, VA + 'movement_speed', 'multiply_base'),
               E("Zaubern in Bewegung (Iron's Spells)", '%', 1, IS + 'casting_movespeed', 'addition', True)]),
         ],
         gipfel=('Chronicle-Abenteurer', 'Bosse verlieren ihren Schrecken.', 'minecraft:nether_star',
                 [S('Schaden gegen Bosse', '%', 15, 'bossdmg'),
                  E('Kritischer Schaden (Apothic)', '%', 10, AP + 'crit_damage', 'addition', True)])),
    dict(id='magie', name='Magie', winkel=-18,
         wurzel=('Erste Funken', 'Die Magie wird spürbar, egal welche Mod du nutzt.', 'irons_spellbooks:arcane_essence',
                 [E("Max. Mana (Iron's Spells)", '', 10, IS + 'max_mana', 'addition'),
                  E('Max. Mana (Ars Nouveau)', '', 10, AN + 'max_mana', 'addition', True)]),
         linien=[
             ('Manavorrat', 'minecraft:lapis_lazuli', [10, 10, 15, 15, 20], 'Jeder Zaubertyp bekommt mehr Reserven.',
              [E("Max. Mana (Iron's Spells)", '', 1, IS + 'max_mana', 'addition'),
               E('Max. Mana (Ars Nouveau)', '', 1, AN + 'max_mana', 'addition', True),
               E('Seelen-Rabatt (Goety)', '%', 0.3, GO + 'soul_discount', 'addition', True)]),
             ('Fließende Energie', 'minecraft:glowstone_dust', [5, 5, 6, 7, 9], 'Das Mana lädt sich schneller auf.',
              [E("Mana-Regeneration (Iron's Spells)", '%', 1, IS + 'mana_regen', 'multiply_base'),
               E('Mana-Regeneration (Ars Nouveau)', '%', 1, AN + 'mana_regen', 'addition', True),
               E('Zaubergeschwindigkeit (Goety)', '%', 1, GO + 'casting_speed', 'addition', True)]),
             ('Zauberkraft', 'minecraft:enchanted_book', [3, 4, 5, 6, 8], 'Deine Zauber treffen härter.',
              [E("Zauberstärke (Iron's Spells)", '%', 1, IS + 'spell_power', 'multiply_base'),
               E('Zauberstärke (Spell Power, Paladine & Co.)', '%', 1, SP + 'generic', 'multiply_base', True),
               E('Zauberpotenz (Goety)', '%', 1, GO + 'spell_potency', 'addition', True),
               E('Zauberschaden (Ars Nouveau)', '%', 1, AN + 'spell_damage', 'addition', True)]),
             ('Konzentration', 'minecraft:clock', [2, 3, 3, 4, 5], 'Zauber sind schneller wieder bereit.',
              [E("Abklingzeit (Iron's Spells)", '%', 1, IS + 'cooldown_reduction', 'multiply_base'),
               E('Abklingzeit (Goety)', '%', 1, GO + 'cooldown_discount', 'addition', True),
               E('Zaubertempo (Spell Power)', '%', 1, SP + 'haste', 'multiply_base', True)]),
         ],
         gipfel=('Chronicle-Magier', 'Dein Zauberschaden steigt in allen Magiesystemen.', 'irons_spellbooks:legendary_ink',
                 [E("Zauberstärke (Iron's Spells)", '%', 20, IS + 'spell_power', 'multiply_base'),
                  E('Zauberstärke (Spell Power, Paladine & Co.)', '%', 20, SP + 'generic', 'multiply_base', True),
                  E('Zauberpotenz (Goety)', '%', 20, GO + 'spell_potency', 'addition', True),
                  E('Zauberschaden (Ars Nouveau)', '%', 20, AN + 'spell_damage', 'addition', True)])),
    dict(id='handwerk', name='Handwerk', winkel=54,
         wurzel=('Fleißige Hände', 'Alles geht etwas leichter von der Hand.', 'minecraft:crafting_table',
                 [E('Erfahrung (Apothic)', '%', 3, AP + 'experience_gained', 'addition', True)]),
         linien=[
             ('Lernbegierde', 'minecraft:experience_bottle', [4, 4, 5, 6, 8], 'Du sammelst mehr Erfahrung.',
              [E('Erfahrung (Apothic)', '%', 1, AP + 'experience_gained', 'addition', True)]),
             ('Glückspilz', 'minecraft:rabbit_foot', [1, 1, 1, 1, 2], 'Mehr Glück bei Beute und beim Angeln.',
              [E('Glück (Vanilla)', '', 1, VA + 'luck', 'addition')]),
             ('Gute Küche', 'minecraft:cooked_beef', [5, 5, 5, 10, 10], 'Essen sättigt länger.',
              [S('Nahrungswert', '%', 1, 'food')]),
             ('Genesung', 'minecraft:glistering_melon_slice', [5, 5, 6, 8, 10], 'Heilung wirkt besser.',
              [E('Erhaltene Heilung (Apothic)', '%', 1, AP + 'healing_received', 'addition', True),
               E('Lebensraub (Apothic)', '%', 0.3, AP + 'life_steal', 'addition', True)]),
         ],
         gipfel=('Chronicle-Meister', 'Deine Arbeit trägt Früchte.', 'minecraft:emerald',
                 [E('Erfahrung (Apothic)', '%', 15, AP + 'experience_gained', 'addition', True),
                  E('Glück (Vanilla)', '', 2, VA + 'luck', 'addition')])),
    dict(id='ueberleben', name='Überleben', winkel=126,
         wurzel=('Abgehärtet', 'Du steckst mehr ein.', 'minecraft:shield',
                 [E('Rüstung (Vanilla)', '', 1, VA + 'armor', 'addition')]),
         linien=[
             ('Panzerung', 'minecraft:iron_chestplate', [1, 1, 1, 2, 2], 'Mehr Rüstung.',
              [E('Rüstung (Vanilla)', '', 1, VA + 'armor', 'addition')]),
             ('Standhaft', 'minecraft:netherite_ingot', [1, 1, 1, 1, 2], 'Mehr Härte und Rückstoßschutz.',
              [E('Rüstungshärte (Vanilla)', '', 1, VA + 'armor_toughness', 'addition'),
               E('Rückstoßresistenz (Vanilla)', '%', 5, VA + 'knockback_resistance', 'addition')]),
             ('Flink', 'minecraft:feather', [3, 3, 4, 5, 6], 'Du weichst aus und fällst weicher.',
              [E('Ausweichen (Apothic)', '%', 1, AP + 'dodge_chance', 'addition', True),
               E('Weniger Fallschaden (Vanilla)', '%', 4, VA + 'fall_damage_multiplier', 'multiply_base', True)]),
             ('Widerstand', 'minecraft:magma_cream', [4, 4, 5, 6, 8], 'Weniger Schaden durch Magie in allen Systemen.',
              [E("Zauberresistenz (Iron's Spells)", '%', 1, IS + 'spell_resist', 'multiply_base'),
               E('Zauberresistenz (Spell Power)', '%', 1, SP + 'resistance.generic', 'addition', True)]),
         ],
         gipfel=('Chronicle-Wächter', 'Fast nichts bringt dich mehr aus der Ruhe.', 'minecraft:totem_of_undying',
                 [E('Erlittener Schaden (Spell Engine)', '%', -10, 'spell_engine:damage_taken', 'multiply_base', True),
                  E('Rüstung (Vanilla)', '', 2, VA + 'armor', 'addition')])),
    dict(id='technik', name='Technik', winkel=198,
         wurzel=('Geübte Hände', 'Maschinen und Werkzeuge gehorchen dir.', 'minecraft:iron_pickaxe',
                 [E('Abbautempo (Vanilla)', '%', 3, 'puffish_skills:player.mining_speed', 'multiply_base')]),
         linien=[
             ('Schürfer', 'minecraft:diamond_pickaxe', [3, 4, 5, 6, 8], 'Du baust schneller ab.',
              [E('Abbautempo (Vanilla)', '%', 1, 'puffish_skills:player.mining_speed', 'multiply_base'),
               E('Abbautempo (Apothic)', '%', 1, AP + 'mining_speed', 'addition', True)]),
             ('Reichweite', 'minecraft:spyglass', [0.5, 0.5, 0.5, 1, 1], 'Du erreichst Blöcke und Gegner weiter weg.',
              [E('Reichweite Blöcke (Vanilla)', '', 1, PL + 'block_interaction_range', 'addition'),
               E('Reichweite Angriff (Vanilla)', '', 0.5, PL + 'entity_interaction_range', 'addition')]),
             ('Stabile Werkzeuge', 'minecraft:anvil', [10, 10, 15, 15, 20], 'Werkzeuge halten länger.',
              [S('Haltbarkeit', '%', 1, 'durability')]),
             ('Tiefseetechnik', 'minecraft:heart_of_the_sea', [4, 4, 6, 6, 10], 'Du arbeitest unter Wasser wie an Land.',
              [E('Abbautempo unter Wasser (Vanilla)', '%', 1, PL + 'submerged_mining_speed', 'multiply_base'),
               E('Bewegung im Wasser (Vanilla)', '%', 1, VA + 'water_movement_efficiency', 'addition', True)]),
         ],
         gipfel=('Chronicle-Techniker', 'Die Maschinen arbeiten für dich.', 'create:precision_mechanism',
                 [E('Abbautempo (Vanilla)', '%', 20, 'puffish_skills:player.mining_speed', 'multiply_base'),
                  E('Abbautempo (Apothic)', '%', 20, AP + 'mining_speed', 'addition', True),
                  E('Reichweite Blöcke (Vanilla)', '', 1, PL + 'block_interaction_range', 'addition')])),
]
