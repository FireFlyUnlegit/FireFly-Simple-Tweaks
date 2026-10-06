package dev.firefly.simpletweaks.enchantments.handlers.epic

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.BlockEvent
import dev.firefly.simpletweaks.compat.event.EventPriority
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.ModEnchantmentKeys
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import kotlin.math.abs

/**
 * 1.21 port of `enchantments/handlers/epic/EnchantTunnelingHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                                   | 1.21.1                                                                 |
 * |----------------------------------------------------------|------------------------------------------------------------------------|
 * | `net.minecraftforge...BlockEvent.BreakEvent`             | `compat.event.BlockEvent.BreakEvent` (bridged from Fabric's `PlayerBlockBreakEvents.BEFORE`) |
 * | `player.world.isRemote`                                  | `WorldSide.isClient(player.world)` (field_9236 is a FIELD)              |
 * | `player.heldItemMainhand`                                | `player.mainHandStack` (method_6047)                                    |
 * | `player.lookVec`                                         | `player.getRotationVec(1.0f)` (Yarn `getRotationVec(float)`, method_5826) — 1.12.2's no-arg `getLookVec()` was `getRotationVec(1.0f)` |
 * | `EnumFacing.Axis`                                        | `net.minecraft.util.math.Direction.Axis`                                |
 * | `state.block` / `state.getBlockHardness(world, pos)`     | `state.block` / `state.getHardness(world, pos)` (on `AbstractBlockState`) |
 * | `state.block.hasTileEntity(state)`                       | `state.hasBlockEntity()`                                                |
 * | `world.getTileEntity(pos)`                               | `world.getBlockEntity(pos)`                                             |
 * | `world.playEvent(2001, pos, Block.getStateId(state))`    | `world.syncWorldEvent(2001, pos, Block.getRawIdFromState(state))` (`WorldAccess.syncWorldEvent`, method_20274) |
 * | `world.setBlockToAir(pos)`                               | `world.removeBlock(pos, false)` (method_8652; `false` = no `MOVED` flag, the `setBlockToAir` behaviour) |
 * | `state.block.harvestBlock(world, player, pos, state, te, tool)` | `Block.dropStacks(state, world, pos, te, player, tool)` (`method_9565`) — the 1.21 entry point for "break this block and drop its loot as this player" |
 * | `tool.damageItem(1, player)`                             | `tool.damage(1, player, EquipmentSlot.MAINHAND)` (method_7970)          |
 * | `EnchantTunneling` (Enchantment)                         | `ModEnchantmentKeys.TUNNELING` (RegistryKey)                            |
 *
 * The 3-level offset masks, the `inTunneling` re-entrancy guard, the "same block, finite hardness, no
 * block entity" filters and the added 1-durability cost per extra block are unchanged.
 *
 * Note: `e.player ?: return` is kept from the 1.12.2 source even though this port's `BreakEvent.player`
 * is declared non-null — the guard is inert, not a behaviour change.
 */
object EnchantTunnelingHandler : Listenable {

    private const val MAX_LEVEL = 3
    private val inTunneling = ThreadLocal.withInitial { false }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onBreak(e: BlockEvent.BreakEvent) {
        if (inTunneling.get()) return
        val player = e.player ?: return
        if (WorldSide.isClient(player.world)) return
        if (player.isSneaking) return

        val tool = player.mainHandStack
        val lvl = getItemSpecificEnchantLevel(tool, ModEnchantmentKeys.TUNNELING)
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
        var broken = 0
        try {
            for (off in offsets) {
                val p = origin.add(off)
                val state = world.getBlockState(p)
                if (state.block != originBlock) continue
                if (state.getHardness(world, p) < 0f) continue
                if (state.hasBlockEntity()) continue

                val te = world.getBlockEntity(p)
                world.syncWorldEvent(2001, p, Block.getRawIdFromState(state))
                world.removeBlock(p, false)
                Block.dropStacks(state, world, p, te, player, tool)

                tool.damage(1, player, EquipmentSlot.MAINHAND)
                broken++
                STLog.log("Tunneling") {
                    "player=${player.name.string}, lvl=$lvl, origin=$origin, normal=$normal, block=$originBlock, " +
                        "broke=$p, brokenSoFar=$broken, toolDamage=${tool.damage}"
                }
                if (tool.isEmpty) break
            }
        } finally {
            inTunneling.set(false)
            if (broken > 0) {
                STLog.log("Tunneling") {
                    "player=${player.name.string}, lvl=$lvl, origin=$origin, extraBlocks=$broken, outcome=done"
                }
            }
        }
    }

    private fun digNormalAxis(player: PlayerEntity): Direction.Axis {
        val look = player.getRotationVec(1.0f)
        val ax = abs(look.x)
        val ay = abs(look.y)
        val az = abs(look.z)
        return when {
            ay >= ax && ay >= az -> Direction.Axis.Y
            ax >= az -> Direction.Axis.X
            else -> Direction.Axis.Z
        }
    }

    private fun planeAxes(normal: Direction.Axis): Pair<Direction.Axis, Direction.Axis> {
        return when (normal) {
            Direction.Axis.X -> Direction.Axis.Y to Direction.Axis.Z
            Direction.Axis.Y -> Direction.Axis.X to Direction.Axis.Z
            Direction.Axis.Z -> Direction.Axis.Y to Direction.Axis.X
        }
    }

    private fun offsets(lvl: Int, u: Direction.Axis, v: Direction.Axis): List<BlockPos> {
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

    private fun axisVec(axis: Direction.Axis, n: Int): BlockPos = when (axis) {
        Direction.Axis.X -> BlockPos(n, 0, 0)
        Direction.Axis.Y -> BlockPos(0, n, 0)
        Direction.Axis.Z -> BlockPos(0, 0, n)
    }
}
