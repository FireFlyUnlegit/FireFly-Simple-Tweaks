package dev.firefly.simplemod.core.config;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Config(modid = "simplemod",name = "DamageIndicator",category = "DamageIndicator")
public class DamageIndicatorConfig {
    @Config.Name("Enable Damage Indicator")
    @Config.Comment("Whether to show damage numbers when attacking mobs")
    @Config.RequiresMcRestart
    public static boolean enabled = true;

    @Config.Name("Display Duration (Ticks)")
    @Config.Comment("How long damage numbers stay visible (20 ticks = 1 second)")
    @Config.RangeInt(min = 10, max = 100)
    public static int duration = 40;

    @Config.Name("Rise Duration (Ticks)")
    @Config.Comment("How many ticks the number rises before stopping (10 ticks = 0.5 seconds)")
    @Config.RangeInt(min = 5, max = 30)
    public static int riseDuration = 10;

    @Config.Name("Max Distance")
    @Config.Comment("Maximum distance to show damage numbers")
    @Config.RangeInt(min = 16, max = 128)
    public static int maxDistance = 64;

    @Config.Name("Scale")
    @Config.Comment("Base scale of damage numbers")
    @Config.RangeDouble(min = 0.5, max = 2.0)
    public static double scale = 1.2;

    @Config.Name("Percentage Mode")
    @Config.Comment("Show damage and health as percentage instead of absolute values")
    public static boolean percentageMode = false;

    @Config.Name("Show Text Shadow")
    @Config.Comment("Show shadow behind damage numbers for better readability")
    public static boolean showShadow = true;
    @Config.Name("yStartFactor")
    @Config.Comment("The y-position factor of number")
    @Config.RangeDouble(min = 0.5, max = 2.0)
    public static double yStartFactor = 1.0;

}

@Mod.EventBusSubscriber(modid = "simplemod")
class DamageIndicatorConfigSync {
    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (event.getModID().equals("simplemod")) {
            ConfigManager.sync("simplemod", Config.Type.INSTANCE);
        }
    }
}