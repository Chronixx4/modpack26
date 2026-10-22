package de.chronicle.arena;

import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Arena-Ereignisse, Gegner-Klassen, Heilbrunnen, Fallen und die Siegertafel in der Lobby. */
final class ArenaExtras {
    private ArenaExtras() {}

    // ------------------------------------------------------------------ Ereignisse

    static final int EV_NONE = 0;
    static final int EV_BLUTMOND = 1;
    static final int EV_GEWITTER = 2;
    static final int EV_MONDSPRUNG = 3;

    static String eventName(int ev) {
        return switch (ev) {
            case EV_BLUTMOND -> "Blutmond";
            case EV_GEWITTER -> "Gewitter";
            case EV_MONDSPRUNG -> "Mondsprung";
            default -> "";
        };
    }

    static String eventText(int ev) {
        return switch (ev) {
            case EV_BLUTMOND -> "Gegner sind schneller, dafuer +50 % Coins";
            case EV_GEWITTER -> "Blitze schlagen in die Gegner ein";
            case EV_MONDSPRUNG -> "Ihr springt hoeher und schwebt sanft";
            default -> "";
        };
    }

    static int rollEvent(net.minecraft.util.RandomSource rnd, int wave, boolean bossWave) {
        if (bossWave || wave < 3 || rnd.nextFloat() > 0.3F) return EV_NONE;
        return 1 + rnd.nextInt(3);
    }

    static double eventCoinMult(int ev) {
        return ev == EV_BLUTMOND ? 1.5 : 1.0;
    }

    // ------------------------------------------------------------------ Gegner-Klassen

    static final int CL_NONE = 0;
    static final int CL_SANITAETER = 1;
    static final int CL_SCHILD = 2;
    static final int CL_BOMBE = 3;
    static final int CL_SCHUETZE = 4;

    static String className(int cl) {
        return switch (cl) {
            case CL_SANITAETER -> "Sanitaeter";
            case CL_SCHILD -> "Schildtraeger";
            case CL_BOMBE -> "Bombenleger";
            case CL_SCHUETZE -> "Schuetze";
            default -> "";
        };
    }

    static ChatFormatting classColor(int cl) {
        return switch (cl) {
            case CL_SANITAETER -> ChatFormatting.GREEN;
            case CL_SCHILD -> ChatFormatting.AQUA;
            case CL_BOMBE -> ChatFormatting.RED;
            default -> ChatFormatting.YELLOW;
        };
    }

    // ------------------------------------------------------------------ Karte: Brunnen und Fallen

    static final int[][] FOUNTAINS = {{14, 14}, {-14, 14}, {14, -14}, {-14, -14}};

    static final int[][] TRAPS = trapOffsets();

    private static int[][] trapOffsets() {
        int[][] t = new int[8][];
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2.0 * i / 8.0 + Math.PI / 8.0;
            t[i] = new int[]{(int) Math.round(Math.cos(a) * 17.0), (int) Math.round(Math.sin(a) * 17.0)};
        }
        return t;
    }

    /** Pro Tick waehrend des Kampfes. */
    static void tick(MinecraftServer server, ServerLevel level, ArenaManager.Run r, int tick) {
        BlockPos c = r.center;
        // Sanitaeter heilen Gegner in der Naehe.
        if (tick % 40 == 0 && !r.classes.isEmpty()) {
            for (Map.Entry<UUID, Integer> en : r.classes.entrySet()) {
                if (en.getValue() != CL_SANITAETER || !(level.getEntity(en.getKey()) instanceof LivingEntity healer) || !healer.isAlive()) continue;
                for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, healer.getBoundingBox().inflate(6.0),
                        e -> e != healer && !(e instanceof Player) && e.isAlive() && r.mobs.contains(e.getUUID()))) {
                    if (other.getHealth() < other.getMaxHealth()) {
                        other.heal(Math.max(4.0F, other.getMaxHealth() * 0.08F));
                        level.sendParticles(ParticleTypes.HEART, other.getX(), other.getY() + other.getBbHeight() + 0.2, other.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
                    }
                }
            }
        }
        // Ereignisse.
        if (r.event == EV_GEWITTER && tick % 70 == 0) {
            double a = level.getRandom().nextDouble() * Math.PI * 2.0;
            double d = 5.0 + level.getRandom().nextDouble() * 24.0;
            double x = c.getX() + 0.5 + Math.cos(a) * d;
            double z = c.getZ() + 0.5 + Math.sin(a) * d;
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(x, c.getY(), z);
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(x - 3.5, c.getY() - 2, z - 3.5, x + 3.5, c.getY() + 6, z + 3.5),
                    e -> !(e instanceof Player) && r.mobs.contains(e.getUUID()))) {
                e.hurt(level.damageSources().lightningBolt(), 18.0F);
            }
        }
        if (r.event == EV_MONDSPRUNG && tick % 40 == 0) {
            for (UUID id : r.alive) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p == null) continue;
                p.addEffect(new MobEffectInstance(MobEffects.JUMP, 70, 1, true, false));
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 70, 0, true, false));
            }
        }
        // Brunnen und Fallen gibt es nur auf der normalen Arena-Karte.
        if (!r.mode.arenaMap()) return;
        // Heilbrunnen.
        for (int i = 0; i < FOUNTAINS.length; i++) {
            double fx = c.getX() + 0.5 + FOUNTAINS[i][0];
            double fz = c.getZ() + 0.5 + FOUNTAINS[i][1];
            boolean ready = r.fountainReady[i] <= tick;
            if (ready) {
                // Gut sichtbar: Lichtsaeule, Ring am Boden, Herzen.
                if (tick % 4 == 0) {
                    for (int k = 0; k < 10; k++) level.sendParticles(ParticleTypes.END_ROD, fx, c.getY() + 1.0 + k * 0.7, fz, 1, 0.05, 0.0, 0.05, 0.0);
                    double a = tick * 0.15;
                    for (int k = 0; k < 6; k++) {
                        double ang = a + k * Math.PI / 3.0;
                        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, fx + Math.cos(ang) * 2.4, c.getY() + 0.3, fz + Math.sin(ang) * 2.4, 1, 0, 0, 0, 0.0);
                    }
                }
                if (tick % 20 == 0) level.sendParticles(ParticleTypes.HEART, fx, c.getY() + 3.0, fz, 2, 0.3, 0.2, 0.3, 0.0);
            } else if (tick % 10 == 0) {
                level.sendParticles(ParticleTypes.SMOKE, fx, c.getY() + 1.2, fz, 2, 0.3, 0.2, 0.3, 0.0);
            }
            if (tick % 20 == 0) fountainLabel(server, level, i, fx, c.getY() + 4.2, fz, ready, (int) ((r.fountainReady[i] - tick + 19) / 20));
            if (!ready) continue;
            for (UUID id : r.alive) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p == null || p.level() != level) continue;
                double dx = p.getX() - fx;
                double dz = p.getZ() - fz;
                if (dx * dx + dz * dz <= 2.5 * 2.5 && Math.abs(p.getY() - c.getY()) < 3.0 && ArenaBalance.needsRecovery(p.getHealth(),p.getMaxHealth(),p.getFoodData().getFoodLevel(),p.isOnFire(),(r.mask&ArenaManager.MOD_NOHEAL)!=0)) {
                    if ((r.mask & ArenaManager.MOD_NOHEAL) == 0) p.heal(Math.max(8.0F, p.getMaxHealth() * 0.4F));
                    p.getFoodData().eat(4,0.5F);
                    p.clearFire();
                    level.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.5, p.getZ(), 6, 0.4, 0.4, 0.4, 0.0);
                    level.playSound(null, fx, c.getY(), fz, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.4F);
                    r.fountainReady[i] = tick + 900;
                    p.displayClientMessage(Component.literal("Recovery: Leben und +2 Hungerkeulen - Cooldown 45 s."), true);
                    break;
                }
            }
        }
        // Fallen: Druckplatten loesen Flammenstoesse aus (treffen Gegner hart, Spieler leicht).
        for (int i = 0; i < TRAPS.length; i++) {
            if (r.trapReady[i] > tick) continue;
            double tx = c.getX() + 0.5 + TRAPS[i][0];
            double tz = c.getZ() + 0.5 + TRAPS[i][1];
            AABB plate = new AABB(tx - 0.8, c.getY() - 0.5, tz - 0.8, tx + 0.8, c.getY() + 1.5, tz + 0.8);
            List<LivingEntity> on = level.getEntitiesOfClass(LivingEntity.class, plate,
                    e -> e.isAlive() && e.onGround() && !(e instanceof ServerPlayer) && r.mobs.contains(e.getUUID()));
            if (on.isEmpty()) continue;
            r.trapReady[i] = tick + 200;
            level.sendParticles(ParticleTypes.FLAME, tx, c.getY() + 0.5, tz, 70, 1.6, 0.8, 1.6, 0.04);
            level.sendParticles(ParticleTypes.LAVA, tx, c.getY() + 1.0, tz, 14, 1.2, 0.5, 1.2, 0.0);
            level.playSound(null, tx, c.getY(), tz, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 1.5F, 0.7F);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(tx - 3.2, c.getY() - 1, tz - 3.2, tx + 3.2, c.getY() + 4, tz + 3.2))) {
                if (e instanceof ServerPlayer) {
                    continue;   // Fallen treffen nur Gegner
                } else if (r.mobs.contains(e.getUUID())) {
                    e.hurt(level.damageSources().inFire(), 14.0F);
                    e.igniteForSeconds(4);
                }
            }
        }
    }

    /** Bombenleger explodieren beim Tod, ohne Bloecke zu zerstoeren. */
    static void bombDeath(ServerLevel level, ArenaManager.Run r, LivingEntity dead) {
        Vec3 p = dead.position();
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.x, p.y + 0.5, p.z, 1, 0, 0, 0, 0);
        level.playSound(null, p.x, p.y, p.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.4F, 1.0F);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, dead.getBoundingBox().inflate(4.0), e -> e != dead && e.isAlive())) {
            if (e instanceof ServerPlayer sp) {
                if (r.alive.contains(sp.getUUID())) sp.hurt(level.damageSources().explosion(null, null), 6.0F);
            } else if (r.mobs.contains(e.getUUID())) {
                e.hurt(level.damageSources().explosion(null, null), 10.0F);
            }
        }
    }

    // ------------------------------------------------------------------ Siegertafel in der Lobby

    static void deduplicateBoard(ServerLevel level) {
        List<Entity> boards=new ArrayList<>();
        for(Entity e:level.getAllEntities())if(e.getTags().contains("chronicle_board"))boards.add(e);
        boards.sort(java.util.Comparator.comparingInt(Entity::getId).reversed());
        for(int i=1;i<boards.size();i++)boards.get(i).discard();
    }

    static net.minecraft.nbt.CompoundTag boardTag(String text) {
        var tag=new net.minecraft.nbt.CompoundTag();
        JsonObject json=new JsonObject();json.addProperty("text",text);json.addProperty("color","gold");
        tag.putString("text",json.toString());tag.putString("billboard","vertical");tag.putString("alignment","center");
        tag.putInt("line_width",320);tag.putInt("background",1275068416);tag.putBoolean("shadow",true);
        return tag;
    }

    static void updateBoard(MinecraftServer server,ArenaManager.Run r,boolean won) {
        ServerLevel level=server.getLevel(ArenaManager.DIM);if(level==null)return;
        List<Entity> old=new ArrayList<>();for(Entity e:level.getAllEntities())if(e.getTags().contains("chronicle_board"))old.add(e);
        old.forEach(Entity::discard);
        StringBuilder text=new StringBuilder("LETZTER RUN\n");
        text.append(r.encounter.name()).append(" - ").append(r.tier.name()).append("\n");
        text.append(r.endless?"Abgrund: Welle "+r.completedWaves:won?"Sieg":"Niederlage").append(" | ").append(ArenaManager.formatTime(r.fightTicks)).append("\n\n");
        for(UUID id:r.participants)r.stat(id);
        List<Map.Entry<UUID,long[]>> rows=new ArrayList<>(r.stat.entrySet());rows.sort((a,b)->Long.compare(b.getValue()[0],a.getValue()[0]));
        for(var en:rows.subList(0,Math.min(8,rows.size()))){long[] st=en.getValue();text.append(r.names.getOrDefault(en.getKey(),"Spieler")).append(" | Kills ").append(st[1]).append("\nSchaden ").append(st[0]).append(" | Wiederbelebt ").append(st[2]).append("\n");}
        if(rows.size()>8)text.append("Alle Spieler: /arena ergebnis");
        var board=EntityType.TEXT_DISPLAY.create(level);if(board==null)return;
        board.load(boardTag(text.toString()));board.addTag("chronicle_board");
        board.moveTo(ArenaManager.LOBBY.getX()+0.5,ArenaManager.LOBBY.getY()+4.5,ArenaManager.LOBBY.getZ()-9.5,0,0);
        level.addFreshEntity(board);
    }

    /** Schwebender Text ueber dem Heilbrunnen: "Bereit" oder Aufladezeit. */
    private static void fountainLabel(MinecraftServer server,ServerLevel level,int i,double x,double y,double z,boolean ready,int secs){
        String tag="chronicle_fount"+i;
        List<Entity> old=new ArrayList<>();for(Entity e:level.getAllEntities())if(e.getTags().contains(tag))old.add(e);
        old.sort(java.util.Comparator.comparingInt(Entity::getId).reversed());for(int k=1;k<old.size();k++)old.get(k).discard();
        net.minecraft.world.entity.Display.TextDisplay label=old.isEmpty()?EntityType.TEXT_DISPLAY.create(level):old.get(0) instanceof net.minecraft.world.entity.Display.TextDisplay d?d:null;
        if(label==null)return;
        var data=boardTag(ready?"RECOVERY\nLeben + Hunger bereit":"RECOVERY\nCooldown: "+Math.max(0,secs)+" s");
        JsonObject text=new JsonObject();text.addProperty("text",ready?"RECOVERY\nLeben + Hunger bereit":"RECOVERY\nCooldown: "+Math.max(0,secs)+" s");text.addProperty("color",ready?"green":"yellow");data.putString("text",text.toString());data.putString("billboard","center");data.putInt("line_width",180);
        label.load(data);label.addTag(tag);label.addTag("chronicle_fountain");label.moveTo(x,y,z,0,0);
        if(old.isEmpty())level.addFreshEntity(label);
    }

    static void clearFountainLabels(MinecraftServer server,ServerLevel level){
        List<Entity> labels=new ArrayList<>();for(Entity e:level.getAllEntities())if(e.getTags().contains("chronicle_fountain"))labels.add(e);labels.forEach(Entity::discard);
    }
}
