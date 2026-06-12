package io.github.landwarderer.futon.settings.about

import android.graphics.drawable.Drawable
import nl.dionsegijn.konfetti.core.Angle
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.Rotation
import nl.dionsegijn.konfetti.core.Spread
import nl.dionsegijn.konfetti.core.emitter.Emitter
import nl.dionsegijn.konfetti.core.models.Shape
import nl.dionsegijn.konfetti.core.models.Size
import java.util.concurrent.TimeUnit

class Presets {
    companion object {
        fun festive(drawable: Shape.DrawableShape? = null): List<Party> {
            val party = Party(
                speed = 30f,
                maxSpeed = 50f,
                damping = 0.9f,
                angle = Angle.TOP,
                spread = 45,
                size = if (drawable != null) listOf(Size(24), Size(32)) else listOf(Size.SMALL, Size.MEDIUM),
                shapes = if (drawable != null) listOf(drawable) else listOf(Shape.Square, Shape.Circle),
                timeToLive = 3000L,
                rotation = Rotation(multiplier3D = 0f),
                colors = listOf(0xfce18a, 0xff726d, 0xf4306d, 0xb48def),
                emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(30),
                position = Position.Relative(0.5, 1.0)
            )

            return listOf(
                party,
                party.copy(
                    speed = 55f,
                    maxSpeed = 65f,
                    spread = 10,
                    emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(10),
                ),
                party.copy(
                    speed = 50f,
                    maxSpeed = 60f,
                    spread = 120,
                    emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(40),
                ),
                party.copy(
                    speed = 65f,
                    maxSpeed = 80f,
                    spread = 10,
                    emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(10),
                )
            )
        }

        fun explode(drawable: Shape.DrawableShape? = null): List<Party> {
            return listOf(
                Party(
                    speed = 0f,
                    maxSpeed = 30f,
                    damping = 0.9f,
                    spread = 360,
                    size = if (drawable != null) listOf(Size(24), Size(32)) else listOf(Size.SMALL, Size.MEDIUM),
                    shapes = if (drawable != null) listOf(drawable) else listOf(Shape.Square, Shape.Circle),
                    colors = listOf(0xfce18a, 0xff726d, 0xf4306d, 0xb48def),
                    emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(100),
                    rotation = Rotation(multiplier3D = 0f),
                    position = Position.Relative(0.5, 0.3)
                )
            )
        }

        fun parade(drawable: Shape.DrawableShape? = null): List<Party> {
            val party = Party(
                speed = 10f,
                maxSpeed = 30f,
                damping = 0.9f,
                angle = Angle.RIGHT - 45,
                spread = Spread.SMALL,
                size = if (drawable != null) listOf(Size(24), Size(32)) else listOf(Size.SMALL, Size.MEDIUM),
                shapes = if (drawable != null) listOf(drawable) else listOf(Shape.Square, Shape.Circle),
                colors = listOf(0xfce18a, 0xff726d, 0xf4306d, 0xb48def),
                emitter = Emitter(duration = 5, TimeUnit.SECONDS).perSecond(30),
                rotation = Rotation(multiplier3D = 0f),
                position = Position.Relative(0.0, 0.5)
            )

            return listOf(
                party,
                party.copy(
                    angle = party.angle - 90, // flip angle from right to left
                    position = Position.Relative(1.0, 0.5)
                ),
            )
        }

        fun rain(drawable: Shape.DrawableShape? = null): List<Party> {
            return listOf(
                Party(
                    speed = 0f,
                    maxSpeed = 15f,
                    damping = 0.9f,
                    angle = Angle.BOTTOM,
                    spread = Spread.ROUND,
                    size = if (drawable != null) listOf(Size(24), Size(32)) else listOf(Size.SMALL, Size.MEDIUM),
                    shapes = if (drawable != null) listOf(drawable) else listOf(Shape.Square, Shape.Circle),
                    colors = listOf(0xfce18a, 0xff726d, 0xf4306d, 0xb48def),
                    emitter = Emitter(duration = 5, TimeUnit.SECONDS).perSecond(100),
                    rotation = Rotation(multiplier3D = 0f),
                    position = Position.Relative(0.0, 0.0).between(Position.Relative(1.0, 0.0))
                )
            )
        }
    }
}