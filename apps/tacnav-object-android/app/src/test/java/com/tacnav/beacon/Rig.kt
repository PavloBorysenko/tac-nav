package com.tacnav.beacon

import com.tacnav.beacon.domain.ActiveContext
import com.tacnav.beacon.domain.Geodesy
import com.tacnav.beacon.domain.ObjectSettings
import com.tacnav.beacon.game.Clock
import com.tacnav.beacon.game.Environment
import com.tacnav.beacon.game.Fix
import com.tacnav.beacon.game.GameState
import com.tacnav.beacon.game.ObjectMachine
import com.tacnav.beacon.game.SignalKind
import com.tacnav.beacon.game.Transition

val CONTEXT = ActiveContext(4, 7)
const val HOME_LATITUDE = 50.0
const val HOME_LONGITUDE = 30.0

/** Drives the machine with fake clocks and a fake GPS. */
class Rig(
    var settings: ObjectSettings = ObjectSettings(objectName = "Antenna"),
    var heading: Double? = 0.0,
    draws: () -> Double = { 0.5 },
) {
    var elapsed = 1_000L
    var epoch = 1_700_000_000_000L
    private var keys = 0
    val machine = ObjectMachine(draws) { "key-${++keys}" }
    var state = GameState("key-0")
    val signals = mutableListOf<SignalKind>()
    var message: String? = null

    val env: Environment
        get() = Environment(settings, CONTEXT, heading)

    val clock: Clock
        get() = Clock(elapsed, epoch)

    init {
        step(machine.tick(state, env, clock))
    }

    fun step(transition: Transition) {
        state = transition.state
        signals += transition.signals
        transition.message?.let { message = it }
    }

    fun fix(north: Double = 0.0, accuracy: Double = 5.0) {
        val point = Geodesy.destination(HOME_LATITUDE, HOME_LONGITUDE, 0.0, north)
        step(
            machine.onFix(
                state,
                env,
                Fix(point.latitude, point.longitude, accuracy, epoch),
                clock,
            ),
        )
    }

    fun tick(millis: Long = 0) {
        elapsed += millis
        epoch += millis
        step(machine.tick(state, env, clock))
    }

    /** Passes time second by second with a good fix at one position. */
    fun run(millis: Long, north: Double = 0.0, accuracy: Double = 5.0) {
        var left = millis
        while (left > 0) {
            val slice = minOf(left, 1_000L)
            tick(slice)
            fix(north, accuracy)
            left -= slice
        }
    }

    fun activate() {
        step(machine.activate(state, env, clock))
    }

    fun deactivate() {
        step(machine.deactivate(state, env))
    }

    /** Good fix, then Activate. */
    fun deploy() {
        fix()
        activate()
    }

    /** Passes time with the process gone: no ticks reach the machine. */
    fun powerOff(millis: Long, rebooted: Boolean = true) {
        epoch += millis
        elapsed = if (rebooted) 500L else elapsed + millis
    }

    fun restore() {
        step(machine.restore(state, env, clock))
    }
}
