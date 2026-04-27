package net.raith.pishock.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

public final class PiShockCompat {
    private static final ResourceLocation MAIN_MENU_ICON = ResourceLocation.fromNamespaceAndPath("pishock", "pishock_setup");

    private PiShockCompat() {
    }

    public static KeyMapping createToggleKey() {
        // 1.21.1 still uses the old string category constructor.
        return new KeyMapping(
                "key.pishock.toggle_enabled",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F12,
                "key.categories.pishock.controls"
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
}
