package erl.bilico.ui.components

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val SPRING_VALUE_THRESHOLD = 0.75f
private const val SPRING_VELOCITY_THRESHOLD = SPRING_VALUE_THRESHOLD * 62.5
private const val SPRING_UNBOUNDED = 2.14748365E9f

/** `COUISpringAnimation` 的逐帧实现：值有上下界，速度继续积分，停在边界上等力反向。 */
internal class CouiSpring(
    initialValue: Float,
    bounce: Float,
    response: Float,
) {
    var value: Float = initialValue
        private set
    var velocity: Float = 0f
    var isRunning: Boolean = false
        private set
    var minValue: Float = -SPRING_UNBOUNDED
    var maxValue: Float = SPRING_UNBOUNDED

    private var target: Float = initialValue
    private var pendingTarget: Float = Float.NaN
    private var dampingRatio: Double = 0.0
    private var naturalFrequency: Double = 0.0

    init {
        configure(bounce, response)
    }

    fun configure(bounce: Float, response: Float) {
        dampingRatio = (1f - bounce).toDouble()
        naturalFrequency = 2.0 * PI / response
    }

    fun setStart(start: Float) {
        value = start
    }

    fun animateTo(finalPosition: Float) {
        if (isRunning) {
            pendingTarget = finalPosition
        } else {
            target = finalPosition
            isRunning = true
        }
    }

    fun snapTo(newValue: Float) {
        isRunning = false
        pendingTarget = Float.NaN
        value = newValue
        target = newValue
        velocity = 0f
    }

    fun step(deltaSeconds: Double): Boolean {
        if (!isRunning) return false
        var position = value.toDouble()
        var speed = velocity.toDouble()
        if (!pendingTarget.isNaN()) {
            val half = deltaSeconds / 2.0
            integrate(position, speed, half) { p, v ->
                position = p
                speed = v
            }
            target = pendingTarget
            pendingTarget = Float.NaN
            integrate(position, speed, half) { p, v ->
                position = p
                speed = v
            }
        } else {
            integrate(position, speed, deltaSeconds) { p, v ->
                position = p
                speed = v
            }
        }
        value = min(max(position.toFloat(), minValue), maxValue)
        velocity = speed.toFloat()
        val atRest = abs(velocity.toDouble()) < SPRING_VELOCITY_THRESHOLD &&
            abs((value - target).toDouble()) < SPRING_VALUE_THRESHOLD
        if (atRest) {
            value = target
            velocity = 0f
            isRunning = false
        }
        return atRest
    }

    private inline fun integrate(
        position: Double,
        speed: Double,
        seconds: Double,
        result: (Double, Double) -> Unit,
    ) {
        val displacement = position - target
        val zeta = dampingRatio
        val omega = naturalFrequency
        if (zeta == 1.0) {
            val a = speed + omega * displacement
            val b = displacement + a * seconds
            val decay = exp(-omega * seconds)
            result(b * decay + target, a * decay - omega * b * decay)
        } else {
            val damped = omega * sqrt(1.0 - zeta * zeta)
            val a = (zeta * omega * displacement + speed) / damped
            val decay = exp(-zeta * omega * seconds)
            val oscillation = decay * (cos(damped * seconds) * displacement + sin(damped * seconds) * a)
            val nextSpeed = -omega * oscillation * zeta +
                decay * (-damped * displacement * sin(damped * seconds) + a * damped * cos(damped * seconds))
            result(oscillation + target, nextSpeed)
        }
    }
}
