package net.raith.pishock;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.raith.pishock.network.ShockHandler;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;


@Mod(PiShock.MOD_ID)
public class PiShock {

    public static final String MOD_ID = "pishock";
    public static final Logger LOGGER = LogUtils.getLogger();

    @Nullable private static Float previousHP = null;
    @Nullable private static Integer cooldownTimer = null;
    @Nullable private static Integer activeTimer = null;
    private static boolean deathShocked = false;

    public PiShock(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.addListener(this::onHurt);
        modContainer.registerConfig(ModConfig.Type.COMMON, PiShockConfig.SPEC);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    // For Servers and to keep this client side only we will have to use the PlayerEventTick
    // LivingIncomingDamageEvent and LivingDeathEvent are not passed when connected to server

    @SubscribeEvent
    public void onHurt(PlayerTickEvent.Post event)
    {
        // TODO: Clean up this and make it more organised
        final Player player = Minecraft.getInstance().player;

        // Player is not actually there yet
        if (player == null) {
            resetSystem();
            return;
        }


        // We are in the pause menu, Stop everything
        if (Minecraft.getInstance().isPaused()) {
            return;
        }

        // Player is Creative of Spec we can't really do anything here
        if (player.isCreative() || player.isSpectator()) {
            resetSystem();
            return;
        }

        // Player Health Logic

        final float currentHP = player.getHealth();
        final float maxHP = player.getMaxHealth();
        final boolean isDead = player.isDeadOrDying();


        if (previousHP == null) {
            previousHP = currentHP;
            activeTimer = 100;
            return;
        }


        if(isDead && PiShockConfig.PISHOCK_TRIGGER_ON_DEATH.get() && !deathShocked)
        {
            deathShocked = true; // We have shocked them for death so set this so it won't happen again
            Utils.unilog("Player is DEAD, LIGHT THEM UP!");

            // Run the shock in a separate thread
            new Thread(() -> {
            ShockHandler.shock(PiShockConfig.PISHOCK_INTENSITY.get());
            }).start();
        }

        else if(deathShocked && currentHP > 0) // RESET THE DEATH SHOCK
        {
            deathShocked = false;
        }
        else if (currentHP < previousHP && currentHP > 0) {
            Utils.unilog("Player has been damaged");
            float damage = previousHP - currentHP;
            float max = (float) PiShockConfig.PISHOCK_INTENSITY.get() / 20;
            int shockpower = (int) (damage * max);

            if (cooldownTimer == null || cooldownTimer <= 0) {
                Utils.unilog("Player has been damaged for %s zapping at %s based on %s".formatted(damage, shockpower, max));

                // Run the shock in a separate thread
                new Thread(() -> {
                    ShockHandler.shock(shockpower);
                }).start();

                cooldownTimer = 200; // Set the cooldown timer
            } else {
                Utils.unilog("Cooldown is active, remaining: " + cooldownTimer);
            }
        }

        // Decrement cooldown timer if it's active
        if (cooldownTimer != null && cooldownTimer > 0) {
            cooldownTimer--;
        }

        // Each Tick we will update the Previous HP
        previousHP = currentHP;

    }
    private static void resetSystem()
    {
        cooldownTimer = null;
        previousHP = null;
        activeTimer = null;
    }

    public enum PiShockMode {
        Shock(0),
        Vibrate(1),
        Beep(2);

        private final int value;

        PiShockMode(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static PiShockMode fromValue(int value) {
            for (PiShockMode mode : values()) {
                if (mode.getValue() == value) {
                    return mode;
                }
            }
            return Shock; // Default mode
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info(Utils.unilog("PiShock initialized"));
    }

    @EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {

        }
    }
}
