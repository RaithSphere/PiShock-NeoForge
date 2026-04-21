package net.raith.pishock;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = PiShock.MOD_ID)
public class PiShockConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<PiShock.PiShockMode> PISHOCK_MODE = BUILDER
            .comment("PiShock mode", "SHOCK is the intended mode for this mod")
            .defineEnum("PiShock_Mode", PiShock.PiShockMode.Shock);

    public static final ModConfigSpec.BooleanValue PISHOCK_ENABLED = BUILDER
            .comment("Master enable switch for API calls")
            .define("PiShock_Enabled", false);

    public static final ModConfigSpec.ConfigValue<String> PISHOCK_USERNAME = BUILDER
            .comment("PiShock username", "Required for websocket broker auth")
            .define("PiShock_Username", "");

    public static final ModConfigSpec.ConfigValue<String> PISHOCK_SHOCKER_ID = BUILDER
            .comment("ShockerId to operate", "Optional: auto-discovered from /Shockers when blank")
            .define("PiShock_ShockerId", "");

    public static final ModConfigSpec.IntValue PISHOCK_HUB_ID = BUILDER
            .comment("HubId used to build websocket target channel", "Optional: auto-discovered from /Shockers when -1")
            .defineInRange("PiShock_HubId", -1, -1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue PISHOCK_USER_ID = BUILDER
            .comment("Your PiShock user ID", "Optional: auto-discovered from /Account when -1")
            .defineInRange("PiShock_UserId", -1, -1, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<String> PISHOCK_LOG_IDENTIFIER = BUILDER
            .comment("Log identifier shown in PiShock logs")
            .define("PiShock_LogIdentifier", "Minecraft");

    public static final ModConfigSpec.BooleanValue PISHOCK_SHOW_SUCCESS_CONFIRMATION = BUILDER
            .comment("Show in-game chat confirmation when a shock command is acknowledged by the broker")
            .define("PiShock_ShowSuccessConfirmation", false);

    public static final ModConfigSpec.BooleanValue PISHOCK_DEBUG = BUILDER
            .comment("Enable verbose PiShock debug logging")
            .define("PiShock_Debug", false);

    public static final ModConfigSpec.BooleanValue PISHOCK_TRIGGER_ON_DEATH = BUILDER
            .comment("Trigger on death")
            .define("PiShock_TriggerOnDeath", true);

    public static final ModConfigSpec.ConfigValue<String> PISHOCK_APIKEY = BUILDER
            .comment("PiShock API key", "Used for websocket auth and discovery REST calls")
            .define("PiShock_API", "");

    public static final ModConfigSpec.IntValue PISHOCK_DURATION = BUILDER
            .comment("Shock duration in milliseconds", "Valid range: 100 to 15000")
            .defineInRange("PiShock_Duration", 1000, 100, 15000);

    public static final ModConfigSpec.BooleanValue PISHOCK_COMBINE_DAMAGE_EVENTS = BUILDER
            .comment("Combine rapid damage events into one queued shock")
            .define("PiShock_CombineDamageEvents", true);

    public static final ModConfigSpec.IntValue PISHOCK_COMBINE_WINDOW_MS = BUILDER
            .comment("Milliseconds to wait for more damage before combining")
            .defineInRange("PiShock_CombineWindowMs", 250, 0, 5000);

    public static final ModConfigSpec.BooleanValue PISHOCK_QUEUE_ENABLED = BUILDER
            .comment("Queue shocks and send them sequentially")
            .define("PiShock_QueueEnabled", true);

    public static final ModConfigSpec.IntValue PISHOCK_QUEUE_MAX_SIZE = BUILDER
            .comment("Maximum queued shock entries")
            .defineInRange("PiShock_QueueMaxSize", 32, 1, 512);

    public static final ModConfigSpec.IntValue PISHOCK_INTENSITY = BUILDER
            .comment("Maximum intensity", "Intensity of the punishment. Must be an integer from 1 to 100.")
            .defineInRange("PiShock_DeathIntensity", 80, 1, 100);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static void save() {
        SPEC.save();
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // Intentionally empty; NeoForge loads values into static config holders.
    }
}
