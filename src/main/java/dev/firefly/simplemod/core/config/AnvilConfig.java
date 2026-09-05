package dev.firefly.simplemod.core.config;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Config(modid = "simplemod", category = "anvil", name = "Anvil")
public class AnvilConfig {

    @Config.Name("Disable Anvil Cost Limit")
    @Config.Comment("Remove the 'Too Expensive' limit on anvils, allowing any enchantment cost")
    @Config.RequiresMcRestart
    public static boolean disableAnvilCostLimit = true;
    @Config.Name("Max Anvil Cost")
    @Config.Comment("Maximum anvil cost before showing 'Too Expensive' (only applies if limit is enabled)")
    @Config.RangeInt(min = 1)
    public static int maxAnvilCost = Integer.MAX_VALUE;
}

@Mod.EventBusSubscriber(modid = "simplemod")
class AnvilConfigSync {
    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (event.getModID().equals("simplemod")) {
            ConfigManager.sync("simplemod", Config.Type.INSTANCE);
        }
    }
}