package net.raith.pishock;

import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
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

import java.util.concurrent.CompletableFuture;

@Mod(PiShock.MOD_ID)
public class PiShock {

    public static final String MOD_ID = "pishock";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PiShock(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.register(this);
        modContainer.registerConfig(ModConfig.Type.COMMON, PiShockConfig.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {

    }

    @SubscribeEvent
    public void onDamage(LivingIncomingDamageEvent e) {
        if (!e.getEntity().level().isClientSide() && e.getEntity() instanceof Player player) {

            Player p = (Player) e.getEntity();

            float damage = e.getAmount();
            String who = p.getScoreboardName();
            float now = p.getHealth();
            float max = p.getMaxHealth();
            int isAlive = p.isAlive() ? 1 : 0;

            // Log the damage for debugging
            LOGGER.info("Player {} took {} damage. Current health: {}/{}", who, damage, now, max);

            CompletableFuture.runAsync(() -> {
                ShockHandler.shock(damage, now, max, isAlive, player);
            });
        }
    }
    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof Player player) {

            float damage = player.getHealth();
            float now = player.getHealth();
            float max = player.getMaxHealth();
            int isAlive = player.isAlive() ? 1 : 0;

            LOGGER.info("Player {} has died. Damage taken: {}, Current health: {}/{}", player.getScoreboardName(), damage, now, max);

            CompletableFuture.runAsync(() -> {
                ShockHandler.shock(damage, now, max, isAlive, player);
            });
        }
    }
    @EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {

        }
    }
}
