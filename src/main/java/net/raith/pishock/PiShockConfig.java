package net.raith.pishock;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
@EventBusSubscriber(modid = PiShock.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class PiShockConfig
{
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue PISHOCK_MODE = BUILDER
            .comment("PiSHock Mode")
            .defineInRange("PiShock_Mode", 0, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<String> PISHOCK_USERNAME = BUILDER
            .comment("PiShock Username")
            .define("PiShock_Username", "API EXAMPLE");

    public static final ModConfigSpec.ConfigValue<String> PISHOCK_CODE = BUILDER
            .comment("PiShock Code")
            .define("PiShock_Code", "123456789");

    public static final ModConfigSpec.BooleanValue PISHOCK_TRIGGER = BUILDER
            .comment("Trigger On Death?")
            .define("PiShock_TriggerOnDeath", true);

    public static final ModConfigSpec.ConfigValue<String> PISHOCK_APIKEY = BUILDER
            .comment("PiShock API Key")
            .define("PiShock_API", "API KEY HERE");

    public static final ModConfigSpec.IntValue PISHOCK_COOLDOWN = BUILDER
            .comment("Cooldown timer")
            .defineInRange("PiShock_Cooldown", 2, 2, 60
            );

    public static final ModConfigSpec.DoubleValue PISHOCK_INTENSITY = BUILDER
            .comment("Death Intensity")
            .defineInRange("PiShock_DeathIntensity", 80d, 1d, 100d);

    static final ModConfigSpec SPEC = BUILDER.build();



    private static boolean validateItemName(final Object obj)
    {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {

    }
}
