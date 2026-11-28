package dev.firefly.simpletweaks.core.config

/**
 * Settings for the floating bar drawn above mobs by `client/MobInfoBarRenderer`.
 *
 * [enabled] and [hideVanillaName] are also read by `LivingEntityRenderNameMixin`: the bar carries the
 * name, so the vanilla name tag must step aside or every mob shows its name twice.
 */
object MobInfoBarConfig {

    enum class HpText { NONE, ABSOLUTE, PERCENT }

    @JvmStatic
    var enabled: Boolean = true

    @JvmStatic
    var hpText: HpText = HpText.PERCENT

    @JvmStatic
    var showArmor: Boolean = true

    @JvmStatic
    var showPlayers: Boolean = false

    @JvmStatic
    var hideVanillaName: Boolean = true

    /** Opacity of the dark panel behind the whole widget, 0..100; 0 disables the panel. */
    @JvmStatic
    var backgroundOpacity: Int = 35

    @JvmStatic
    var maxDistance: Double = 24.0

    /** Bar size in percent of the design size; 100 puts the 80 px track at roughly one block. */
    @JvmStatic
    var scalePercent: Int = 100
}
