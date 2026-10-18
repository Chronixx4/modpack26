package de.chronicle.arena;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Iron Jetpacks do not work in the arena dimension: worn jetpacks go back into the inventory. */
final class ArenaJetpacks {
    static final String MOD = "ironjetpacks";

    static boolean jetpack(String namespace) { return MOD.equals(namespace); }

    static boolean jetpack(ItemStack stack) {
        return !stack.isEmpty() && jetpack(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace());
    }

    static void tick(MinecraftServer server) {
        ServerLevel level = server.getLevel(ArenaManager.DIM);
        if (level == null) return;
        for (ServerPlayer p : level.players()) {
            if (p.isCreative() || p.isSpectator()) continue;
            boolean removed = false;
            ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
            if (jetpack(chest)) { p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY); stow(p, chest); removed = true; }
            removed |= curios(p);
            if (removed) p.displayClientMessage(Component.literal("Jetpacks funktionieren in der Arena nicht. Es liegt jetzt in deinem Inventar."), true);
        }
    }

    private static void stow(ServerPlayer p, ItemStack stack) {
        if (!p.getInventory().add(stack) && !stack.isEmpty()) p.drop(stack, false);
    }

    /** Optional Curios slot (back/body), read by reflection so the arena does not depend on Curios. */
    private static boolean curios(ServerPlayer p) {
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Object opt = api.getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class).invoke(null, p);
            if (!(opt instanceof java.util.Optional<?> o) || o.isEmpty()) return false;
            Object handler = o.get();
            Object items = handler.getClass().getMethod("getEquippedCurios").invoke(handler);
            if (!(items instanceof net.neoforged.neoforge.items.IItemHandlerModifiable inv)) return false;
            boolean removed = false;
            for (int i = 0; i < inv.getSlots(); i++) {
                ItemStack stack = inv.getStackInSlot(i);
                if (jetpack(stack)) { inv.setStackInSlot(i, ItemStack.EMPTY); stow(p, stack.copy()); removed = true; }
            }
            return removed;
        } catch (ReflectiveOperationException | RuntimeException e) {
            // No Curios installed or a changed API: only the chest slot is checked.
            return false;
        }
    }

    private ArenaJetpacks() {}
}
