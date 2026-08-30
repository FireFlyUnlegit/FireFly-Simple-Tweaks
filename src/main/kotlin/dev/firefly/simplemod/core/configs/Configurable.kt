package dev.firefly.simplemod.core.configs

import java.io.File
import java.util.Properties

open class Configurable(open val name: String) {
    private val props = Properties()
    private val configFile = File("config/simplemod/${name}.properties")
    private val _values = mutableListOf<Value<*>>()
    val values: List<Value<*>> = _values

    init {
        configFile.parentFile.mkdirs()
        load()
    }

    protected fun <T : Value<*>> addValue(value: T): T {
        _values.add(value)
        value.owner = this
        return value
    }

    fun int(name: String, value: Int, min: Int = 0, max: Int = Int.MAX_VALUE) =
        addValue(IntValue(name, value, min, max))

    fun float(name: String, value: Float, min: Float = 0f, max: Float = Float.MAX_VALUE) =
        addValue(FloatValue(name, value, min, max))

    fun boolean(name: String, value: Boolean = false) =
        addValue(BoolValue(name, value))

    fun choices(name: String, values: Array<String>, default: String) =
        addValue(ChoiceValue(name, values.toList(), default))

    fun save() {
        _values.forEach { props.setProperty(it.name, it.toText()) }
        props.store(configFile.outputStream(), "Config for $name")
    }

    fun load() {
        if (!configFile.exists()) return
        props.load(configFile.inputStream())
        _values.forEach { value ->
            props.getProperty(value.name)?.let { value.fromText(it) }
        }
    }

    operator fun get(valueName: String): Value<*>? = _values.find { it.name.equals(valueName, ignoreCase = true) }
}