package net.raith.pishock.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.PlayerHeartTypeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.raith.pishock.PiShock;
import net.raith.pishock.PiShockConfig;
import org.lwjgl.glfw.GLFW;

public final class PiShockCompat {
    // 1.21.10 has the newer key category and heart event APIs, but still uses ResourceLocation.
    private static final ResourceLocation MAIN_MENU_ICON = ResourceLocation.fromNamespaceAndPath("pishock", "pishock_setup");
    private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
            ResourceLocation.fromNamespaceAndPath("pishock", "controls")
    );

    private PiShockCompat() {
    }

    public static KeyMapping createToggleKey() {
        return new KeyMapping(
                "key.pishock.toggle_enabled",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F12,
                KEY_CATEGORY
        );
    }

    public static void onScreenInit(ScreenEvent.Init.Post event) {
        PiShockClothConfigScreen.onScreenInit(event);

        if (event.getScreen() instanceof TitleScreen titleScreen) {
            addSetupButton(event, titleScreen);
        }

        if (event.getScreen() instanceof PauseScreen pauseScreen) {
            addSetupButton(event, pauseScreen);
        }
    }

    private static void addSetupButton(ScreenEvent.Init.Post event, net.minecraft.client.gui.screens.Screen parent) {
        int buttonSize = 20;
        int x = parent.width - buttonSize - 6;
        int y = 6;

        event.addListener(new ImageButton(
                x,
                y,
                buttonSize,
                buttonSize,
                new WidgetSprites(MAIN_MENU_ICON, MAIN_MENU_ICON),
                button -> Minecraft.getInstance().setScreen(PiShockClothConfigScreen.create(parent)),
                Component.literal("PiShock Setup")
        ));
    }

    @EventBusSubscriber(modid = PiShock.MOD_ID, value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent
        public static void onPlayerHeartType(PlayerHeartTypeEvent event) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || event.getEntity() != minecraft.player) {
                return;
            }
            if (!PiShockConfig.PISHOCK_ENABLED.get()) {
                return;
            }
            if (PiShock.isShockVisualActive()) {
                event.setType(Gui.HeartType.FROZEN);
            }
        }
    }
}
