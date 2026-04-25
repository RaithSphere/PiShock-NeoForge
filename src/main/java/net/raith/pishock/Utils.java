package net.raith.pishock;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;

public class Utils {

    public static String unilog(@Nonnull final String message) {
        PiShock.LOGGER.info("[PiShock Mod] {}", message);
        return message;
    }

    public static void sendToMinecraftChat(@Nonnull final String message) {
        sendToMinecraftChat(Component.literal(message));
    }

    public static void sendToMinecraftChat(@Nonnull final Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        // Ensure chat mutations always run on the main client thread.
        minecraft.execute(() -> minecraft.gui.getChat().addMessage(message));
    }
}
