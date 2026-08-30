package dev.firefly.simplemod.modules

import dev.firefly.simplemod.core.Module

object NoFov : Module("NoFOV", "Render", ) {
    val fov = float("Fov",90f,30f,120f)
}
