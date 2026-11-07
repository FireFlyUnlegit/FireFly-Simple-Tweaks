package dev.firefly.simpletweaks.client.tooltips

import dev.firefly.simpletweaks.SimpleTweaks
import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.enchantments.handlers.legendary.EnchantComboHandler
import dev.firefly.simpletweaks.enchantments.handlers.mythic.infinitepower.FlightHandler
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.component.EnchantmentEffectComponentTypes
import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.enchantment.effect.AttributeEnchantmentEffect
import net.minecraft.entity.attribute.EntityAttribute
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.item.ItemStack
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.entry.RegistryEntry

/**
 * Folds the attributes this mod's enchantments grant into the **vanilla attribute totals**, so an item
 * reads what it actually does while held:
 *
 * ```text
 * 在主手时：
 * 7 攻击伤害
 * 7.2 攻击速度        <- vanilla's own line, rewritten from `1.6` (combo 10: ×(1 + 3.5))
 * ```
 *
 * ## Why the totals are wrong without this
 * `ItemStack#appendAttributeModifiersTooltip` (bytecode) reads **only**
 * `DataComponentTypes.ATTRIBUTE_MODIFIERS` — the item's own component — and the flat total it prints is
 * `modifier.value() + player.getAttributeBaseValue(attribute)`. Nothing an *enchantment* grants can
 * reach that number, because an enchantment's `minecraft:attributes` effect is applied to the holder
 * at runtime and a `@ModEnchantment`'s code-driven bonus is applied by a handler. Vanilla renders
 * those as their own `+X%` lines inside the enchantment block; the *total* line never moves. That is
 * exactly what "combo 10 and the tooltip still says 1.6" is, and it is structural, not a bug in one
 * enchantment.
 *
 * ## Two sources, deliberately kept distinct
 * | source | example | how it is found |
 * |---|---|---|
 * | **declarative** | `prismatic_blessing`'s 17 `AttributeSpec`s | read from the live enchantment registry: `EnchantmentEffectComponentTypes.ATTRIBUTES`, evaluated at the enchantment's level |
 * | **code-driven** | `combo`'s attack speed | a handler applies it every tick, so the tooltip asks for it — see [CODE_DRIVEN] |
 *
 * The declarative half is read from the same place the game reads it, so a change to an `AttributeSpec`
 * is reflected with no second list to maintain (§4.10's hazard). The code-driven half exists because
 * that bonus is **not** in the JSON — and writing a second copy of the constant here is precisely the
 * drift the project keeps getting bitten by, so each provider lives next to the handler that applies
 * it and this file only asks for the number.
 *
 * ## How a bonus is applied (the maths, from `EntityAttributeInstance#computeValue`)
 * Bytecode order: `d = base; d += Σ ADD_VALUE; d += base × Σ ADD_MULTIPLIED_BASE; d ×= (1 + Σ
 * ADD_MULTIPLIED_TOTAL)`. The printed total already equals the item's own contribution, so the new
 * number is
 *
 * ```text
 * (displayed + Σ ADD_VALUE + base × Σ ADD_MULTIPLIED_BASE) × (1 + Σ ADD_MULTIPLIED_TOTAL)
 * ```
 *
 * `base` is the attribute's base value, which is what `ADD_MULTIPLIED_BASE` scales against — read from
 * the local player when there is one (the same source vanilla used to build the line), falling back to
 * the attribute's registered default. There is no player in the Fabric tooltip context
 * (`Item.TooltipContext` exposes only a registry lookup — bytecode), which is why the fallback exists.
 *
 * ## Deliberate restrictions
 * 1. **The item must declare the attribute in its own `ATTRIBUTE_MODIFIERS`** ([TooltipTotals.
 *    declaresAttribute]). Without that gate an enchanted **book** — whose `STORED_ENCHANTMENTS`
 *    `EnchantmentHelper.getEnchantments` happily returns — would get numbers for attributes it does not
 *    have. This is also what bounds the blast radius: in vanilla 1.21.1 a *flat total* line is only
 *    ever printed for attack damage and attack speed (the only ids
 *    `appendAttributeModifierTooltip` treats as a total), so the fold can only reach those two lines.
 * 2. **This mod's namespace only**, and **entries carrying `requirements` are skipped** — same
 *    reasoning as the damage fold: a tooltip has no `LootContext` to evaluate a conditional entry with.
 *    (Only the `minecraft:damage` component has such wrappers; `ATTRIBUTES` is a bare list, see the
 *    comment at the read site.)
 * 3. **No fallback line.** When there is no total line to correct, nothing is printed rather than
 *    inventing a line: the declarative bonuses are already visible as `+X%` lines in the enchantment
 *    block, and a separate absolute line for a code-driven bonus would be a new display element nobody
 *    asked for. (The damage fold does have a fallback, because the author asked for one — checklist §X5.)
 * 4. **Conditional bonuses are not represented, on purpose.** `celestial_blessing` grants
 *    `0.75 × level` attack speed only while its mana pool is non-empty, and the total line is a static
 *    property of the item; folding it in would print a number that is wrong most of the time. Same for
 *    any future state-dependent aura.
 * 5. **`infinite_power` is not "conditional", it is `+1000`.** [FlightHandler] applies an absolute
 *    `+1000` attack speed while the item is held, and the item must carry the enchantment for that to
 *    happen, so it *is* deterministic per item and is folded in — a held `infinite_power` sword will
 *    read about `1001.6 攻击速度`. That number is honest; see FlightHandler's KDoc.
 *
 * ## Ordering against [EnchantDamageToolTipHandler]
 * Both handlers rewrite the same `attribute.modifier.equals.0` line for attack damage, each reading the
 * current value and adding its own share. That composes because the operations involved are all
 * `ADD_VALUE` and independent of the running total — `ADD_MULTIPLIED_TOTAL` is only ever used for
 * attack speed by the code above (combo), and nothing else in this mod multiplies attack damage, so no
 * share can be scaled by a total that another handler has already changed.
 */
object EnchantAttributeToolTipHandler {

    /**
     * One attribute bonus, expressed exactly as the vanilla attribute would express it.
     *
     * @param value the raw modifier value; how it combines is decided by [operation].
     */
    private class Contribution(
        val attribute: RegistryEntry<EntityAttribute>,
        val operation: EntityAttributeModifier.Operation,
        val value: Double,
    )

    /**
     * A bonus applied by a handler rather than declared in the enchantment JSON.
     *
     * @param enchantment the enchantment whose presence on the stack arms this provider
     * @param bonuses the contributions at a given level; return an empty list for a level it applies
     *   nothing at
     */
    private class CodeDrivenBonus(
        val enchantment: RegistryKey<Enchantment>,
        val bonuses: (Int) -> List<Contribution>,
    )

    /**
     * The complete list of code-driven attribute bonuses in this mod.
     *
     * **This is the one place to add an entry** when a handler starts granting an attribute outside the
     * JSON. Each provider delegates to the handler that applies the modifier, so the number is defined
     * once (the constant lives with the code that uses it, not here).
     *
     * Deliberately absent: `celestial_blessing` (mana-conditional, see restriction 4),
     * `heavenly_punishment` (it debuffs the *victim*, so it is never a property of the hovered item).
     */
    private val CODE_DRIVEN: List<CodeDrivenBonus> = listOf(
        CodeDrivenBonus(GeneratedEnchantments.COMBO) { level ->
            listOf(
                Contribution(
                    EntityAttributes.GENERIC_ATTACK_SPEED,
                    EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                    EnchantComboHandler.attackSpeedBonus(level),
                )
            )
        },
        CodeDrivenBonus(GeneratedEnchantments.INFINITE_POWER) { level ->
            listOf(
                Contribution(
                    EntityAttributes.GENERIC_ATTACK_SPEED,
                    EntityAttributeModifier.Operation.ADD_VALUE,
                    FlightHandler.attackSpeedBonus(level),
                )
            )
        },
    )

    fun register() {
        ItemTooltipCallback.EVENT.register { stack, _, _, lines ->
            if (stack.isEmpty) return@register

            val contributions = collectContributions(stack)
            if (contributions.isEmpty()) return@register

            for ((attribute, bonuses) in contributions) {
                val attributeKey = attribute.value().translationKey

                if (!TooltipTotals.declaresAttribute(stack, attribute)) {
                    FSTLog.log("EnchantAttributeTooltip") {
                        "attribute=$attributeKey, contributions=${bonuses.size}, " +
                            "outcome=skipped-item-declares-no-such-attribute"
                    }
                    continue
                }

                val total = TooltipTotals.findTotalLine(lines, attributeKey)
                if (total == null) {
                    FSTLog.log("EnchantAttributeTooltip") {
                        "attribute=$attributeKey, contributions=${bonuses.size}, " +
                            "outcome=skipped-no-flat-total-line"
                    }
                    continue
                }

                val numberIndex = total.content.args.indexOfFirst {
                    TooltipTotals.parseFlatNumber(TooltipTotals.numberOf(it)) != null
                }
                if (numberIndex < 0) continue
                val displayed = TooltipTotals.parseFlatNumber(TooltipTotals.numberOf(total.content.args[numberIndex]))
                    ?: continue

                val merged = applyOperations(displayed, baseValue(attribute), bonuses)
                if (!TooltipTotals.replaceNumber(lines, total, TooltipTotals.format(merged.toFloat()))) continue

                FSTLog.log("EnchantAttributeTooltip") {
                    val detail = bonuses.joinToString("+") { "${it.operation.name}:${it.value}" }
                    "attribute=$attributeKey, displayed=${TooltipTotals.format(displayed)}, " +
                        "base=${baseValue(attribute)}, contributions=[$detail], " +
                        "shown=${TooltipTotals.format(merged.toFloat())}, line=${total.lineIndex}, " +
                        "sibling=${total.siblingIndex}, outcome=folded-into-vanilla-total"
                }
            }
        }
    }

    /**
     * Applies [bonuses] to [displayed], which is vanilla's printed total for the item.
     *
     * Mirrors `EntityAttributeInstance#computeValue` (bytecode): `ADD_VALUE` and `ADD_MULTIPLIED_BASE`
     * are both relative to the **base**, and `ADD_MULTIPLIED_TOTAL` scales the result.
     */
    private fun applyOperations(
        displayed: Float,
        base: Double,
        bonuses: List<Contribution>,
    ): Double {
        var addValue = 0.0
        var multipliedBase = 0.0
        var multipliedTotal = 0.0
        for (bonus in bonuses) {
            when (bonus.operation) {
                EntityAttributeModifier.Operation.ADD_VALUE -> addValue += bonus.value
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE -> multipliedBase += bonus.value
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL -> multipliedTotal += bonus.value
            }
        }
        return (displayed + addValue + base * multipliedBase) * (1.0 + multipliedTotal)
    }

    /**
     * The attribute's base value — what `ADD_MULTIPLIED_BASE` is measured against.
     *
     * Read from the local player when there is one, because that is the exact value vanilla used to
     * build the line being rewritten. Without a player (no world loaded) this falls back to the
     * attribute's registered default, which is the same number for every attribute vanilla prints a
     * total for, since the game does not move those base values off their defaults.
     */
    private fun baseValue(attribute: RegistryEntry<EntityAttribute>): Double =
        MinecraftClient.getInstance().player?.getAttributeBaseValue(attribute)
            ?: attribute.value().defaultValue

    /**
     * Every attribute contribution this mod's enchantments on [stack] account for, grouped by attribute.
     *
     * Reads the stack's enchantments through `EnchantmentHelper.getEnchantments`, i.e. incl. an
     * enchanted book's `STORED_ENCHANTMENTS` — the caller's [TooltipTotals.declaresAttribute] gate is
     * what keeps that honest (see restriction 1).
     */
    private fun collectContributions(stack: ItemStack): Map<RegistryEntry<EntityAttribute>, List<Contribution>> {
        val enchantments = EnchantmentHelper.getEnchantments(stack)
        if (enchantments.isEmpty) return emptyMap()

        val out = mutableListOf<Contribution>()

        for (entry in enchantments.enchantments) {
            val key = entry.key.orElse(null) ?: continue
            if (key.value.namespace != SimpleTweaks.MOD_ID) continue

            val level = enchantments.getLevel(entry)
            if (level <= 0) continue

            // (a) declarative: the `minecraft:attributes` block vanilla itself applies.
            //
            // ⚠️ The element type is a **bare** `AttributeEnchantmentEffect`, *not* an
            // `EnchantmentEffectEntry<…>` wrapper — unlike `DAMAGE`, which *is* wrapped. Bytecode:
            //   DAMAGE     = ComponentType<List<EnchantmentEffectEntry<EnchantmentValueEffect>>>
            //   ATTRIBUTES = ComponentType<List<AttributeEnchantmentEffect>>
            // Consequence: there is no per-entry `requirements` here and nothing to filter, so the
            // loop reads the effect directly. (Getting this wrong is easy: `javap`'s output wraps, and
            // the wrapped form is visually identical to the DAMAGE line. The compiler disagreed with
            // the wrapped reading, which is why the explicit type below is worth keeping.)
            val effects: List<AttributeEnchantmentEffect>? =
                entry.value().effects().get(EnchantmentEffectComponentTypes.ATTRIBUTES)
            if (effects != null) {
                for (attributeEffect in effects) {
                    val amount = attributeEffect.amount().getValue(level).toDouble()
                    if (amount == 0.0) continue
                    out += Contribution(attributeEffect.attribute(), attributeEffect.operation(), amount)
                }
            }

            // (b) code-driven: applied by a handler every tick, so it has to be asked for by name.
            for (provider in CODE_DRIVEN) {
                if (!entry.matchesKey(provider.enchantment)) continue
                out += provider.bonuses(level)
            }
        }

        return out.groupBy { it.attribute }
    }
}
