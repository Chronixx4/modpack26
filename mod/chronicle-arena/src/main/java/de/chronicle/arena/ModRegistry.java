package de.chronicle.arena;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ChronicleArena.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ChronicleArena.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ChronicleArena.MOD_ID);

    public static final DeferredBlock<ArenaPortalBlock> PORTAL = BLOCKS.register("arena_portal",
            () -> new ArenaPortalBlock(BlockBehaviour.Properties.of()
                    .strength(10.0F, 1200.0F)
                    .lightLevel(state -> 12)
                    .sound(SoundType.AMETHYST)
                    .noOcclusion()));

    public static final DeferredBlock<ArenaTerminalBlock> TERMINAL = BLOCKS.register("arena_terminal",
            () -> new ArenaTerminalBlock(BlockBehaviour.Properties.of()
                    .strength(-1.0F, 3600000.0F)
                    .lightLevel(state -> 10)
                    .sound(SoundType.METAL)));

    public static final DeferredBlock<ArenaShopBlock> SHOP = BLOCKS.register("arena_shop",
            () -> new ArenaShopBlock(BlockBehaviour.Properties.of()
                    .strength(-1.0F, 3600000.0F)
                    .lightLevel(state -> 12)
                    .sound(SoundType.METAL)));

    public static final DeferredBlock<ArenaHoloBlock> HOLO_TERMINAL = BLOCKS.register("arena_holo_terminal",
            () -> new ArenaHoloBlock(holoProps(), false));

    public static final DeferredBlock<ArenaHoloBlock> HOLO_SHOP = BLOCKS.register("arena_holo_shop",
            () -> new ArenaHoloBlock(holoProps(), true));

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ChronicleArena.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HoloBlockEntity>> HOLO = BLOCK_ENTITIES.register("holo",
            () -> BlockEntityType.Builder.of(HoloBlockEntity::new, TERMINAL.get(), SHOP.get()).build(null));

    private static BlockBehaviour.Properties holoProps() {
        return BlockBehaviour.Properties.of().strength(-1.0F, 3600000.0F).noCollission().noOcclusion().noLootTable()
                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK);
    }

    public static final DeferredItem<BlockItem> PORTAL_ITEM = ITEMS.registerSimpleBlockItem(PORTAL);
    public static final DeferredItem<BlockItem> TERMINAL_ITEM = ITEMS.registerSimpleBlockItem(TERMINAL);

    public static final DeferredItem<BlockItem> SHOP_ITEM = ITEMS.registerSimpleBlockItem(SHOP);

    public static final DeferredItem<Item> TOKEN = ITEMS.registerSimpleItem("arena_token", new Item.Properties().rarity(Rarity.RARE));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.chronicle_arena"))
                    .icon(() -> new ItemStack(PORTAL_ITEM.get()))
                    .displayItems((params, out) -> {
                        out.accept(PORTAL_ITEM.get());
                        out.accept(TERMINAL_ITEM.get());
                        out.accept(SHOP_ITEM.get());
                        out.accept(TOKEN.get());
                    })
                    .build());

    private ModRegistry() {}
}
