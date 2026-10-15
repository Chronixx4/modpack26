package com.keybindoverview;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(KeybindOverview.MOD_ID)
public class KeybindOverviewMod {
    public static final String MOD_ID = "keybindoverview";
    private static final Logger LOGGER = LogUtils.getLogger();

    public KeybindOverviewMod(IEventBus modEventBus) {
        LOGGER.info("Keybind Overview mod initialized!");
    }
}
