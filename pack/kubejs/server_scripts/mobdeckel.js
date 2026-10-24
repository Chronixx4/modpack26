// Mobdeckel: begrenzt Gegner in der Naehe, egal wie sie entstehen (Spawner, Raids, Strukturen, Patrouillen).
// Vanilla begrenzt nur pro Spawner (MaxNearbyEntities), nicht ueber alle Spawner einer Anlage.
// Haengt am Betreten der Welt, weil Spawner mit eigenen Daten (Festung in The Other) kein checkSpawn ausloesen.
const MOB_DECKEL = 35
const MOB_RADIUS = 64
const MobAABB = Java.loadClass('net.minecraft.world.phys.AABB')
const MobEnemy = Java.loadClass('net.minecraft.world.entity.monster.Enemy')

const MobBoss = Java.loadClass('net.minecraft.world.entity.boss.enderdragon.EnderDragon')
let geblockt = 0
console.info('Mobdeckel geladen: max ' + MOB_DECKEL + ' Gegner im Umkreis ' + MOB_RADIUS)

EntityEvents.spawned(event => {
  const e = event.entity
  if (!(e instanceof MobEnemy)) return
  if (e.hasCustomName() || e instanceof MobBoss) return // Bosse und benannte Mobs nie blocken
  const r = MOB_RADIUS
  const box = new MobAABB(e.x - r, e.y - r, e.z - r, e.x + r, e.y + r, e.z + r)
  if (event.level.getEntitiesOfClass(MobEnemy, box).size() >= MOB_DECKEL) {
    event.cancel()
    geblockt++
    if (geblockt % 100 == 1) console.info('Mobdeckel: ' + geblockt + ' Spawns verhindert')
  }
})
