package net.raith.pishock;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = PiShock.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class PiShockConfig
{
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<PiShock.PiShockMode> PISHOCK_MODE = BUILDER
            .comment("PiSHock Mode", "SHOCK is the intended mode for this mod")
            .defineEnum("PiShock_Mode", PiShock.PiShockMode.Shock);

    public static final ModConfigSpec.ConfigValue<String> PISHOCK_USERNAME = BUILDER
            .comment("PiShock Username", "Username you use to log into PiShock.com")
            .define("PiShock_Username", "API EXAMPLE");

    public static final ModConfigSpec.ConfigValue<String> PISHOCK_CODE = BUILDER
            .comment("PiShock Code", "Sharecode generated on PiShock.com. Limitations can be set when generating the code.")
            .define("PiShock_Code", "123456789");

    public static final ModConfigSpec.BooleanValue PISHOCK_TRIGGER_ON_DEATH = BUILDER
            .comment("Trigger On Death?")
            .define("PiShock_TriggerOnDeath", true);

    public static final ModConfigSpec.ConfigValue<String> PISHOCK_APIKEY = BUILDER
            .comment("PiShock API Key", "API Key generated on PiShock.com Can be found in the Account section of the website.")
            .define("PiShock_API", "API KEY HERE");

    public static final ModConfigSpec.IntValue PISHOCK_DURATION = BUILDER
            .comment("Duration timer")
            .defineInRange("PiShock_Duration", 5, 1, 15
            );

    public static final ModConfigSpec.IntValue PISHOCK_INTENSITY = BUILDER
            .comment("Death Intensity", "Intensity of the punishment shock. Must be a integer between 1 and 100.")
            .defineInRange("PiShock_DeathIntensity", 80, 1, 100);

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
