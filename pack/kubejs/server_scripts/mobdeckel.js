// Mobdeckel: begrenzt Gegner in der Naehe (Spawner, natuerliches Spawnen, Strukturen, Patrouillen).
// Vanilla begrenzt nur pro Spawner (MaxNearbyEntities), nicht ueber alle Spawner einer Anlage.
const MOB_DECKEL = 30
const MOB_RADIUS = 40
const ARTEN = ['spawner', 'natural', 'chunk_generation', 'patrol']
const AABB = Java.loadClass('net.minecraft.world.phys.AABB')
const Enemy = Java.loadClass('net.minecraft.world.entity.monster.Enemy')

EntityEvents.checkSpawn(event => {
  if (ARTEN.indexOf(String(event.type).toLowerCase()) < 0) return
  if (!(event.entity instanceof Enemy)) return
  const r = MOB_RADIUS
  const box = new AABB(event.x - r, event.y - r, event.z - r, event.x + r, event.y + r, event.z + r)
  if (event.level.getEntitiesOfClass(Enemy, box).size() >= MOB_DECKEL) event.cancel()
})
