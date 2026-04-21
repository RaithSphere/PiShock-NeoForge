package net.raith.pishock;

import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.PlayerHeartTypeEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.raith.pishock.client.PiShockClothConfigScreen;
import net.raith.pishock.network.ShockHandler;
import org.slf4j.Logger;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Mod(PiShock.MOD_ID)
public class PiShock {

    public static final String MOD_ID = "pishock";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final Identifier MAIN_MENU_ICON = Identifier.fromNamespaceAndPath(MOD_ID, "pishock_setup");
    private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "controls")
    );
    private static final KeyMapping TOGGLE_KEY = new KeyMapping(
            "key.pishock.toggle_enabled",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F12,
            KEY_CATEGORY
    );
    private static ModContainer MOD_CONTAINER;

    @Nullable
    private static Float previousHP = null;
    private static boolean deathShocked = false;
    private static final LinkedBlockingQueue<ShockRequest> SHOCK_QUEUE = new LinkedBlockingQueue<>();
    private static final AtomicBoolean QUEUE_WORKER_STARTED = new AtomicBoolean(false);

    private static float pendingDamageRatio = 0.0F;
    private static long pendingDamageLastEventAtMs = -1L;
    private static final AtomicLong SHOCK_VISUAL_UNTIL_MS = new AtomicLong(0L);

    public PiShock(IEventBus modEventBus, ModContainer modContainer) {
        MOD_CONTAINER = modContainer;
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(ClientModEvents::onRegisterKeyMappings);

        // Register only once; onHurt uses @SubscribeEvent.
        NeoForge.EVENT_BUS.register(this);

        modContainer.registerConfig(ModConfig.Type.COMMON, PiShockConfig.SPEC);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, (minecraft, parent) -> PiShockClothConfigScreen.create(parent));
    }

    public static ModContainer getModContainer() {
        return MOD_CONTAINER;
    }

    @SubscribeEvent
    public void onHurt(PlayerTickEvent.Post event) {
        final Player player = Minecraft.getInstance().player;

        if (player == null) {
            resetSystem();
            return;
        }

        if (Minecraft.getInstance().isPaused()) {
            return;
        }

        if (player.isCreative() || player.isSpectator()) {
            resetSystem();
            return;
        }

        final float currentHP = player.getHealth();
        long now = System.currentTimeMillis();
        final boolean isDead = player.isDeadOrDying();

        if (!PiShockConfig.PISHOCK_ENABLED.get()) {
            clearPendingDamage();
            SHOCK_QUEUE.clear();
            deathShocked = false;
            previousHP = currentHP;
            return;
        }

        if (previousHP == null) {
            previousHP = currentHP;
            return;
        }

        flushPendingDamageIfReady(now);

        if (isDead && PiShockConfig.PISHOCK_TRIGGER_ON_DEATH.get() && !deathShocked) {
            deathShocked = true;
            clearPendingDamage();
            Utils.unilog("Player is dead, triggering configured death response");
            queueShock(PiShockConfig.PISHOCK_INTENSITY.get(), PiShockConfig.PISHOCK_DURATION.get());
        } else if (deathShocked && currentHP > 0) {
            deathShocked = false;
        } else if (currentHP < previousHP && currentHP > 0) {
            float damage = previousHP - currentHP;
            float maxHealth = Math.max(1.0F, player.getMaxHealth());
            float damageRatio = damage / maxHealth;

            if (PiShockConfig.PISHOCK_COMBINE_DAMAGE_EVENTS.get()) {
                pendingDamageRatio += damageRatio;
                pendingDamageLastEventAtMs = now;
            } else {
                int shockPower = computeIntensityFromDamageRatio(damageRatio);
                Utils.unilog("Player damaged by %s, queueing intensity %s".formatted(damage, shockPower));
                queueShock(shockPower, PiShockConfig.PISHOCK_DURATION.get());
            }
        }

        previousHP = currentHP;
    }

    private static void resetSystem() {
        clearPendingDamage();
        SHOCK_QUEUE.clear();
        previousHP = null;
        SHOCK_VISUAL_UNTIL_MS.set(0L);
    }

    private static void flushPendingDamageIfReady(long nowMs) {
        if (pendingDamageRatio <= 0.0F || pendingDamageLastEventAtMs < 0L) {
            return;
        }

        int windowMs = PiShockConfig.PISHOCK_COMBINE_WINDOW_MS.get();
        if (windowMs > 0 && nowMs - pendingDamageLastEventAtMs < windowMs) {
            return;
        }

        int shockPower = computeIntensityFromDamageRatio(pendingDamageRatio);
        Utils.unilog("Combined rapid damage events into queued intensity %s".formatted(shockPower));
        queueShock(shockPower, PiShockConfig.PISHOCK_DURATION.get());
        clearPendingDamage();
    }

    private static int computeIntensityFromDamageRatio(float damageRatio) {
        int intensityLimit = PiShockConfig.PISHOCK_INTENSITY.get();
        int rawShockPower = Math.round(Math.max(0.0F, damageRatio) * intensityLimit);
        return Math.min(intensityLimit, Math.max(0, rawShockPower));
    }

    private static void clearPendingDamage() {
        pendingDamageRatio = 0.0F;
        pendingDamageLastEventAtMs = -1L;
    }

    private static void queueShock(int intensity, int durationMs) {
        int clampedIntensity = Math.max(1, Math.min(100, intensity));
        int clampedDurationMs = Math.max(100, Math.min(15000, durationMs));

        if (!PiShockConfig.PISHOCK_QUEUE_ENABLED.get()) {
            ShockHandler.shock(clampedIntensity, clampedDurationMs);
            return;
        }

        synchronized (SHOCK_QUEUE) {
            int maxSize = PiShockConfig.PISHOCK_QUEUE_MAX_SIZE.get();
            while (SHOCK_QUEUE.size() >= maxSize) {
                SHOCK_QUEUE.poll();
            }
            SHOCK_QUEUE.offer(new ShockRequest(clampedIntensity, clampedDurationMs));
        }
        ensureQueueWorker();
    }

    private static void ensureQueueWorker() {
        if (!QUEUE_WORKER_STARTED.compareAndSet(false, true)) {
            return;
        }

        Thread worker = new Thread(() -> {
            while (true) {
                try {
                    ShockRequest request = SHOCK_QUEUE.take();
                    ShockHandler.shock(request.intensity(), request.durationMs());
                    Thread.sleep(Math.max(100L, request.durationMs()));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception ex) {
                    Utils.unilog("Shock queue worker error: " + ex.getMessage());
                }
            }
        }, "pishock-queue");
        worker.setDaemon(true);
        worker.start();
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
            return Shock;
        }
    }

    private record ShockRequest(int intensity, int durationMs) {
    }

    public static void markShockVisual(int durationMs) {
        long now = System.currentTimeMillis();
        int clampedDurationMs = Math.max(100, Math.min(15000, durationMs));
        SHOCK_VISUAL_UNTIL_MS.accumulateAndGet(now + clampedDurationMs, Math::max);
    }

    private static boolean isShockVisualActive() {
        return System.currentTimeMillis() < SHOCK_VISUAL_UNTIL_MS.get();
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        Utils.unilog("PiShock initialized");
    }

    @EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
    public static class ClientModEvents {
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE_KEY);
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // No-op for now.
        }

        @SubscribeEvent
        public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
            event.getDispatcher().register(
                    Commands.literal("pishock")
                            .then(Commands.literal("debug")
                                    .executes(context -> {
                                        boolean enabled = ShockHandler.isDebugEnabled();
                                        Utils.sendToMinecraftChat("[PiShock] Debug is currently " + (enabled ? "ON" : "OFF")
                                                + ". Use /pishock debug <true|false>.");
                                        return Command.SINGLE_SUCCESS;
                                    })
                                    .then(Commands.argument("enabled", BoolArgumentType.bool())
                                            .executes(context -> {
                                                boolean enabled = BoolArgumentType.getBool(context, "enabled");
                                                ShockHandler.setDebugEnabled(enabled);
                                                Utils.sendToMinecraftChat("[PiShock] Debug " + (enabled ? "enabled" : "disabled") + ".");
                                                return Command.SINGLE_SUCCESS;
                                            }))));
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            while (TOGGLE_KEY.consumeClick()) {
                boolean enabled = !PiShockConfig.PISHOCK_ENABLED.get();
                PiShockConfig.PISHOCK_ENABLED.set(enabled);
                PiShockConfig.save();
                Utils.sendToMinecraftChat(enabled
                        ? "[PiShock] Quick toggle: Enabled"
                        : "[PiShock] Quick toggle: Disabled");
            }
        }

        @SubscribeEvent
        public static void onPlayerHeartType(PlayerHeartTypeEvent event) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || event.getEntity() != minecraft.player) {
                return;
            }
            if (!PiShockConfig.PISHOCK_ENABLED.get()) {
                return;
            }
            if (isShockVisualActive()) {
                event.setType(Gui.HeartType.FROZEN);
            }
        }

        @SubscribeEvent
        public static void onScreenInit(ScreenEvent.Init.Post event) {
            PiShockClothConfigScreen.onScreenInit(event);

            if (event.getScreen() instanceof TitleScreen titleScreen) {
                int buttonSize = 20;
                int x = titleScreen.width - buttonSize - 6;
                int y = 6;

                event.addListener(new ImageButton(
                        x,
                        y,
                        buttonSize,
                        buttonSize,
                        new WidgetSprites(MAIN_MENU_ICON, MAIN_MENU_ICON),
                        button -> {
                            Minecraft minecraft = Minecraft.getInstance();
                            minecraft.setScreen(PiShockClothConfigScreen.create(titleScreen));
                        },
                        Component.literal("PiShock Setup")
                ));
            }

            if (event.getScreen() instanceof PauseScreen pauseScreen) {
                int buttonSize = 20;
                int x = pauseScreen.width - buttonSize - 6;
                int y = 6;

                event.addListener(new ImageButton(
                        x,
                        y,
                        buttonSize,
                        buttonSize,
                        new WidgetSprites(MAIN_MENU_ICON, MAIN_MENU_ICON),
                        button -> {
                            Minecraft minecraft = Minecraft.getInstance();
                            minecraft.setScreen(PiShockClothConfigScreen.create(pauseScreen));
                        },
                        Component.literal("PiShock Setup")
                ));
            }
        }

        @SubscribeEvent
        public static void onScreenRender(ScreenEvent.Render.Post event) {
            PiShockClothConfigScreen.onScreenRender(event);
        }
    }

}
