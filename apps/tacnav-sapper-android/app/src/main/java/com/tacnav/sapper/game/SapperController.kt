package com.tacnav.sapper.game

import com.tacnav.sapper.domain.ActiveContext
import com.tacnav.sapper.domain.MINEFIELD_ROLE
import com.tacnav.sapper.domain.MapObject
import com.tacnav.sapper.domain.Plane
import com.tacnav.sapper.domain.SapperSettings
import com.tacnav.sapper.network.AuthorizationLostException
import com.tacnav.sapper.network.ConflictException
import com.tacnav.sapper.network.MapApi
import com.tacnav.sapper.network.ObjectGoneException
import com.tacnav.sapper.network.RejectedException
import java.io.IOException
import kotlin.math.max
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Mode { HOME, DETECT, LAY, CLEAR }

/** Monotonic time for holds, wall time for fix age. */
data class Clock(val elapsedMillis: Long, val epochMillis: Long)

/** Settings and map context of the current activation. */
data class Session(val settings: SapperSettings, val context: ActiveContext)

fun interface GameStore {
    fun saveGame(state: GameState)
}

/** Button actions of the mode screens. */
interface SapperActions {
    fun pressMark()
    fun releaseMark()
    fun placeNoMines()
    fun pressLay()
    fun releaseLay()
    fun resetLaying()
    fun pressClear()
    fun releaseClear()
}

fun interface SignalPort {
    /** Plays [count] short signals on the alarm channel. */
    fun play(count: Int)
}

/** One frame of what the screens show. Distances and directions are never part of it. */
data class SapperView(
    val mode: Mode = Mode.HOME,
    val online: Boolean = true,
    val hasFix: Boolean = false,
    val gpsUsable: Boolean = false,
    val headingKnown: Boolean = false,
    val compassReliable: Boolean = false,
    val level: DetectorLevel = DetectorLevel.NONE,
    val flashSerial: Int = 0,
    val markAvailable: Boolean = false,
    val markPressed: Boolean = false,
    val markFraction: Float = 0f,
    val noMinesOffered: Boolean = false,
    val noMinesLeft: Int? = null,
    val minefieldsLeft: Int? = null,
    val laying: Laying? = null,
    val layPressed: Boolean = false,
    val layFraction: Float = 0f,
    val clearSearched: Boolean = false,
    val clearTargetId: Long? = null,
    val clearPressed: Boolean = false,
    val clearFraction: Float = 0f,
    val safe: Boolean = false,
    val pendingWrites: Int = 0,
    val message: String? = null,
)

class SapperController(
    private val store: GameStore,
    private val api: MapApi,
    private val signals: SignalPort,
    private val session: () -> Session?,
    private val clock: () -> Clock,
    private val newKey: () -> String,
) : SapperActions {
    private var game = GameState()
    private var mode = Mode.HOME
    private var fix: Fix? = null
    private var heading: Double? = null
    private var compassReliable = false
    private var online = true
    private var message: String? = null
    private var flashSerial = 0

    private var level = DetectorLevel.NONE
    private var detectFields: List<MapObject> = emptyList()
    private var detectSearch: SearchMark? = null
    private var markTarget: MapObject? = null
    private var insideAny = false
    private var markHold = MarkHold()

    private var layHold = LayHold()

    private var clearFields: List<MapObject> = emptyList()
    private var clearSearch: SearchMark? = null
    private var clearSearched = false
    private var clearHold = ClearHold()
    private var safe = false

    private var nextWriteAttempt = 0L

    private val mutableView = MutableStateFlow(SapperView())
    val view: StateFlow<SapperView> = mutableView.asStateFlow()

    val state: GameState get() = snapshot()

    /** Restores persisted progress, or starts fresh after an activation. */
    fun load(saved: GameState?) {
        game = saved ?: GameState()
        layHold = LayHold(game.laying)
        clearHold = ClearHold(game.clearProgress.associateBy(ClearProgress::fieldId))
        markHold = MarkHold()
        mode = Mode.HOME
        message = null
        publish()
    }

    fun enter(next: Mode) {
        releaseAll()
        mode = next
        message = null
        safe = false
        level = DetectorLevel.NONE
        if (next == Mode.DETECT) detectSearch = null
        if (next == Mode.CLEAR) {
            clearSearch = null
            clearSearched = false
            clearFields = emptyList()
        }
        publish()
    }

    /** Leaving the screen pauses every hold and keeps its saved progress. */
    fun pause() {
        releaseAll()
        persist()
        publish()
    }

    fun onFix(value: Fix) {
        fix = value
    }

    fun onHeading(value: Double?, reliable: Boolean) {
        heading = value
        compassReliable = reliable
    }

    override fun pressMark() {
        val target = markTarget ?: return
        val current = session() ?: return
        if (!usable(current)) {
            message = LayingMachine.GPS_REQUIRED
        } else {
            markHold = markHold.press(target, clock().elapsedMillis)
        }
        publish()
    }

    override fun releaseMark() {
        markHold = markHold.release()
        publish()
    }

    override fun pressLay() {
        val current = session() ?: return
        val (hold, refusal) = LayingMachine.press(
            layHold,
            fix,
            usable(current),
            heading,
            clock().elapsedMillis,
            Limits.allows(Limits.minefieldsLeft(current.settings, game)),
            newKey,
        )
        layHold = hold
        message = refusal
        persist()
        publish()
    }

    override fun releaseLay() {
        layHold = LayingMachine.release(layHold)
        persist()
        publish()
    }

    override fun resetLaying() {
        layHold = LayingMachine.reset()
        message = null
        persist()
        publish()
    }

    override fun pressClear() {
        val current = session() ?: return
        val position = fix?.coordinate
        val target = position?.let { ClearingMachine.target(clearFields, it) }
        val (hold, refusal) = ClearingMachine.press(clearHold, target, usable(current), clock().elapsedMillis)
        clearHold = hold
        message = refusal
        publish()
    }

    override fun releaseClear() {
        clearHold = ClearingMachine.release(clearHold)
        persist()
        publish()
    }

    override fun placeNoMines() {
        val current = session() ?: return
        val position = fix
        if (position == null || !usable(current)) {
            message = LayingMachine.GPS_REQUIRED
        } else if (!noMinesOffered()) {
            return
        } else if (!Limits.allows(Limits.noMinesLeft(current.settings, game))) {
            message = NO_MARKERS_LEFT
        } else {
            enqueue(
                PendingWrite.CreateNoMines(
                    newKey(),
                    current.context,
                    position.coordinate,
                    position.accuracyMetres,
                    current.settings.noMinesIconId,
                ),
            )
            message = "No mines marker placed."
        }
        publish()
    }

    fun clearMessage() {
        message = null
        publish()
    }

    /** Called about once a second while the application is on screen. */
    suspend fun tick() {
        val current = session() ?: return
        val now = clock()
        val gps = usable(current)
        when (mode) {
            Mode.HOME -> Unit
            Mode.DETECT -> detect(current, now, gps)
            Mode.LAY -> lay(current, now, gps)
            Mode.CLEAR -> clear(current, now, gps)
        }
        publish()
        if (now.epochMillis >= nextWriteAttempt && game.outbox.isNotEmpty()) flush(now)
        publish()
    }

    private suspend fun detect(current: Session, now: Clock, gps: Boolean) {
        val position = fix ?: return
        val step = DetectorMachine.step(
            level,
            detectFields,
            position.coordinate,
            heading,
            current.settings,
            current.context.teamId,
        )
        level = step.level
        markTarget = step.markTarget
        insideAny = step.insideAny
        if (step.signals > 0) {
            signals.play(step.signals)
            flashSerial += 1
        }
        val (hold, marked) = markHold.advance(now.elapsedMillis, markTarget, gps, current.settings)
        markHold = hold
        if (marked != null) {
            enqueue(
                PendingWrite.MarkField(
                    newKey(),
                    current.context,
                    marked.id,
                    Plane.centre(marked.geometry),
                    MarkHold.union(marked.visibleTeamIds, current.context.teamId),
                    marked.updatedAt,
                ),
            )
            // Treat the field as visible at once, so the button disappears.
            detectFields = detectFields.map {
                if (it.id == marked.id) it.copy(visibleTeamIds = MarkHold.union(it.visibleTeamIds, current.context.teamId)) else it
            }
            markTarget = null
            message = "Minefield marked for your team."
        }
        if (SearchPolicy.due(detectSearch, position, now.epochMillis)) {
            val radius = 2 * current.settings.detectRadiusMetres
            search(current.context, position, radius)?.let {
                detectFields = it
                detectSearch = SearchMark(position.coordinate, now.epochMillis)
            }
        }
    }

    private fun lay(current: Session, now: Clock, gps: Boolean) {
        val wasPressed = layHold.pressed
        val (hold, done) = LayingMachine.advance(layHold, now.elapsedMillis, fix, gps, current.settings)
        layHold = hold
        if (hold.laying?.locked == true) message = LayingMachine.MOVED_AWAY
        if (done != null) {
            enqueue(
                PendingWrite.CreateMinefield(
                    done.id,
                    current.context,
                    LayingMachine.geometry(done, current.settings),
                    current.settings.minefieldIconId,
                ),
            )
            message = "Minefield laid."
        } else if (wasPressed || hold.laying?.locked == true) {
            persist()
        }
    }

    private suspend fun clear(current: Session, now: Clock, gps: Boolean) {
        val position = fix ?: return
        val target = ClearingMachine.target(clearFields, position.coordinate)
        val wasPressed = clearHold.pressed
        val (hold, cleared) = ClearingMachine.advance(clearHold, now.elapsedMillis, target, gps, current.settings)
        clearHold = hold
        if (cleared != null) {
            enqueue(PendingWrite.ExpireField(newKey(), current.context, cleared.id))
            clearFields = clearFields.filterNot { it.id == cleared.id }
            safe = true
            message = SAFE
        } else if (wasPressed) {
            persist()
        }
        if (SearchPolicy.due(clearSearch, position, now.epochMillis)) {
            val radius = max(position.accuracyMetres, 1.0)
            search(current.context, position, radius)?.let { found ->
                clearFields = found
                clearSearch = SearchMark(position.coordinate, now.epochMillis)
                clearSearched = true
                val kept = ClearingMachine.afterSearch(clearHold, found, position.coordinate, radius)
                if (kept != clearHold) {
                    clearHold = kept
                    persist()
                }
            }
        }
    }

    /** Null on failure, so the previous results stay in use. */
    private suspend fun search(context: ActiveContext, position: Fix, radius: Double): List<MapObject>? =
        try {
            api.search(context, position.coordinate, radius, MINEFIELD_ROLE).also { online = true }
        } catch (error: AuthorizationLostException) {
            message = error.message
            null
        } catch (error: IOException) {
            online = false
            null
        }

    private suspend fun flush(now: Clock) {
        for (write in game.outbox.toList()) {
            val outcome = send(write)
            if (outcome == null) return
            val (next, note) = Outbox.apply(game, write, outcome)
            game = next
            persist()
            note?.let { message = it }
            if (outcome == WriteOutcome.Failed) {
                online = false
                nextWriteAttempt = now.epochMillis + Outbox.RETRY_MILLIS
                return
            }
            online = true
        }
    }

    /** Null when the activation is lost; the write stays queued. */
    private suspend fun send(write: PendingWrite): WriteOutcome? = try {
        when (write) {
            is PendingWrite.CreateMinefield, is PendingWrite.CreateNoMines -> api.create(write)
            is PendingWrite.MarkField -> api.mark(write)
            is PendingWrite.ExpireField -> api.expire(write)
        }
        WriteOutcome.Accepted
    } catch (error: AuthorizationLostException) {
        message = error.message
        null
    } catch (error: ObjectGoneException) {
        if (write is PendingWrite.MarkField || write is PendingWrite.ExpireField) {
            WriteOutcome.Gone
        } else {
            WriteOutcome.Rejected(error.message ?: "The map is unavailable.")
        }
    } catch (error: ConflictException) {
        refreshed(write)
    } catch (error: RejectedException) {
        WriteOutcome.Rejected(error.message ?: "The server refused the change.")
    } catch (error: IOException) {
        WriteOutcome.Failed
    }

    /** Reads a conflicting field again. Failure to read keeps the write for a retry. */
    private suspend fun refreshed(write: PendingWrite): WriteOutcome {
        val mark = write as? PendingWrite.MarkField ?: return WriteOutcome.Failed
        return try {
            val found = api.search(mark.context, mark.centre, REFRESH_RADIUS_METRES, MINEFIELD_ROLE)
            WriteOutcome.Conflict(found.firstOrNull { it.id == mark.fieldId })
        } catch (error: IOException) {
            WriteOutcome.Failed
        }
    }

    private fun enqueue(write: PendingWrite) {
        game = snapshot().copy(outbox = game.outbox + write)
        persist()
        nextWriteAttempt = 0
    }

    private fun releaseAll() {
        markHold = markHold.release()
        layHold = LayingMachine.release(layHold)
        clearHold = ClearingMachine.release(clearHold)
    }

    private fun usable(current: Session): Boolean =
        fix?.usable(clock().epochMillis, current.settings.maxLocationAccuracyMetres) == true

    private fun noMinesOffered(): Boolean = when (mode) {
        Mode.DETECT -> !insideAny
        Mode.CLEAR -> safe
        else -> false
    }

    private fun snapshot(): GameState = game.copy(
        laying = layHold.laying,
        clearProgress = clearHold.progress.values.toList(),
    )

    private fun persist() {
        game = snapshot()
        store.saveGame(game)
    }

    private fun publish() {
        val current = session()
        val settings = current?.settings ?: SapperSettings()
        val clearTarget = fix?.coordinate?.let { ClearingMachine.target(clearFields, it) }
        mutableView.value = SapperView(
            mode = mode,
            online = online,
            hasFix = fix != null,
            gpsUsable = current != null && usable(current),
            headingKnown = heading != null,
            compassReliable = compassReliable,
            level = level,
            flashSerial = flashSerial,
            markAvailable = mode == Mode.DETECT && markTarget != null,
            markPressed = markHold.pressed,
            markFraction = markHold.fraction(settings),
            noMinesOffered = noMinesOffered(),
            noMinesLeft = Limits.noMinesLeft(settings, game),
            minefieldsLeft = Limits.minefieldsLeft(settings, game),
            laying = layHold.laying,
            layPressed = layHold.pressed,
            layFraction = LayingMachine.fraction(layHold.laying, settings),
            clearSearched = clearSearched,
            clearTargetId = clearTarget?.id,
            clearPressed = clearHold.pressed,
            clearFraction = ClearingMachine.fraction(clearHold, clearTarget?.id, settings),
            safe = safe,
            pendingWrites = game.outbox.size,
            message = message,
        )
    }

    companion object {
        const val SAFE = "This area is safe"
        const val NO_MARKERS_LEFT = "No markers left for this activation."
        const val REFRESH_RADIUS_METRES = 1.0
    }
}
