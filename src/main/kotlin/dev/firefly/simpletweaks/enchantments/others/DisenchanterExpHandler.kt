package dev.firefly.simpletweaks.enchantments.others

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.disenchanter.DisenchanterLogic
import net.minecraft.init.SoundEvents
import net.minecraft.util.SoundCategory
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent

object DisenchanterExpHandler : Listenable {

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val p = e.player
        if (p.world.isRemote) return

        var gained = 0

        for (stack in p.inventory.mainInventory) {
            val exp = DisenchanterLogic.readExpTag(stack)
            if (exp > 0) {
                gained += exp
                DisenchanterLogic.clearExpTag(stack)
            }
        }
        for (stack in p.inventory.armorInventory) {
            val exp = DisenchanterLogic.readExpTag(stack)
            if (exp > 0) {
                gained += exp
                DisenchanterLogic.clearExpTag(stack)
            }
        }
        val off = p.heldItemOffhand
        if (!off.isEmpty) {
            val exp = DisenchanterLogic.readExpTag(off)
            if (exp > 0) {
                gained += exp
                DisenchanterLogic.clearExpTag(off)
            }
        }

        if (gained > 0) {
            if (!p.capabilities.isCreativeMode) {
                p.addExperienceLevel(1)
            }
            p.addExperience(gained)

            p.world.playSound(
                null, p.posX, p.posY, p.posZ,
                SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                SoundCategory.PLAYERS,
                1f, 1f,
            )
        }
    }
}