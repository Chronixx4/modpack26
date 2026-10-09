package de.chronicle.arena;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(ChronicleArena.MOD_ID)
public class ChronicleArena {
    public static final String MOD_ID = "chronicle_arena";

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public ChronicleArena(IEventBus modBus, ModContainer container) {
        ModRegistry.BLOCKS.register(modBus);
        ModRegistry.ITEMS.register(modBus);
        ModRegistry.TABS.register(modBus);
        modBus.addListener(Net::register);
        NeoForge.EVENT_BUS.addListener(ArenaDefs::onAddReloadListeners);
        NeoForge.EVENT_BUS.register(new ArenaManager());
    }
}
