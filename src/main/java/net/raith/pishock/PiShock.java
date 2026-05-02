package net.raith.pishock;

import com.mojang.logging.LogUtils;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.player.Player;
import net.minecraft.commands.Commands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ClientRegistry;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.VersionChecker;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.raith.pishock.client.PiShockClothConfigScreen;
import net.raith.pishock.client.PiShockCompat;
import net.raith.pishock.network.ShockCore;
import net.raith.pishock.network.ShockHandler;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Mod(PiShock.MOD_ID)
public class PiShock {

    public static final String MOD_ID = "pishock";
    public static final Logger LOGGER = LogUtils.getLogger();
    // Minecraft moved a few client APIs between releases.
    // PiShockCompat keeps those details out of the common mod code.
    private static final KeyMapping TOGGLE_KEY = PiShockCompat.createToggleKey();

    @Nullable
    private static Float previousHP = null;
    private static boolean deathShocked = false;
    private static final LinkedBlockingQueue<ShockRequest> SHOCK_QUEUE = new LinkedBlockingQueue<>();
    private static final AtomicBoolean QUEUE_WORKER_STARTED = new AtomicBoolean(false);

    private static float pendingDamageRatio = 0.0F;
    private static long pendingDamageLastEventAtMs = -1L;
    private static final AtomicLong SHOCK_VISUAL_UNTIL_MS = new AtomicLong(0L);

    public PiShock() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(ClientModEvents::onClientSetup);

        // Register only once; onHurt uses @SubscribeEvent.
        MinecraftForge.EVENT_BUS.register(this);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, PiShockConfig.SPEC);
    }

    @SubscribeEvent
    public void onHurt(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

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
        return ShockCore.computeIntensityFromDamageRatio(damageRatio, PiShockConfig.PISHOCK_INTENSITY.get());
    }

    private static void clearPendingDamage() {
        pendingDamageRatio = 0.0F;
        pendingDamageLastEventAtMs = -1L;
    }

    private static void queueShock(int intensity, int durationMs) {
        int clampedIntensity = Math.max(1, Math.min(100, intensity));
        int clampedDurationMs = Math.max(100, Math.min(15000, durationMs));

        synchronized (SHOCK_QUEUE) {
            // "Queue disabled" still goes through the worker so damage ticks never block on API calls.
            if (!PiShockConfig.PISHOCK_QUEUE_ENABLED.get() && !SHOCK_QUEUE.isEmpty()) {
                return;
            }

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

    public enum PiShockTransport {
        WebSocket,
        Serial
    }

    private record ShockRequest(int intensity, int durationMs) {
    }

    public static void markShockVisual(int durationMs) {
        long now = System.currentTimeMillis();
        int clampedDurationMs = Math.max(100, Math.min(15000, durationMs));
        SHOCK_VISUAL_UNTIL_MS.accumulateAndGet(now + clampedDurationMs, Math::max);
    }

    public static boolean isShockVisualActive() {
        return System.currentTimeMillis() < SHOCK_VISUAL_UNTIL_MS.get();
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        Utils.unilog("PiShock initialized");
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
    public static class ClientModEvents {
        private static final int UPDATE_CHECK_MAX_TICKS = 600;
        private static final int UPDATE_CHECK_RETRY_TICKS = 20;
        private static boolean updateNotificationDone = false;
        private static int updateCheckTicksRemaining = 0;
        private static int updateCheckRetryTicks = 0;

        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> ClientRegistry.registerKeyBinding(TOGGLE_KEY));
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
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }

            while (TOGGLE_KEY.consumeClick()) {
                boolean enabled = !PiShockConfig.PISHOCK_ENABLED.get();
                PiShockConfig.PISHOCK_ENABLED.set(enabled);
                PiShockConfig.save();
                Utils.sendToMinecraftChat(enabled
                        ? "[PiShock] Quick toggle: Enabled"
                        : "[PiShock] Quick toggle: Disabled");
            }

            showUpdateNotificationWhenReady();
        }

        @SubscribeEvent
        public static void onClientPlayerLoggingIn(ClientPlayerNetworkEvent.LoggedInEvent event) {
            if (updateNotificationDone) {
                return;
            }

            updateCheckTicksRemaining = UPDATE_CHECK_MAX_TICKS;
            updateCheckRetryTicks = 0;
        }

        private static void showUpdateNotificationWhenReady() {
            Minecraft minecraft = Minecraft.getInstance();
            if (updateNotificationDone || minecraft.player == null || updateCheckTicksRemaining <= 0) {
                return;
            }

            updateCheckTicksRemaining--;
            if (updateCheckRetryTicks > 0) {
                updateCheckRetryTicks--;
                return;
            }

            VersionChecker.CheckResult result = ModList.get()
                    .getModContainerById(MOD_ID)
                    .map(container -> VersionChecker.getResult(container.getModInfo()))
                    .orElse(null);
            if (result == null) {
                updateNotificationDone = true;
                return;
            }
            switch (result.status()) {
                case OUTDATED, BETA_OUTDATED -> {
                    Utils.sendToMinecraftChat("[PiShock] Update available: " + result.target()
                            + ". Download: " + result.url());
                    updateNotificationDone = true;
                }
                case PENDING -> updateCheckRetryTicks = UPDATE_CHECK_RETRY_TICKS;
                default -> updateNotificationDone = true;
            }
        }

        @SubscribeEvent
        public static void onScreenInit(ScreenEvent.InitScreenEvent.Post event) {
            PiShockCompat.onScreenInit(event);
        }

        @SubscribeEvent
        public static void onScreenRender(ScreenEvent.DrawScreenEvent.Post event) {
            PiShockClothConfigScreen.onScreenRender(event);
        }
    }

}
