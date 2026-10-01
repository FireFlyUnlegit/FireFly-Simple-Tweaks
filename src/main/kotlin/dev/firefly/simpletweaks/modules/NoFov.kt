package dev.firefly.simpletweaks.modules

import dev.firefly.simpletweaks.core.Module

object NoFov : Module("NoFOV", "Render", ) {
    val fov = float("Fov",90f,30f,120f)
}
