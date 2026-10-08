// Startkiste: beim ersten Betreten einer Welt bekommt jeder Spieler Questbuch und etwas Proviant.
PlayerEvents.loggedIn(event => {
  const p = event.player
  if (p.persistentData.getBoolean('chronicle_startkiste')) return
  p.persistentData.putBoolean('chronicle_startkiste', true)
  p.give(Item.of('ftbquests:book'))
  p.give(Item.of('minecraft:cooked_beef', 16))
  p.give(Item.of('minecraft:torch', 32))
  p.give(Item.of('minecraft:stone_pickaxe'))
  p.give(Item.of('minecraft:stone_axe'))
  p.tell(Text.gold('Willkommen bei Chronicle! Dein Questbuch liegt im Inventar.'))
})
