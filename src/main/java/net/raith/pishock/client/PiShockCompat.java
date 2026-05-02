package net.raith.pishock.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ScreenEvent;
import net.raith.pishock.PiShock;
import org.lwjgl.glfw.GLFW;

public final class PiShockCompat {
    private static final ResourceLocation MAIN_MENU_ICON = new ResourceLocation(PiShock.MOD_ID, "textures/gui/sprites/pishock_setup.png");

    private PiShockCompat() {
    }

    public static KeyMapping createToggleKey() {
        return new KeyMapping(
                "key.pishock.toggle_enabled",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F12,
                "key.categories.pishock.controls"
        );
    }

    public static void onScreenInit(ScreenEvent.InitScreenEvent.Post event) {
        PiShockClothConfigScreen.onScreenInit(event);

        if (event.getScreen() instanceof TitleScreen || event.getScreen() instanceof PauseScreen) {
            addSetupButton(event, event.getScreen());
        }
    }

    private static void addSetupButton(ScreenEvent.InitScreenEvent.Post event, Screen parent) {
        int buttonSize = 20;
        int x = parent.width - buttonSize - 6;
        int y = 6;

        event.addListener(new ImageButton(
                x,
                y,
                buttonSize,
                buttonSize,
                0,
                0,
                0,
                MAIN_MENU_ICON,
                buttonSize,
                buttonSize,
                button -> Minecraft.getInstance().setScreen(PiShockClothConfigScreen.create(parent)),
                new TextComponent("PiShock Setup")
        ));
    }
}
