package dev.firefly.simplemod.core.config;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Config(modid = "simplemod",name = "GeneralSettings",category = "GeneralSettings")

public class GeneralConfig {
    @Config.Name("Cancel Vanilla Damage Indicator")
    @Config.Comment("Cancel vanilla Damage-Indicator to reduce particles spawn")
    public static boolean cancelVanillaDamageIndicator = true;
}
@Mod.EventBusSubscriber(modid = "simplemod")
class GeneralConfigSync {
    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (event.getModID().equals("simplemod")) {
            ConfigManager.sync("simplemod", Config.Type.INSTANCE);
        }
    }
}
