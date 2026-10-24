// Mobdeckel: begrenzt Gegner in der Naehe (Spawner, natuerliches Spawnen, Strukturen, Patrouillen).
// Vanilla begrenzt nur pro Spawner (MaxNearbyEntities), nicht ueber alle Spawner einer Anlage.
const MOB_DECKEL = 30
const MOB_RADIUS = 40
const ARTEN = ['spawner', 'natural', 'chunk_generation', 'patrol', 'event', 'structure']
const AABB = Java.loadClass('net.minecraft.world.phys.AABB')
const Enemy = Java.loadClass('net.minecraft.world.entity.monster.Enemy')

let geblockt = 0
console.info('Mobdeckel geladen: max ' + MOB_DECKEL + ' Gegner im Umkreis ' + MOB_RADIUS)

EntityEvents.checkSpawn(event => {
  if (ARTEN.indexOf(String(event.type).toLowerCase()) < 0) return
  if (!(event.entity instanceof Enemy)) return
  const r = MOB_RADIUS
  const box = new AABB(event.x - r, event.y - r, event.z - r, event.x + r, event.y + r, event.z + r)
  if (event.level.getEntitiesOfClass(Enemy, box).size() >= MOB_DECKEL) {
    event.cancel()
    geblockt++
    if (geblockt % 100 == 1) console.info('Mobdeckel: ' + geblockt + ' Spawns verhindert')
  }
})
