package dev.firefly.simplemod

import dev.firefly.simplemod.core.CommandManager
import dev.firefly.simplemod.core.EnchantmentManager
import dev.firefly.simplemod.core.ModuleManager
import dev.firefly.simplemod.core.config.DamageIndicatorConfig
import dev.firefly.simplemod.core.config.GeneralConfig
import dev.firefly.simplemod.damageindicator.DamageIndicatorHandler
import dev.firefly.simplemod.damageindicator.DamageIndicatorRenderer
import dev.firefly.simplemod.gui.ModGuiScreen
import dev.firefly.simplemod.network.NetworkManager
import net.minecraft.client.Minecraft
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import net.minecraftforge.fml.common.event.FMLServerStartingEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.InputEvent
import org.apache.logging.log4j.LogManager
import org.lwjgl.input.Keyboard
import java.io.File
import java.util.Properties

@Mod(modid = SimpleMod.MOD_ID, name = SimpleMod.NAME, version = SimpleMod.VERSION)
class SimpleMod {
    companion object {
        const val MOD_ID = "simplemod"
        const val NAME = "Simple Optimization Mod"
        const val VERSION = "1.0.2"
        val LOGGER = LogManager.getLogger(NAME)

        private val configFile = File("config/simplemod/gui.properties")
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