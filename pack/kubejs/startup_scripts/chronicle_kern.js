// Chronicle-Kern: eigenes Endgame-Ziel des Packs.
// Drei Fragmente (Technik, Magie, Abenteuer) werden im mechanischen Crafter von Create zum Kern.
// Rezepte: kubejs/data/kubejs/recipe/*.json, Namen: kubejs/assets/kubejs/lang/*.json
StartupEvents.registry('item', event => {
  event.create('technik_fragment').maxStackSize(16).rarity('rare')
  event.create('magie_fragment').maxStackSize(16).rarity('rare')
  event.create('abenteuer_fragment').maxStackSize(16).rarity('rare')
  event.create('chronicle_core').maxStackSize(1).rarity('epic').fireResistant()
})

// Ohne Kreativ-Tab fehlen die Items in der JEI-Liste (und damit ihre Rezepte)
StartupEvents.modifyCreativeTab('minecraft:ingredients', event => {
  event.add('kubejs:technik_fragment')
  event.add('kubejs:magie_fragment')
  event.add('kubejs:abenteuer_fragment')
  event.add('kubejs:chronicle_core')
})
