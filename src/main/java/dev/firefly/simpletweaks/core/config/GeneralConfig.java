package dev.firefly.simpletweaks.core.config;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Config(modid = "simple_tweaks",name = "GeneralSettings",category = "GeneralSettings")

public class GeneralConfig {
    @Config.Name("Cancel Vanilla Damage Indicator")
    @Config.Comment("Cancel vanilla Damage-Indicator to reduce particles spawn")
    public static boolean cancelVanillaDamageIndicator = true;
    @Config.Name("Disable Anvil Cost Limit")
    @Config.Comment("Remove the 'Too Expensive' limit on anvils, allowing any enchantment cost")
    public static boolean disableAnvilCostLimit = true;
    @Config.Name("Max Anvil Cost")
    @Config.Comment("Maximum anvil cost before showing 'Too Expensive' (only applies if limit is enabled)")
    @Config.RangeInt(min = 1)
    public static int maxAnvilCost = Integer.MAX_VALUE;

    @Config.Name("Disable Enchantment Table Enchantability Limit")
    @Config.Comment("Remove the max limit of EnchantmentTable's Enchantability")
    public static boolean disableEnchantmentTableLimit = true;
    @Config.Name("Max Enchantment Table Power")
    @Config.Comment(
            "Maximum enchantment power used by the enchanting table. " +
                    "Vanilla value is 15 (30 levels max with 15 bookshelves). " +
                    "Higher values scale the displayed enchantment levels proportionally."
    )
    @Config.RangeInt(min = 1)
    public static int maxEnchantmentPower = 15;
    @Config.Name("Enabled Enchantments' Color")
    @Config.Comment(
            "Let Enchantments have themselves display color."
    )
    public static boolean enabledEnchantmentColor = true;
    @Config.Name("Enabled Enchantments' Special Particles")
    @Config.Comment(
            "Let some enchantments have themselves special particles"
    )
    public static boolean enabledSpecialParticles = true;

    @Config.Name("Enabled Enchantments' Special Particles")
    @Config.Comment(
            "Let some enchantments have themselves special particles"
    )
    public static boolean anvilDisenchant = true;

}
@Mod.EventBusSubscriber(modid = "simple_tweaks")
class GeneralConfigSync {
    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (event.getModID().equals("simple_tweaks")) {
            ConfigManager.sync("simple_tweaks", Config.Type.INSTANCE);
        }
    }
}
