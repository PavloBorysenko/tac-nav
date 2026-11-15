package com.tacnav.sapper

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tacnav.sapper.activation.ActivationApi
import com.tacnav.sapper.activation.ActivationPayload
import com.tacnav.sapper.activation.ActivationRejectedException
import com.tacnav.sapper.domain.ActivationResult
import com.tacnav.sapper.domain.ActiveContext
import com.tacnav.sapper.domain.CatalogIcon
import com.tacnav.sapper.domain.Coordinate
import com.tacnav.sapper.domain.Geometry
import com.tacnav.sapper.domain.MapScope
import com.tacnav.sapper.domain.SapperSettings
import com.tacnav.sapper.domain.TeamScope
import com.tacnav.sapper.game.ClearProgress
import com.tacnav.sapper.game.Counters
import com.tacnav.sapper.game.Fix
import com.tacnav.sapper.game.GameState
import com.tacnav.sapper.game.Laying
import com.tacnav.sapper.game.PendingWrite
import com.tacnav.sapper.security.PinAttempt
import com.tacnav.sapper.security.PinPolicy
import com.tacnav.sapper.security.SapperRepository
import com.tacnav.sapper.security.SecureStateStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private class FakeActivation(var reference: SapperSettings = SapperSettings(), var pin: String = "2468") : ActivationApi {
    var refuse = false

    override suspend fun activate(payload: ActivationPayload): ActivationResult {
        if (refuse) throw ActivationRejectedException("Refused.")
        return ActivationResult(
            serverUrl = payload.serverUrl,
            applicationId = 9,
            installationId = "12",
            bearerCredential = "bearer-${payload.oneTimeCode}",
            maps = listOf(MapScope(4, "Map", listOf(TeamScope(7, "Blue"), TeamScope(8, "Red")))),
            defaultMapId = 4,
            defaultTeamId = 7,
            referenceSettings = reference,
            settingsPin = pin,
        )
    }

    override suspend fun fetchIconCatalog(activation: ActivationResult): List<CatalogIcon> = emptyList()
}

@RunWith(AndroidJUnit4::class)
class StorageTest {
    private val store = SecureStateStore(ApplicationProvider.getApplicationContext())
    private val api = FakeActivation()
    private val payload = ActivationPayload("https://tacnav.example", "CODE")

    // A fast, fixed clock keeps the PIN delay predictable.
    private fun repository() = SapperRepository(store, api, PinPolicy(now = { 1_000L }))

    @Before
    @After
    fun clean() = store.clear()

    @Test
    fun gameStateRoundTripsThroughTheEncryptedStore() {
        val context = ActiveContext(4, 7)
        val square = Geometry.Polygon(listOf(Coordinate(50.0, 30.0), Coordinate(50.0, 30.1), Coordinate(50.1, 30.1)))
        val state = GameState(
            counters = Counters(2, 5),
            laying = Laying("lay-1", Fix(50.0, 30.0, 3.5, 99L), 40.0, 120_000, locked = true),
            clearProgress = listOf(ClearProgress(31, square, 90_000)),
            markProgress = mapOf(41L to 20_000L),
            outbox = listOf(
                PendingWrite.CreateMinefield("lay-0", context, square, 13),
                PendingWrite.CreateNoMines("tap-1", context, Coordinate(50.0, 30.0), 4.0, null),
                PendingWrite.MarkField("m-1", context, 31, Coordinate(50.0, 30.0), listOf(8, 7), "t1"),
                PendingWrite.ExpireField("c-1", context, 32),
            ),
        )
        store.saveGame(state)
        assertEquals(state, store.loadGame())
    }

    @Test
    fun activationReplacesSettingsAndPinAndClearsProgress() = runTest {
        api.reference = SapperSettings(detectRadiusMetres = 15.0, maxMinefields = 2)
        val repository = repository()
        repository.activate(payload)
        repository.saveSettings(SapperSettings(detectRadiusMetres = 30.0))
        store.saveGame(GameState(counters = Counters(minefields = 2)))

        repository.activate(payload.copy(oneTimeCode = "NEW"))

        val state = repository.state.value!!
        assertEquals(15.0, state.settings.detectRadiusMetres, 0.0)
        assertEquals("bearer-NEW", state.activation.bearerCredential)
        assertNull(store.loadGame())
        assertEquals(PinAttempt.Accepted, repository.verifyPin("2468"))
    }

    @Test
    fun refusedActivationKeepsTheCurrentOne() = runTest {
        val repository = repository()
        repository.activate(payload)
        api.refuse = true
        try {
            repository.activate(payload.copy(oneTimeCode = "BAD"))
            fail("Activation must be refused.")
        } catch (error: ActivationRejectedException) {
            assertEquals("bearer-CODE", repository.state.value!!.activation.bearerCredential)
            assertEquals("bearer-CODE", SapperRepository(store, api).state.value!!.activation.bearerCredential)
        }
    }

    @Test
    fun defaultPinOpensSettingsBeforeActivationAndFailuresDelay() {
        val repository = repository()
        assertEquals(PinAttempt.Accepted, repository.verifyPin("4321"))
        repeat(4) { assertTrue(repository.verifyPin("0000") is PinAttempt.Rejected) }
        assertTrue(repository.verifyPin("0000") is PinAttempt.Throttled)
        assertTrue(repository.verifyPin("4321") is PinAttempt.Throttled)
    }

    @Test
    fun resetConnectionForgetsEverything() = runTest {
        val repository = repository()
        repository.activate(payload)
        store.saveGame(GameState(counters = Counters(minefields = 1)))
        repository.resetConnection()
        assertNull(repository.state.value)
        assertNull(store.load())
        assertNull(store.loadGame())
        assertEquals(PinAttempt.Accepted, repository.verifyPin("4321"))
    }
}
