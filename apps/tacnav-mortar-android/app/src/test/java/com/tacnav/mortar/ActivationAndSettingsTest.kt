package com.tacnav.mortar

import com.tacnav.mortar.activation.ActivationPayloadParser
import com.tacnav.mortar.domain.AimingMode
import com.tacnav.mortar.domain.FiringMode
import com.tacnav.mortar.domain.GameplaySettings
import com.tacnav.mortar.domain.MapScope
import com.tacnav.mortar.domain.ScopeRules
import com.tacnav.mortar.domain.TeamScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ActivationAndSettingsTest {
    @Test
    fun `manual and QR activation produce the same contract`() {
        val manual = ActivationPayloadParser.fromManual(
            "https://tacnav.example/",
            "one-time-code",
        )
        val qr = ActivationPayloadParser.parse(
            """{"server_url":"https:\/\/tacnav.example","activation_code":"one-time-code"}""",
        )

        assertEquals(manual, qr)
    }

    @Test
    fun `activation requires HTTPS`() {
        assertThrows(IllegalArgumentException::class.java) {
            ActivationPayloadParser.fromManual("http://tacnav.example", "code")
        }
    }

    @Test
    fun `gameplay defaults match the contract`() {
        val settings = GameplaySettings()

        assertEquals(setOf(FiringMode.BUTTON), settings.firingModes)
        assertEquals(setOf(AimingMode.FREE), settings.aimingModes)
        assertEquals(30.0, settings.approximateRadiusMetres, 0.0)
        assertEquals(60.0, settings.elevationReferenceDegrees, 0.0)
        assertEquals(15.0, settings.maxLocationAccuracyMetres, 0.0)
        assertEquals(15L, settings.maxLocationAgeSeconds)
    }

    @Test
    fun `empty protected mode sets are refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            GameplaySettings(firingModes = emptySet()).validated()
        }
        assertThrows(IllegalArgumentException::class.java) {
            GameplaySettings(aimingModes = emptySet()).validated()
        }
    }

    @Test
    fun `context is limited to map team scopes`() {
        val maps = listOf(
            MapScope(10, "Training", listOf(TeamScope(20, "Blue"))),
        )

        assertEquals(10, ScopeRules.select(maps, 10, 20).mapId)
        assertThrows(IllegalArgumentException::class.java) {
            ScopeRules.select(maps, 10, 99)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ScopeRules.select(maps, 99, 20)
        }
    }
}
