package com.keybindoverview;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = KeybindOverview.MOD_ID, value = Dist.CLIENT)
public class KeybindOverview {
    public static final String MOD_ID = "keybindoverview";
    public static final KeyMapping OPEN = new KeyMapping(
            "key.keybindoverview.overview", GLFW.GLFW_KEY_UNKNOWN, "key.categories.keybindoverview");

    // Controlling uses the original screen from this event and may overwrite a
    // replacement made earlier. Run last and inspect the final proposed screen.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof KeyBindsScreen) {
            event.setNewScreen(new KeybindOverviewScreen(event.getCurrentScreen()));
        }
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN.consumeClick()) {
            if (mc.player != null && mc.screen == null) mc.setScreen(new KeybindOverviewScreen(null));
        }
    }

    @EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class Registration {
        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent event) { event.register(OPEN); }
    }
}
