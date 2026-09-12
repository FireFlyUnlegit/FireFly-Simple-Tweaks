package dev.firefly.simplemod.commands

import net.minecraft.command.CommandBase
import net.minecraft.command.CommandException
import net.minecraft.command.ICommandSender
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.server.MinecraftServer
import net.minecraft.util.math.BlockPos
import net.minecraft.util.text.TextComponentString

class CommandAttribute : CommandBase() {

    override fun getName(): String = "attribute"

    override fun getRequiredPermissionLevel(): Int = 2

    override fun getUsage(sender: ICommandSender): String =
        "/attribute <target> <attribute> <get|set|add> [value]"

    override fun execute(server: MinecraftServer, sender: ICommandSender, args: Array<String>) {
        if (args.size < 3) throw CommandException("commands.generic.usage", getUsage(sender))

        val target = getEntity(server, sender, args[0]) as? EntityLivingBase
            ?: throw CommandException("Target must be a living entity")

        val instance = target.attributeMap.getAttributeInstanceByName(args[1])
            ?: throw CommandException("Unknown attribute: ${args[1]}")

        val attrName = instance.attribute.name

        when (args[2].lowercase()) {
            "get" -> sender.sendMessage(TextComponentString(
                "${target.name}: $attrName = ${instance.attributeValue} " +
                        "(base ${instance.baseValue}, default ${instance.attribute.defaultValue})"
            ))

            "set" -> {
                if (args.size < 4) throw CommandException("Missing value")
                val value = parseDouble(args[3])
                instance.baseValue = value
                sender.sendMessage(TextComponentString(
                    "Set $attrName of ${target.name} to $value (now ${instance.attributeValue})"
                ))
            }

            "add" -> {
                if (args.size < 4) throw CommandException("Missing value")
                val delta = parseDouble(args[3])
                val old = instance.baseValue
                instance.baseValue = old + delta
                sender.sendMessage(TextComponentString(
                    "Added $delta to $attrName of ${target.name}: $old -> ${instance.baseValue}"
                ))
            }

            else -> throw CommandException("Unknown action: ${args[2]} (use get/set/add)")
        }
    }

    override fun getTabCompletions(
        server: MinecraftServer,
        sender: ICommandSender,
        args: Array<String>,
        targetPos: BlockPos?
    ): List<String> = when (args.size) {
        1 -> {
            val candidates = server.onlinePlayerNames.toMutableList()
            val looked = getLookedEntity(sender)
            if (looked != null) candidates.add(0, looked.uniqueID.toString())
            getListOfStringsMatchingLastWord(args, candidates)
        }
        2 -> getListOfStringsMatchingLastWord(args, COMMON_ATTRIBUTES)
        3 -> getListOfStringsMatchingLastWord(args, listOf("get", "set", "add"))
        else -> emptyList()
    }

    private fun getLookedEntity(sender: ICommandSender): Entity? {
        val player = sender as? EntityPlayerMP ?: return null
        val world = player.world
        val eyes = player.getPositionEyes(1f)
        val reach = 32.0
        val rawEnd = eyes.add(player.lookVec.scale(reach))

        val blockHit = world.rayTraceBlocks(eyes, rawEnd, false, true, false)
        val end = blockHit?.hitVec ?: rawEnd
        val maxDistance = eyes.distanceTo(end)

        var closest: Entity? = null
        var closestDistance = Double.MAX_VALUE

        val searchBox = player.entityBoundingBox.grow(reach)
        for (entity in world.getEntitiesWithinAABBExcludingEntity(player, searchBox)) {
            if (!entity.canBeCollidedWith()) continue
            val box = entity.entityBoundingBox.grow(entity.collisionBorderSize.toDouble())
            val hit = box.calculateIntercept(eyes, end) ?: continue
            val distance = eyes.distanceTo(hit.hitVec)
            if (distance > maxDistance) continue
            if (distance < closestDistance) {
                closest = entity
                closestDistance = distance
            }
        }
        return closest
    }

    companion object {
        private val COMMON_ATTRIBUTES = listOf(
            "generic.maxHealth",
            "generic.followRange",
            "generic.knockbackResistance",
            "generic.movementSpeed",
            "generic.flyingSpeed",
            "generic.attackDamage",
            "generic.attackSpeed",
            "generic.armor",
            "generic.armorToughness",
            "generic.luck",
            "generic.reachDistance",
        )
    }
}