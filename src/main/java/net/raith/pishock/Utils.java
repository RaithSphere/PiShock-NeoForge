package net.raith.pishock;

/*
  Utils.java

  This class provides utility methods for the PiShock Mod in Minecraft.
  It includes methods for formatting log messages and sending messages
  to the in-game chat. All methods ensure that input messages are
  non-null to maintain the integrity of log outputs and chat messages.

  Author: RaithSphere
  Date: 22 April 2025
 */

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;

public class Utils {

    /**
     * Formats a log message for the PiShock Mod.
     *
     * @param message the message to log, must not be null
     * @return a formatted log message prefixed with "[PiShock Mod]"
     */
    public static String unilog(@Nonnull final String message) {
        PiShock.LOGGER.info("[PiShock Mod] {}", message);
        return message;
    }

    /**
     * Sends a log message to the in-game chat.
     *
     * @param message the message to send to the chat, must not be null
     */
    public static void sendToMinecraftChat(@Nonnull final String message) {
        Minecraft.getInstance().gui.getChat().addMessage(Component.literal(message));
    }
}
