package dev.firefly.simplemod.core.configs

import java.io.File
import java.util.Properties

abstract class Value<T>(
    val name: String,
    var value: T,
    val suffix: String? = null,
    val description: String? = null
) {
    var owner: Configurable? = null

    open fun toText(): String = value.toString()
    open fun fromText(text: String) {}

    fun set(newValue: T) {
        if (newValue == value) return
        value = newValue
        owner?.save()
    }

    fun get(): T = value

    open fun shouldRender(): Boolean = true
}

// ========== BoolValue ==========

class BoolValue(
    name: String,
    value: Boolean = false,
    description: String? = null
) : Value<Boolean>(name, value, description = description) {

    override fun fromText(text: String) {
        value = text.lowercase() in listOf("true", "t", "yes", "y", "1")
    }

    fun toggle() {
        set(!value)
    }

    override fun toText(): String = value.toString()
}

// ========== IntValue ==========

class IntValue(
    name: String,
    value: Int,
    val min: Int = 0,
    val max: Int = 100,
    suffix: String? = null,
    description: String? = null
) : Value<Int>(name, value, suffix, description) {

    override fun fromText(text: String) {
        value = text.toIntOrNull()?.coerceIn(min, max) ?: value
    }

    override fun toText(): String = value.toString()
}

// ========== FloatValue ==========

class FloatValue(
    name: String,
    value: Float,
    val min: Float = 0f,
    val max: Float = 100f,
    suffix: String? = null,
    description: String? = null
) : Value<Float>(name, value, suffix, description) {

    override fun fromText(text: String) {
        value = text.toFloatOrNull()?.coerceIn(min, max) ?: value
    }

    override fun toText(): String = value.toString()
}


class ChoiceValue(
    name: String,
    val choices: List<String>,
    value: String,
    description: String? = null
) : Value<String>(name, value, description = description) {

    override fun fromText(text: String) {
        value = choices.find { it.equals(text, ignoreCase = true) } ?: value
    }

    override fun toText(): String = value

    fun next(): String {
        val currentIndex = choices.indexOf(value)
        val nextIndex = (currentIndex + 1) % choices.size
        return choices[nextIndex]
    }
}
