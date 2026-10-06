package dev.firefly.simpletweaks.enchantments.handlers.epic

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.epic.EnchantTunneling
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.block.Block
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Blocks
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraftforge.event.world.BlockEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import kotlin.math.abs

object EnchantTunnelingHandler : Listenable {

    private const val MAX_LEVEL = 3
    private val inTunneling = ThreadLocal.withInitial { false }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onBreak(e: BlockEvent.BreakEvent) {
        if (inTunneling.get()) return
        val player = e.player ?: return
        if (player.world.isRemote) return
        if (player.isSneaking) return

        val tool = player.heldItemMainhand
        val lvl = getItemSpecificEnchantLevel(tool, EnchantTunneling)
        if (lvl <= 0) return

        val world = player.world
        val origin = e.pos
        val originState = world.getBlockState(origin)
        val originBlock = originState.block
        if (originBlock == Blocks.AIR) return

        val normal = digNormalAxis(player)
        val (u, v) = planeAxes(normal)
        val offsets = offsets(lvl.coerceAtMost(MAX_LEVEL), u, v)

        inTunneling.set(true)
        try {
            for (off in offsets) {
                val p = origin.add(off)
                val state = world.getBlockState(p)
                if (state.block != originBlock) continue
                if (state.getBlockHardness(world, p) < 0f) continue
                if (state.block.hasTileEntity(state)) continue

                val te = world.getTileEntity(p)
                world.playEvent(2001, p, Block.getStateId(state))
                world.setBlockToAir(p)
                state.block.harvestBlock(world, player, p, state, te, tool)

                tool.damageItem(1, player)
                if (tool.isEmpty) break
            }
        } finally {
            inTunneling.set(false)
        }
    }

    private fun digNormalAxis(player: EntityPlayer): EnumFacing.Axis {
        val look = player.lookVec
        val ax = abs(look.x)
        val ay = abs(look.y)
        val az = abs(look.z)
        return when {
            ay >= ax && ay >= az -> EnumFacing.Axis.Y
            ax >= az -> EnumFacing.Axis.X
            else -> EnumFacing.Axis.Z
        }
    }

    private fun planeAxes(normal: EnumFacing.Axis): Pair<EnumFacing.Axis, EnumFacing.Axis> {
        return when (normal) {
            EnumFacing.Axis.X -> EnumFacing.Axis.Y to EnumFacing.Axis.Z
            EnumFacing.Axis.Y -> EnumFacing.Axis.X to EnumFacing.Axis.Z
            EnumFacing.Axis.Z -> EnumFacing.Axis.Y to EnumFacing.Axis.X
        }
    }

    private fun offsets(lvl: Int, u: EnumFacing.Axis, v: EnumFacing.Axis): List<BlockPos> {
        val result = mutableListOf<BlockPos>()
        when (lvl) {
            1 -> {
                result += axisVec(u, 1)
                result += axisVec(u, -1)
            }
            2 -> {
                for (a in -1..1) for (b in -1..1) {
                    if (a == 0 && b == 0) continue
                    if (abs(a) + abs(b) != 1) continue
                    result += axisVec(u, a).add(axisVec(v, b))
                }
            }
            else -> {
                for (a in -1..1) for (b in -1..1) {
                    if (a == 0 && b == 0) continue
                    result += axisVec(u, a).add(axisVec(v, b))
                }
            }
        }
        return result
    }

    private fun axisVec(axis: EnumFacing.Axis, n: Int): BlockPos = when (axis) {
        EnumFacing.Axis.X -> BlockPos(n, 0, 0)
        EnumFacing.Axis.Y -> BlockPos(0, n, 0)
        EnumFacing.Axis.Z -> BlockPos(0, 0, n)
    }
}