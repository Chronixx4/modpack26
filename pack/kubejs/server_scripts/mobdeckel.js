// Mobdeckel: Spawner (z. B. Festung in The Other) duerfen nur begrenzt viele Monster in der Naehe haben.
// Vanilla begrenzt nur pro Spawner (MaxNearbyEntities), nicht ueber alle Spawner einer Anlage.
const MOB_DECKEL = 30
const MOB_RADIUS = 40
const AABB = Java.loadClass('net.minecraft.world.phys.AABB')
const Monster = Java.loadClass('net.minecraft.world.entity.monster.Monster')

EntityEvents.checkSpawn(event => {
  if (String(event.type).toLowerCase().indexOf('spawner') < 0) return
  if (!(event.entity instanceof Monster)) return
  const r = MOB_RADIUS
  const box = new AABB(event.x - r, event.y - r, event.z - r, event.x + r, event.y + r, event.z + r)
  if (event.level.getEntitiesOfClass(Monster, box).size() >= MOB_DECKEL) event.cancel()
})
