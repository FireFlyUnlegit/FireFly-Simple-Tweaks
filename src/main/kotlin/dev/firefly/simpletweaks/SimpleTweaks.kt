package dev.firefly.simpletweaks

import dev.firefly.simpletweaks.client.tooltips.ManaPoolToolTipHandler
import dev.firefly.simpletweaks.core.CommandManager
import dev.firefly.simpletweaks.core.EnchantmentManager
import dev.firefly.simpletweaks.core.ModuleManager
import dev.firefly.simpletweaks.damageindicator.DamageIndicatorHandler
import dev.firefly.simpletweaks.damageindicator.DamageIndicatorRenderer
import dev.firefly.simpletweaks.enchantments.others.AnvilCostHandler
import dev.firefly.simpletweaks.gui.ModGuiScreen
import dev.firefly.simpletweaks.network.NetworkManager
import net.minecraft.client.Minecraft
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import net.minecraftforge.fml.common.event.FMLServerStartingEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.InputEvent
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.lwjgl.input.Keyboard
import java.io.File
import java.util.*

@Mod(modid = SimpleTweaks.MOD_ID, name = SimpleTweaks.NAME, version = SimpleTweaks.VERSION)
class SimpleTweaks {
    companion object {
        const val MOD_ID = "simple_tweaks"
        const val NAME = "FireFly's Simple Tweaks"
        const val VERSION = "1.0.5"
        val LOGGER: Logger = LogManager.getLogger(NAME)

        private val configFile = File("config/simpletweaks/gui.properties")
        private val props = Properties()
        var guiKey: Int = Keyboard.KEY_RSHIFT
            private set

        init {
            loadGuiConfig()
        }

        private fun loadGuiConfig() {
            if (!configFile.exists()) {
                configFile.parentFile.mkdirs()
                saveGuiConfig()
                return
            }
            props.load(configFile.inputStream())
            guiKey = props.getProperty("guiKey", Keyboard.KEY_RSHIFT.toString()).toIntOrNull() ?: Keyboard.KEY_RSHIFT
        }

        fun saveGuiConfig() {
            props.setProperty("guiKey", guiKey.toString())
            props.store(configFile.outputStream(), "GUI Configuration")
        }

        fun setGuiKey(key: Int) {
            guiKey = key
            saveGuiConfig()
            LOGGER.info("GUI Open Key was changed to: ${Keyboard.getKeyName(key)}")
        }
    }

    @Mod.EventHandler
    fun preInit(event: FMLPreInitializationEvent) {
        LOGGER.info("{} Loading...", NAME)
        ModuleManager.registerModules()
        NetworkManager.registerPackets()
        EnchantmentManager.registerEnchantments()
        MinecraftForge.EVENT_BUS.register(DamageIndicatorRenderer)
        MinecraftForge.EVENT_BUS.register(DamageIndicatorHandler)
        MinecraftForge.EVENT_BUS.register(ManaPoolToolTipHandler)
        MinecraftForge.EVENT_BUS.register(AnvilCostHandler)
        CommandManager.registerCommands()
        LOGGER.info("GUI Open Key: ${Keyboard.getKeyName(guiKey)}")
    }

    @Mod.EventHandler
    fun onServerStarting(e: FMLServerStartingEvent) {
        CommandManager.onServerStarting(e)
    }
    @Mod.EventHandler
    fun init(event: FMLInitializationEvent) {
        LOGGER.info("{} Load Completed!", NAME)
        MinecraftForge.EVENT_BUS.register(this)
    }

    @SubscribeEvent
    fun onKeyInput(event: InputEvent.KeyInputEvent) {
        if (Keyboard.isKeyDown(guiKey) && Minecraft.getMinecraft().currentScreen == null) {
            Minecraft.getMinecraft().displayGuiScreen(ModGuiScreen())
        }
    }
}